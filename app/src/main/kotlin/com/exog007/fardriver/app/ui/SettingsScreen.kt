package com.exog007.fardriver.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exog007.fardriver.app.settings.AppSettings
import com.exog007.fardriver.app.settings.brightnessScaled

/**
 * 设置页
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onPickPhoto: () -> Unit,
    onResetTrip: () -> Unit,
    tripDistance: String,
    tripConsumption: String,
    tripAvgPower: String
) {
    val sportMode by settings.sportMode.collectAsState()
    val rpmMult by settings.rpmMultiplier.collectAsState()
    val rpmMax by settings.rpmScaleMax.collectAsState()
    val battFull by settings.battFull.collectAsState()
    val battEmpty by settings.battEmpty.collectAsState()
    val speedTrim by settings.speedTrim.collectAsState()
    val brightness by settings.textBrightness.collectAsState()
    val amberHex by settings.amberHex.collectAsState()
    val cyanHex by settings.cyanHex.collectAsState()
    val bgHex by settings.bgHex.collectAsState()
    val bgType by settings.bgType.collectAsState()
    val bgBmp by settings.bgBitmap.collectAsState()
    val photoDim by settings.photoDim.collectAsState()

    var launchSport by remember { mutableStateOf(settings.launchInSportMode) }
    var bootSound by remember { mutableStateOf(settings.bootSoundEnabled) }
    var chemistry by remember { mutableStateOf(settings.chemistry) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0C))
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ 返回", fontSize = 16.sp, color = SportColors.Cyan,
                 modifier = Modifier.clickable { onBack() }.padding(end = 16.dp))
            Text("设置", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        // ── 蓝牙 ──
        Section("蓝牙") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenBluetooth() }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("管理蓝牙设备", fontSize = 14.sp, color = Color.White)
                Spacer(Modifier.weight(1f))
                Text("连接控制器 / BMS", fontSize = 11.sp, color = SportColors.Secondary)
                Spacer(Modifier.width(6.dp))
                Text("›", fontSize = 18.sp, color = SportColors.Secondary)
            }
        }

        // ── 仪表风格 ──
        Section("仪表风格") {
            SwitchRow("竞技风格", "黑底琥珀线框，转速刻度条 + 大号速度",
                      sportMode) { settings.setSportMode(it) }
            DividerLine()
            SwitchRow("启动直接进竞技仪表", "打开 App 就横屏进竞技模式",
                      launchSport) {
                launchSport = it; settings.launchInSportMode = it
            }
        }

        // ── 转速 ──
        Section("转速显示") {
            SliderRow("转速倍率", "×%.0f".format(rpmMult), rpmMult.toFloat(),
                      1f..20f, 18) { settings.setRpmMultiplier(it.toDouble()) }
            Text("原厂报 100 rpm × 倍率 10 = 显示 1000 rpm",
                 fontSize = 10.sp, color = SportColors.Secondary)
            Spacer(Modifier.height(8.dp))
            SliderRow("转速表满量程", "%,d rpm".format(rpmMax.toInt()), rpmMax.toFloat(),
                      4000f..30000f, 25) { settings.setRpmScaleMax(it.toDouble()) }
        }

        // ── 油表 ──
        Section("仪表电量表") {
            SliderRow("满电电压", "%.1f V".format(battFull), battFull.toFloat(),
                      20f..120f, 999) { settings.setBattFull(it.toDouble()) }
            Spacer(Modifier.height(6.dp))
            SliderRow("没电电压", "%.1f V".format(battEmpty), battEmpty.toFloat(),
                      10f..110f, 999) { settings.setBattEmpty(it.toDouble()) }
            Text("百分比 = (当前 - 没电) ÷ (满电 - 没电)",
                 fontSize = 10.sp, color = SportColors.Secondary)
        }

        // ── 速度校准 ──
        Section("速度校准") {
            SliderRow("速度微调", "×%.3f".format(speedTrim), speedTrim.toFloat(),
                      0.05f..10f, 0) { settings.setSpeedTrim(it.toDouble()) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallBtn("÷2") { settings.setSpeedTrim(speedTrim / 2) }
                SmallBtn("−0.01") { settings.setSpeedTrim(speedTrim - 0.01) }
                SmallBtn("+0.01") { settings.setSpeedTrim(speedTrim + 0.01) }
                SmallBtn("×2") { settings.setSpeedTrim(speedTrim * 2) }
                SmallBtn("重置") { settings.setSpeedTrim(1.0) }
            }
            Text("只影响无 GPS 时用转速算的速度",
                 fontSize = 10.sp, color = SportColors.Secondary)
        }

        // ── 字体亮度 ──
        Section("字体亮度") {
            SliderRow("亮度", "%.0f%%".format(brightness * 100), brightness.toFloat(),
                      0.5f..1.6f, 22) { settings.setTextBrightness(it.toDouble()) }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PreviewSwatch("转速", Color(amberHex).brightnessScaled(brightness))
                PreviewSwatch("功率", Color(amberHex).brightnessScaled(brightness))
                PreviewSwatch("正常", SportColors.Green.brightnessScaled(brightness))
            }
        }

        // ── 仪表颜色 ──
        Section("仪表颜色") {
            HexColorRow("主色（数字/线框）", amberHex) { settings.setAmber(it) }
            Spacer(Modifier.height(10.dp))
            HexColorRow("强调色（状态点）", cyanHex) { settings.setCyan(it) }
        }

        // ── 背景 ──
        Section("背景") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("当前", fontSize = 13.sp, color = Color.White)
                Spacer(Modifier.weight(1f))
                Text(
                    if (bgType == "photo" && bgBmp != null) "自定义图片" else "纯色",
                    fontSize = 12.sp, color = SportColors.Cyan
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionBtn("从相册选图", Color(0x33FF9F0A), SportColors.Amber) { onPickPhoto() }
                if (bgBmp != null) {
                    ActionBtn("删除图片", Color(0x33FF453A), SportColors.Red) {
                        settings.clearBackgroundPhoto()
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            HexColorRow("纯色背景", bgHex) {
                settings.setBg(it)
                settings.setBgType("solid")
            }
            if (bgBmp != null) {
                Spacer(Modifier.height(10.dp))
                SliderRow("图片暗度", "%.0f%%".format(photoDim * 100), photoDim.toFloat(),
                          0f..0.85f, 17) { settings.setPhotoDim(it.toDouble()) }
                Text("暗度调高，琥珀色数字更清楚",
                     fontSize = 10.sp, color = SportColors.Secondary)
            }
        }

        // ── 电池 ──
        Section("电池") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("化学类型", fontSize = 14.sp, color = Color.White)
                Spacer(Modifier.weight(1f))
                SegBtn("三元锂", chemistry == "ncm") {
                    chemistry = "ncm"; settings.chemistry = "ncm"
                }
                Spacer(Modifier.width(6.dp))
                SegBtn("磷酸铁锂", chemistry == "lfp") {
                    chemistry = "lfp"; settings.chemistry = "lfp"
                }
            }
        }

        // ── 音效 ──
        Section("音效") {
            SwitchRow("自检音效", "进入竞技仪表时播放（暂未支持自定义音频）",
                      bootSound) {
                bootSound = it; settings.bootSoundEnabled = it
            }
        }

        // ── 行程 ──
        Section("行程统计") {
            KeyValue("小计里程", "$tripDistance km")
            KeyValue("百公里电耗", "$tripConsumption kWh")
            KeyValue("平均功率", "$tripAvgPower W")
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onResetTrip,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF453A)),
                modifier = Modifier.fillMaxWidth()
            ) { Text("清空小计里程", color = SportColors.Red) }
        }

        Spacer(Modifier.height(20.dp))
    }
}

// ══════════════════════════════════════════
// 通用控件
// ══════════════════════════════════════════

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x14FFFFFF), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0x10FFFFFF), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold,
             color = SportColors.Secondary, modifier = Modifier.padding(bottom = 10.dp))
        content()
    }
}

@Composable
private fun DividerLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x10FFFFFF)))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = Color.White)
            if (subtitle.isNotEmpty())
                Text(subtitle, fontSize = 10.sp, color = SportColors.Secondary)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    title: String, valueText: String, value: Float,
    range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 14.sp, color = Color.White)
            Spacer(Modifier.weight(1f))
            Text(valueText, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                 color = SportColors.Cyan)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps
        )
    }
}

@Composable
private fun SmallBtn(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0x1FFFFFFF)),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.height(30.dp)
    ) { Text(text, fontSize = 11.sp, color = Color.White) }
}

@Composable
private fun SegBtn(text: String, active: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) SportColors.Amber else Color(0x1FFFFFFF)
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        modifier = Modifier.height(30.dp)
    ) { Text(text, fontSize = 11.sp, color = if (active) Color.Black else Color.White) }
}

@Composable
private fun ActionBtn(text: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = bg),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
    ) { Text(text, fontSize = 12.sp, color = fg, fontWeight = FontWeight.Bold) }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(key, fontSize = 13.sp, color = SportColors.Secondary)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = Color.White)
    }
}

@Composable
private fun PreviewSwatch(label: String, color: Color) {
    Text(
        label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(Color(0x12FFFFFF), RoundedCornerShape(5.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

/**
 * 十六进制色板选择（不用系统 ColorPicker，兼容性好）
 */
