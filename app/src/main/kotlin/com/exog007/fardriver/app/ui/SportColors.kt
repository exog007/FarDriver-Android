package com.exog007.fardriver.app.ui

import androidx.compose.ui.graphics.Color

/**
 * 竞技仪表配色
 *
 * 和 iOS 版一致：黑底 + 琥珀色单色线框
 */
object SportColors {
    /** 主色：琥珀（数字、线框） */
    val Amber = Color(0xFFFF9F0A)

    /** 强调色：青（状态点、药丸） */
    val Cyan = Color(0xFF00E5FF)

    /** 警告色：红（故障灯、超限） */
    val Red = Color(0xFFFF453A)

    /** 正常色：绿 */
    val Green = Color(0xFF32D74B)

    /** 故障灯黄 */
    val LampYellow = Color(0xFFFFC107)

    /** 电瓶灯橙黄 */
    val LampAmber = Color(0xFFFB9B08)

    /** 纯黑底 */
    val Background = Color(0xFF000000)

    /** 次要文字 */
    val Secondary = Color(0x8AFFFFFF)

    /** 暗色文字 */
    val Dim = Color(0x40FFFFFF)

    /** 卡片背景 */
    val CardBg = Color(0x12FFFFFF)
}
