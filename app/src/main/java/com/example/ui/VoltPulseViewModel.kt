package com.example.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmManagerHelper
import com.example.data.model.BatteryHealthStatus
import com.example.data.model.BatteryState
import com.example.data.model.ChargingSource
import com.example.data.preferences.VoltPulsePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VoltPulseUiState(
    val batteryState: BatteryState = BatteryState(),
    val targetChargePercent: Int = 100,
    val fullChargeAlarmEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val powerConnectedAlert: Boolean = true,
    val powerDisconnectedAlert: Boolean = true,
    val isAlarmRinging: Boolean = false
)

class VoltPulseViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = VoltPulsePreferences(application)
    private val _batteryState = MutableStateFlow(BatteryState())

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                parseBatteryIntent(intent)
            }
        }
    }

    val uiState: StateFlow<VoltPulseUiState> = combine(
        _batteryState,
        preferences.targetChargePercent,
        preferences.fullChargeAlarmEnabled,
        preferences.soundEnabled,
        preferences.vibrateEnabled,
        preferences.powerConnectedAlert,
        preferences.powerDisconnectedAlert,
        preferences.isAlarmRinging
    ) { params ->
        VoltPulseUiState(
            batteryState = params[0] as BatteryState,
            targetChargePercent = params[1] as Int,
            fullChargeAlarmEnabled = params[2] as Boolean,
            soundEnabled = params[3] as Boolean,
            vibrateEnabled = params[4] as Boolean,
            powerConnectedAlert = params[5] as Boolean,
            powerDisconnectedAlert = params[6] as Boolean,
            isAlarmRinging = params[7] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VoltPulseUiState()
    )

    init {
        // Register sticky battery intent receiver
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val initialIntent = application.registerReceiver(batteryReceiver, intentFilter)
        if (initialIntent != null) {
            parseBatteryIntent(initialIntent)
        }
    }

    private fun parseBatteryIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val chargingSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> ChargingSource.AC
            BatteryManager.BATTERY_PLUGGED_USB -> ChargingSource.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> ChargingSource.WIRELESS
            else -> if (isCharging) ChargingSource.UNKNOWN else ChargingSource.NONE
        }

        val healthCode = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val health = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealthStatus.GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealthStatus.OVERHEAT
            BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealthStatus.DEAD
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealthStatus.OVER_VOLTAGE
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealthStatus.UNSPECIFIED_FAILURE
            BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealthStatus.COLD
            else -> BatteryHealthStatus.UNKNOWN
        }

        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val temperatureCelsius = tempTenths / 10.0f
        val technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"
        val present = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)

        val updated = BatteryState(
            level = level,
            scale = scale,
            isCharging = isCharging,
            status = status,
            chargingSource = chargingSource,
            health = health,
            voltageMv = voltageMv,
            temperatureCelsius = temperatureCelsius,
            technology = technology,
            present = present
        )
        _batteryState.value = updated

        // Check if charge target reached while charging
        viewModelScope.launch {
            val alarmEnabled = preferences.fullChargeAlarmEnabled
            val target = preferences.targetChargePercent
            val isRinging = preferences.isAlarmRinging
            // Handled when state is evaluated
            if (isCharging && updated.percentage >= uiState.value.targetChargePercent && uiState.value.fullChargeAlarmEnabled && !uiState.value.isAlarmRinging) {
                AlarmManagerHelper.triggerFullChargeAlarm(getApplication(), updated.percentage)
            }
        }
    }

    fun setTargetChargePercent(percent: Int) {
        viewModelScope.launch {
            preferences.setTargetChargePercent(percent)
        }
    }

    fun setFullChargeAlarmEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setFullChargeAlarmEnabled(enabled)
            if (!enabled) {
                AlarmManagerHelper.stopAlarm(getApplication())
            }
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setSoundEnabled(enabled)
        }
    }

    fun setVibrateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setVibrateEnabled(enabled)
        }
    }

    fun setPowerConnectedAlert(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setPowerConnectedAlert(enabled)
        }
    }

    fun setPowerDisconnectedAlert(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setPowerDisconnectedAlert(enabled)
        }
    }

    fun triggerTestAlarm() {
        AlarmManagerHelper.triggerFullChargeAlarm(getApplication(), uiState.value.batteryState.percentage)
    }

    fun dismissAlarm() {
        AlarmManagerHelper.stopAlarm(getApplication())
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
    }
}
