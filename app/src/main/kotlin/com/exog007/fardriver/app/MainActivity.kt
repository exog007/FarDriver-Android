package com.exog007.fardriver.app

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exog007.fardriver.app.ble.BleManager
import com.exog007.fardriver.app.gps.GpsSpeedProvider
import com.exog007.fardriver.app.ui.SportDashboard
import com.exog007.fardriver.app.vm.MainViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private lateinit var gps: GpsSpeedProvider

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

        // 常亮，方便当仪表用
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gps = GpsSpeedProvider(this).apply {
            onUpdate = { kmh, acc, fix ->
                runOnUiThread { vm.onGpsUpdate(kmh, acc, fix) }
            }
        }

        setContent {
            MaterialTheme {
                Surface(color = Color.Black) {
                    AppRoot(vm)
                }
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

/**
 * 顶层 Composable，避免在 Activity 里写 @Composable 方法带来的作用域问题
 */
@Composable
private fun AppRoot(vm: MainViewModel) {
    val ctrl by vm.ctrlData.collectAsStateWithLifecycle()
    val bms by vm.bmsData.collectAsStateWithLifecycle()
    val speed by vm.displaySpeed.collectAsStateWithLifecycle()
    val ctrlState by vm.controllerBle.state.collectAsStateWithLifecycle()
    val bmsState by vm.bmsBle.state.collectAsStateWithLifecycle()

    // 里程/电耗是可变对象，每秒 tick 一次触发重组
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }

    SportDashboard(
        ctrl = ctrl,
        bms = bms,
        displaySpeed = speed,
        tripDistance = vm.trip.distanceText,
        tripConsumption = vm.trip.consumptionText,
        tripAvgPower = vm.trip.avgPowerText,
        rpmMultiplier = 10.0,
        rpmScaleMax = 12000.0,
        battFull = 90.3,
        battEmpty = 63.0,
        bikeConnected = ctrlState == BleManager.State.CONNECTED,
        bmsConnected = bmsState == BleManager.State.CONNECTED,
        tickKey = tick
    )
}
