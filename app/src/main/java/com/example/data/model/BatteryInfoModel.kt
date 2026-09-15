package com.example.data.model

/**
 * Immutable domain representation of real-time device battery diagnostics
 * and charge threshold metrics.
 */
data class BatteryInfoModel(
    val percentage: Int = 0,
    val isCharging: Boolean = false,
    val healthStatus: String = "Good",
    val temperatureCelsius: Float = 0.0f,
    val voltageMilliVolts: Int = 0,
    val technology: String = "Li-ion",
    val lastFullChargeFormatted: String = "Never"
)
