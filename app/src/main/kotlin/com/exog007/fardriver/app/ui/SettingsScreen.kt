package com.exog007.fardriver.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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

/**
 * 设置页
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
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
                 modifier = Modifier
                     .clickable { onBack() }
                     .padding(end = 16.dp))
            Text("设置", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        // ── 仪表风格 ──
        Section("仪表风格") {
            SwitchRow(
                title = "竞技风格",
                subtitle = "黑底琥珀线框，转速刻度条 + 大号速度",
                checked = sportMode
            ) { settings.setSportMode(it) }
            DividerLine()
            SwitchRow(
                title = "启动直接进竞技仪表",
                subtitle = "打开 App 就横屏进竞技模式",
                checked = launchSport
            ) {
                launchSport = it
                settings.launchInSportMode = it
            }
        }

        // ── 转速 ──
        Section("转速显示") {
            SliderRow(
                title = "转速倍率",
                valueText = "×%.0f".format(rpmMult),
                value = rpmMult.toFloat(),
                range = 1f..20f,
                steps = 18
            ) { settings.setRpmMultiplier(it.toDouble()) }
            Text(
                "原厂报 100 rpm 💛 倍率 10 = 显示 1000 rpm",
                fontSize = 10.sp, color = SportColors.Secondary
            )
            Spacer(Modifier.height(8.dp))
            SliderRow(
                title = "转速表满量程",
                valueText = "%,d rpm".format(rpmMax.toInt()),
                value = rpmMax.toFloat(),
                range = 4000f..30000f,
                steps = 25
            ) { settings.setRpmScaleMax(it.toDouble()) }
        }

        // ── 油表 ──
        Section("仪表电量表") {
            SliderRow(
                title = "满电电压",
                valueText = "%.1f V".format(battFull),
                value = battFull.toFloat(),
                range = 20f..120f,
                steps = 999
            ) { settings.setBattFull(it.toDouble()) }
            Spacer(Modifier.height(6.dp))
            SliderRow(
                title = "没电电压",
                valueText = "%.1f V".format(battEmpty),
                value = battEmpty.toFloat(),
                range = 10f..110f,
                steps = 999
            ) { settings.setBattEmpty(it.toDouble()) }
            Text(
                "油表百分比 = (当前电压 - 没电电压) ÷ (满电电压 - 没电电压)",
                fontSize = 10.sp, color = SportColors.Secondary
            )
        }

        // ── 速度校准 ──
        Section("速度校准") {
            SliderRow(
                title = "速度微调",
                valueText = "×%.3f".format(speedTrim),
                value = speedTrim.toFloat(),
                range = 0.05f..10f,
                steps = 0
            ) { settings.setSpeedTrim(it.toDouble()) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallBtn("÷2") { settings.setSpeedTrim(speedTrim / 2) }
                SmallBtn("−0.01") { settings.setSpeedTrim(speedTrim - 0.01) }
                SmallBtn("+0.01") { settings.setSpeedTrim(speedTrim + 0.01) }
                SmallBtn("×2") { settings.setSpeedTrim(speedTrim * 2) }
                SmallBtn("重置") { settings.setSpeedTrim(1.0) }
            }
            Text(
                "仅影响「无 GPS 时」用转速算出的速度。有 GPS 时显示 GPS 真实速度。",
                fontSize = 10.sp, color = SportColors.Secondary
            )
        }

        // ── 字体亮度 ──
        Section("字体亮度") {
            SliderRow(
                title = "亮度",
                valueText = "%.0f%%".format(brightness * 100),
                value = brightness.toFloat(),
                range = 0.5f..1.6f,
                steps = 22
            ) { settings.setTextBrightness(it.toDouble()) }
        }

        // ── 电池类型 ──
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
            SwitchRow(
                title = "自检音效",
                subtitle = "进入竞技仪表时播放（当前版本暂未支持自定义音频）",
                checked = bootSound
            ) {
                bootSound = it
                settings.bootSoundEnabled = it
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
            ) {
                Text("清空小计里程", color = SportColors.Red)
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}


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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0x10FFFFFF))
            .padding(vertical = 8.dp)
    )
}

@Composable
private fun SwitchRow(
    title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = Color.White)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, fontSize = 10.sp, color = SportColors.Secondary)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    title: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>,
    steps: Int, onChange: (Float) -> Unit
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
    ) {
        Text(text, fontSize = 11.sp, color = Color.White)
    }
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
    ) {
        Text(text, fontSize = 11.sp, color = if (active) Color.Black else Color.White)
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(key, fontSize = 13.sp, color = SportColors.Secondary)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = Color.White)
    }
}
