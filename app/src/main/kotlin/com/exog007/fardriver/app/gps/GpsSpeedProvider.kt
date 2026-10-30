package com.exog007.fardriver.app.gps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat

/**
 * GPS 测速
 *
 * Android 的 GPS 硬件同样是 1Hz，用系统 LocationManager 即可。
 */
class GpsSpeedProvider(context: Context) : LocationListener {

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val appContext = context.applicationContext

    /** 速度回调 (kmh, accuracy, hasFix) */
    var onUpdate: ((Double, Double, Boolean) -> Unit)? = null

    var enabled = true
        private set

    private var started = false

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (started || !enabled || !hasPermission()) return
        val providers = lm.getProviders(true)
        if (providers.isEmpty()) {
            onUpdate?.invoke(0.0, -1.0, false)
            return
        }
        // 优先 GPS，其次网络定位
        val provider = when {
            providers.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            providers.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> providers.first()
        }
        try {
            lm.requestLocationUpdates(provider, 1000L, 0f, this)
            started = true
        } catch (_: Exception) {
            onUpdate?.invoke(0.0, -1.0, false)
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!started) return
        try {
            lm.removeUpdates(this)
        } catch (_: Exception) { }
        started = false
        onUpdate?.invoke(0.0, -1.0, false)
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (value) start() else stop()
    }

    // ── LocationListener ──

    override fun onLocationChanged(location: Location) {
        val kmh = location.speed * 3.6
        val acc = location.accuracy.toDouble()
        // 精度太差（>50m）不算有效
        val valid = acc >= 0 && acc < 50 && kmh >= 0
        onUpdate?.invoke(if (kmh > 0) kmh else 0.0, acc, valid)
    }

    @Deprecated("Deprecated in API 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) { }

    override fun onProviderEnabled(provider: String) { }

    override fun onProviderDisabled(provider: String) {
        onUpdate?.invoke(0.0, -1.0, false)
    }
}
