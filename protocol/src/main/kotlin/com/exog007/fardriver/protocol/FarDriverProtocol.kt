package com.exog007.fardriver.protocol

/**
 * 远驱控制器 BLE 协议解码
 *
 * 帧结构（16 字节）：
 *   [0]    0xAA 魔数
 *   [1]    id(6位) | flags(2位)
 *   [2..13] 数据 12 字节
 *   [14..15] CRC16
 *
 * 帧号 → 寄存器地址映射见 FLASH_READ_ADDR
 *
 * 已实测验证的字段：
 *   0xE2  p[0] bit0  = P 档（0 = 挂入 P，1 = 解除）
 *   0xE2  p[3] bit7  = 刹车
 *   0xE2  p[6..7]    = 原始转速（int16 小端）
 *   0xE8  p[0..1]    = 电压 ×0.1V
 *   0xE8  p[4..5]    = 电流 ×0.25A（int16）
 *   0xD6  p[10..11]  = MOS 温度
 *   0xF4  p[0..1]    = 电机温度
 */

/** 控制器实时数据 */
data class ControllerData(
    var voltage: Double = 0.0,       // V
    var current: Double = 0.0,       // A（负 = 回收）
    var power: Double = 0.0,         // W
    var rpm: Double = 0.0,           // 电机转速
    var rawRpm: Int = 0,             // 原始值
    var gear: Int = 0,               // 档位 1/2/3，0 = 无数据
    var speedKmh: Double = 0.0,      // 换算时速
    var mosTemp: Int = 0,            // 控制器 MOS 温度
    var motorTemp: Int = 0,          // 电机温度
    var soc: Int = 0,                // 电量 %
    var isRegen: Boolean = false,    // 回收中
    var pGearOn: Boolean = false,    // P 档挂入
    var braking: Boolean = false,    // 刹车中
    var e2Seen: Boolean = false,     // 收到过 0xE2
    var faultCode: Int = 0,
    var faultText: String = "",
    var updatedAt: Long = 0L
) {
    /** 有故障 */
    val hasFault: Boolean get() = faultText.isNotEmpty()

    fun recomputePower() {
        if (voltage > 0) {
            power = voltage * current
            isRegen = current < 0
        }
    }
}

object FarDriverBle {
    const val SERVICE_UUID = "0000FFE0-0000-1000-8000-00805F9B34FB"
    const val CHAR_UUID = "0000FFEC-0000-1000-8000-00805F9B34FB"

    /** 包索引 → 寄存器地址 */
    val FLASH_READ_ADDR = intArrayOf(
        0xE2, 0xE8, 0xEE, 0xE4, 0x06, 0x0C, 0x12, 0xE2, 0xE8, 0xEE, 0x18, 0x1E, 0x24, 0x2A,
        0xE2, 0xE8, 0xEE, 0x30, 0x5D, 0x63, 0x69, 0xE2, 0xE8, 0xEE, 0x7C, 0x82, 0x88, 0x8E,
        0xE2, 0xE8, 0xEE, 0x94, 0x9A, 0xA0, 0xA6, 0xE2, 0xE8, 0xEE, 0xAC, 0xB2, 0xB8, 0xBE,
        0xE2, 0xE8, 0xEE, 0xC4, 0xCA, 0xD0, 0xE2, 0xE8, 0xEE, 0xD6, 0xDC, 0xF4, 0xFA
    )
}

/**
 * 控制器数据解码器
 *
 * @param wheelCircumferenceM 轮周长（米），默认 17 寸
 * @param motorPolePairs 电机极对数
 */
class FarDriverDecoder(
    var wheelCircumferenceM: Double = 1.416,
    var motorPolePairs: Int = 20
) {
    var data = ControllerData()
        private set

    /**
     * 解一个 16 字节包
     * @return 是否解析成功
     */
    fun process(packet: ByteArray): Boolean {
        if (packet.size != 16) return false
        if (!verifyCrc(packet)) return false
        if (packet[0].toInt() and 0xFF != 0xAA) return false

        val id = packet[1].toInt() and 0x3F
        if (id >= FarDriverBle.FLASH_READ_ADDR.size) return false
        val address = FarDriverBle.FLASH_READ_ADDR[id]

        // 数据从第 3 个字节开始，取 12 字节
        val p = ByteArray(12) { packet[2 + it] }
        val d = data

        when (address) {
            0xE2 -> {
                val b0 = p[0].toInt() and 0xFF
                d.gear = ((b0 shr 2) and 0b11) + 1
                // P 档：bit0，0 = 挂入 P（实测 0x80 挂入，0x81 解除）
                d.pGearOn = (b0 and 0x01) == 0
                // 刹车：p[3] bit7（实测 0x80 刹车，0x00 松开）
                val b3 = p[3].toInt() and 0xFF
                d.braking = (b3 and 0x80) != 0
                // 原始转速 int16 小端
                d.rawRpm = readI16(p, 6)
                d.e2Seen = true
            }

            0xE8 -> {
                val v = readU16(p, 0) / 10.0
                if (v in 0.0..100.0) d.voltage = v

                val a = readI16(p, 4) / 4.0
                if (a in -100.0..300.0) d.current = a
            }

            0xD6 -> {
                val t = readI16(p, 10)
                if (t > -20 && t < 100) d.mosTemp = t
            }

            0xF4 -> {
                val mt = readI16(p, 0)
                if (mt > -20 && mt < 200) d.motorTemp = mt
                d.soc = p[3].toInt() and 0xFF
            }
        }

        // 转速换算
        val displayRpm = if (d.rawRpm < 0) 0 else d.rawRpm
        d.rpm = displayRpm * 4.0 / motorPolePairs

        // 速度 = 转速 × 轮周长 × 60 / 1000
        d.speedKmh = d.rpm * wheelCircumferenceM * 60.0 / 1000.0

        d.recomputePower()
        d.updatedAt = System.currentTimeMillis()
        return true
    }

    fun reset() {
        data = ControllerData()
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

    companion object {
        /** CRC16（初值 0x7F3C，多项式 0xA001） */
        fun verifyCrc(packet: ByteArray): Boolean {
            if (packet.size != 16) return false
            var crc = 0x7F3C
            for (i in 0 until 14) {
                crc = crc xor (packet[i].toInt() and 0xFF)
                repeat(8) {
                    crc = if (crc and 1 != 0) (crc shr 1) xor 0xA001 else crc shr 1
                }
            }
            val expected = (packet[14].toInt() and 0xFF) or ((packet[15].toInt() and 0xFF) shl 8)
            return (crc and 0xFFFF) == expected
        }

        /** 转 hex 字符串（调试用） */
        fun hex(bytes: ByteArray, count: Int = bytes.size): String {
            val n = minOf(count, bytes.size)
            return (0 until n).joinToString(" ") { "%02X".format(bytes[it]) }
        }
    }
}
