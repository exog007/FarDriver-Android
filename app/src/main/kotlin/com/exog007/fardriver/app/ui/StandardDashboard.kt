package com.exog007.fardriver.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exog007.fardriver.protocol.ControllerData

/**
 * 标准模式（竖屏卡片式）
 *
 * 对应 iOS 版标准风格：状态卡 → 档位条 → 转速/功率环 → 温度 → 电池
 */
@Composable
fun StandardDashboard(
    ctrl: ControllerData,
    displaySpeed: Double,
    tripDistance: String,
    tripConsumption: String,
    tripAvgPower: String,
    bikeConnected: Boolean
) {
    val amber = SportColors.Amber
    val cyan = SportColors.Cyan

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SportColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 状态卡
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (bikeConnected) SportColors.Green else Color.Gray)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (bikeConnected) "已连接控制器" else "未连接",
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
        }

        // 档位条
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("档位", fontSize = 11.sp, color = SportColors.Secondary)
                Spacer(Modifier.width(12.dp))
                GearChip("P", ctrl.pGearOn, SportColors.Red)
                Spacer(Modifier.width(6.dp))
                GearChip("1", ctrl.gear == 1 && !ctrl.pGearOn, amber)
                Spacer(Modifier.width(6.dp))
                GearChip("2", ctrl.gear == 2 && !ctrl.pGearOn, amber)
                Spacer(Modifier.width(6.dp))
                GearChip("3", ctrl.gear == 3 && !ctrl.pGearOn, amber)
                Spacer(Modifier.weight(1f))
                Text("%.0f km/h".format(displaySpeed), fontSize = 15.sp,
                     fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = amber)
            }
        }

        // 转速 + 功率
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GaugeCard(
                title = "电机转速",
                value = "%,d".format(ctrl.rpm.toInt()),
                unit = "rpm",
                color = amber,
                progress = (ctrl.rpm / 12000.0).coerceIn(0.0, 1.0).toFloat(),
                modifier = Modifier.weight(1f)
            )
            GaugeCard(
                title = "实时功率",
                value = "${ctrl.power.toInt()}",
                unit = "W",
                color = if (ctrl.isRegen) SportColors.Green else amber,
                progress = (ctrl.power / 5000.0).coerceIn(0.0, 1.0).toFloat(),
                modifier = Modifier.weight(1f)
            )
        }

        // 温度
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("MOS 温度", "${ctrl.mosTemp}", "°C",
                     if (ctrl.mosTemp > 60) SportColors.Red else SportColors.Cyan,
                     Modifier.weight(1f))
            StatCard("电机温度", "${ctrl.motorTemp}", "°C",
                     if (ctrl.motorTemp > 50) SportColors.Red else SportColors.Cyan,
                     Modifier.weight(1f))
        }

        // 电池
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("电压", "%.1f".format(ctrl.voltage), "V", SportColors.Cyan, Modifier.weight(1f))
            StatCard("电流", "%.1f".format(ctrl.current), "A",
                     if (ctrl.isRegen) SportColors.Green else SportColors.Cyan, Modifier.weight(1f))
        }

        // 行程
        Card {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                TripStat("小计", tripDistance, "km", cyan)
                TripStat("百公里", tripConsumption, "kWh", amber)
                TripStat("平均", tripAvgPower, "W", SportColors.Secondary)
            }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x14FFFFFF), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0x10FFFFFF), RoundedCornerShape(14.dp))
            .padding(14.dp),
        content = content
    )
}

@Composable
private fun GearChip(label: String, active: Boolean, color: Color) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) color else Color(0x14FFFFFF))
            .border(1.dp, if (active) color else Color(0x33FFFFFF), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) Color.Black else SportColors.Secondary
        )
    }
}

@Composable
private fun GaugeCard(
    title: String, value: String, unit: String,
    color: Color, progress: Float, modifier: Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0x14FFFFFF), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0x10FFFFFF), RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 11.sp, color = SportColors.Secondary)
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 32.sp, fontWeight = FontWeight.Black,
             fontFamily = FontFamily.Monospace, color = Color.White)
        Text(unit, fontSize = 10.sp, color = SportColors.Secondary)
        Spacer(Modifier.height(8.dp))
        // 进度条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x1FFFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, unit: String, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(Color(0x14FFFFFF), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0x10FFFFFF), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(title, fontSize = 11.sp, color = SportColors.Secondary)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                 fontFamily = FontFamily.Monospace, color = color)
            Spacer(Modifier.width(3.dp))
            Text(unit, fontSize = 11.sp, color = SportColors.Secondary,
                 modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

@Composable
private fun TripStat(title: String, value: String, unit: String, color: Color) {
    Column {
        Text(title, fontSize = 10.sp, color = SportColors.Secondary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                 fontFamily = FontFamily.Monospace, color = color)
            Spacer(Modifier.width(2.dp))
            Text(unit, fontSize = 9.sp, color = SportColors.Secondary,
                 modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}
