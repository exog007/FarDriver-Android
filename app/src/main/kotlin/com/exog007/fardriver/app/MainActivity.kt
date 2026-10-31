package com.exog007.fardriver.app

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
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

    /** 相册选图回调（由 Compose 里赋值） */
    private var photoPicked: ((Uri) -> Unit)? = null

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.BLUETOOTH_SCAN] == true) {
            vm.startControllerScan()
            gps.start()
        }
    }

    private val pickPhoto = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) photoPicked?.invoke(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        settings = AppSettings(this)

        gps = GpsSpeedProvider(this).apply {
            onUpdate = { kmh, acc, fix -> runOnUiThread { vm.onGpsUpdate(kmh, acc, fix) } }
        }

        setContent {
            MaterialTheme {
                Surface(color = Color.Black) {
                    AppRoot(
                        vm = vm,
                        settings = settings,
                        onPickPhoto = {
                            pickPhoto.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        registerPhotoCallback = { cb -> photoPicked = cb }
                    )
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

private enum class Screen { DASHBOARD, BMS, SETTINGS, BLUETOOTH }

@Composable
private fun AppRoot(
    vm: MainViewModel,
    settings: AppSettings,
    onPickPhoto: () -> Unit,
    registerPhotoCallback: (((Uri) -> Unit)) -> Unit
) {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf(Screen.DASHBOARD) }

    // 处理选中的图片
    LaunchedEffect(Unit) {
        registerPhotoCallback { uri ->
            try {
                val input = ctx.contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(input)
                input?.close()
                if (bmp != null) settings.setBackgroundPhoto(bmp)
            } catch (_: Exception) { }
        }
    }

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
    val amberHex by settings.amberHex.collectAsStateWithLifecycle()
    val cyanHex by settings.cyanHex.collectAsStateWithLifecycle()
    val bgHex by settings.bgHex.collectAsStateWithLifecycle()
    val bgBmp by settings.bgBitmap.collectAsStateWithLifecycle()
    val photoDim by settings.photoDim.collectAsStateWithLifecycle()
    val bgType by settings.bgType.collectAsStateWithLifecycle()

    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); tick++ }
    }

    val bikeConnected = ctrlState == BleManager.State.CONNECTED
    val bmsConnected = bmsState == BleManager.State.CONNECTED

    // 竞技仪表锁横屏，其它页面自由旋转
    val activity = ctx as? android.app.Activity
    LaunchedEffect(sportMode, screen) {
        activity?.requestedOrientation =
            if (screen == Screen.DASHBOARD && sportMode)
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
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
            onOpenBluetooth = { screen = Screen.BLUETOOTH },
            onPickPhoto = onPickPhoto,
            onResetTrip = { vm.resetTrip() },
            tripDistance = vm.trip.distanceText,
            tripConsumption = vm.trip.consumptionText,
            tripAvgPower = vm.trip.avgPowerText
        )

        Screen.BLUETOOTH -> BluetoothScreen(
            controllerBle = vm.controllerBle,
            bmsBle = vm.bmsBle,
            savedControllerId = vm.savedControllerId,
            savedControllerName = vm.savedControllerName,
            savedBmsId = vm.savedBmsId,
            savedBmsName = vm.savedBmsName,
            onBack = { screen = Screen.SETTINGS },
            onScanController = { vm.startControllerScan() },
            onScanBms = { vm.startBmsScan() },
            onConnectController = { vm.connectControllerDevice(it) },
            onConnectBms = { vm.connectBmsDevice(it) },
            onDisconnectController = { vm.disconnectController() },
            onDisconnectBms = { vm.disconnectBms() },
            onForgetController = { vm.forgetController() },
            onForgetBms = { vm.forgetBms() }
        )

        Screen.DASHBOARD -> {
            Box(Modifier.fillMaxSize()) {
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
                        brightness = brightness,
                        amberColor = Color(amberHex),
                        cyanColor = Color(cyanHex),
                        bgColor = Color(bgHex),
                        bgBitmap = if (bgType == "photo") bgBmp else null,
                        photoDim = photoDim
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

                // 右上角浮层
                Row(
                    Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 8.dp),
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
        Modifier
            .background(Color(0x40FFFFFF), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 12.sp, color = Color.White)
    }
}
