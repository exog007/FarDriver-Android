package com.exog007.fardriver.protocol

/**
 * 小计里程 + 平均功率 + 百公里电耗
 *
 * 每收到一帧控制器数据调用一次 [update]，
 * 用「速度 × 时间差」累加里程，用「功率 × 时间差」累加能量。
 */
class TripMeter {

    var distanceKm: Double = 0.0
        private set
    var energyKWh: Double = 0.0
        private set
    var movingSeconds: Double = 0.0
        private set

    /** 平均功率 W */
    val averagePowerW: Double
        get() = if (movingSeconds <= 1) 0.0 else energyKWh * 1000.0 / (movingSeconds / 3600.0)

    /** 百公里电耗 kWh/100km */
    val consumptionPer100km: Double
        get() = if (distanceKm <= 0.05) 0.0 else energyKWh / distanceKm * 100.0

    val hasEnoughData: Boolean get() = distanceKm > 0.05

    private var lastTick: Long = 0L
    /** 间隔太长（断开重连）就丢弃这一帧 */
    private val maxGapMs = 3000L

    /** 每 N 次采样回调一次，让 UI 存盘 */
    private var sampleCounter = 0
    var onNeedSave: (() -> Unit)? = null

    fun update(speedKmh: Double, powerW: Double) {
        val now = System.currentTimeMillis()
        val prev = lastTick
        lastTick = now

        if (prev == 0L) return
        val dtMs = now - prev
        if (dtMs <= 0 || dtMs > maxGapMs) return

        // 低速不计里程，避免停车噪声累积
        if (speedKmh < 1.0) return

        val dt = dtMs / 1000.0
        distanceKm += speedKmh * dt / 3600.0
        energyKWh += powerW * dt / 3600.0 / 1000.0
        movingSeconds += dt

        sampleCounter++
        if (sampleCounter >= 20) {
            sampleCounter = 0
            onNeedSave?.invoke()
        }
    }

    fun reset() {
        distanceKm = 0.0
        energyKWh = 0.0
        movingSeconds = 0.0
        lastTick = 0L
        sampleCounter = 0
        onNeedSave?.invoke()
    }

    /** 从持久化恢复 */
    fun restore(distance: Double, energy: Double, seconds: Double) {
        distanceKm = distance
        energyKWh = energy
        movingSeconds = seconds
        lastTick = 0L
    }

    // ── 格式化 ──

    val distanceText: String
        get() = if (distanceKm < 1) "%.3f".format(distanceKm) else "%.2f".format(distanceKm)

    val consumptionText: String
        get() = if (!hasEnoughData) "--" else "%.1f".format(consumptionPer100km)

    val avgPowerText: String
        get() = if (movingSeconds <= 1) "--" else averagePowerW.toInt().toString()

    /** 重置计时（断开连接时调用，避免下次连接算出巨大的 dt） */
    fun breakContinuity() {
        lastTick = 0L
    }
}

/**
 * GPS 速度融合
 *
 * 方案 C：GPS 为主，控制器兜底
 *   ① 有 GPS 且精度够 → 用 GPS（真实对地速度）
 *   ② GPS 没信号 → 用控制器转速换算
 *   ③ GPS 读到 0 但控制器有速度（刚起步）→ 用控制器
 */
class GpsFusion(
    /** GPS 原始速度 km/h */
    var gpsSpeedKmh: Double = 0.0,
    /** 定位精度 m，负 = 无定位 */
    var accuracy: Double = -1.0,
    /** 有没有定位 */
    var hasFix: Boolean = false,
    /** 用户开关 */
    var enabled: Boolean = true
) {
    /** 平滑后的显示速度（每帧朝目标值追 18%） */
    var smoothSpeed: Double = 0.0
        private set

    private val smoothFactor = 0.18

    /** 有 GPS 参与 */
    val isContributing: Boolean
        get() = enabled && hasFix && accuracy >= 0 && accuracy < 30

    /** 融合一次，返回显示速度 */
    fun fused(wheelSpeed: Double): Double {
        val target = if (isContributing) {
            if (gpsSpeedKmh >= 1.0) gpsSpeedKmh else wheelSpeed
        } else {
            wheelSpeed
        }
        // 平滑
        val diff = target - smoothSpeed
        smoothSpeed = if (kotlin.math.abs(diff) < 0.05) target else smoothSpeed + diff * smoothFactor
        return smoothSpeed
    }

    /** 直接跳变（停止时用） */
    fun reset() {
        smoothSpeed = 0.0
    }
}

/** 电池化学类型 */
enum class BatteryChemistry(val displayName: String, val designCycles: Int) {
    NCM("三元锂", 2200),
    LFP("磷酸铁锂", 3500);

    companion object {
        fun fromName(name: String?): BatteryChemistry =
            entries.firstOrNull { it.displayName == name } ?: NCM
    }
}

/** 电池健康度等级 */
enum class HealthLevel {
    HEALTHY, WATCH, WARNING, CRITICAL;

    val showsTriangle: Boolean
        get() = this == WARNING || this == CRITICAL

    val message: String?
        get() = when (this) {
            WATCH -> "健康度低于 90%，建议定期观察，避免深度放电。"
            WARNING -> "健康度到达 80%，电池性能下降较大，请注意不要进行过充过放。正常使用情况下 80% 是安全范围。"
            CRITICAL -> "健康度到达 70%，不推荐再进行使用，会有很大安全隐患。"
            HEALTHY -> null
        }

    companion object {
        fun from(soh: Double): HealthLevel = when {
            soh < 70 -> CRITICAL
            soh < 80 -> WARNING
            soh < 90 -> WATCH
            else -> HEALTHY
        }
    }
}

/** 电池健康度计算 */
object BatteryHealth {
    /**
     * 健康度 = 100 - 循环次数 × 每循环损耗
     */
    fun soh(cycles: Int, chemistry: BatteryChemistry): Double? {
        if (cycles < 0) return null
        val lossPerCycle = 100.0 / chemistry.designCycles
        return (100.0 - cycles * lossPerCycle).coerceIn(0.0, 100.0)
    }
}

/** 电压 → 电量百分比（用于油表） */
object BatteryPercent {
    fun compute(voltage: Double, full: Double, empty: Double): Double {
        if (full <= empty || voltage <= 0) return 0.0
        return ((voltage - empty) / (full - empty)).coerceIn(0.0, 1.0)
    }
}
