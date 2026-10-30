package com.exog007.fardriver.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exog007.fardriver.protocol.AntBmsData
import com.exog007.fardriver.protocol.ControllerData
import com.exog007.fardriver.protocol.BatteryPercent
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * 竞技仪表（完整版）
 *
 * 布局和 iOS 版一致：
 *   顶部：标题 + 日期时间 + 天气占位 + 故障灯 + 药丸
 *   转速带：大数字 + 刻度条
 *   中间：速度 / 档位 / 油表
 *   底部：六格数据
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
    bmsConnected: Boolean
) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SportColors.Background)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TopBar(
            ctrl = ctrl,
            inSelfTest = inSelfTest,
            bikeConnected = bikeConnected
        )

        RpmBand(
            rpm = if (bootAnimating) rpmScaleMax * bootProgress else ctrl.rpm * rpmMultiplier,
            scaleMax = rpmScaleMax
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            MiddleRow(
                ctrl = ctrl,
                bms = bms,
                displaySpeed = if (bootAnimating) 299.0 * bootProgress else displaySpeed,
                tripDistance = tripDistance,
                tripConsumption = tripConsumption,
                battFull = battFull,
                battEmpty = battEmpty,
                bikeConnected = bikeConnected,
                bmsConnected = bmsConnected,
                inSelfTest = inSelfTest
            )
        }

        BottomRow(
            ctrl = ctrl,
            avgPower = tripAvgPower
        )
    }
}

// ══════════════════════════════════════════
// 顶部栏
// ══════════════════════════════════════════

@Composable
private fun TopBar(ctrl: ControllerData, inSelfTest: Boolean, bikeConnected: Boolean) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }

    val dateFmt = remember { SimpleDateFormat("M月d日 EEE", Locale.CHINA) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.US) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左：标题
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "⚡",
                fontSize = 15.sp,
                color = SportColors.Red
            )
            Spacer(Modifier.width(4.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "车辆状态诊断",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = SportColors.Amber
                    )
                    Spacer(Modifier.width(5.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                if (inSelfTest) SportColors.Amber else SportColors.Green,
                                RoundedCornerShape(2.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (inSelfTest) "自检中" else "正常",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                }
                Text(
                    text = "VEHICLE DIAGNOSTIC",
                    fontSize = 6.sp,
                    color = SportColors.Amber.copy(alpha = 0.55f),
                    letterSpacing = 1.2.sp
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // 中：日期时间
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = dateFmt.format(now),
                fontSize = 9.sp,
                color = SportColors.Amber.copy(alpha = 0.65f)
            )
            Text(
                text = timeFmt.format(now),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = SportColors.Amber
            )
        }

        Spacer(Modifier.weight(1f))

        // 右：故障灯
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 刹车（红圆圈感叹号）
            Text(
                text = "⚠",
                fontSize = 15.sp,
                color = if (inSelfTest || ctrl.braking) SportColors.Red
                        else SportColors.Red.copy(alpha = 0.16f)
            )
            // 发动机（MOS ≥ 60）
            Text(
                text = "🔥",
                fontSize = 13.sp,
                color = if (inSelfTest || ctrl.mosTemp >= 60) SportColors.LampYellow
                        else SportColors.LampYellow.copy(alpha = 0.16f)
            )
            // 水温（MOS > 50）
            Text(
                text = "🌡",
                fontSize = 13.sp,
                color = if (inSelfTest || ctrl.mosTemp > 50) SportColors.LampYellow
                        else SportColors.LampYellow.copy(alpha = 0.16f)
            )
            // 电瓶（用简单方块代替）
            Text(
                text = "▭",
                fontSize = 14.sp,
                color = if (inSelfTest) SportColors.LampAmber
                        else SportColors.LampAmber.copy(alpha = 0.16f)
            )
        }

        Spacer(Modifier.width(8.dp))

        // 药丸
        Pill("DIAGNOSTIC", !inSelfTest)
        Spacer(Modifier.width(4.dp))
        Pill("SETTINGS", true)
    }
}

