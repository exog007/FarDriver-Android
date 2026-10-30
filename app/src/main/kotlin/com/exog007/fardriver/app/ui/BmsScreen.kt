package com.exog007.fardriver.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exog007.fardriver.protocol.AntBmsData
import com.exog007.fardriver.protocol.BatteryChemistry
import com.exog007.fardriver.protocol.BatteryHealth
import com.exog007.fardriver.protocol.HealthLevel

/**
 * BMS 详情页
 *
 * 卡片顺序（和 iOS 版对齐）：
 *   SOC 主卡片 → 单体极值 → 状态 → 温度 → 单体电压网格 → 充电建议
 */
@Composable
fun BmsScreen(
    bms: AntBmsData,
    connected: Boolean,
    chemistry: BatteryChemistry,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0C))
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "‹ 返回",
                fontSize = 16.sp,
                color = SportColors.Cyan,
                modifier = Modifier
                    .background(Color.Transparent)
                    .padding(end = 16.dp)
            )
            Text(
                text = if (connected) "蚂蚁 BMS" else "未连接",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        if (!bms.hasData) {
            Card {
                Text(
                    "等待 BMS 数据…",
                    fontSize = 14.sp,
                    color = SportColors.Secondary
                )
            }
            return@Column
        }

        // ① 主卡片
        MainCard(bms, chemistry)

        // ② 单体极值
        CellExtremeCard(bms)

        // ③ 状态
        StatusCard(bms)

        // ④ 温度
        TemperatureCard(bms)

        // ⑤ 单体电压
        CellsCard(bms)

        // ⑥ 健康度建议
        AdviceCard(bms, chemistry)
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x14FFFFFF), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x10FFFFFF), RoundedCornerShape(16.dp))
            .padding(14.dp),
        content = content
    )
}

@Composable
private fun MainCard(bms: AntBmsData, chemistry: BatteryChemistry) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 第一行：SOC + 电压/电流/功率
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // SOC 环（简化成大字）
            Column(
                modifier = Modifier.width(100.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "${bms.soc}",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = socColor(bms.soc)
                )
                Text("%", fontSize = 11.sp, color = SportColors.Secondary)
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GridItem("电压", "%.2f".format(bms.totalVoltage), "V", SportColors.Cyan, Modifier.weight(1f))
                    GridItem("电流", "%.1f".format(bms.current), "A",
                             if (bms.current < 0) SportColors.Green else SportColors.Cyan, Modifier.weight(1f))
                    GridItem("功率", "%.0f".format(bms.totalVoltage * bms.current), "W",
                             SportColors.Amber, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GridItem("容量", "%.0f/%.0f".format(bms.capacityRemaining, bms.capacityTotal), "Ah",
                             Color.White, Modifier.weight(1f))
                    GridItem("循环", "${bms.cycles}", "次", SportColors.Amber, Modifier.weight(1f))
                    val soh = BatteryHealth.soh(bms.cycles, chemistry)
                    GridItem("健康度", soh?.let { "%.2f".format(it) } ?: "—", "%",
                             sohColor(soh), Modifier.weight(1f))
                }
            }
        }
    }
}

private fun socColor(soc: Int): Color = when {
    soc < 15 -> SportColors.Red
    soc < 30 -> SportColors.LampAmber
    else -> SportColors.Green
}

private fun sohColor(soh: Double?): Color {
    if (soh == null) return SportColors.Secondary
    return when (HealthLevel.from(soh)) {
        HealthLevel.CRITICAL -> SportColors.Red
        HealthLevel.WARNING -> SportColors.LampAmber
        HealthLevel.WATCH -> SportColors.Amber
        HealthLevel.HEALTHY -> SportColors.Green
    }
}

@Composable
private fun GridItem(title: String, value: String, unit: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(title, fontSize = 10.sp, color = SportColors.Secondary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = color,
                maxLines = 1
            )
            Spacer(Modifier.width(1.dp))
            Text(unit, fontSize = 8.sp, color = SportColors.Secondary,
                 modifier = Modifier.padding(bottom = 1.dp))
        }
    }
}

@Composable
private fun CellExtremeCard(bms: AntBmsData) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MiniStat("平均电压", "%.3f".format(bms.avgCell), "V", SportColors.Cyan)
            MiniStat("最低电压", "%.3f #%d".format(bms.minCell, bms.minCellIndex), "V", SportColors.Green)
            MiniStat("最高电压", "%.3f #%d".format(bms.maxCell, bms.maxCellIndex), "V", SportColors.Amber)
            MiniStat("压差", "%.3f".format(bms.cellSpread), "V",
                     if (bms.cellSpread > 0.1) SportColors.Red else SportColors.Cyan)
        }
    }
}

