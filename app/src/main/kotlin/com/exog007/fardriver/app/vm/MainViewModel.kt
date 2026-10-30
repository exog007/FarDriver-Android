package com.exog007.fardriver.app.vm

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exog007.fardriver.app.ble.BleManager
import com.exog007.fardriver.protocol.AntBmsBle
import com.exog007.fardriver.protocol.AntBmsData
import com.exog007.fardriver.protocol.AntBmsDecoder
import com.exog007.fardriver.protocol.ControllerData
import com.exog007.fardriver.protocol.FarDriverBle
import com.exog007.fardriver.protocol.FarDriverDecoder
import com.exog007.fardriver.protocol.GpsFusion
import com.exog007.fardriver.protocol.TripMeter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 主 ViewModel：管理控制器 + BMS 两路 BLE 连接和数据
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("fardriver", Context.MODE_PRIVATE)

    // ── 控制器 ──

    private val ctrlDecoder = FarDriverDecoder()

    val controllerBle = BleManager(
        context = app,
        serviceUuid = FarDriverBle.SERVICE_UUID,
        charUuid = FarDriverBle.CHAR_UUID,
        savedDeviceId = { prefs.getString("ctrl_id", null) },
        onSaveDevice = { id, name ->
            prefs.edit().putString("ctrl_id", id).putString("ctrl_name", name).apply()
        }
    ).apply {
        savedName = { prefs.getString("ctrl_name", null) }
        onData = { bytes ->
            if (ctrlDecoder.process(bytes)) {
                _ctrlData.value = ctrlDecoder.data.copy()
                trip.update(ctrlDecoder.data.speedKmh, ctrlDecoder.data.power)
                updateSpeed()
            }
        }
        onDisconnected = {
            trip.breakContinuity()
            fused.reset()
        }
    }

    private val _ctrlData = MutableStateFlow(ControllerData())
    val ctrlData: StateFlow<ControllerData> = _ctrlData

    // ── BMS ──

    private val bmsDecoder = AntBmsDecoder()

    val bmsBle = BleManager(
        context = app,
        serviceUuid = AntBmsBle.SERVICE_UUID,
        charUuid = AntBmsBle.CHAR_UUID,
        savedDeviceId = { prefs.getString("bms_id", null) },
        onSaveDevice = { id, name ->
            prefs.edit().putString("bms_id", id).putString("bms_name", name).apply()
        }
    ).apply {
        savedName = { prefs.getString("bms_name", null) }
        onData = { bytes ->
            if (bmsDecoder.process(bytes)) {
                _bmsData.value = bmsDecoder.data
            }
        }
        onConnected = {
            // 连上后开始轮询
            startBmsPolling()
        }
        onDisconnected = {
            stopBmsPolling()
        }
    }

    private val _bmsData = MutableStateFlow(AntBmsData())
    val bmsData: StateFlow<AntBmsData> = _bmsData

    // ── 里程 / 速度融合 ──

    val trip = TripMeter().apply {
        restore(
            prefs.getFloat("trip_dist", 0f).toDouble(),
            prefs.getFloat("trip_energy", 0f).toDouble(),
            prefs.getFloat("trip_sec", 0f).toDouble()
        )
        onNeedSave = {
            prefs.edit()
                .putFloat("trip_dist", distanceKm.toFloat())
                .putFloat("trip_energy", energyKWh.toFloat())
                .putFloat("trip_sec", movingSeconds.toFloat())
                .apply()
        }
    }

    val fused = GpsFusion()

    private val _displaySpeed = MutableStateFlow(0.0)
    val displaySpeed: StateFlow<Double> = _displaySpeed

    private val _gpsSpeed = MutableStateFlow(0.0)
    val gpsSpeed: StateFlow<Double> = _gpsSpeed

    // ── 定时器 ──

    private val mainHandler = Handler(Looper.getMainLooper())
    private var bmsPollRunnable: Runnable? = null
    private var smoothRunnable: Runnable? = null

    init {
        startSmoothLoop()
    }

    // ── 速度平滑（60Hz）──

    private fun startSmoothLoop() {
        val r = object : Runnable {
            override fun run() {
                _displaySpeed.value = fused.fused(_ctrlData.value.speedKmh)
                mainHandler.postDelayed(this, 16)
            }
        }
        smoothRunnable = r
        mainHandler.post(r)
    }

    private fun updateSpeed() {
        // 由平滑循环持续更新，这里不用做
    }

    /** GPS 回调 */
    fun onGpsUpdate(speedKmh: Double, accuracy: Double, hasFix: Boolean) {
        fused.gpsSpeedKmh = speedKmh
        fused.accuracy = accuracy
        fused.hasFix = hasFix
        _gpsSpeed.value = speedKmh
    }

    // ── BMS 轮询（2 秒一次）──

    private fun startBmsPolling() {
        stopBmsPolling()
        val r = object : Runnable {
            override fun run() {
                bmsBle.write(AntBmsBle.PROBE_STATUS)
                mainHandler.postDelayed(this, 2000)
            }
        }
        bmsPollRunnable = r
        // 立刻先发一次
        bmsBle.write(AntBmsBle.PROBE_STATUS)
        mainHandler.postDelayed(r, 2000)
    }

    private fun stopBmsPolling() {
        bmsPollRunnable?.let { mainHandler.removeCallbacks(it) }
        bmsPollRunnable = null
    }

    // ── 连接控制 ──

    fun startControllerScan() {
        controllerBle.startScan(autoConnect = true)
    }

    fun startBmsScan() {
        bmsBle.startScan(autoConnect = true)
    }

    @SuppressLint("MissingPermission")
    fun connectControllerDevice(dev: BleManager.Device) {
        val ad = (getApplication<Application>()
            .getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
        val device = ad?.getRemoteDevice(dev.id) ?: return
        controllerBle.connect(device)
    }

    @SuppressLint("MissingPermission")
    fun connectBmsDevice(dev: BleManager.Device) {
        val ad = (getApplication<Application>()
            .getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
        val device = ad?.getRemoteDevice(dev.id) ?: return
        bmsBle.connect(device)
    }

    fun disconnectController() = controllerBle.disconnect()
    fun disconnectBms() = bmsBle.disconnect()

    fun resetTrip() = trip.reset()

    override fun onCleared() {
        super.onCleared()
        smoothRunnable?.let { mainHandler.removeCallbacks(it) }
        stopBmsPolling()
    }
}
