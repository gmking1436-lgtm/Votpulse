package com.example.data.model

import android.os.BatteryManager

enum class ChargingSource {
    NONE,
    AC,
    USB,
    WIRELESS,
    DOCK,
    UNKNOWN
}

enum class BatteryHealthStatus {
    GOOD,
    OVERHEAT,
    DEAD,
    OVER_VOLTAGE,
    UNSPECIFIED_FAILURE,
    COLD,
    UNKNOWN
}

data class BatteryState(
    val level: Int = 0,
    val scale: Int = 100,
    val isCharging: Boolean = false,
    val status: Int = BatteryManager.BATTERY_STATUS_UNKNOWN,
    val chargingSource: ChargingSource = ChargingSource.NONE,
    val health: BatteryHealthStatus = BatteryHealthStatus.GOOD,
    val voltageMv: Int = 0,
    val temperatureCelsius: Float = 0f,
    val technology: String = "Li-ion",
    val present: Boolean = true
) {
    val percentage: Int
        get() = if (scale > 0) ((level.toFloat() / scale.toFloat()) * 100).toInt().coerceIn(0, 100) else level

    val isFull: Boolean
        get() = percentage >= 100 || status == BatteryManager.BATTERY_STATUS_FULL

    val statusDescription: String
        get() = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging via ${chargingSource.name}"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Fully Charged (100%)"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Plugged, Not Charging"
            else -> "Operating on Battery"
        }
}
