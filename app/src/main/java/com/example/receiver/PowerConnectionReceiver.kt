package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.example.alarm.AlarmManagerHelper
import com.example.data.preferences.VoltPulsePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Static BroadcastReceiver for monitoring hardware power connection states:
 * - Intent.ACTION_POWER_CONNECTED
 * - Intent.ACTION_POWER_DISCONNECTED
 * - Intent.ACTION_BOOT_COMPLETED
 */
class PowerConnectionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PowerConnectionReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return
        Log.d(TAG, "Power connection state event received: $action")

        val pendingResult = try {
            goAsync()
        } catch (e: Exception) {
            null
        }
        val prefs = VoltPulsePreferences(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Ensure channels exist
                AlarmManagerHelper.createNotificationChannels(context)

                // Inspect current battery level from sticky battery intent
                val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                    context.registerReceiver(null, filter)
                }
                val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val batteryPercent = if (level >= 0 && scale > 0) {
                    ((level.toFloat() / scale.toFloat()) * 100).toInt()
                } else {
                    level
                }

                when (action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        val alertEnabled = prefs.powerConnectedAlert.first()
                        if (alertEnabled) {
                            val plugType = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
                            val plugName = when (plugType) {
                                BatteryManager.BATTERY_PLUGGED_AC -> "Fast AC Charger"
                                BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
                                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Charging Pad"
                                else -> "External Power"
                            }
                            val msg = "Connected to $plugName. Current battery: $batteryPercent%"
                            AlarmManagerHelper.showPowerNotification(context, isConnected = true, message = msg)
                        }

                        // Check if already at or above alarm target
                        val alarmEnabled = prefs.fullChargeAlarmEnabled.first()
                        val targetPercent = prefs.targetChargePercent.first()
                        if (alarmEnabled && batteryPercent >= targetPercent) {
                            AlarmManagerHelper.triggerFullChargeAlarm(context, batteryPercent)
                        }
                    }

                    Intent.ACTION_POWER_DISCONNECTED -> {
                        // Automatically stop alarm if it is currently ringing because user unplugged
                        AlarmManagerHelper.stopAlarm(context)

                        val alertEnabled = prefs.powerDisconnectedAlert.first()
                        if (alertEnabled) {
                            val msg = "Discharging on battery power. Level: $batteryPercent%"
                            AlarmManagerHelper.showPowerNotification(context, isConnected = false, message = msg)
                        }
                    }

                    Intent.ACTION_BOOT_COMPLETED -> {
                        Log.i(TAG, "Device boot completed. VoltPulse receiver initialized.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling power connection event", e)
            } finally {
                try {
                    pendingResult?.finish()
                } catch (e: Exception) {
                    Log.w(TAG, "Error finishing pendingResult", e)
                }
            }
        }
    }
}
