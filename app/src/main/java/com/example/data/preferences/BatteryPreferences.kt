package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.batteryPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "battery_preferences"
)

/**
 * Production-grade DataStore Preferences repository for battery event settings.
 *
 * Persists alert configurations, audio notification URI preferences,
 * and historical charging timestamps.
 */
class BatteryPreferences(private val context: Context) {

    companion object {
        val KEY_IS_PLUGGED_ALERT_ENABLED = booleanPreferencesKey("is_plugged_alert_enabled")
        val KEY_IS_UNPLUGGED_ALERT_ENABLED = booleanPreferencesKey("is_unplugged_alert_enabled")
        val KEY_IS_FULL_CHARGE_ALERT_ENABLED = booleanPreferencesKey("is_full_charge_alert_enabled")
        val KEY_PLUGGED_SOUND_URI = stringPreferencesKey("plugged_sound_uri")
        val KEY_UNPLUGGED_SOUND_URI = stringPreferencesKey("unplugged_sound_uri")
        val KEY_FULL_CHARGE_SOUND_URI = stringPreferencesKey("full_charge_sound_uri")
        val KEY_LAST_FULL_CHARGE_TIMESTAMP = longPreferencesKey("last_full_charge_timestamp")

        // Advanced Longevity, Hardware Protection & Safety Keys
        val KEY_IS_PROTECTION_CAP_ENABLED = booleanPreferencesKey("is_protection_cap_enabled")
        val KEY_CHARGE_LIMIT_TARGET = intPreferencesKey("charge_limit_target")
        val KEY_TARGET_CHARGE_PERCENTAGE = intPreferencesKey("target_charge_percentage")
        val KEY_IS_LOW_BATTERY_ALERT_ENABLED = booleanPreferencesKey("is_low_battery_alert_enabled")
        val KEY_LOW_BATTERY_THRESHOLD = intPreferencesKey("low_battery_threshold")
        val KEY_IS_OVERHEAT_ALERT_ENABLED = booleanPreferencesKey("is_overheat_alert_enabled")
        val KEY_OVERHEAT_TEMPERATURE_THRESHOLD = floatPreferencesKey("overheat_temperature_threshold")
        val KEY_IS_OVERNIGHT_TIMER_ALERT_ENABLED = booleanPreferencesKey("is_overnight_timer_alert_enabled")

        // Voice Announcements (Text-to-Speech)
        val KEY_IS_VOICE_ANNOUNCEMENT_ENABLED = booleanPreferencesKey("is_voice_announcement_enabled")
    }

    private val safeData: Flow<Preferences> = context.batteryPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    // ========================================================================
    // Flow Accessors
    // ========================================================================

    /**
     * Emits whether an alert is triggered when the device is plugged in. Default: true.
     */
    val isPluggedAlertEnabled: Flow<Boolean> = safeData.map { preferences ->
        preferences[KEY_IS_PLUGGED_ALERT_ENABLED] ?: true
    }

    /**
     * Emits whether an alert is triggered when the device is unplugged. Default: true.
     */
    val isUnpluggedAlertEnabled: Flow<Boolean> = safeData.map { preferences ->
        preferences[KEY_IS_UNPLUGGED_ALERT_ENABLED] ?: true
    }

    /**
     * Emits whether an alarm is triggered when reaching 100% full charge. Default: true.
     */
    val isFullChargeAlertEnabled: Flow<Boolean> = safeData.map { preferences ->
        preferences[KEY_IS_FULL_CHARGE_ALERT_ENABLED] ?: true
    }

    /**
     * Emits the custom URI string for the plugged-in sound, or null if using system default.
     */
    val pluggedSoundUri: Flow<String?> = safeData.map { preferences ->
        preferences[KEY_PLUGGED_SOUND_URI]
    }

