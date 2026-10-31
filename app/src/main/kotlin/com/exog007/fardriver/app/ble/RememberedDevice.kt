package com.exog007.fardriver.app.ble

/**
 * 记忆设备信息（地址 + 名称）
 */
data class RememberedDevice(val address: String, val name: String) {
    val displayName: String
        get() = if (name.isBlank()) address else "$name  ($address)"
}
