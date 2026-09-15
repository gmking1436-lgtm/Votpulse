package com.example.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.RingtoneManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundHelper
import com.example.audio.VoiceAlertManager
import com.example.data.ChargingSessionTracker
import com.example.data.model.BatteryInfoModel
import com.example.data.model.ChargingSessionPoint
import com.example.data.preferences.BatteryPreferences
import com.example.service.BatteryProtectionManager
import com.example.service.ChargingPowerTelemetry
import com.example.service.ChargingSpeedCategory
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ThemeManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Identifies which audio preview track is currently playing in the settings UI.
 */
enum class PreviewTarget {
    NONE,
    CONNECTED,
    DISCONNECTED,
    FULL_CHARGE
}

/**
 * Production-ready ViewModel managing real-time battery telemetry, alert configurations,
 * and unified audio preview state.
 *
 * Key Architecture:
 * - Lifecycle-Aware: Uses [callbackFlow] combined with [SharingStarted.WhileSubscribed(5000)] to
 *   automatically unregister broadcast receivers and pause hardware polling whenever the UI
 *   transitions to the background.
 * - Single-Instance Audio Previews: Eliminates overlapping audio by delegating to [SoundHelper.playPreview]
 *   and tracking the active [PreviewTarget].
 * - Resilient SAF Permissions: Handles [ContentResolver.takePersistableUriPermission] for custom
 *   tones selected through the Storage Access Framework.
 */
class BatteryViewModel(application: Application) : AndroidViewModel(application) {

    private val batteryPreferences = BatteryPreferences(application.applicationContext)
    private val soundHelper = SoundHelper.getInstance(application.applicationContext)
    private val themeManager = ThemeManager.getInstance(application.applicationContext)

    // ========================================================================
    // Dynamic Theme StateFlow
    // ========================================================================

