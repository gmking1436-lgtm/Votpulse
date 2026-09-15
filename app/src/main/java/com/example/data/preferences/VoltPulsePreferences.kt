package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "voltpulse_settings")

class VoltPulsePreferences(private val context: Context) {

    companion object {
        val KEY_FULL_CHARGE_ALARM_ENABLED = booleanPreferencesKey("full_charge_alarm_enabled")
        val KEY_TARGET_CHARGE_PERCENT = intPreferencesKey("target_charge_percent")
        val KEY_SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val KEY_VIBRATE_ENABLED = booleanPreferencesKey("vibrate_enabled")
        val KEY_POWER_CONNECTED_ALERT = booleanPreferencesKey("power_connected_alert")
        val KEY_POWER_DISCONNECTED_ALERT = booleanPreferencesKey("power_disconnected_alert")
        val KEY_IS_ALARM_RINGING = booleanPreferencesKey("is_alarm_ringing")
    }

    val fullChargeAlarmEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_FULL_CHARGE_ALARM_ENABLED] ?: true
    }

    val targetChargePercent: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_TARGET_CHARGE_PERCENT] ?: 100
    }

    val soundEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SOUND_ENABLED] ?: true
    }

    val vibrateEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_VIBRATE_ENABLED] ?: true
    }

    val powerConnectedAlert: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_POWER_CONNECTED_ALERT] ?: true
    }

    val powerDisconnectedAlert: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_POWER_DISCONNECTED_ALERT] ?: true
    }

    val isAlarmRinging: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_IS_ALARM_RINGING] ?: false
    }

    suspend fun setFullChargeAlarmEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FULL_CHARGE_ALARM_ENABLED] = enabled
        }
    }

    suspend fun setTargetChargePercent(percent: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TARGET_CHARGE_PERCENT] = percent.coerceIn(50, 100)
        }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SOUND_ENABLED] = enabled
        }
    }

    suspend fun setVibrateEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_VIBRATE_ENABLED] = enabled
        }
    }

    suspend fun setPowerConnectedAlert(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_POWER_CONNECTED_ALERT] = enabled
        }
    }

    suspend fun setPowerDisconnectedAlert(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_POWER_DISCONNECTED_ALERT] = enabled
        }
    }

    suspend fun setAlarmRinging(ringing: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_IS_ALARM_RINGING] = ringing
        }
    }
}
