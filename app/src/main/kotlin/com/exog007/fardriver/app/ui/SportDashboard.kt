package com.exog007.fardriver.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exog007.fardriver.app.settings.brightnessScaled
import com.exog007.fardriver.protocol.AntBmsData
import com.exog007.fardriver.protocol.BatteryPercent
import com.exog007.fardriver.protocol.ControllerData
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * 竞技仪表（全自适应版）
 *
 * 所有字号和控件尺寸由 [AdaptiveSize] 按屏幕宽高计算，
 * 从小屏手机到平板都能正常显示。
 */
@Composable
fun SportDashboard(
    ctrl: ControllerData,
    bms: AntBmsData,
    displaySpeed: Double,
    tripDistance: String,
    tripConsumption: String,
    tripAvgPower: String,
    rpmMultiplier: Double,
    rpmScaleMax: Double,
    battFull: Double,
    battEmpty: Double,
    bikeConnected: Boolean,
    bmsConnected: Boolean,
    tickKey: Int = 0,
    brightness: Double = 1.0
) {
    val sz = rememberAdaptiveSize()

    // 自检动画
    var bootProgress by remember { mutableStateOf(1f) }
    var bootAnimating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        bootAnimating = true
        bootProgress = 0f
        val steps = 120
        for (i in 1..steps) {
            delay(20)
            val p = i.toFloat() / steps
            bootProgress = if (p < 0.5f) p * 2f else (1f - p) * 2f
        }
        bootProgress = 0f
        bootAnimating = false
    }

    val inSelfTest = !bikeConnected || bootAnimating

    val amber = SportColors.Amber.brightnessScaled(brightness)
    val cyan = SportColors.Cyan.brightnessScaled(brightness)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SportColors.Background)
            .padding(sz.pad),
        verticalArrangement = Arrangement.spacedBy(sz.pad * 0.6f)
    ) {
        TopBar(sz, ctrl, inSelfTest, bikeConnected, amber, cyan)

        RpmBand(
            sz = sz,
            rpm = if (bootAnimating) rpmScaleMax * bootProgress else ctrl.rpm * rpmMultiplier,
            scaleMax = rpmScaleMax,
            amber = amber
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            MiddleRow(
                sz = sz,
                ctrl = ctrl,
                bms = bms,
                displaySpeed = if (bootAnimating) 299.0 * bootProgress else displaySpeed,
                tripDistance = tripDistance,
                tripConsumption = tripConsumption,
                battFull = battFull,
                battEmpty = battEmpty,
                bikeConnected = bikeConnected,
                bmsConnected = bmsConnected,
                inSelfTest = inSelfTest,
                amber = amber,
                cyan = cyan
            )
        }

        BottomRow(sz, ctrl, tripAvgPower, amber, cyan)
    }
}

// ══════════════════════════════════════════
// 顶部栏
// ══════════════════════════════════════════

@Composable
private fun TopBar(
    sz: AdaptiveSize,
    ctrl: ControllerData,
    inSelfTest: Boolean,
    bikeConnected: Boolean,
    amber: Color,
    cyan: Color
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) { now = Date(); delay(1000) }
    }
    val dateFmt = remember { SimpleDateFormat("M月d日 EEE", Locale.CHINA) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.US) }

    Row(
        modifier = Modifier.fillMaxWidth().height(sz.topBarH),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚡", fontSize = sz.lampFont, color = SportColors.Red)
            Spacer(Modifier.width(4.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("车辆状态诊断", fontSize = sz.titleFont,
                         fontWeight = FontWeight.Black, color = amber)
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                if (inSelfTest) amber else SportColors.Green,
                                RoundedCornerShape(2.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            if (inSelfTest) "自检中" else "正常",
                            fontSize = sz.subTitleFont,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                }
                Text("VEHICLE DIAGNOSTIC", fontSize = sz.subTitleFont,
                     color = amber.copy(alpha = 0.55f))
            }
        }

        Spacer(Modifier.weight(1f))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(dateFmt.format(now), fontSize = sz.dateFont,
                 color = amber.copy(alpha = 0.65f))
            Text(timeFmt.format(now), fontSize = sz.timeFont,
                 fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = amber)
        }

        Spacer(Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚠", fontSize = sz.lampFont,
                 color = if (inSelfTest || ctrl.braking) SportColors.Red
                         else SportColors.Red.copy(alpha = 0.16f))
            Text("🔥", fontSize = sz.lampFont * 0.9f,
                 color = if (inSelfTest || ctrl.mosTemp >= 60) SportColors.LampYellow
                         else SportColors.LampYellow.copy(alpha = 0.16f))
            Text("🌡", fontSize = sz.lampFont * 0.9f,
                 color = if (inSelfTest || ctrl.mosTemp > 50) SportColors.LampYellow
                         else SportColors.LampYellow.copy(alpha = 0.16f))
            Text("▭", fontSize = sz.lampFont,
                 color = if (inSelfTest) SportColors.LampAmber
                         else SportColors.LampAmber.copy(alpha = 0.16f))
        }
    }
}

