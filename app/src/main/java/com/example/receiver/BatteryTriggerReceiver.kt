package com.example.receiver

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.SoundHelper
import com.example.audio.VoiceAlertManager
import com.example.data.ChargingSessionTracker
import com.example.data.preferences.BatteryPreferences
import com.example.service.BatteryChargingService
import com.example.widget.BatteryGlanceWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Production-grade background hardware trigger receiver.
 *
 * Energy Optimization Architecture:
 * - Listens for static power connection events (ACTION_POWER_CONNECTED / ACTION_POWER_DISCONNECTED).
 * - On ACTION_POWER_CONNECTED:
 *     * Reads preferences asynchronously with [goAsync].
 *     * Plays connected sound via [SoundHelper].
 *     * Starts [BatteryChargingService] via [ContextCompat.startForegroundService] to maintain
 *       active Doze mode observation and prevent background drops.
 * - On ACTION_POWER_DISCONNECTED:
 *     * Reads preferences asynchronously with [goAsync].
 *     * Plays disconnected sound via [SoundHelper].
 *     * Stops the continuous 100% full-charge alarm if active.
 *     * Stops [BatteryChargingService] immediately via intent / [Context.stopService].
 *     * Unregisters dynamic battery receivers to guarantee ZERO passive background CPU/battery drain.
 */
class BatteryTriggerReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BatteryTriggerReceiver"
        const val CHANNEL_ID = "voltpulse_full_charge_alarm_channel"
        const val NOTIFICATION_ID = 2001

        private val lock = Any()

        @Volatile
        private var dynamicBatteryReceiver: BroadcastReceiver? = null

        @Volatile
        private var hasAlertedInCurrentChargeCycle = false

        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Safely cancels the active 100% full-charge heads-up notification.
         */
        fun cancelNotification(context: Context) {
            try {
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_ID)
                notificationManager.cancel(BatteryChargingService.FULL_CHARGE_NOTIFICATION_ID)
                notificationManager.cancel(BatteryChargingService.OVERHEAT_NOTIFICATION_ID)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cancel notifications", e)
            }
        }

        /**
         * Called when user dismisses the alarm or power is disconnected.
         */
        fun resetAlarmState() {
            synchronized(lock) {
                hasAlertedInCurrentChargeCycle = false
            }
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return
        val appContext = context.applicationContext

        Log.d(TAG, "Hardware battery event received: $action")

        when (action) {
            Intent.ACTION_POWER_CONNECTED -> {
                handlePowerConnected(appContext)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                handlePowerDisconnected(appContext)
            }
        }
    }

    // ========================================================================
    // Power Connected Handling
    // ========================================================================

    private fun handlePowerConnected(appContext: Context) {
        val pendingResult = try {
            goAsync()
        } catch (e: Exception) {
            null
        }
        receiverScope.launch {
            try {
                resetAlarmState()

                // 1. Read preferences and immediately play connected sound via SoundHelper
                val prefs = BatteryPreferences(appContext)
                val isPluggedEnabled = prefs.isPluggedAlertEnabled.first()
                val pluggedUri = prefs.pluggedSoundUri.first()

                if (isPluggedEnabled) {
                    SoundHelper.getInstance(appContext).playPluggedSound(pluggedUri)
                }

                // 2. Dynamic Voice Announcement (Text-to-Speech)
                val batteryStatus = BatteryGlanceWidget.readBatterySnapshot(appContext)
                VoiceAlertManager.getInstance(appContext).announcePluggedIn(batteryStatus.percentage, batteryStatus.wattage)

                // 3. Refresh Home Screen Glance Widget
                BatteryGlanceWidget.updateAllWidgets(appContext)

                // 4. Initialize charging curve telemetry session
                ChargingSessionTracker.getInstance().startNewSession(
                    batteryStatus.percentage,
                    batteryStatus.wattage,
                    batteryStatus.temperatureCelsius
                )

                // 5. Start BatteryChargingService using ContextCompat.startForegroundService()
                val serviceIntent = Intent(appContext, BatteryChargingService::class.java).apply {
                    action = BatteryChargingService.ACTION_START_SERVICE
                }
                ContextCompat.startForegroundService(appContext, serviceIntent)

                // 6. Register temporary dynamic battery receiver to monitor level up to 100%
                registerDynamicBatteryReceiver(appContext)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing ACTION_POWER_CONNECTED", e)
            } finally {
                try {
                    pendingResult?.finish()
                } catch (e: Exception) {
                    Log.w(TAG, "Error finishing pendingResult", e)
                }
            }
        }
    }

    // ========================================================================
    // Power Disconnected Handling
    // ========================================================================

    private fun handlePowerDisconnected(appContext: Context) {
        val pendingResult = try {
            goAsync()
        } catch (e: Exception) {
            null
        }
        receiverScope.launch {
            try {
                // 1. Crucial: Stop BatteryChargingService immediately by sending Intent / stopService
                val stopIntent = Intent(appContext, BatteryChargingService::class.java).apply {
                    action = BatteryChargingService.ACTION_STOP_SERVICE
                }
                appContext.stopService(stopIntent)
                BatteryChargingService.stopService(appContext)

                // 2. Immediately unregister battery level receiver to avoid background battery consumption
                unregisterDynamicBatteryReceiver(appContext)

                // 3. Stop any ringing continuous full charge or overheat alarm and release audio focus
                SoundHelper.getInstance(appContext).stopFullChargeAlarm()
                SoundHelper.getInstance(appContext).stopOverheatAlarm()

                // 4. Dismiss heads-up notifications and reset state
                cancelNotification(appContext)
                resetAlarmState()

                // 5. Read preferences and immediately play disconnected sound via SoundHelper
                val prefs = BatteryPreferences(appContext)
                val isUnpluggedEnabled = prefs.isUnpluggedAlertEnabled.first()
                val unpluggedUri = prefs.unpluggedSoundUri.first()

                if (isUnpluggedEnabled) {
                    SoundHelper.getInstance(appContext).playUnpluggedSound(unpluggedUri)
                }

                // 6. Dynamic Voice Announcement (Text-to-Speech)
                val batteryStatus = BatteryGlanceWidget.readBatterySnapshot(appContext)
                VoiceAlertManager.getInstance(appContext).announceUnplugged(batteryStatus.percentage)

                // 7. Refresh Home Screen Glance Widget
                BatteryGlanceWidget.updateAllWidgets(appContext)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing ACTION_POWER_DISCONNECTED", e)
            } finally {
                try {
                    pendingResult?.finish()
                } catch (e: Exception) {
                    Log.w(TAG, "Error finishing pendingResult", e)
                }
            }
        }
    }

    // ========================================================================
    // Dynamic Battery Level Monitoring (Zero-Drain Lifecycle)
    // ========================================================================

    private fun registerDynamicBatteryReceiver(appContext: Context) {
        synchronized(lock) {
            if (dynamicBatteryReceiver != null) {
                Log.d(TAG, "Dynamic battery receiver already registered")
                return
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (context == null || intent == null) return
                    val c = context.applicationContext
                    evaluateBatteryLevelAndTrigger(c, intent)
                }
            }

            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val stickyIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                appContext.registerReceiver(receiver, filter)
            }

            dynamicBatteryReceiver = receiver
            Log.d(TAG, "Dynamic battery level receiver registered successfully")

            // Immediately check sticky status in case the device was already at 100% when plugged in
            stickyIntent?.let { intent ->
                evaluateBatteryLevelAndTrigger(appContext, intent)
            }
        }
    }

    private fun unregisterDynamicBatteryReceiver(appContext: Context) {
        synchronized(lock) {
            dynamicBatteryReceiver?.let { receiver ->
                try {
                    appContext.unregisterReceiver(receiver)
                    Log.d(TAG, "Dynamic battery receiver unregistered successfully")
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Receiver was not registered or already removed", e)
                } finally {
                    dynamicBatteryReceiver = null
                }
            }
        }
    }

    // ========================================================================
    // 100% Full Charge Evaluation & Alert Dispatch
    // ========================================================================

    private fun evaluateBatteryLevelAndTrigger(appContext: Context, intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

        val percentage = if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            level
        }

        val isFull = status == BatteryManager.BATTERY_STATUS_FULL || percentage >= 100

        if (isFull) {
            synchronized(lock) {
                if (hasAlertedInCurrentChargeCycle) {
                    return
                }
                hasAlertedInCurrentChargeCycle = true
            }

            // Trigger full-charge workflow
            triggerFullChargeWorkflow(appContext)
        }
    }

    private fun triggerFullChargeWorkflow(appContext: Context) {
        receiverScope.launch {
            try {
                val prefs = BatteryPreferences(appContext)
                val isFullChargeEnabled = prefs.isFullChargeAlertEnabled.first()

                if (!isFullChargeEnabled) {
                    Log.d(TAG, "100% reached, but full-charge alert is disabled in preferences")
                    return@launch
                }

                // 1. Update lastFullChargeTimestamp in DataStore
                val now = System.currentTimeMillis()
                prefs.setLastFullChargeTimestamp(now)

                // 2. Play continuous looping alarm via SoundHelper
                val soundUri = prefs.fullChargeSoundUri.first()
                SoundHelper.getInstance(appContext).startFullChargeAlarm(soundUri)

                // 3. Post high-priority heads-up notification with "DISMISS ALARM" action button
                postFullChargeNotification(appContext)

                Log.i(TAG, "VoltPulse 100% Full Charge Alert triggered successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Error executing full-charge alert workflow", e)
            }
        }
    }

    private fun postFullChargeNotification(context: Context) {
        ensureNotificationChannel(context)

        // Dismiss action intent directed to AlarmDismissReceiver
        val dismissIntent = Intent(context, AlarmDismissReceiver::class.java).apply {
            action = AlarmDismissReceiver.ACTION_DISMISS_ALARM
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Tap notification to open MainActivity
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            102,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.full_charge_title))
            .setContentText(context.getString(R.string.full_charge_message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .addAction(
                R.mipmap.ic_launcher,
                context.getString(R.string.alarm_action_dismiss),
                dismissPendingIntent
            )
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } else {
            Log.w(TAG, "Cannot post notification: POST_NOTIFICATIONS permission not granted")
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_alarm_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