    val selectedThemeMode: StateFlow<AppThemeMode> = themeManager.themeModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppThemeMode.DYNAMIC_MATERIAL_YOU
        )

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            themeManager.setThemeMode(mode)
        }
    }

    // ========================================================================
    // Settings StateFlows
    // ========================================================================

    val isPluggedAlertEnabled: StateFlow<Boolean> = batteryPreferences.isPluggedAlertEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val isUnpluggedAlertEnabled: StateFlow<Boolean> = batteryPreferences.isUnpluggedAlertEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val isFullChargeAlertEnabled: StateFlow<Boolean> = batteryPreferences.isFullChargeAlertEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val pluggedSoundUri: StateFlow<String?> = batteryPreferences.pluggedSoundUri
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val unpluggedSoundUri: StateFlow<String?> = batteryPreferences.unpluggedSoundUri
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val fullChargeSoundUri: StateFlow<String?> = batteryPreferences.fullChargeSoundUri
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val isProtectionCapEnabled: StateFlow<Boolean> = batteryPreferences.isProtectionCapEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val chargeLimitTarget: StateFlow<Int> = batteryPreferences.chargeLimitTarget
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 80
        )

    val targetChargePercentage: StateFlow<Int> = batteryPreferences.targetChargePercentage
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 80
        )

    val isLowBatteryAlertEnabled: StateFlow<Boolean> = batteryPreferences.isLowBatteryAlertEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val lowBatteryThreshold: StateFlow<Int> = batteryPreferences.lowBatteryThreshold
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 20
        )

    val isOvernightTimerAlertEnabled: StateFlow<Boolean> = batteryPreferences.isOvernightTimerAlertEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val isOverheatAlertEnabled: StateFlow<Boolean> = batteryPreferences.isOverheatAlertEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val overheatTemperatureThreshold: StateFlow<Float> = batteryPreferences.overheatTemperatureThreshold
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 42.0f
        )

    val isVoiceAnnouncementEnabled: StateFlow<Boolean> = batteryPreferences.isVoiceAnnouncementEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val chargingSessionPoints: StateFlow<List<ChargingSessionPoint>> =
        ChargingSessionTracker.getInstance().sessionPoints

    // ========================================================================
    // Unified Audio Preview State
    // ========================================================================

    private val _currentPlayingPreview = MutableStateFlow(PreviewTarget.NONE)
    val currentPlayingPreview: StateFlow<PreviewTarget> = _currentPlayingPreview.asStateFlow()

    /**
     * Toggles preview playback for a specific sound category.
     * - If currently playing this target -> stops playback immediately and resets to NONE.
     * - If currently playing a different target -> stops the previous sound and begins this target.
     * - When audio naturally completes -> resets to NONE.
     */
    fun togglePreview(target: PreviewTarget, uriString: String?) {
        if (_currentPlayingPreview.value == target) {
            stopPreview()
        } else {
            _currentPlayingPreview.value = target

            val defaultType = when (target) {
                PreviewTarget.CONNECTED, PreviewTarget.DISCONNECTED -> RingtoneManager.TYPE_NOTIFICATION
                PreviewTarget.FULL_CHARGE -> RingtoneManager.TYPE_ALARM
                PreviewTarget.NONE -> {
                    stopPreview()
                    return
                }
            }

            val parsedUri = uriString?.let {
                try {
                    Uri.parse(it)
                } catch (e: Exception) {
                    Log.w(TAG, "Malformed preview URI: $it", e)
                    null
                }
            }

            soundHelper.playPreview(
                context = getApplication<Application>().applicationContext,
                uri = parsedUri,
                defaultType = defaultType,
                onComplete = {
                    if (_currentPlayingPreview.value == target) {
                        _currentPlayingPreview.value = PreviewTarget.NONE
                    }
                }
            )
        }
    }

    /**
     * Stops any active preview audio and resets the active target to NONE.
     */
    fun stopPreview() {
        soundHelper.stopPreview()
        _currentPlayingPreview.value = PreviewTarget.NONE
    }

    // ========================================================================
    // Lifecycle-Aware Hardware Battery Telemetry Flow
    // ========================================================================

    private data class RawBatteryData(
        val percentage: Int,
        val isCharging: Boolean,
        val healthStatus: String,
        val temperatureCelsius: Float,
        val voltageMilliVolts: Int,
        val technology: String
    )

    private val batteryRawFlow: Flow<RawBatteryData> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null) {
                    trySend(parseBatteryIntent(intent))
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val initialIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().registerReceiver(
                receiver,
                filter,
                Context.RECEIVER_EXPORTED
            )
        } else {
            getApplication<Application>().registerReceiver(receiver, filter)
        }

        // Deliver initial sticky battery broadcast immediately
        if (initialIntent != null) {
            trySend(parseBatteryIntent(initialIntent))
        }

        awaitClose {
            try {
                getApplication<Application>().unregisterReceiver(receiver)
                Log.d(TAG, "Battery broadcast receiver unregistered due to UI inactivity")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Battery receiver already unregistered or not found", e)
            }
        }
    }

    /**
     * Exposes real-time, consolidated battery state to the Jetpack Compose UI.
     * Pauses observation and frees CPU/battery when the UI is detached from view.
     */
    val batteryState: StateFlow<BatteryInfoModel> = combine(
        batteryRawFlow,
        batteryPreferences.lastFullChargeTimestamp
    ) { raw, lastTimestamp ->
        BatteryInfoModel(
            percentage = raw.percentage,
            isCharging = raw.isCharging,
            healthStatus = raw.healthStatus,
            temperatureCelsius = raw.temperatureCelsius,
            voltageMilliVolts = raw.voltageMilliVolts,
            technology = raw.technology,
            lastFullChargeFormatted = formatTimestamp(lastTimestamp)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = getInitialBatteryInfo()
    )

    /**
     * Real-time charging wattage and hardware speed category telemetry.
     */
    val chargingTelemetry: StateFlow<ChargingPowerTelemetry> = batteryRawFlow
        .map { raw ->
            val telemetry = BatteryProtectionManager.calculateChargingPower(
                context = getApplication<Application>().applicationContext,
                voltageMilliVolts = raw.voltageMilliVolts,
                batteryPercentage = raw.percentage,
                isCharging = raw.isCharging
            )
            ChargingSessionTracker.getInstance().recordTelemetry(
                percentage = raw.percentage,
                wattage = telemetry.watts,
                temperatureCelsius = raw.temperatureCelsius,
                isCharging = raw.isCharging
            )
            telemetry
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ChargingPowerTelemetry(
                voltageMilliVolts = 4100,
                currentMicroAmps = 0,
                currentMilliAmps = 0,
                watts = 0f,
                speedCategory = ChargingSpeedCategory.DISCHARGING,
                isHardwareReported = false
            )
        )

    // ========================================================================
    // Settings Mutators
    // ========================================================================

    fun setVoiceAnnouncementEnabled(enabled: Boolean) {
        viewModelScope.launch {
            batteryPreferences.setVoiceAnnouncementEnabled(enabled)
            if (enabled) {
                val current = batteryState.value
                val watts = chargingTelemetry.value.watts
                VoiceAlertManager.getInstance(getApplication<Application>().applicationContext)
                    .announcePluggedIn(current.percentage, watts)
            }
        }
    }

    fun testVoiceAnnouncement() {
        val current = batteryState.value
        val watts = chargingTelemetry.value.watts
        VoiceAlertManager.getInstance(getApplication<Application>().applicationContext)
            .announcePluggedIn(current.percentage, watts)
    }

    fun setProtectionCapEnabled(enabled: Boolean) {
        viewModelScope.launch {
            batteryPreferences.setProtectionCapEnabled(enabled)
        }
    }

    fun setChargeLimitTarget(target: Int) {
        viewModelScope.launch {
            batteryPreferences.setChargeLimitTarget(target)
        }
    }

    fun setLowBatteryAlertEnabled(enabled: Boolean) {
        viewModelScope.launch {
            batteryPreferences.setLowBatteryAlertEnabled(enabled)
        }
    }

    fun setLowBatteryThreshold(threshold: Int) {
        viewModelScope.launch {
            batteryPreferences.setLowBatteryThreshold(threshold)
        }
    }

    fun setOvernightTimerAlertEnabled(enabled: Boolean) {
        viewModelScope.launch {
            batteryPreferences.setOvernightTimerAlertEnabled(enabled)
        }
    }

    fun setTargetChargePercentage(percentage: Int) {
        viewModelScope.launch {
            batteryPreferences.setTargetChargePercentage(percentage)
        }
    }

    fun setOverheatAlertEnabled(enabled: Boolean) {
        viewModelScope.launch {
            batteryPreferences.setOverheatAlertEnabled(enabled)
        }
    }

    fun setOverheatTemperatureThreshold(threshold: Float) {
        viewModelScope.launch {
            batteryPreferences.setOverheatTemperatureThreshold(threshold)
        }
    }

    fun setPluggedAlertEnabled(enabled: Boolean) {
        viewModelScope.launch {
            batteryPreferences.setPluggedAlertEnabled(enabled)
        }
    }

    fun setUnpluggedAlertEnabled(enabled: Boolean) {
        viewModelScope.launch {
            batteryPreferences.setUnpluggedAlertEnabled(enabled)
        }
    }

    fun setFullChargeAlertEnabled(enabled: Boolean) {
        viewModelScope.launch {
            batteryPreferences.setFullChargeAlertEnabled(enabled)
        }
    }

    /**
     * Saves custom plugged audio URI and acquires persistent read permissions via SAF.
     */
    fun setPluggedSoundUri(uri: Uri?) {
        takePersistablePermissionSafely(uri)
        viewModelScope.launch {
            batteryPreferences.setPluggedSoundUri(uri?.toString())
        }
    }

    /**
     * Overload taking URI string directly (or null to revert to system default).
     */
    fun setPluggedSoundUri(uriString: String?) {
        val uri = uriString?.let { Uri.parse(it) }
        takePersistablePermissionSafely(uri)
        viewModelScope.launch {
            batteryPreferences.setPluggedSoundUri(uriString)
        }
    }

    /**
     * Saves custom unplugged audio URI and acquires persistent read permissions via SAF.
     */
    fun setUnpluggedSoundUri(uri: Uri?) {
        takePersistablePermissionSafely(uri)
        viewModelScope.launch {
            batteryPreferences.setUnpluggedSoundUri(uri?.toString())
        }
    }

    /**
     * Overload taking URI string directly (or null to revert to system default).
     */
    fun setUnpluggedSoundUri(uriString: String?) {
        val uri = uriString?.let { Uri.parse(it) }
        takePersistablePermissionSafely(uri)
        viewModelScope.launch {
            batteryPreferences.setUnpluggedSoundUri(uriString)
        }
    }

    /**
     * Saves custom 100% full-charge alarm URI and acquires persistent read permissions via SAF.
     */
    fun setFullChargeSoundUri(uri: Uri?) {
        takePersistablePermissionSafely(uri)
        viewModelScope.launch {
            batteryPreferences.setFullChargeSoundUri(uri?.toString())
        }
    }

    /**
     * Overload taking URI string directly (or null to revert to system default).
     */
    fun setFullChargeSoundUri(uriString: String?) {
        val uri = uriString?.let { Uri.parse(it) }
        takePersistablePermissionSafely(uri)
        viewModelScope.launch {
            batteryPreferences.setFullChargeSoundUri(uriString)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPreview()
    }

    // ========================================================================
    // Internal Helpers
    // ========================================================================

    private fun takePersistablePermissionSafely(uri: Uri?) {
        if (uri == null) return
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, takeFlags)
            Log.d(TAG, "Persistable URI permission acquired for: $uri")
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not take persistable URI permission for: $uri ($e)")
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error acquiring persistable URI permission", e)
        }
    }

    private fun parseBatteryIntent(intent: Intent): RawBatteryData {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percentage = if (level >= 0 && scale > 0) {
            ((level.toFloat() / scale.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val healthCode = intent.getIntExtra(
            BatteryManager.EXTRA_HEALTH,
            BatteryManager.BATTERY_HEALTH_UNKNOWN
        )
        val healthStatus = mapHealthCodeToString(healthCode)

        val rawTemperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val temperatureCelsius = rawTemperature / 10.0f

        val voltageMilliVolts = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

        val technologyRaw = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
        val technology = if (technologyRaw.isNullOrBlank()) "Li-ion" else technologyRaw

        return RawBatteryData(
            percentage = percentage,
            isCharging = isCharging,
            healthStatus = healthStatus,
            temperatureCelsius = temperatureCelsius,
            voltageMilliVolts = voltageMilliVolts,
            technology = technology
        )
    }

    private fun mapHealthCodeToString(healthCode: Int): String {
        return when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unspecified"
        }
    }

    private fun getInitialBatteryInfo(): BatteryInfoModel {
        return try {
            val stickyIntent = getApplication<Application>().registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            if (stickyIntent != null) {
                val raw = parseBatteryIntent(stickyIntent)
                BatteryInfoModel(
                    percentage = raw.percentage,
                    isCharging = raw.isCharging,
                    healthStatus = raw.healthStatus,
                    temperatureCelsius = raw.temperatureCelsius,
                    voltageMilliVolts = raw.voltageMilliVolts,
                    technology = raw.technology,
                    lastFullChargeFormatted = "Never"
                )
            } else {
                BatteryInfoModel()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading initial sticky battery status", e)
            BatteryInfoModel()
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0L) return "Never"
        return try {
            val formatter = SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault())
            formatter.format(Date(timestamp))
        } catch (e: Exception) {
            "Never"
        }
    }

    companion object {
        private const val TAG = "BatteryViewModel"
    }
}
