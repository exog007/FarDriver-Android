package com.exog007.fardriver.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exog007.fardriver.app.ble.BleManager
import kotlinx.coroutines.delay

/**
 * 蓝牙设备管理页
 *
 * 显示：连接状态 / 记住的设备 / 扫描到的设备 / 扫描和断开按钮
 */
@Composable
fun BluetoothScreen(
    controllerBle: BleManager,
    bmsBle: BleManager,
    savedControllerId: String?,
    savedControllerName: String?,
    savedBmsId: String?,
    savedBmsName: String?,
    onBack: () -> Unit,
    onScanController: () -> Unit,
    onScanBms: () -> Unit,
    onConnectController: (BleManager.Device) -> Unit,
    onConnectBms: (BleManager.Device) -> Unit,
    onDisconnectController: () -> Unit,
    onDisconnectBms: () -> Unit,
    onForgetController: () -> Unit,
    onForgetBms: () -> Unit
) {
    val ctrlState by controllerBle.state.collectAsStateWithLifecycle()
    val ctrlDevices by controllerBle.devices.collectAsStateWithLifecycle()
    val bmsState by bmsBle.state.collectAsStateWithLifecycle()
    val bmsDevices by bmsBle.devices.collectAsStateWithLifecycle()
    val ctrlErr by controllerBle.error.collectAsStateWithLifecycle()
    val bmsErr by bmsBle.error.collectAsStateWithLifecycle()

    // 每秒刷新（状态提示的动效）
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); tick++ }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0C))
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹ 返回", fontSize = 16.sp, color = SportColors.Cyan,
                modifier = Modifier.clickable { onBack() }.padding(end = 16.dp)
            )
            Text("蓝牙设备", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        // ══════════ 控制器 ══════════
        SectionCard("控制器") {
            StatusRow(
                title = "状态",
                state = ctrlState,
                error = ctrlErr,
                connected = ctrlState == BleManager.State.CONNECTED
            )

            if (!savedControllerId.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                RememberRow(
                    label = "记忆设备",
                    name = savedControllerName ?: savedControllerId,
                    address = savedControllerId,
                    onForget = onForgetController
                )
            } else {
                Spacer(Modifier.height(6.dp))
                Text("还没记住控制器，连一次就会记住",
                     fontSize = 11.sp, color = SportColors.Secondary)
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ctrlState == BleManager.State.CONNECTED) {
                    ActionBtn("断开", Color(0x33FF453A), SportColors.Red) {
                        onDisconnectController()
                    }
                } else {
                    ActionBtn(
                        if (ctrlState == BleManager.State.SCANNING) "扫描中…" else "搜索控制器",
                        Color(0x33FF9F0A), SportColors.Amber
                    ) { onScanController() }
                }
            }

            // 设备列表
            if (ctrlDevices.isNotEmpty() && ctrlState != BleManager.State.CONNECTED) {
                Spacer(Modifier.height(10.dp))
                Text("找到 ${ctrlDevices.size} 个设备，点击连接：",
                     fontSize = 11.sp, color = SportColors.Secondary)
                Spacer(Modifier.height(4.dp))
                ctrlDevices.forEach { dev ->
                    DeviceRow(dev, isSaved = dev.id == savedControllerId) {
                        onConnectController(dev)
                    }
                }
            }
        }

        // ══════════ BMS ══════════
        SectionCard("蚂蚁 BMS") {
            StatusRow(
                title = "状态",
                state = bmsState,
                error = bmsErr,
                connected = bmsState == BleManager.State.CONNECTED
            )

            if (!savedBmsId.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                RememberRow(
                    label = "记忆设备",
                    name = savedBmsName ?: savedBmsId,
                    address = savedBmsId,
                    onForget = onForgetBms
                )
            } else {
                Spacer(Modifier.height(6.dp))
                Text("还没记住 BMS，连一次就会记住",
                     fontSize = 11.sp, color = SportColors.Secondary)
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bmsState == BleManager.State.CONNECTED) {
                    ActionBtn("断开", Color(0x33FF453A), SportColors.Red) {
                        onDisconnectBms()
                    }
                } else {
                    ActionBtn(
                        if (bmsState == BleManager.State.SCANNING) "扫描中…" else "搜索 BMS",
                        Color(0x3300E5FF), SportColors.Cyan
                    ) { onScanBms() }
                }
            }

            if (bmsDevices.isNotEmpty() && bmsState != BleManager.State.CONNECTED) {
                Spacer(Modifier.height(10.dp))
                Text("找到 ${bmsDevices.size} 个设备，点击连接：",
                     fontSize = 11.sp, color = SportColors.Secondary)
                Spacer(Modifier.height(4.dp))
                bmsDevices.forEach { dev ->
                    DeviceRow(dev, isSaved = dev.id == savedBmsId) {
                        onConnectBms(dev)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "提示：首次连接后会自动记住设备，下次打开 App 搜到就自动连接。",
            fontSize = 10.sp, color = SportColors.Secondary
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x14FFFFFF), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0x10FFFFFF), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold,
             color = SportColors.Amber, modifier = Modifier.padding(bottom = 10.dp))
        content()
    }
}

@Composable
private fun StatusRow(title: String, state: BleManager.State, error: String?, connected: Boolean) {
    val (text, color) = when (state) {
        BleManager.State.CONNECTED -> "已连接" to SportColors.Green
        BleManager.State.SCANNING -> "扫描中…" to SportColors.Amber
        BleManager.State.CONNECTING -> "连接中…" to SportColors.Amber
        BleManager.State.DISCONNECTED -> "已断开" to SportColors.Secondary
        BleManager.State.NO_PERMISSION -> "缺少权限" to SportColors.Red
        BleManager.State.BT_OFF -> "蓝牙未开启" to SportColors.Red
        BleManager.State.IDLE -> "未连接" to SportColors.Secondary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 12.sp, color = SportColors.Secondary)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(50))
        )
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
    if (!error.isNullOrEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(error, fontSize = 11.sp, color = SportColors.Amber)
    }
}

@Composable
private fun RememberRow(label: String, name: String, address: String, onForget: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 10.sp, color = SportColors.Secondary)
            Text(name, fontSize = 12.sp, color = Color.White, maxLines = 1)
            Text(address, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                 color = SportColors.Secondary)
        }
        Text(
            "忘记", fontSize = 11.sp, color = SportColors.Red,
            modifier = Modifier
                .clickable { onForget() }
                .background(Color(0x22FF453A), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun DeviceRow(dev: BleManager.Device, isSaved: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(Color(0x0FFFFFFF), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dev.name, fontSize = 13.sp, color = Color.White, maxLines = 1)
                if (isSaved) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "记忆", fontSize = 8.sp, color = Color.Black,
                        modifier = Modifier
                            .background(SportColors.Green, RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            Text(dev.id, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                 color = SportColors.Secondary)
        }
        Text("${dev.rssi} dBm", fontSize = 10.sp, color = SportColors.Secondary)
    }
}

@Composable
private fun ActionBtn(text: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = bg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(text, fontSize = 13.sp, color = fg, fontWeight = FontWeight.Bold)
    }
}