@Composable
private fun HexColorRow(label: String, currentHex: Int, onPick: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = Color.White)
            Spacer(Modifier.weight(1f))
            Text("#%06X".format(currentHex and 0xFFFFFF),
                 fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                 color = SportColors.Secondary)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(28.dp)
                    .background(Color(currentHex), RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(6.dp))
                    .clickable { expanded = !expanded }
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            ColorPalette(currentHex, onPick)
        }
    }
}

/** 预设色板 */
private val PALETTE: List<Int> = listOf(
    0xFFFF9F0A.toInt(), 0xFFFFC107.toInt(), 0xFFFF453A.toInt(), 0xFFFF6B9D.toInt(),
    0xFF32D74B.toInt(), 0xFF30D158.toInt(), 0xFF00E5FF.toInt(), 0xFF64D2FF.toInt(),
    0xFF0A84FF.toInt(), 0xFF5E5CE6.toInt(), 0xFFBF5AF2.toInt(), 0xFFFFFFFF.toInt(),
    0xFF8E8E93.toInt(), 0xFFA2845E.toInt(), 0xFFFFD60A.toInt(), 0xFF000000.toInt()
)

@Composable
private fun ColorPalette(selected: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in PALETTE.chunked(8)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { hex ->
                    Box(
                        Modifier
                            .size(30.dp)
                            .background(Color(hex), RoundedCornerShape(6.dp))
                            .border(
                                if (hex == selected) 2.dp else 1.dp,
                                if (hex == selected) Color.White else Color(0x33FFFFFF),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onPick(hex) }
                    )
                }
            }
        }
    }
}
