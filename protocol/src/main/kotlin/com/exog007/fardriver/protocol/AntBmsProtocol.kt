package com.exog007.fardriver.protocol

/**
 * 蚂蚁保护板（ANT BMS）BLE 协议解码
 *
 * 帧结构：
 *   7E A1 <func> <addrLo> <addrHi> <val> | <数据> | CRC | AA 55
 *   └────────── 6 字节头 ──────────┘
 *   数据从 [6] 开始，到倒数第 4 字节结束
 *
 * 已验证的关键字段（数据区偏移 d[]）：
 *   d[0]     权限
 *   d[1]     电池状态
 *   d[2]     温度传感器数量（仅参考，实际按槽位算）
 *   d[3]     串数（最可靠）
 *   d[4..27] 保护位/提示位/推送位
 *   d[28..]  单体电压（2 字节 × 串数，高 3 位是均衡标志）
 *   之后       N 个温度 → MOS 温度 → 总电压 → 电流 → SOC → 容量 → 循环 → 均衡 → 极值
 *
 * 解析策略：不猜偏移，用「单体合计 ≈ 总电压」自校验反推起始位置。
 * 这样任何串数（16S/20S/21S/24S/25S...）都能自动对上。
 */

/** BMS 实时数据 */
data class AntBmsData(
    var cells: List<Double> = emptyList(),      // 单体电压 V
    var cellCount: Int = 0,
    var temps: List<Int> = emptyList(),         // 温度 °C
    var mosTemp: Int = 0,                       // 保护板 MOS 温度
    var totalVoltage: Double = 0.0,             // 总电压 V
    var current: Double = 0.0,                  // 电流 A（负 = 放电）
    var soc: Int = 0,                           // 电量 %
    var capacityTotal: Double = 0.0,            // 总容量 Ah
    var capacityRemaining: Double = 0.0,        // 剩余容量 Ah
    var totalCycleCapacity: Double = 0.0,       // 累计循环容量 Ah
    var cycles: Int = 0,                        // 循环次数
    var balancingActive: Boolean = false,
    var balancing: List<Boolean> = emptyList(),
    var chargeMosOn: Boolean = false,
    var dischargeMosOn: Boolean = false,
    var maxCell: Double = 0.0,
    var minCell: Double = 0.0,
    var maxCellIndex: Int = 0,
    var minCellIndex: Int = 0,
    var chargeRemainMin: Int = 0,
    var dischargeRemainMin: Int = 0,
    var chemistryCode: Int = 0,
    var updatedAt: Long = 0L
) {
    /** 有没有有效数据 */
    val hasData: Boolean get() = cellCount > 0 && cells.isNotEmpty()

    /** 压差 V */
    val cellSpread: Double
        get() = if (cells.isEmpty()) 0.0 else (cells.max() - cells.min())

    /** 平均单体电压 */
    val avgCell: Double
        get() = if (cells.isEmpty()) 0.0 else cells.sum() / cells.size

    /** 电池类型名 */
    val chemistryName: String
        get() = when (chemistryCode) {
            0xFAF1 -> "三元锂"
            0xFAF2 -> "磷酸铁锂"
            0xFAF3 -> "钛酸锂"
            else -> "三元锂"
        }

    /** 健康度（按循环次数） */
    fun soh(designCycles: Int): Double? {
        if (cycles <= 0 || designCycles <= 0) return null
        val lossPerCycle = 100.0 / designCycles
        return (100.0 - cycles * lossPerCycle).coerceIn(0.0, 100.0)
    }
}

object AntBmsBle {
    const val SERVICE_UUID = "0000FFE0-0000-1000-8000-00805F9B34FB"
    const val CHAR_UUID = "0000FFE1-0000-1000-8000-00805F9B34FB"

    /** 官方探针指令（原样取自原厂 app-service.js） */
    val PROBE_STATUS: ByteArray = hexToBytes("7ea1010000f55862aa55")

    fun hexToBytes(s: String): ByteArray {
        val clean = s.replace(" ", "")
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            out[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return out
    }
}

/** BMS 配置（可选，用户手填；留空则全自动） */
data class AntBmsConfig(
    var cellNumber: Int = 0,       // 0 = 自动
    var capacity: Double = 0.0     // 0 = 用 BMS 上报的
) {
    companion object {
        val EMPTY = AntBmsConfig()
    }
}

class AntBmsDecoder(var config: AntBmsConfig = AntBmsConfig.EMPTY) {

    var data = AntBmsData()
        private set

    /** 上次成功解析的串数，串数不会变，用来加速 */
    private var lastGoodCellCount: Int = 0

