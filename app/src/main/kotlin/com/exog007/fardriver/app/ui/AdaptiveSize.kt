package com.exog007.fardriver.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 自适应尺寸
 *
 * 按屏幕宽度/高度动态计算字号和控件大小，
 * 从小屏手机（640dp 宽）到平板（1280dp 宽）都能正常显示。
 */
data class AdaptiveSize(
    val widthDp: Float,
    val heightDp: Float
) {
    /** 取宽高中较小的作为「基准短边」 */
    private val shortSide = minOf(widthDp, heightDp)

    /** 速度大数字字号 */
    val speedFont: TextUnit = (shortSide * 0.19f).coerceIn(38f, 130f).sp

    /** 速度单位字号 */
    val speedUnitFont: TextUnit = (shortSide * 0.032f).coerceIn(9f, 20f).sp

    /** 转速大数字字号 */
    val rpmFont: TextUnit = (shortSide * 0.075f).coerceIn(16f, 44f).sp

    /** 数据格数值字号 */
    val valueFont: TextUnit = (shortSide * 0.048f).coerceIn(11f, 26f).sp

    /** 数据格标题字号 */
    val labelFont: TextUnit = (shortSide * 0.020f).coerceIn(6f, 11f).sp

    /** 单位字号 */
    val unitFont: TextUnit = (shortSide * 0.024f).coerceIn(6f, 13f).sp

    /** 档位框尺寸 */
    val gearBox = (shortSide * 0.075f).coerceIn(20f, 46f).dp

    /** 档位字号 */
    val gearFont: TextUnit = (shortSide * 0.04f).coerceIn(10f, 22f).sp

    /** 油表尺寸（宽 × 高） */
    val gaugeW = (heightDp * 0.30f).coerceIn(56f, 120f).dp
    val gaugeH = (heightDp * 0.48f).coerceIn(86f, 190f).dp

    /** BMS 温度块字号 */
    val bmsFont: TextUnit = (shortSide * 0.036f).coerceIn(9f, 20f).sp
    val bmsLabelFont: TextUnit = (shortSide * 0.017f).coerceIn(5f, 10f).sp

    /** 里程/电耗字号 */
    val odoFont: TextUnit = (shortSide * 0.028f).coerceIn(8f, 17f).sp
    val odoLabelFont: TextUnit = (shortSide * 0.017f).coerceIn(5f, 10f).sp

    /** 顶部栏高度 */
    val topBarH = (heightDp * 0.09f).coerceIn(26f, 54f).dp

    /** 转速带高度 */
    val rpmBandH = (heightDp * 0.14f).coerceIn(40f, 90f).dp

    /** 底部数据行高度 */
    val bottomRowH = (heightDp * 0.12f).coerceIn(34f, 78f).dp

    /** 主角字号（标题） */
    val titleFont: TextUnit = (shortSide * 0.036f).coerceIn(10f, 20f).sp
    val subTitleFont: TextUnit = (shortSide * 0.019f).coerceIn(5f, 10f).sp

    /** 时间字号 */
    val timeFont: TextUnit = (shortSide * 0.044f).coerceIn(11f, 24f).sp
    val dateFont: TextUnit = (shortSide * 0.025f).coerceIn(7f, 13f).sp

    /** 故障灯字号 */
    val lampFont: TextUnit = (shortSide * 0.04f).coerceIn(10f, 22f).sp

    /** 外围 padding */
    val pad = (shortSide * 0.02f).coerceIn(4f, 16f).dp
}

@Composable
fun rememberAdaptiveSize(): AdaptiveSize {
    val cfg = LocalConfiguration.current
    return remember(cfg.screenWidthDp, cfg.screenHeightDp) {
        AdaptiveSize(
            widthDp = cfg.screenWidthDp.toFloat(),
            heightDp = cfg.screenHeightDp.toFloat()
        )
    }
}
