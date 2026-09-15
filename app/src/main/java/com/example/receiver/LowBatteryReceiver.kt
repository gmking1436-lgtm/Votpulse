package com.example.receiver

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.SoundHelper
import com.example.audio.VoiceAlertManager
import com.example.data.preferences.BatteryPreferences
import com.example.widget.BatteryGlanceWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Systems-grade BroadcastReceiver listening for [Intent.ACTION_BATTERY_LOW].
 *
 * Responsibilities:
 * - Alerts user before catastrophic deep discharge occurs.
 * - Adheres strictly to user settings: [BatteryPreferences.isLowBatteryAlertEnabled].
 * - Plays distinct audible low battery warning via [SoundHelper].
 * - Emits voice announcement via [VoiceAlertManager] if enabled.
 * - Posts high-priority notification with Dismiss action.
 * - Automatically dismisses notification on [Intent.ACTION_BATTERY_OKAY].
 * - Zero idle battery consumption (only runs on system OS broadcasts).
 */
class LowBatteryReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return
        val appContext = context.applicationContext

        Log.d(TAG, "LowBatteryReceiver onReceive action: $action")

        when (action) {
            Intent.ACTION_BATTERY_LOW -> handleBatteryLow(appContext)
            Intent.ACTION_BATTERY_OKAY,
            Intent.ACTION_POWER_CONNECTED -> handleBatteryRecovered(appContext)
        }
    }

    private fun handleBatteryLow(appContext: Context) {
        val pendingResult = try {
            goAsync()
        } catch (e: Exception) {
            null
        }
        receiverScope.launch {
            try {
                val prefs = BatteryPreferences(appContext)
                val isLowBatteryEnabled = prefs.isLowBatteryAlertEnabled.first()

                if (!isLowBatteryEnabled) {
                    Log.d(TAG, "Battery low broadcast received, but alert is disabled in preferences")
                    return@launch
                }

                // 1. Snapshot current battery telemetry
                val snapshot = BatteryGlanceWidget.readBatterySnapshot(appContext)
                val percentage = snapshot.percentage

                // 2. Audible tone via SoundHelper
                SoundHelper.getInstance(appContext).playLowBatteryAlert()

                // 3. Dynamic voice announcement
                VoiceAlertManager.getInstance(appContext).announceLowBattery(percentage)

                // 4. Post high-priority alert notification
                postLowBatteryNotification(appContext, percentage)

                // 5. Update Glance widget
                BatteryGlanceWidget.updateAllWidgets(appContext)

                Log.i(TAG, "Low battery warning ($percentage%) dispatched successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling ACTION_BATTERY_LOW", e)
            } finally {
                try {
                    pendingResult?.finish()
                } catch (e: Exception) {
                    Log.w(TAG, "Error finishing pendingResult", e)
                }
            }
        }
    }

    private fun handleBatteryRecovered(appContext: Context) {
        try {
            val notificationManager =
                appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(LOW_BATTERY_NOTIFICATION_ID)
            Log.d(TAG, "Low battery alert dismissed on battery recovery")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel low battery notification", e)
        }
    }

    private fun postLowBatteryNotification(context: Context, percentage: Int) {
        ensureNotificationChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            110,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = PendingIntent.getBroadcast(
            context,
            111,
            Intent(context, AlarmDismissReceiver::class.java).apply {
                action = AlarmDismissReceiver.ACTION_DISMISS_ALARM
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.low_battery_alert_title, percentage)
        val message = context.getString(R.string.low_battery_alert_message)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setDeleteIntent(dismissIntent)
            .addAction(R.mipmap.ic_launcher, context.getString(R.string.alarm_action_dismiss), dismissIntent)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(LOW_BATTERY_NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_low_battery_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_low_battery_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 350, 150, 350)
                setShowBadge(true)
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "LowBatteryReceiver"
        const val CHANNEL_ID = "voltpulse_low_battery_alarm_channel"
        const val LOW_BATTERY_NOTIFICATION_ID = 2004
    }
}