// ══════════════════════════════════════════
// 转速带
// ══════════════════════════════════════════

@Composable
private fun RpmBand(sz: AdaptiveSize, rpm: Double, scaleMax: Double, amber: Color) {
    val ratio = (rpm / scaleMax).coerceIn(0.0, 1.0).toFloat()
    val animatedRatio by animateFloatAsState(ratio, tween(120), label = "rpm")

    Row(
        modifier = Modifier.fillMaxWidth().height(sz.rpmBandH),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.width(sz.rpmFont.value.dp * 5)) {
            Text("ENGINE  RPM / 转速", fontSize = sz.labelFont,
                 fontWeight = FontWeight.SemiBold, color = amber.copy(alpha = 0.7f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "%,d".format(rpm.toInt()),
                    fontSize = sz.rpmFont,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = amber,
                    maxLines = 1
                )
                Spacer(Modifier.width(2.dp))
                Text("rpm", fontSize = sz.unitFont,
                     color = amber.copy(alpha = 0.6f),
                     modifier = Modifier.padding(bottom = 2.dp))
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            // 细刻度
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(40) { i ->
                    Box(
                        Modifier
                            .width(1.5.dp)
                            .height(if (i % 2 == 0) 4.dp else 2.dp)
                            .background(amber.copy(alpha = 0.25f))
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Canvas(Modifier.fillMaxWidth().height(sz.rpmBandH * 0.24f)) {
                val w = size.width
                val h = size.height
                drawRect(amber.copy(alpha = 0.12f), size = Size(w, h))
                drawRect(SportColors.Red.copy(alpha = 0.22f),
                         topLeft = Offset(w * 0.75f, 0f), size = Size(w * 0.25f, h))
                drawRect(
                    if (animatedRatio > 0.85f) SportColors.Red else amber,
                    size = Size(w * animatedRatio, h)
                )
                for (i in 1..19) {
                    val x = w * i / 20f
                    drawRect(Color.Black.copy(alpha = 0.85f),
                             topLeft = Offset(x - 1f, 0f), size = Size(2f, h))
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0, 2000, 4000, 6000, 8000, 10000, 12000).forEach {
                    Text("%,d".format(it), fontSize = sz.labelFont * 0.9f,
                         fontFamily = FontFamily.Monospace,
                         color = amber.copy(alpha = 0.5f))
                }
            }
        }
    }
}

// ══════════════════════════════════════════
// 中间行
// ══════════════════════════════════════════

@Composable
private fun MiddleRow(
    sz: AdaptiveSize,
    ctrl: ControllerData,
    bms: AntBmsData,
    displaySpeed: Double,
    tripDistance: String,
    tripConsumption: String,
    battFull: Double,
    battEmpty: Double,
    bikeConnected: Boolean,
    bmsConnected: Boolean,
    inSelfTest: Boolean,
    amber: Color,
    cyan: Color
) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        // 连接灯
        Column(
            modifier = Modifier.width(sz.gearBox * 1.2f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(sz.labelFont.value.dp * 0.9f)
                    .background(
                        if (bikeConnected) SportColors.Green else amber.copy(alpha = 0.25f),
                        RoundedCornerShape(50)
                    )
            )
            Spacer(Modifier.height(3.dp))
            Box(Modifier.width(1.dp).height(sz.gearBox).background(amber.copy(alpha = 0.3f)))
        }

        // 速度
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "%.0f".format(displaySpeed),
                fontSize = sz.speedFont,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = amber,
                maxLines = 1
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "km/h", fontSize = sz.speedUnitFont, fontWeight = FontWeight.SemiBold,
                color = amber.copy(alpha = 0.75f),
                modifier = Modifier.padding(bottom = sz.speedFont.value.dp * 0.15f)
            )
        }

        Spacer(Modifier.weight(1f))

        // 中列：档位居中 + 里程贴底
        Box(Modifier.width(sz.gearBox * 5.4f).fillMaxHeight()) {
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(sz.gearBox * 0.22f)
            ) {
                GearBox(sz, "P", ctrl.pGearOn, SportColors.Red)
                GearBox(sz, "1", ctrl.gear == 1 && !ctrl.pGearOn, amber)
                GearBox(sz, "2", ctrl.gear == 2 && !ctrl.pGearOn, amber)
                GearBox(sz, "3", ctrl.gear == 3 && !ctrl.pGearOn, amber)
            }
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(sz.odoFont.value.dp * 0.6f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ODO", fontSize = sz.odoLabelFont, color = amber.copy(alpha = 0.5f))
                    Spacer(Modifier.width(2.dp))
                    Text(tripDistance, fontSize = sz.odoFont, fontWeight = FontWeight.Bold,
                         fontFamily = FontFamily.Monospace, color = cyan)
                    Spacer(Modifier.width(2.dp))
                    Text("km", fontSize = sz.odoLabelFont, color = amber.copy(alpha = 0.5f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tripConsumption, fontSize = sz.odoFont, fontWeight = FontWeight.Bold,
                         fontFamily = FontFamily.Monospace, color = amber)
                    Spacer(Modifier.width(2.dp))
                    Text("kWh/100", fontSize = sz.odoLabelFont, color = amber.copy(alpha = 0.5f))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // 右：BMS 温度 + 油表
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sz.pad * 0.8f)
        ) {
            if (!bikeConnected || bmsConnected) {
                BmsTempBlock(sz, bms, bmsConnected, amber, cyan)
            }
            FuelGauge(
                sz = sz,
                percent = BatteryPercent.compute(bms.totalVoltage, battFull, battEmpty),
                inSelfTest = inSelfTest
            )
        }
    }
}