@Composable
private fun Pill(text: String, on: Boolean) {
    Row(
        modifier = Modifier
            .border(
                1.dp,
                if (on) SportColors.Cyan.copy(alpha = 0.7f)
                else SportColors.Amber.copy(alpha = 0.25f),
                RoundedCornerShape(2.dp)
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(
                    if (on) SportColors.Cyan else SportColors.Amber.copy(alpha = 0.3f),
                    RoundedCornerShape(2.dp)
                )
        )
        Text(
            text = text,
            fontSize = 6.sp,
            fontWeight = FontWeight.Bold,
            color = if (on) SportColors.Cyan else SportColors.Amber.copy(alpha = 0.45f),
            letterSpacing = 0.6.sp
        )
    }
}

// ══════════════════════════════════════════
// 转速带
// ══════════════════════════════════════════

@Composable
private fun RpmBand(rpm: Double, scaleMax: Double) {
    val ratio = (rpm / scaleMax).coerceIn(0.0, 1.0).toFloat()
    val animatedRatio by animateFloatAsState(
        targetValue = ratio,
        animationSpec = tween(120),
        label = "rpm"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // 左：大数字
        Column(modifier = Modifier.width(130.dp)) {
            Text(
                text = "ENGINE  RPM / 转速",
                fontSize = 7.sp,
                fontWeight = FontWeight.SemiBold,
                color = SportColors.Amber.copy(alpha = 0.7f),
                letterSpacing = 0.8.sp
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "%,d".format(rpm.toInt()),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = SportColors.Amber
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "rpm",
                    fontSize = 9.sp,
                    color = SportColors.Amber.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }

        // 右：刻度条
        Column(modifier = Modifier.weight(1f)) {
            // 上方细刻度
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(40) { i ->
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(if (i % 2 == 0) 5.dp else 3.dp)
                            .background(SportColors.Amber.copy(alpha = 0.25f))
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            // 主进度条
            Canvas(modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
            ) {
                val w = size.width
                val h = size.height
                // 背景
                drawRect(
                    color = SportColors.Amber.copy(alpha = 0.12f),
                    size = Size(w, h)
                )
                // 红区（后 25%）
                drawRect(
                    color = SportColors.Red.copy(alpha = 0.22f),
                    topLeft = Offset(w * 0.75f, 0f),
                    size = Size(w * 0.25f, h)
                )
                // 当前转速
                drawRect(
                    color = if (animatedRatio > 0.85f) SportColors.Red else SportColors.Amber,
                    size = Size(w * animatedRatio, h)
                )
                // 分段缺口
                for (i in 1..19) {
                    val x = w * i / 20f
                    drawRect(
                        color = Color.Black.copy(alpha = 0.85f),
                        topLeft = Offset(x - 1f, 0f),
                        size = Size(2f, h)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            // 刻度数字
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val labels = listOf(0, 2000, 4000, 6000, 8000, 10000, 12000)
                labels.forEach { n ->
                    Text(
                        text = "%,d".format(n),
                        fontSize = 6.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SportColors.Amber.copy(alpha = 0.5f)
                    )
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
    ctrl: ControllerData,
    bms: AntBmsData,
    displaySpeed: Double,
    tripDistance: String,
    tripConsumption: String,
    battFull: Double,
    battEmpty: Double,
    bikeConnected: Boolean,
    bmsConnected: Boolean,
    inSelfTest: Boolean
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左：连接灯 + 速度
        Column(
            modifier = Modifier.width(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(
                        if (bikeConnected) SportColors.Green else SportColors.Amber.copy(alpha = 0.25f),
                        RoundedCornerShape(5.dp)
                    )
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(26.dp)
                    .background(SportColors.Amber.copy(alpha = 0.3f))
            )
        }

        // 速度大数字
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "%.0f".format(displaySpeed),
                fontSize = 68.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = SportColors.Amber
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "km/h",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = SportColors.Amber.copy(alpha = 0.75f),
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        // 中间列：档位（居中）+ 里程（底部）
        Box(
            modifier = Modifier
                .width(140.dp)
                .fillMaxHeight()
        ) {
            // 档位框，垂直居中
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GearBox("P", ctrl.pGearOn, SportColors.Red)
                GearBox("1", ctrl.gear == 1 && !ctrl.pGearOn, SportColors.Amber)
                GearBox("2", ctrl.gear == 2 && !ctrl.pGearOn, SportColors.Amber)
                GearBox("3", ctrl.gear == 3 && !ctrl.pGearOn, SportColors.Amber)
            }
            // 里程，贴底
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ODO", fontSize = 6.sp, color = SportColors.Amber.copy(alpha = 0.5f))
                    Spacer(Modifier.width(2.dp))
                    Text(tripDistance, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                         fontFamily = FontFamily.Monospace, color = SportColors.Cyan)
                    Spacer(Modifier.width(2.dp))
                    Text("km", fontSize = 6.sp, color = SportColors.Amber.copy(alpha = 0.5f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tripConsumption, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                         fontFamily = FontFamily.Monospace, color = SportColors.Amber)
                    Spacer(Modifier.width(2.dp))
                    Text("kWh/100", fontSize = 6.sp, color = SportColors.Amber.copy(alpha = 0.5f))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // 右：BMS 温度 + 油表
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (bikeConnected == false || bmsConnected) {
                BmsTempBlock(bms, bmsConnected)
            }
            FuelGauge(
                percent = BatteryPercent.compute(bms.totalVoltage, battFull, battEmpty),
                inSelfTest = inSelfTest
            )
        }
    }
}

@Composable
private fun GearBox(label: String, active: Boolean, activeColor: Color) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .background(if (active) activeColor else Color.Transparent)
            .border(
                1.dp,
                if (active) activeColor else SportColors.Amber.copy(alpha = 0.45f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = if (active) Color.Black else SportColors.Amber.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun BmsTempBlock(bms: AntBmsData, connected: Boolean) {
    val t = bms.mosTemp
    val hot = t > 55
    Column(
        modifier = Modifier
            .background(SportColors.Amber.copy(alpha = 0.07f), RoundedCornerShape(3.dp))
            .border(1.dp,
                    if (hot) SportColors.Red.copy(alpha = 0.7f)
                    else SportColors.Amber.copy(alpha = 0.28f),
                    RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("BMS", fontSize = 6.sp, color = SportColors.Amber.copy(alpha = 0.55f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (connected && t > 0) "$t" else "--",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = if (hot) SportColors.Red else SportColors.Cyan
            )
            Text("°", fontSize = 7.sp, color = SportColors.Amber.copy(alpha = 0.55f))
        }
        Text("MOS", fontSize = 6.sp, color = SportColors.Amber.copy(alpha = 0.45f))
    }
}

// ══════════════════════════════════════════
// 油表（扇形，和 iOS 版一致）
// ══════════════════════════════════════════

@Composable
private fun FuelGauge(percent: Double, inSelfTest: Boolean) {
    val w = 72f
    val h = 116f
    val cx = 8f
    val cy = 58f
    val r = 46f
    val startDeg = -55f
    val sweepDeg = 110f

    fun px(t: Float, radius: Float): Offset {
        val deg = startDeg + sweepDeg * t
        val rad = Math.toRadians(deg.toDouble())
        return Offset(
            cx + (cos(rad) * radius).toFloat(),
            cy + (sin(rad) * radius).toFloat()
        )
    }

    val p = percent.coerceIn(0.0, 1.0).toFloat()

    Box(modifier = Modifier.size(w.dp, h.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // ① 弧
            val path = Path()
            val steps = 70
            for (i in 0..steps) {
                val pt = px(i.toFloat() / steps, r)
                if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
            }
            drawPath(path, SportColors.Amber, style = Stroke(width = 2.2f))

            // ② 刻度（4 根）
            val ticks = listOf(0.06f to 9f, 0.38f to 5.5f, 0.62f to 5.5f, 0.94f to 9f)
            ticks.forEach { (t, len) ->
                val p1 = px(t, r - 2.5f)
                val p2 = px(t, r - 2.5f - len)
                drawLine(SportColors.Amber, p1, p2, strokeWidth = 2.4f, cap = StrokeCap.Round)
            }

            // ③ 指针（红，根部粗尖端细）
            val needleLen = (r - 2.5f) - 9f + 3f
            val angleDeg = (startDeg + 3f) + (sweepDeg - 6f) * (1f - p)
            val rad = Math.toRadians(angleDeg.toDouble())
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
            drawLine(SportColors.Red, root, mid, strokeWidth = 6f, cap = StrokeCap.Round)
            drawLine(SportColors.Red, mid, tip, strokeWidth = 2.8f, cap = StrokeCap.Round)
            // 圆心
            drawCircle(SportColors.Red, radius = 2f, center = root)
        }

        // F / E 标签（用 Compose 文字，位置按半径计算）
        val density = LocalDensity.current
        val fPos = px(0f, r + 10f)
        val ePos = px(1f, r + 10f)
        val fX = with(density) { fPos.x.toDp() }
        val fY = with(density) { fPos.y.toDp() }
        val eX = with(density) { ePos.x.toDp() }
        val eY = with(density) { ePos.y.toDp() }
        Text(
            text = "F",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = SportColors.Amber,
            modifier = Modifier.offset(x = fX - 6.dp, y = fY - 8.dp)
        )
        Text(
            text = "E",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = SportColors.Amber,
            modifier = Modifier.offset(x = eX - 6.dp, y = eY - 8.dp)
        )

        // 加油灯（左上角）
        Text(
            text = "⛽",
            fontSize = 13.sp,
            color = if (inSelfTest || p < 0.30f) SportColors.LampAmber
                    else SportColors.Amber.copy(alpha = 0.16f)
        )
    }
}

// ══════════════════════════════════════════
// 底部数据行（6 格）
// ══════════════════════════════════════════

@Composable
private fun BottomRow(ctrl: ControllerData, avgPower: String) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SportColors.Amber.copy(alpha = 0.35f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DataCell("电压", "%.1f".format(ctrl.voltage), "V", SportColors.Cyan, Modifier.weight(1f))
            Divider1()
            DataCell("电流", "%.1f".format(ctrl.current), "A",
                     if (ctrl.isRegen) SportColors.Green else SportColors.Cyan, Modifier.weight(1f))
            Divider1()
            DataCell("功率", powerText(ctrl), "W",
                     if (ctrl.isRegen) SportColors.Green else SportColors.Amber, Modifier.weight(1f))
            Divider1()
            DataCell("MOS", "${ctrl.mosTemp}", "°C",
                     if (ctrl.mosTemp > 60) SportColors.Red else SportColors.Cyan, Modifier.weight(1f))
            Divider1()
            DataCell("电机", "${ctrl.motorTemp}", "°C",
                     if (ctrl.motorTemp > 50) SportColors.Red else SportColors.Cyan, Modifier.weight(1f))
            Divider1()
            DataCell("平均", avgPower, "W", SportColors.Cyan, Modifier.weight(1f))
        }
    }
}

private fun powerText(ctrl: ControllerData): String {
    val p = ctrl.power.toInt()
    return if (ctrl.power < -1) "−${-p}" else "$p"
}

@Composable
private fun Divider1() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(24.dp)
            .background(SportColors.Amber.copy(alpha = 0.25f))
    )
}

@Composable
private fun DataCell(title: String, value: String, unit: String, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier.padding(start = 6.dp)
    ) {
        Text(title, fontSize = 7.sp, fontWeight = FontWeight.SemiBold,
             color = SportColors.Amber.copy(alpha = 0.6f), letterSpacing = 0.6.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = color,
                maxLines = 1
            )
            Spacer(Modifier.width(2.dp))
            Text(unit, fontSize = 8.sp, color = SportColors.Amber.copy(alpha = 0.55f),
                 modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}
