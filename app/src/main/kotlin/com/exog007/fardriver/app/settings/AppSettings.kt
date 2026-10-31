package com.exog007.fardriver.app.settings

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 全局设置（存 SharedPreferences，用 StateFlow 通知界面）
 */
class AppSettings(context: Context) {

    private val ctx: Context = context.applicationContext
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ── 仪表风格 ──

    /** true = 竞技风格，false = 标准风格 */
    private val _sportMode = MutableStateFlow(sp.getBoolean("sport_mode", true))
    val sportMode: StateFlow<Boolean> = _sportMode
    fun setSportMode(v: Boolean) {
        _sportMode.value = v
        sp.edit().putBoolean("sport_mode", v).apply()
    }

    /** 打开 App 直接进竞技仪表 */
    var launchInSportMode: Boolean
        get() = sp.getBoolean("launch_sport", true)
        set(v) = sp.edit().putBoolean("launch_sport", v).apply()

    // ── 转速 ──

    /** 转速显示倍率 */
    private val _rpmMultiplier = MutableStateFlow(sp.getFloat("rpm_mult", 10f).toDouble())
    val rpmMultiplier: StateFlow<Double> = _rpmMultiplier
    fun setRpmMultiplier(v: Double) {
        _rpmMultiplier.value = v
        sp.edit().putFloat("rpm_mult", v.toFloat()).apply()
    }

    /** 转速表满量程 */
    private val _rpmScaleMax = MutableStateFlow(sp.getFloat("rpm_max", 12000f).toDouble())
    val rpmScaleMax: StateFlow<Double> = _rpmScaleMax
    fun setRpmScaleMax(v: Double) {
        _rpmScaleMax.value = v
        sp.edit().putFloat("rpm_max", v.toFloat()).apply()
    }

    // ── 油表阈值 ──

    private val _battFull = MutableStateFlow(sp.getFloat("batt_full", 90.3f).toDouble())
    val battFull: StateFlow<Double> = _battFull
    fun setBattFull(v: Double) {
        _battFull.value = v
        sp.edit().putFloat("batt_full", v.toFloat()).apply()
    }

    private val _battEmpty = MutableStateFlow(sp.getFloat("batt_empty", 63f).toDouble())
    val battEmpty: StateFlow<Double> = _battEmpty
    fun setBattEmpty(v: Double) {
        _battEmpty.value = v
        sp.edit().putFloat("batt_empty", v.toFloat()).apply()
    }

    // ── 速度微调 ──

    private val _speedTrim = MutableStateFlow(sp.getFloat("speed_trim", 1f).toDouble())
    val speedTrim: StateFlow<Double> = _speedTrim
    fun setSpeedTrim(v: Double) {
        val clamped = v.coerceIn(0.05, 10.0)
        _speedTrim.value = clamped
        sp.edit().putFloat("speed_trim", clamped.toFloat()).apply()
    }

    // ── 电池 ──

    /** 电池化学类型：ncm / lfp */
    var chemistry: String
        get() = sp.getString("chemistry", "ncm") ?: "ncm"
        set(v) = sp.edit().putString("chemistry", v).apply()

    // ── 音效 ──

    var bootSoundEnabled: Boolean
        get() = sp.getBoolean("boot_sound", true)
        set(v) = sp.edit().putBoolean("boot_sound", v).apply()

    // ── 配色 ──

    /** 主色（琥珀） */
    private val _amberHex = MutableStateFlow(sp.getInt("color_amber", 0xFFFF9F0A.toInt()))
    val amberHex: StateFlow<Int> = _amberHex
    fun setAmber(hex: Int) {
        _amberHex.value = hex
        sp.edit().putInt("color_amber", hex).apply()
    }

    /** 强调色（青） */
    private val _cyanHex = MutableStateFlow(sp.getInt("color_cyan", 0xFF00E5FF.toInt()))
    val cyanHex: StateFlow<Int> = _cyanHex
    fun setCyan(hex: Int) {
        _cyanHex.value = hex
        sp.edit().putInt("color_cyan", hex).apply()
    }

    /** 背景色 */
    private val _bgHex = MutableStateFlow(sp.getInt("color_bg", 0xFF000000.toInt()))
    val bgHex: StateFlow<Int> = _bgHex
    fun setBg(hex: Int) {
        _bgHex.value = hex
        sp.edit().putInt("color_bg", hex).apply()
    }

    // ── 字体亮度 ──

    private val _textBrightness = MutableStateFlow(sp.getFloat("text_brightness", 1f).toDouble())
    val textBrightness: StateFlow<Double> = _textBrightness
    fun setTextBrightness(v: Double) {
        val c = v.coerceIn(0.5, 1.6)
        _textBrightness.value = c
        sp.edit().putFloat("text_brightness", c.toFloat()).apply()
    }

    // ── 背景类型：solid / photo ──

    private val _bgType = MutableStateFlow(sp.getString("bg_type", "solid") ?: "solid")
    val bgType: StateFlow<String> = _bgType

    /** 背景图片（改了这个就重画） */
    private val _bgBitmap = MutableStateFlow<android.graphics.Bitmap?>(null)
    val bgBitmap: StateFlow<android.graphics.Bitmap?> = _bgBitmap

    init {
        _bgBitmap.value = BackgroundImageStore.load(ctx)
    }

    fun setBgType(type: String) {
        _bgType.value = type
        sp.edit().putString("bg_type", type).apply()
    }

    fun setBackgroundPhoto(bitmap: android.graphics.Bitmap) {
        if (BackgroundImageStore.save(ctx, bitmap)) {
            _bgBitmap.value = BackgroundImageStore.load(ctx)
            setBgType("photo")
        }
    }

    fun clearBackgroundPhoto() {
        BackgroundImageStore.delete(ctx)
        _bgBitmap.value = null
        setBgType("solid")
    }

    /** 图片遮罩暗度（0~0.85，让数字看得清） */
    private val _photoDim = MutableStateFlow(sp.getFloat("photo_dim", 0.35f).toDouble())
    val photoDim: StateFlow<Double> = _photoDim
    fun setPhotoDim(v: Double) {
        val c = v.coerceIn(0.0, 0.85)
        _photoDim.value = c
        sp.edit().putFloat("photo_dim", c.toFloat()).apply()
    }
}

/** 把亮度系数应用到颜色上 */
fun Color.brightnessScaled(factor: Double): Color {
    val f = factor.coerceIn(0.05, 2.0)
    if (kotlin.math.abs(f - 1.0) < 0.001) return this
    return if (f <= 1.0) {
        Color(
            red = red * f.toFloat(),
            green = green * f.toFloat(),
            blue = blue * f.toFloat(),
            alpha = alpha
        )
    } else {
        val t = (f - 1.0).toFloat()
        Color(
            red = red + (1f - red) * t,
            green = green + (1f - green) * t,
            blue = blue + (1f - blue) * t,
            alpha = alpha
        )
    }
}