@Composable
private fun GearBox(sz: AdaptiveSize, label: String, active: Boolean, activeColor: Color) {
    Box(
        modifier = Modifier
            .size(sz.gearBox)
            .background(if (active) activeColor else Color.Transparent)
            .border(1.dp, if (active) activeColor else SportColors.Amber.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = sz.gearFont, fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = if (active) Color.Black else SportColors.Amber.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun BmsTempBlock(
    sz: AdaptiveSize, bms: AntBmsData, connected: Boolean, amber: Color, cyan: Color
) {
    val t = bms.mosTemp
    val hot = t > 55
    Column(
        modifier = Modifier
            .background(amber.copy(alpha = 0.07f), RoundedCornerShape(3.dp))
            .border(
                1.dp,
                if (hot) SportColors.Red.copy(alpha = 0.7f) else amber.copy(alpha = 0.28f),
                RoundedCornerShape(3.dp)
            )
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("BMS", fontSize = sz.bmsLabelFont, color = amber.copy(alpha = 0.55f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (connected && t > 0) "$t" else "--",
                fontSize = sz.bmsFont, fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = if (hot) SportColors.Red else cyan
            )
            Text("°", fontSize = sz.bmsLabelFont, color = amber.copy(alpha = 0.55f))
        }
        Text("MOS", fontSize = sz.bmsLabelFont, color = amber.copy(alpha = 0.45f))
    }
}

// ══════════════════════════════════════════
// 油表
// ══════════════════════════════════════════

@Composable
private fun FuelGauge(sz: AdaptiveSize, percent: Double, inSelfTest: Boolean) {
    val density = LocalDensity.current
    val boxW = sz.gaugeW
    val boxH = sz.gaugeH

    Box(Modifier.size(boxW, boxH)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // 圆心：靠左，垂直略偏上留出 F 标签
            val cx = w * 0.12f
            val cy = h * 0.52f
            val r = minOf(w * 0.80f, h * 0.40f)
            val startDeg = -55f
            val sweepDeg = 110f
            val p = percent.coerceIn(0.0, 1.0).toFloat()

            fun pt(t: Float, radius: Float): Offset {
                val deg = startDeg + sweepDeg * t
                val rad = Math.toRadians(deg.toDouble())
                return Offset(
                    cx + (cos(rad) * radius).toFloat(),
                    cy + (sin(rad) * radius).toFloat()
                )
            }

            // 弧
            val path = Path()
            val steps = 70
            for (i in 0..steps) {
                val o = pt(i.toFloat() / steps, r)
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, SportColors.Amber, style = Stroke(width = r * 0.048f))

            // 刻度
            listOf(0.06f to 0.20f, 0.38f to 0.12f, 0.62f to 0.12f, 0.94f to 0.20f).forEach { (t, len) ->
                val r1 = pt(t, r - r * 0.05f)
                val r2 = pt(t, r - r * 0.05f - r * len)
                drawLine(SportColors.Amber, r1, r2,
                         strokeWidth = r * 0.052f, cap = StrokeCap.Round)
            }

            // 指针
            val needleLen = r - r * 0.05f - r * 0.20f + r * 0.065f
            val angle = (startDeg + 3f) + (sweepDeg - 6f) * (1f - p)
            val rad = Math.toRadians(angle.toDouble())
            val root = Offset(cx, cy)
            val midLen = needleLen * 0.42f
            val mid = Offset(
                cx + (cos(rad) * midLen).toFloat(),
                cy + (sin(rad) * midLen).toFloat()
            )
            val tip = Offset(
                cx + (cos(rad) * needleLen).toFloat(),
                cy + (sin(rad) * needleLen).toFloat()
            )
            drawLine(SportColors.Red, root, mid, strokeWidth = r * 0.13f, cap = StrokeCap.Round)
            drawLine(SportColors.Red, mid, tip, strokeWidth = r * 0.06f, cap = StrokeCap.Round)
            drawCircle(SportColors.Red, radius = r * 0.045f, center = root)
        }

        // F / E 标签
        val wPx = with(density) { boxW.toPx() }
        val hPx = with(density) { boxH.toPx() }
        val cxPx = wPx * 0.12f
        val cyPx = hPx * 0.52f
        val rPx = minOf(wPx * 0.80f, hPx * 0.40f)
        fun labelOffset(t: Float): Pair<Float, Float> {
            val deg = -55f + 110f * t
            val rad = Math.toRadians(deg.toDouble())
            return Pair(
                cxPx + (cos(rad) * (rPx + rPx * 0.22f)).toFloat(),
                cyPx + (sin(rad) * (rPx + rPx * 0.22f)).toFloat()
            )
        }
        val (fx, fy) = labelOffset(0f)
        val (ex, ey) = labelOffset(1f)
        val fDp = with(density) { fx.toDp() }
        val fyDp = with(density) { fy.toDp() }
        val eDp = with(density) { ex.toDp() }
        val eyDp = with(density) { ey.toDp() }
        Text(
            "F", fontSize = sz.odoFont, fontWeight = FontWeight.Bold,
            color = SportColors.Amber,
            modifier = Modifier.offset(x = fDp - sz.odoFont.value.dp * 0.3f, y = fyDp - sz.odoFont.value.dp * 0.5f)
        )
        Text(
            "E", fontSize = sz.odoFont, fontWeight = FontWeight.Bold,
            color = SportColors.Amber,
            modifier = Modifier.offset(x = eDp - sz.odoFont.value.dp * 0.3f, y = eyDp - sz.odoFont.value.dp * 0.5f)
        )
        // 加油灯
        Text(
            "⛽", fontSize = sz.odoFont,
            color = if (inSelfTest || percent < 0.30) SportColors.LampAmber
                    else SportColors.Amber.copy(alpha = 0.16f)
        )
    }
}

// ══════════════════════════════════════════
// 底部 6 格
// ══════════════════════════════════════════

@Composable
private fun BottomRow(
    sz: AdaptiveSize, ctrl: ControllerData, avgPower: String, amber: Color, cyan: Color
) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(amber.copy(alpha = 0.35f)))
        Row(
            Modifier.fillMaxWidth().height(sz.bottomRowH),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DataCell(sz, "电压", "%.1f".format(ctrl.voltage), "V", cyan, Modifier.weight(1f))
            VDiv()
            DataCell(sz, "电流", "%.1f".format(ctrl.current), "A",
                     if (ctrl.isRegen) SportColors.Green else cyan, Modifier.weight(1f))
            VDiv()
            DataCell(sz, "功率", powerText(ctrl), "W",
                     if (ctrl.isRegen) SportColors.Green else amber, Modifier.weight(1f))
            VDiv()
            DataCell(sz, "MOS", "${ctrl.mosTemp}", "°C",
                     if (ctrl.mosTemp > 60) SportColors.Red else cyan, Modifier.weight(1f))
            VDiv()
            DataCell(sz, "电机", "${ctrl.motorTemp}", "°C",
                     if (ctrl.motorTemp > 50) SportColors.Red else cyan, Modifier.weight(1f))
            VDiv()
            DataCell(sz, "平均", avgPower, "W", cyan, Modifier.weight(1f))
        }
    }
}

private fun powerText(ctrl: ControllerData): String {
    val p = ctrl.power.toInt()
    return if (ctrl.power < -1) "−${-p}" else "$p"
}

@Composable
private fun VDiv() {
    Box(Modifier.width(1.dp).height(24.dp).background(SportColors.Amber.copy(alpha = 0.25f)))
}

@Composable
private fun DataCell(
    sz: AdaptiveSize, title: String, value: String, unit: String, color: Color, modifier: Modifier
) {
    Column(modifier = modifier.padding(start = sz.pad * 0.5f)) {
        Text(title, fontSize = sz.labelFont, fontWeight = FontWeight.SemiBold,
             color = SportColors.Amber.copy(alpha = 0.6f), maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value, fontSize = sz.valueFont, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, color = color, maxLines = 1
            )
            Spacer(Modifier.width(1.dp))
            Text(unit, fontSize = sz.unitFont,
                 color = SportColors.Amber.copy(alpha = 0.55f),
                 modifier = Modifier.padding(bottom = 1.dp), maxLines = 1)
        }
    }
}
