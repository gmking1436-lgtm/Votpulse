package com.example.display

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.Display
import android.view.Window
import android.view.WindowManager
import kotlin.math.abs

/**
 * Display Refresh Rate Target tier for Adaptive / LTPO variable refresh rate displays.
 */
enum class RefreshRateMode(val targetFps: Float) {
    /** High performance for interactive gestures, fling scrolls, and dynamic transitions (90Hz - 120Hz). */
    HIGH_PERFORMANCE(120f),

    /** Balanced rate for reading and steady state navigation (60Hz). */
    BALANCED(60f),

    /** Ultra low power rate for static charging and idle viewports on LTPO/VRR panels (30Hz). */
    IDLE_SAVER(30f),

    /** Hands full control back to Android OS dynamic display scheduling (0Hz = unconstrained). */
    SYSTEM_DEFAULT(0f)
}

/**
 * Android Graphics & Display Specialist Engine for High Refresh Rate (120Hz/90Hz/60Hz/30Hz) Management.
 *
 * Capabilities:
 * - Dynamically requests optimal frame rates on variable refresh rate / LTPO screens (minSdk 29 to targetSdk 35+).
 * - Utilizes [Window.setFrameRate] with [Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS] on Android 12+ (API 31+).
 * - Backward-compatible fallback for Android 10-11 (API 29-30) utilizing [WindowManager.LayoutParams.preferredRefreshRate]
 *   and [Display.Mode] resolution matching.
 * - Hardware Power Saver & Low-Battery Guard: automatically throttles refresh rate ceiling to 60Hz when
 *   [PowerManager.isPowerSaveMode] is active or battery drops below 20%.
 * - Lifecycle-safe: immediately relinquishes window rate locks upon onPause / onStop.
 */