    /**
     * Emits the custom URI string for the unplugged sound, or null if using system default.
     */
    val unpluggedSoundUri: Flow<String?> = safeData.map { preferences ->
        preferences[KEY_UNPLUGGED_SOUND_URI]
    }

    /**
     * Emits the custom URI string for the full-charge alarm, or null if using system default.
     */
    val fullChargeSoundUri: Flow<String?> = safeData.map { preferences ->
        preferences[KEY_FULL_CHARGE_SOUND_URI]
    }

    /**
     * Emits the epoch millisecond timestamp of the last 100% full charge event. Default: 0L.
     */
    val lastFullChargeTimestamp: Flow<Long> = safeData.map { preferences ->
        preferences[KEY_LAST_FULL_CHARGE_TIMESTAMP] ?: 0L
    }

    /**
     * Emits whether the battery protection cap alert is enabled. Default: true.
     */
    val isProtectionCapEnabled: Flow<Boolean> = safeData.map { preferences ->
        preferences[KEY_IS_PROTECTION_CAP_ENABLED] ?: true
    }

    /**
     * Emits the target battery charge limit percentage (75 - 100). Default: 80.
     */
    val chargeLimitTarget: Flow<Int> = safeData.map { preferences ->
        preferences[KEY_CHARGE_LIMIT_TARGET]
            ?: preferences[KEY_TARGET_CHARGE_PERCENTAGE]
            ?: 80
    }

    /**
     * Emits the user-selected target battery charge alarm percentage (75 - 100). Default: 80.
     * Maintained for backward compatibility.
     */
    val targetChargePercentage: Flow<Int> = chargeLimitTarget

    /**
     * Emits whether an alert is triggered when the battery drops to low levels. Default: true.
     */
    val isLowBatteryAlertEnabled: Flow<Boolean> = safeData.map { preferences ->
        preferences[KEY_IS_LOW_BATTERY_ALERT_ENABLED] ?: true
    }

    /**
     * Emits the low battery threshold percentage (e.g. 15%, 20%). Default: 20.
     */
    val lowBatteryThreshold: Flow<Int> = safeData.map { preferences ->
        preferences[KEY_LOW_BATTERY_THRESHOLD] ?: 20
    }

    /**
     * Emits whether high temperature / overheat alert is enabled. Default: true.
     */
    val isOverheatAlertEnabled: Flow<Boolean> = safeData.map { preferences ->
        preferences[KEY_IS_OVERHEAT_ALERT_ENABLED] ?: true
    }

    /**
     * Emits the temperature threshold in Celsius that triggers an overheat warning. Default: 42.0°C.
     */
    val overheatTemperatureThreshold: Flow<Float> = safeData.map { preferences ->
        preferences[KEY_OVERHEAT_TEMPERATURE_THRESHOLD] ?: 42.0f
    }

    /**
     * Emits whether a recurring gentle reminder is triggered when the device remains connected
     * 30 minutes after reaching target charge. Default: true.
     */
    val isOvernightTimerAlertEnabled: Flow<Boolean> = safeData.map { preferences ->
        preferences[KEY_IS_OVERNIGHT_TIMER_ALERT_ENABLED] ?: true
    }

    /**
     * Emits whether dynamic voice announcements (TTS) are enabled for battery events. Default: false.
     */
    val isVoiceAnnouncementEnabled: Flow<Boolean> = safeData.map { preferences ->
        preferences[KEY_IS_VOICE_ANNOUNCEMENT_ENABLED] ?: false
    }

    // ========================================================================
    // Suspend Setters
    // ========================================================================

    suspend fun setPluggedAlertEnabled(enabled: Boolean) {
        context.batteryPreferencesDataStore.edit { preferences ->
            preferences[KEY_IS_PLUGGED_ALERT_ENABLED] = enabled
        }
    }

    suspend fun setUnpluggedAlertEnabled(enabled: Boolean) {
        context.batteryPreferencesDataStore.edit { preferences ->
            preferences[KEY_IS_UNPLUGGED_ALERT_ENABLED] = enabled
        }
    }

