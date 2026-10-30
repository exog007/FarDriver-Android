package com.exog007.fardriver.app.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 通用 BLE 连接管理器
 *
 * 控制器和 BMS 各用一个实例，传入不同的 service/char UUID。
 *
 * 关键设计：
 *   · 持续扫描，不做超时停止（这样后开机/后靠近的设备也能被抓到）
 *   · 允许重复上报（同一设备会持续回调）
 *   · 记忆设备判断在去重之前，否则第 2 次回调就被拦住了
 */
class BleManager(
    private val context: Context,
    private val serviceUuid: String,
    private val charUuid: String,
    /** 记忆设备 UUID（持久化） */
    private val savedDeviceId: () -> String?,
    /** 连接成功后保存 */
    private val onSaveDevice: (String, String) -> Unit
) {

    enum class State { IDLE, SCANNING, CONNECTING, CONNECTED, DISCONNECTED, NO_PERMISSION, BT_OFF }

    data class Device(val id: String, val name: String, val rssi: Int)

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    var onData: ((ByteArray) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = btManager.adapter

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var scanning = false
    /** 用户手动停止后不再自动重连 */
    private var userStopped = false

    // ── 权限检查 ──

    private fun hasPerm(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
    }

    // ── 扫描 ──

    @SuppressLint("MissingPermission")
    fun startScan(autoConnect: Boolean = true) {
        if (!hasPerm()) {
            _state.value = State.NO_PERMISSION
            _error.value = "需要蓝牙权限"
            return
        }
        val ad = adapter
        if (ad == null || !ad.isEnabled) {
            _state.value = State.BT_OFF
            _error.value = "蓝牙未开启"
            return
        }
        if (scanning) return
        userStopped = false
        _devices.value = emptyList()
        _error.value = null
        scanning = true
        _state.value = State.SCANNING

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        // 允许重复上报：后开机/后靠近的设备也能发现
        ad.bluetoothLeScanner?.startScan(null, settings, scanCallback)

        // 记下这次的 autoConnect 意图，供回调使用
        scanAutoConnect = autoConnect
    }

    private var scanAutoConnect = true

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        scanning = false
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) { }
        if (_state.value == State.SCANNING) _state.value = State.IDLE
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device ?: return
            val id = dev.address ?: return
            val name = result.scanRecord?.deviceName ?: dev.name ?: "未知设备"

            // ── 记忆设备判断放最前面（必须在去重之前）──
            val savedId = savedDeviceId()
            val savedName = savedName()
            val idMatch = savedId != null && savedId == id
            val nameMatch = !savedName.isNullOrEmpty() && savedName == name && name != "未知设备"

            if (scanAutoConnect && gatt == null && (idMatch || nameMatch)) {
                // 找到了，停扫并连
                stopScan()
                _state.value = State.CONNECTING
                _error.value = "找到记忆设备 $name，正在连接…"
                connect(dev)
                return
            }

            // 去重 + 加入列表
            val cur = _devices.value
            if (cur.any { it.id == id }) return
            _devices.value = cur + Device(id, name, result.rssi)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            _error.value = "扫描失败（code=$errorCode）"
            _state.value = State.IDLE
        }
    }

    /** 由外部提供上次保存的名字，用于名称匹配 */
    var savedName: () -> String? = { null }

    // ── 连接 ──

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (!hasPerm()) return
        stopScan()
        _state.value = State.CONNECTING
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        userStopped = true
        stopScan()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        writeChar = null
        _state.value = State.DISCONNECTED
    }

    @SuppressLint("MissingPermission")
    fun write(bytes: ByteArray) {
        val g = gatt ?: return
        val c = writeChar ?: return
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        c.value = bytes
        g.writeCharacteristic(c)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _state.value = State.CONNECTING
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _state.value = State.DISCONNECTED
                writeChar = null
                gatt?.close()
                gatt = null
                onDisconnected?.invoke()
                // 没被用户手动断开就自动重连
                if (!userStopped) {
                    _error.value = "连接断开，正在重连…"
                    startScan(autoConnect = true)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(java.util.UUID.fromString(serviceUuid))
            if (service == null) {
                _error.value = "找不到服务 $serviceUuid"
                return
            }
            val ch = service.getCharacteristic(java.util.UUID.fromString(charUuid))
            if (ch == null) {
                _error.value = "找不到特征 $charUuid"
                return
            }
            writeChar = ch

            // 开启通知
            g.setCharacteristicNotification(ch, true)
            val desc = ch.getDescriptor(
                java.util.UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
            )
            if (desc != null) {
                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(desc)
            }

            _state.value = State.CONNECTED
            _error.value = null
            // 保存设备记忆
            val dev = g.device
            val addr = dev.address ?: ""
            val name = dev.name ?: ""
            if (addr.isNotEmpty()) onSaveDevice(addr, name)
            onConnected?.invoke()
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            onData?.invoke(value)
        }
    }

    fun hasPermission(): Boolean = hasPerm()
}