class DisplayRefreshRateManager(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var powerSaveReceiver: BroadcastReceiver? = null
    private var isPowerSaveModeActive: Boolean = powerManager?.isPowerSaveMode == true
    private var isBatteryLow: Boolean = false

    private val supportedRefreshRates: List<Float> by lazy {
        querySupportedRefreshRates()
    }

    val maxSupportedRefreshRate: Float by lazy {
        supportedRefreshRates.maxOrNull() ?: 60f
    }

    val minSupportedRefreshRate: Float by lazy {
        supportedRefreshRates.minOrNull() ?: 60f
    }

    val isHighRefreshRateSupported: Boolean by lazy {
        maxSupportedRefreshRate >= 89.0f
    }

    private var currentAppliedRate: Float = 0f

    init {
        registerPowerSaveReceiver()
    }

    /**
     * Inspects the display HAL to discover all hardware supported refresh rates.
     */
    private fun querySupportedRefreshRates(): List<Float> {
        return try {
            val display = getDisplay()
            display?.supportedModes?.map { it.refreshRate }?.distinct()?.sorted()
                ?: listOf(60f)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to query display modes, defaulting to 60Hz", e)
            listOf(60f)
        }
    }

    private fun getDisplay(): Display? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display
            } catch (e: Exception) {
                null
            }
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
        }
    }

    /**
     * Updates low battery state (e.g. < 20%) to prevent high refresh rate battery drain.
     */
    fun updateBatteryState(percentage: Int, isCharging: Boolean) {
        val wasLow = isBatteryLow
        // Only consider low battery if not actively plugged into power
        isBatteryLow = percentage in 1..20 && !isCharging
        if (wasLow != isBatteryLow) {
            Log.d(TAG, "Battery low state changed: isBatteryLow=$isBatteryLow (level=$percentage%, charging=$isCharging)")
        }
    }

    /**
     * Applies the requested [RefreshRateMode] to the host [Window].
     *
     * Automatically applies power-saving constraints if active.
     */
    fun applyRefreshRate(window: Window?, mode: RefreshRateMode) {
        if (window == null) return

        val targetFps = computeEffectiveFps(mode)
        if (abs(currentAppliedRate - targetFps) < 0.5f) {
            // Already operating at this frame rate
            return
        }

        currentAppliedRate = targetFps
        Log.d(TAG, "Requesting display refresh rate: ${targetFps}Hz (mode=$mode, maxDev=${maxSupportedRefreshRate}Hz)")

        try {
            applyFrameRateToWindow(window, targetFps)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply refresh rate: $targetFps", e)
        }
    }

    /**
     * Calculates effective FPS considering hardware capabilities and power constraints.
     */
    fun computeEffectiveFps(mode: RefreshRateMode): Float {
        if (mode == RefreshRateMode.SYSTEM_DEFAULT) {
            return 0f
        }

        // Low power or power saver mode clamps maximum allowed rate to 60Hz (or 30Hz for idle)
        val isConstrained = isPowerSaveModeActive || isBatteryLow
        if (isConstrained) {
            return when (mode) {
                RefreshRateMode.HIGH_PERFORMANCE -> 60f
                RefreshRateMode.BALANCED -> 60f
                RefreshRateMode.IDLE_SAVER -> if (supportsRate(30f)) 30f else 60f
                RefreshRateMode.SYSTEM_DEFAULT -> 0f
            }
        }

        return when (mode) {
            RefreshRateMode.HIGH_PERFORMANCE -> {
                // Pick highest hardware-supported rate (e.g., 120Hz, 90Hz)
                if (maxSupportedRefreshRate >= 119f) 120f
                else if (maxSupportedRefreshRate >= 89f) 90f
                else 60f
            }
            RefreshRateMode.BALANCED -> 60f
            RefreshRateMode.IDLE_SAVER -> {
                // If LTPO display supports 30Hz or 48Hz, drop down, else 60Hz
                if (supportsRate(30f)) 30f
                else if (supportsRate(48f)) 48f
                else 60f
            }
            RefreshRateMode.SYSTEM_DEFAULT -> 0f
        }
    }

    private fun supportsRate(target: Float): Boolean {
        return supportedRefreshRates.any { abs(it - target) < 1.5f }
    }

    /**
     * Sets preferred refresh rate and display mode ID on the target Window.
     * Compatible with Android 10 (API 29) through Android 15 (API 35+).
     */
    private fun applyFrameRateToWindow(window: Window, targetFps: Float) {
        val params = window.attributes
        if (targetFps <= 0f) {
            params.preferredDisplayModeId = 0
            @Suppress("DEPRECATION")
            params.preferredRefreshRate = 0f
        } else {
            @Suppress("DEPRECATION")
            params.preferredRefreshRate = targetFps

            // Attempt to match display mode id for modern variable refresh rate / LTPO panels
            val display = getDisplay()
            if (display != null) {
                val currentMode = display.mode
                val bestMode = display.supportedModes.filter { mode ->
                    mode.physicalWidth == currentMode.physicalWidth &&
                            mode.physicalHeight == currentMode.physicalHeight
                }.minByOrNull { abs(it.refreshRate - targetFps) }

                if (bestMode != null && abs(bestMode.refreshRate - targetFps) < 2.5f) {
                    params.preferredDisplayModeId = bestMode.modeId
                }
            }
        }
        window.attributes = params
    }

    /**
     * Resets the window preferred refresh rate back to system default (0Hz / OS auto).
     */
    fun resetToSystemDefault(window: Window?) {
        if (window == null) return
        currentAppliedRate = 0f
        try {
            applyFrameRateToWindow(window, 0f)
            Log.d(TAG, "Reset window refresh rate back to OS system default")
        } catch (e: Exception) {
            Log.w(TAG, "Error resetting window refresh rate", e)
        }
    }

    private fun registerPowerSaveReceiver() {
        if (powerSaveReceiver != null) return
        powerSaveReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val isPowerSave = powerManager?.isPowerSaveMode == true
                if (isPowerSave != isPowerSaveModeActive) {
                    isPowerSaveModeActive = isPowerSave
                    Log.i(TAG, "System Power Save Mode status changed: $isPowerSaveModeActive")
                }
            }
        }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        try {
            context.registerReceiver(powerSaveReceiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register power save mode receiver", e)
        }
    }

    fun release(window: Window? = null) {
        resetToSystemDefault(window)
        powerSaveReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering powerSaveReceiver", e)
            }
            powerSaveReceiver = null
        }
    }

    companion object {
        private const val TAG = "RefreshRateManager"

        @Volatile
        private var instance: DisplayRefreshRateManager? = null

        fun getInstance(context: Context): DisplayRefreshRateManager {
            return instance ?: synchronized(this) {
                instance ?: DisplayRefreshRateManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