@Composable
private fun MiniStat(title: String, value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 10.sp, color = SportColors.Secondary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                 fontFamily = FontFamily.Monospace, color = color)
            Spacer(Modifier.width(2.dp))
            Text(unit, fontSize = 8.sp, color = SportColors.Secondary)
        }
    }
}

@Composable
private fun StatusCard(bms: AntBmsData) {
    Card {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot("充电 MOS", bms.chargeMosOn)
            StatusDot("放电 MOS", bms.dischargeMosOn)
            StatusDot("均衡", bms.balancingActive)
            val state = when {
                bms.current > 1.0 -> "充电中"
                bms.current < -1.0 -> "放电中"
                else -> "待机"
            }
            Text(state, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                 color = when {
                     bms.current > 1.0 -> SportColors.Green
                     bms.current < -1.0 -> SportColors.Amber
                     else -> SportColors.Secondary
                 })
        }
    }
}

@Composable
private fun StatusDot(label: String, on: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(if (on) SportColors.Green else Color(0x33FFFFFF), RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, color = SportColors.Secondary)
    }
}

@Composable
private fun TemperatureCard(bms: AntBmsData) {
    Card {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("温度", fontSize = 11.sp, color = SportColors.Secondary)
            bms.temps.take(6).forEachIndexed { i, t ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("T${i + 1}", fontSize = 8.sp, color = SportColors.Secondary)
                    Text("$t°", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                         fontFamily = FontFamily.Monospace,
                         color = if (t > 45) SportColors.Red else SportColors.Cyan)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("MOS", fontSize = 8.sp, color = SportColors.Secondary)
                Text("${bms.mosTemp}°", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                     fontFamily = FontFamily.Monospace,
                     color = if (bms.mosTemp > 55) SportColors.Red else SportColors.Cyan)
            }
        }
    }
}

@Composable
private fun CellsCard(bms: AntBmsData) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("单体电压", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("${bms.cellCount} 串", fontSize = 12.sp, color = SportColors.Secondary)
            Spacer(Modifier.weight(1f))
            Text("%.3f V 平均".format(bms.avgCell), fontSize = 10.sp, color = SportColors.Secondary)
        }
        Spacer(Modifier.height(10.dp))
        // 用固定列数的网格（Compose nested scroll 里 LazyVerticalGrid 高度需固定）
        CellGrid(bms)
    }
}

@Composable
private fun CellGrid(bms: AntBmsData) {
    val cols = 6
    val rows = (bms.cells.size + cols - 1) / cols
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (r in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (c in 0 until cols) {
                    val idx = r * cols + c
                    if (idx < bms.cells.size) {
                        CellChip(idx + 1, bms.cells[idx], bms.avgCell,
                                 bms.balancing.getOrNull(idx) == true,
                                 Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CellChip(index: Int, voltage: Double, avg: Double, balancing: Boolean, modifier: Modifier) {
    val isMax = voltage > avg + 0.02
    val isMin = voltage < avg - 0.02
    val borderColor = when {
        isMax -> SportColors.Red
        isMin -> SportColors.Cyan
        balancing -> SportColors.Green
        else -> SportColors.Cyan.copy(alpha = 0.35f)
    }
    Column(
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$index", fontSize = 9.sp, color = SportColors.Secondary)
        Text(
            "%.3f".format(voltage),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = when {
                isMax -> SportColors.Red
                isMin -> SportColors.Cyan
                else -> Color.White
            }
        )
    }
}

@Composable
private fun AdviceCard(bms: AntBmsData, chemistry: BatteryChemistry) {
    val soh = BatteryHealth.soh(bms.cycles, chemistry)
    Card {
        Text("充电建议", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SportColors.Secondary)
        Spacer(Modifier.height(6.dp))
        val advice = when (chemistry) {
            BatteryChemistry.NCM -> listOf(
                "推荐充到 80% 就切断充电器",
                "20% 时尽量开始充电",
                "10% 请及时充电"
            )
            BatteryChemistry.LFP -> listOf(
                "推荐充到 90% 就切断充电器",
                "20% 时尽量开始充电",
                "10% 请及时充电"
            )
        }
        advice.forEach {
            Text("· $it", fontSize = 12.sp, color = Color(0xCCFFFFFF),
                 modifier = Modifier.padding(vertical = 1.dp))
        }
        if (soh != null) {
            val level = HealthLevel.from(soh)
            level.message?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(
                    msg,
                    fontSize = 11.sp,
                    color = when (level) {
                        HealthLevel.CRITICAL -> SportColors.Red
                        HealthLevel.WARNING -> SportColors.LampAmber
                        else -> SportColors.Amber
                    }
                )
            }
        }
    }
}