    suspend fun setFullChargeAlertEnabled(enabled: Boolean) {
        context.batteryPreferencesDataStore.edit { preferences ->
            preferences[KEY_IS_FULL_CHARGE_ALERT_ENABLED] = enabled
        }
    }

    suspend fun setPluggedSoundUri(uriString: String?) {
        context.batteryPreferencesDataStore.edit { preferences ->
            if (uriString.isNullOrBlank()) {
                preferences.remove(KEY_PLUGGED_SOUND_URI)
            } else {
                preferences[KEY_PLUGGED_SOUND_URI] = uriString
            }
        }
    }

    suspend fun setUnpluggedSoundUri(uriString: String?) {
        context.batteryPreferencesDataStore.edit { preferences ->
            if (uriString.isNullOrBlank()) {
                preferences.remove(KEY_UNPLUGGED_SOUND_URI)
            } else {
                preferences[KEY_UNPLUGGED_SOUND_URI] = uriString
            }
        }
    }

    suspend fun setFullChargeSoundUri(uriString: String?) {
        context.batteryPreferencesDataStore.edit { preferences ->
            if (uriString.isNullOrBlank()) {
                preferences.remove(KEY_FULL_CHARGE_SOUND_URI)
            } else {
                preferences[KEY_FULL_CHARGE_SOUND_URI] = uriString
            }
        }
    }

    suspend fun setLastFullChargeTimestamp(timestamp: Long) {
        context.batteryPreferencesDataStore.edit { preferences ->
            preferences[KEY_LAST_FULL_CHARGE_TIMESTAMP] = timestamp
        }
    }

    suspend fun setProtectionCapEnabled(enabled: Boolean) {
        context.batteryPreferencesDataStore.edit { preferences ->
            preferences[KEY_IS_PROTECTION_CAP_ENABLED] = enabled
        }
    }

    suspend fun setChargeLimitTarget(target: Int) {
        context.batteryPreferencesDataStore.edit { preferences ->
            val clamped = target.coerceIn(75, 100)
            preferences[KEY_CHARGE_LIMIT_TARGET] = clamped
            preferences[KEY_TARGET_CHARGE_PERCENTAGE] = clamped
        }
    }

    suspend fun setTargetChargePercentage(percentage: Int) {
        setChargeLimitTarget(percentage)
    }

    suspend fun setLowBatteryAlertEnabled(enabled: Boolean) {
        context.batteryPreferencesDataStore.edit { preferences ->
            preferences[KEY_IS_LOW_BATTERY_ALERT_ENABLED] = enabled
        }
    }

    suspend fun setLowBatteryThreshold(threshold: Int) {
        context.batteryPreferencesDataStore.edit { preferences ->
            preferences[KEY_LOW_BATTERY_THRESHOLD] = threshold.coerceIn(5, 50)
        }
    }

    suspend fun setOverheatAlertEnabled(enabled: Boolean) {
        context.batteryPreferencesDataStore.edit { preferences ->
            preferences[KEY_IS_OVERHEAT_ALERT_ENABLED] = enabled
        }
    }

    suspend fun setOverheatTemperatureThreshold(threshold: Float) {
        context.batteryPreferencesDataStore.edit { preferences ->
            preferences[KEY_OVERHEAT_TEMPERATURE_THRESHOLD] = threshold
        }
    }

    suspend fun setOvernightTimerAlertEnabled(enabled: Boolean) {
        context.batteryPreferencesDataStore.edit { preferences ->
            preferences[KEY_IS_OVERNIGHT_TIMER_ALERT_ENABLED] = enabled
        }
    }

    suspend fun setVoiceAnnouncementEnabled(enabled: Boolean) {
        context.batteryPreferencesDataStore.edit { preferences ->
            preferences[KEY_IS_VOICE_ANNOUNCEMENT_ENABLED] = enabled
        }
    }
}
