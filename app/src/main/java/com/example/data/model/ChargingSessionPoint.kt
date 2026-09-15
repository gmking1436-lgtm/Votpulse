package com.example.data.model

/**
 * Represents a single telemetry snapshot along a charging timeline curve.
 *
 * @param timestampMs Epoch timestamp in milliseconds when this sample was recorded.
 * @param percentage Battery charge percentage (0 - 100).
 * @param wattage Measured charging power in Watts at this timestamp.
 * @param temperatureCelsius Measured battery temperature in °C.
 */
data class ChargingSessionPoint(
    val timestampMs: Long,
    val percentage: Int,
    val wattage: Float = 0f,
    val temperatureCelsius: Float = 0f
)
