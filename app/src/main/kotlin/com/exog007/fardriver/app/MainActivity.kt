package com.exog007.fardriver.app

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exog007.fardriver.app.ble.BleManager
import com.exog007.fardriver.app.gps.GpsSpeedProvider
import com.exog007.fardriver.app.settings.AppSettings
import com.exog007.fardriver.app.ui.*
import com.exog007.fardriver.app.vm.MainViewModel
import com.exog007.fardriver.protocol.BatteryChemistry
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private lateinit var gps: GpsSpeedProvider
    lateinit var settings: AppSettings
        private set

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.BLUETOOTH_SCAN] == true) {
            vm.startControllerScan()
            gps.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        settings = AppSettings(this)

        gps = GpsSpeedProvider(this).apply {
            onUpdate = { kmh, acc, fix ->
                runOnUiThread { vm.onGpsUpdate(kmh, acc, fix) }
            }
        }

        setContent {
            MaterialTheme {
                Surface(color = Color.Black) { AppRoot(vm, settings) }
            }
        }

        permLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    override fun onResume() {
        super.onResume()
        gps.start()
        vm.startControllerScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        gps.stop()
    }
}

private enum class Screen { DASHBOARD, BMS, SETTINGS }

@Composable
private fun AppRoot(vm: MainViewModel, settings: AppSettings) {
    var screen by remember { mutableStateOf(Screen.DASHBOARD) }

    val ctrl by vm.ctrlData.collectAsStateWithLifecycle()
    val bms by vm.bmsData.collectAsStateWithLifecycle()
    val speed by vm.displaySpeed.collectAsStateWithLifecycle()
    val ctrlState by vm.controllerBle.state.collectAsStateWithLifecycle()
    val bmsState by vm.bmsBle.state.collectAsStateWithLifecycle()
    val sportMode by settings.sportMode.collectAsStateWithLifecycle()
    val rpmMult by settings.rpmMultiplier.collectAsStateWithLifecycle()
    val rpmMax by settings.rpmScaleMax.collectAsStateWithLifecycle()
    val battFull by settings.battFull.collectAsStateWithLifecycle()
    val battEmpty by settings.battEmpty.collectAsStateWithLifecycle()
    val trim by settings.speedTrim.collectAsStateWithLifecycle()
    val brightness by settings.textBrightness.collectAsStateWithLifecycle()

    // 每秒 tick 触发重组（里程/电耗是可变对象）
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); tick++ }
    }

    val bikeConnected = ctrlState == BleManager.State.CONNECTED
    val bmsConnected = bmsState == BleManager.State.CONNECTED

    // ── 竞技模式锁横屏，标准模式自由旋转 ──
    val activity = LocalContext.current as? android.app.Activity
    LaunchedEffect(sportMode, screen) {
        val target = if (screen == Screen.DASHBOARD && sportMode) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
        activity?.requestedOrientation = target
    }

    when (screen) {
        Screen.BMS -> BmsScreen(
            bms = bms,
            connected = bmsConnected,
            chemistry = BatteryChemistry.fromName(
                if (settings.chemistry == "lfp") "磷酸铁锂" else "三元锂"
            ),
            onBack = { screen = Screen.DASHBOARD }
        )

        Screen.SETTINGS -> SettingsScreen(
            settings = settings,
            onBack = { screen = Screen.DASHBOARD },
            onResetTrip = { vm.resetTrip() },
            tripDistance = vm.trip.distanceText,
            tripConsumption = vm.trip.consumptionText,
            tripAvgPower = vm.trip.avgPowerText
        )

        Screen.DASHBOARD -> {
            Box(modifier = Modifier.fillMaxSize()) {
                if (sportMode) {
                    SportDashboard(
                        ctrl = ctrl,
                        bms = bms,
                        displaySpeed = speed * trim,
                        tripDistance = vm.trip.distanceText,
                        tripConsumption = vm.trip.consumptionText,
                        tripAvgPower = vm.trip.avgPowerText,
                        rpmMultiplier = rpmMult,
                        rpmScaleMax = rpmMax,
                        battFull = battFull,
                        battEmpty = battEmpty,
                        bikeConnected = bikeConnected,
                        bmsConnected = bmsConnected,
                        tickKey = tick,
                        brightness = brightness
                    )
                } else {
                    StandardDashboard(
                        ctrl = ctrl,
                        displaySpeed = speed * trim,
                        tripDistance = vm.trip.distanceText,
                        tripConsumption = vm.trip.consumptionText,
                        tripAvgPower = vm.trip.avgPowerText,
                        bikeConnected = bikeConnected
                    )
                }

                // 右上角浮层按钮：BMS / 设置 / 风格切换
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FloatBtn(if (sportMode) "标准" else "竞技") {
                        settings.setSportMode(!sportMode)
                    }
                    FloatBtn("BMS") { screen = Screen.BMS }
                    FloatBtn("设置") { screen = Screen.SETTINGS }
                }
            }
        }
    }
}

@Composable
private fun FloatBtn(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color(0x40FFFFFF), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 12.sp, color = Color.White)
    }
}
