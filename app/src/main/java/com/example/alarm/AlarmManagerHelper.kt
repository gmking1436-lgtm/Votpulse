package com.example.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.preferences.VoltPulsePreferences
import com.example.receiver.DismissReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object AlarmManagerHelper {
    private const val TAG = "AlarmManagerHelper"
    const val ALARM_NOTIFICATION_ID = 1001
    const val POWER_EVENT_NOTIFICATION_ID = 1002
    const val CHANNEL_ALARM_ID = "voltpulse_charge_alarm_channel"
    const val CHANNEL_POWER_ID = "voltpulse_power_events_channel"

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // Channel 1: Urgent full-charge alarm
            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM_ID,
                context.getString(R.string.notification_channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_alarm_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 300, 600)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            // Channel 2: Power and battery event updates
            val powerChannel = NotificationChannel(
                CHANNEL_POWER_ID,
                context.getString(R.string.notification_channel_power_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_power_desc)
                enableVibration(false)
            }

            notificationManager.createNotificationChannel(alarmChannel)
            notificationManager.createNotificationChannel(powerChannel)
        }
    }

    fun triggerFullChargeAlarm(context: Context, currentPercent: Int) {
        val prefs = VoltPulsePreferences(context)
        CoroutineScope(Dispatchers.IO).launch {
            val soundEnabled = prefs.soundEnabled.first()
            val vibrateEnabled = prefs.vibrateEnabled.first()

            prefs.setAlarmRinging(true)

            // Start sound playback in loop
            if (soundEnabled) {
                startSound(context)
            }

            // Start repeating vibration
            if (vibrateEnabled) {
                startVibration(context)
            }

            // Post persistent heads-up notification with Dismiss action
            showFullChargeNotification(context, currentPercent)
        }
    }

    private fun startSound(context: Context) {
        try {
            stopSound()
            var alertUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alertUri == null) {
                alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            if (alertUri == null) {
                alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing alarm sound", e)
        }
    }

    private fun startVibration(context: Context) {
        try {
            val pattern = longArrayOf(0, 800, 400, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibrator = vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, 0) // Repeat from index 0
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting vibration", e)
        }
    }

    private fun stopSound() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping sound", e)
        } finally {
            mediaPlayer = null
        }
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping vibration", e)
        } finally {
            vibrator = null
        }
    }

    fun stopAlarm(context: Context) {
        stopSound()
        stopVibration()

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(ALARM_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling notification", e)
        }

        CoroutineScope(Dispatchers.IO).launch {
            val prefs = VoltPulsePreferences(context)
            prefs.setAlarmRinging(false)
        }
    }

    private fun showFullChargeNotification(context: Context, batteryPercent: Int) {
        createNotificationChannels(context)

        // Dismiss action intent
        val dismissIntent = Intent(context, DismissReceiver::class.java).apply {
            action = DismissReceiver.ACTION_DISMISS_ALARM
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Open app intent
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            102,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALARM_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(context.getString(R.string.full_charge_title))
            .setContentText("Target charge reached ($batteryPercent%). Unplug device to prevent battery strain.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openAppPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.alarm_action_dismiss),
                dismissPendingIntent
            )
            .build()

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(ALARM_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing notification permission", e)
        }
    }

    fun showPowerNotification(context: Context, isConnected: Boolean, message: String) {
        createNotificationChannels(context)

        val openAppIntent = Intent(context, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            103,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isConnected) {
            context.getString(R.string.power_connected_title)
        } else {
            context.getString(R.string.power_disconnected_title)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_POWER_ID)
            .setSmallIcon(if (isConnected) android.R.drawable.ic_lock_idle_charging else android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .build()

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(POWER_EVENT_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing notification permission for power event", e)
        }
    }
}