    /**
     * 处理一帧
     * @return 是否解析成功
     */
    fun process(frame: ByteArray): Boolean {
        if (frame.size < 10) return false
        if (frame[0].toInt() and 0xFF != 0x7E) return false
        if (frame[1].toInt() and 0xFF != 0xA1) return false
        if (frame[frame.size - 2].toInt() and 0xFF != 0xAA) return false
        if (frame[frame.size - 1].toInt() and 0xFF != 0x55) return false

        // 数据区：从 [6] 到倒数第 4 字节
        val dataEnd = frame.size - 4
        if (dataEnd <= 6) return false
        val d = frame.copyOfRange(6, dataEnd)
        if (d.size < 40) return false

        return parseStatus(d)
    }

    private fun parseStatus(d: ByteArray): Boolean {
        val out = AntBmsData()
        out.updatedAt = System.currentTimeMillis()

        // ── ① 自校验定位 ──
        //
        // 串数从 d[3] 读（BMS 自己报），
        // 然后对每个候选起点取 N 节，算合计，往后找总电压，
        // 只有「合计 ≈ 总电压」的起点才是真的。
        val declaredCells = d[3].toInt() and 0xFF
        val cellTarget = if (declaredCells in 4..32) declaredCells else 0

        var cellCount = 0
        var cellStart = 28
        var base = 0
        var resolved = false

        val countCandidates: List<Int> = if (cellTarget > 0) {
            listOf(cellTarget, cellTarget + 1, cellTarget - 1, cellTarget + 2, cellTarget - 2)
        } else {
            (12..28).toList()
        }

        outer@ for (tryCount in countCandidates) {
            if (tryCount < 4 || tryCount > 32) continue
            for (start in 20..36) {
                if (start + tryCount * 2 > d.size) continue

                val cells = ArrayList<Double>(tryCount)
                var ok = true
                for (i in 0 until tryCount) {
                    val off = start + i * 2
                    val mv = (d[off].toInt() and 0xFF) or ((d[off + 1].toInt() and 0x1F) shl 8)
                    if (mv < 2000 || mv > 5000) { ok = false; break }
                    cells.add(mv / 1000.0)
                }
                if (!ok) continue

                val sum = cells.sum()
                if (sum <= 10) continue

                val tempStart = start + tryCount * 2
                val maxOff = maxOf(24, d.size - tempStart - 24)
                var off = 0
                while (off <= maxOff) {
                    val p = tempStart + off
                    if (p + 21 < d.size) {
                        val total = readU16(d, p) / 100.0
                        if (total > 5 && kotlin.math.abs(total - sum) < 0.3) {
                            cellStart = start
                            cellCount = tryCount
                            base = p
                            resolved = true
                            break@outer
                        }
                    }
                    off += 2
                }
            }
        }

        // 兜底：自校验失败就用 d[3] + 固定偏移
        if (!resolved) {
            if (cellTarget > 0 && 28 + cellTarget * 2 <= d.size) {
                cellStart = 28
                cellCount = cellTarget
                base = cellStart + cellCount * 2 + 8
            } else {
                return false
            }
        }

        if (cellCount <= 0) return false
        if (cellCount in 3..40) lastGoodCellCount = cellCount

        // ── ② 单体电压 ──
        val cells = ArrayList<Double>(cellCount)
        val balFlags = ArrayList<Boolean>(cellCount)
        for (i in 0 until cellCount) {
            val off = cellStart + i * 2
            if (off + 1 >= d.size) break
            val b1 = d[off + 1].toInt() and 0xFF
            val bal = (b1 shr 5) and 0x07
            val mv = (d[off].toInt() and 0xFF) or ((b1 and 0x1F) shl 8)
            cells.add(if (mv in 1000..5000) mv / 1000.0 else 0.0)
            balFlags.add(bal > 0)
        }
        out.cells = cells
        out.cellCount = cellCount
        out.balancing = balFlags

        // ── ③ 温度 + 总电压位置 ──
        val tempStart = cellStart + cellCount * 2
        val declaredTemps = (d[2].toInt() and 0xFF).let { if (it in 1..8) it else 4 }
        var nTemps = declaredTemps
        if (resolved && base > tempStart) {
            val slots = (base - tempStart) / 2
            if (slots in 1..10) {
                nTemps = minOf(declaredTemps, slots)
                if (declaredTemps < slots - 1 && slots <= 8) nTemps = slots
            }
        }
        if (!resolved) {
            // 没校验成功就退回默认布局
            base = tempStart + nTemps * 2 + 4
        }

        // 温度
        val temps = ArrayList<Int>(nTemps)
        for (k in 0 until nTemps) {
            val off = tempStart + k * 2
            if (off + 1 >= d.size) break
            val t = readI16(d, off)
            if (t > -40 && t < 120) temps.add(t)
        }
        out.temps = temps

        // MOS 温度 = 温度区最后一个（紧贴总电压前）
        if (base >= tempStart + 2) {
            val mosOff = base - 2
            val t = readI16(d, mosOff)
            if (t > -40 && t < 120) out.mosTemp = t
            else out.mosTemp = temps.firstOrNull() ?: 0
        } else {
            out.mosTemp = temps.firstOrNull() ?: 0
        }

        // ── ④ 总电压 / 电流 / SOC ──
        val cellSum = out.cells.sum()
        if (base + 1 < d.size) {
            val v = readU16(d, base)
            if (v in 1 until 20000) out.totalVoltage = v / 100.0
        }
        if (out.totalVoltage <= 0 || kotlin.math.abs(out.totalVoltage - cellSum) > 5) {
            if (cellSum > 1) out.totalVoltage = cellSum
        }

        if (base + 3 < d.size) {
            val a = readI16(d, base + 2)
            if (kotlin.math.abs(a) < 5000) out.current = a / 10.0
        }

        if (base + 5 < d.size) {
            val soc = readU16(d, base + 4)
            if (soc in 0..100) out.soc = soc
        }

        // ── ⑤ MOS 状态 ──
        if (base + 9 < d.size) {
            out.dischargeMosOn = (d[base + 8].toInt() and 0xFF) == 0x01
            out.chargeMosOn = (d[base + 9].toInt() and 0xFF) == 0x01
        }

        // ── ⑥ 容量 ──
        if (base + 15 < d.size) {
            val cap = readU32(d, base + 12) / 1_000_000.0
            if (cap > 0 && cap < 10000) out.capacityTotal = cap
        }
        if (base + 19 < d.size) {
            val rem = readU32(d, base + 16) / 1_000_000.0
            if (rem in 0.0..10000.0) out.capacityRemaining = rem
        }

        // ── ⑦ 累计循环容量 ──
        if (base + 23 < d.size) {
            val cyc = readU32(d, base + 20) / 1000.0
            if (cyc in 0.0..10_000_000.0) out.totalCycleCapacity = cyc
        }

        // 循环次数 = 累计循环容量 ÷ 额定容量
        val rated = if (config.capacity > 0) config.capacity
                    else if (out.capacityTotal > 0) out.capacityTotal else 0.0
        if (rated > 0 && out.totalCycleCapacity > 0) {
            out.cycles = kotlin.math.round(out.totalCycleCapacity / rated).toInt()
        }

        // ── ⑧ 均衡 ──
        if (base + 35 < d.size) {
            val mask = readU32(d, base + 32)
            out.balancingActive = mask != 0L
        }

        // ── ⑨ 单体极值 ──
        if (base + 41 < d.size) {
            val maxV = readU16(d, base + 36)
            val maxIdx = readU16(d, base + 38)
            val minV = readU16(d, base + 40)
            val minIdx = readU16(d, base + 42)
            if (maxV in 1000..5000) { out.maxCell = maxV / 1000.0; out.maxCellIndex = maxIdx + 1 }
            if (minV in 1000..5000) { out.minCell = minV / 1000.0; out.minCellIndex = minIdx + 1 }
        }
        if (out.maxCell <= 0 && out.cells.isNotEmpty()) {
            out.maxCell = out.cells.max()
            out.minCell = out.cells.min()
            out.maxCellIndex = out.cells.indexOf(out.maxCell) + 1
            out.minCellIndex = out.cells.indexOf(out.minCell) + 1
        }

        // ── ⑩ 剩余时间 / 化学类型 ──
        if (base + 53 < d.size) {
            out.chargeRemainMin = readU16(d, base + 50)
            out.dischargeRemainMin = readU16(d, base + 52)
        }
        if (base + 57 < d.size) {
            out.chemistryCode = readU16(d, base + 56)
        }

        data = out
        return true
    }

    fun reset() {
        data = AntBmsData()
        lastGoodCellCount = 0
    }

    // ── 工具 ──

    private fun readU16(b: ByteArray, off: Int): Int {
        if (off + 1 >= b.size) return 0
        return (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
    }

    private fun readI16(b: ByteArray, off: Int): Int {
        val v = readU16(b, off)
        return if (v > 32767) v - 65536 else v
    }

    private fun readU32(b: ByteArray, off: Int): Long {
        if (off + 3 >= b.size) return 0L
        return ((b[off].toLong() and 0xFF)) or
               ((b[off + 1].toLong() and 0xFF) shl 8) or
               ((b[off + 2].toLong() and 0xFF) shl 16) or
               ((b[off + 3].toLong() and 0xFF) shl 24)
    }
}
