package com.example.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.audio.SoundHelper
import com.example.service.BatteryChargingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver triggered by the "DISMISS ALARM" notification action button or swipe dismiss.
 *
 * Responsibilities:
 * - Stops the continuous 100% loop audio in [SoundHelper] immediately.
 * - Cancels the heads-up notification from [NotificationManager].
 * - Resets the charge cycle alarm latch in [BatteryTriggerReceiver].
 */
class AlarmDismissReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DISMISS_ALARM = "com.example.voltpulse.ACTION_DISMISS_ALARM"
        private const val TAG = "AlarmDismissReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val action = intent?.action
        Log.d(TAG, "Alarm dismissal requested with action: $action")

        val appContext = context.applicationContext
        val pendingResult = try {
            goAsync()
        } catch (e: Exception) {
            null
        }

        CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                // 1. Stop continuous loop audio in SoundHelper immediately
                SoundHelper.getInstance(appContext).stopFullChargeAlarm()
                SoundHelper.getInstance(appContext).stopOverheatAlarm()

                // 2. Cancel heads-up and service notifications
                val notificationManager =
                    appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(BatteryChargingService.FULL_CHARGE_NOTIFICATION_ID)
                notificationManager.cancel(BatteryChargingService.OVERHEAT_NOTIFICATION_ID)
                notificationManager.cancel(BatteryChargingService.PROLONGED_CHARGING_NOTIFICATION_ID)
                notificationManager.cancel(LowBatteryReceiver.LOW_BATTERY_NOTIFICATION_ID)
                notificationManager.cancel(BatteryTriggerReceiver.NOTIFICATION_ID)

                // 3. Reset internal trigger state
                BatteryTriggerReceiver.resetAlarmState()

                Log.d(TAG, "Full-charge alarm stopped, notification cancelled, and audio resources released")
            } catch (e: Exception) {
                Log.e(TAG, "Error dismissing full-charge alarm", e)
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
