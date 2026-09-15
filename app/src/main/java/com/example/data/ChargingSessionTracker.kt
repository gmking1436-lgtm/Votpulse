package com.example.data

import com.example.data.model.ChargingSessionPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/**
 * In-memory telemetry recorder tracking battery charging curve progression over time.
 *
 * Characteristics:
 * - Thread-safe, bounded memory footprint (max 120 chronological points per session).
 * - Records timestamps, battery percentage, wattage, and thermals.
 * - Automatically initializes a new session when charging begins or updates existing session points.
 * - Seeds an initial reference session if empty so the chart renders gracefully even on initial launch.
 */
class ChargingSessionTracker private constructor() {

    private val _sessionPoints = MutableStateFlow<List<ChargingSessionPoint>>(emptyList())
    val sessionPoints: StateFlow<List<ChargingSessionPoint>> = _sessionPoints.asStateFlow()

    private var sessionStartTimeMs: Long = 0L
    private var lastRecordedTimeMs: Long = 0L
    private var lastRecordedPercentage: Int = -1

    init {
        // Initialize with default reference baseline curve if empty
        seedInitialBaselineIfEmpty()
    }

    private fun seedInitialBaselineIfEmpty() {
        val now = System.currentTimeMillis()
        val samplePoints = listOf(
            ChargingSessionPoint(now - 35 * 60 * 1000L, 38, 22.4f, 29.5f),
            ChargingSessionPoint(now - 28 * 60 * 1000L, 48, 24.1f, 31.0f),
            ChargingSessionPoint(now - 20 * 60 * 1000L, 59, 21.8f, 32.8f),
            ChargingSessionPoint(now - 12 * 60 * 1000L, 69, 18.5f, 33.6f),
            ChargingSessionPoint(now - 5 * 60 * 1000L, 77, 14.2f, 33.2f),
            ChargingSessionPoint(now, 81, 11.5f, 32.7f)
        )
        _sessionPoints.value = samplePoints
    }

    /**
     * Resets current session and begins recording for a newly plugged-in charger cycle.
     */
    @Synchronized
    fun startNewSession(initialPercentage: Int, initialWattage: Float, temperatureCelsius: Float) {
        val now = System.currentTimeMillis()
        sessionStartTimeMs = now
        lastRecordedTimeMs = now
        lastRecordedPercentage = initialPercentage

        val initialPoint = ChargingSessionPoint(
            timestampMs = now,
            percentage = initialPercentage,
            wattage = initialWattage,
            temperatureCelsius = temperatureCelsius
        )
        _sessionPoints.value = listOf(initialPoint)
    }

    /**
     * Appends a new telemetry point during an active charging cycle.
     * Throttled to avoid unnecessary duplicate points (minimum 15 seconds or percentage change).
     */
    @Synchronized
    fun recordTelemetry(
        percentage: Int,
        wattage: Float,
        temperatureCelsius: Float,
        isCharging: Boolean
    ) {
        if (!isCharging) return

        val now = System.currentTimeMillis()
        if (sessionStartTimeMs == 0L || _sessionPoints.value.isEmpty()) {
            startNewSession(percentage, wattage, temperatureCelsius)
            return
        }

        // Throttle: record if percentage changed OR at least 20 seconds elapsed
        val timeDeltaMs = now - lastRecordedTimeMs
        val percentageDelta = percentage != lastRecordedPercentage

        if (percentageDelta || timeDeltaMs >= 20_000L) {
            lastRecordedTimeMs = now
            lastRecordedPercentage = percentage

            val newPoint = ChargingSessionPoint(
                timestampMs = now,
                percentage = percentage,
                wattage = wattage,
                temperatureCelsius = temperatureCelsius
            )

            val currentList = _sessionPoints.value
            // Bounded list: keep maximum 120 points
            val updatedList = if (currentList.size >= 120) {
                currentList.drop(1) + newPoint
            } else {
                currentList + newPoint
            }
            _sessionPoints.value = updatedList
        }
    }

    companion object {
        @Volatile
        private var instance: ChargingSessionTracker? = null

        fun getInstance(): ChargingSessionTracker {
            return instance ?: synchronized(this) {
                instance ?: ChargingSessionTracker().also { instance = it }
            }
        }
    }
}
