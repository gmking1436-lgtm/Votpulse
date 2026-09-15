package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.SoundHelper
import com.example.data.preferences.BatteryPreferences
import com.example.receiver.AlarmDismissReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Production-grade Foreground Service ensuring uninterrupted charging telemetry and protection on modern Android.
 *
 * Capabilities:
 * - Compatible with Android 14+ (API 34+) Foreground Service execution policies.
 * - Prevents OS process termination and Doze mode deep-sleep dropouts during charging cycles.
 * - Holds a safe, bounded [PowerManager.WakeLock] to guarantee battery threshold detection.
 * - Manages custom target percentage (80% - 100%) longevity alarm triggering with instant dismissal support.
 * - Monitors battery thermals during charging, firing high-priority heads-up warning and sound if overheating.
 * - Self-terminates immediately when the charger is unplugged to preserve battery life.
 */
class BatteryChargingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var hasAlertedTargetCharge = false
    private var hasAlertedOverheat = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BatteryChargingService onCreate")
        acquireWakeLock()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "BatteryChargingService onStartCommand action=$action")

        if (action == ACTION_STOP_SERVICE) {
            stopMonitoringAndSelf()
            return START_NOT_STICKY
        }

        // Start Foreground Service with appropriate Android 14+ type
        val notification = buildForegroundNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }

        registerBatteryMonitor()
        return START_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "VoltPulse:ChargingServiceWakeLock"
            ).apply {
                setReferenceCounted(false)
                // Safety timeout: 4 hours maximum
                acquire(4 * 60 * 60 * 1000L)
            }
            Log.d(TAG, "Charging WakeLock acquired safely")
        } catch (e: Exception) {
            Log.w(TAG, "Failed acquiring WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Charging WakeLock released")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing WakeLock", e)
        } finally {
            wakeLock = null
        }
    }

    private fun registerBatteryMonitor() {
        if (batteryReceiver != null) return

        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                val action = intent.action

                if (action == Intent.ACTION_POWER_DISCONNECTED) {
                    Log.d(TAG, "Power disconnected detected in service, stopping self")
                    stopMonitoringAndSelf()
                    return
                }

                if (action == Intent.ACTION_BATTERY_CHANGED) {
                    handleBatteryChanged(intent)
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }
        Log.d(TAG, "Active battery level receiver registered in charging service")
    }

    private fun handleBatteryChanged(intent: Intent) {
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

        if (!isCharging) {
            Log.d(TAG, "Device is no longer charging according to battery broadcast")
            return
        }

        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val temperatureCelsius = if (tempTenths > 0) tempTenths / 10.0f else 0.0f

        serviceScope.launch {
            try {
                val prefs = BatteryPreferences(applicationContext)

                // 1. Check Custom Target Battery Percentage (80% - 100%)
                val isFullChargeAlertEnabled = prefs.isFullChargeAlertEnabled.first()
                val targetPercentage = prefs.targetChargePercentage.first()

                if (isFullChargeAlertEnabled && BatteryProtectionManager.isTargetReached(percentage, targetPercentage)) {
                    if (!hasAlertedTargetCharge) {
                        hasAlertedTargetCharge = true
                        triggerTargetChargeAlert(targetPercentage)
                    }
                } else if (percentage < targetPercentage) {
                    hasAlertedTargetCharge = false
                }

                // 2. Check High Temperature / Overheat Threshold (e.g. 40°C, 42°C, 45°C)
                val isOverheatAlertEnabled = prefs.isOverheatAlertEnabled.first()
                val overheatThreshold = prefs.overheatTemperatureThreshold.first()

                if (isOverheatAlertEnabled && BatteryProtectionManager.isOverheating(temperatureCelsius, overheatThreshold)) {
                    if (!hasAlertedOverheat) {
                        hasAlertedOverheat = true
                        triggerOverheatAlert(temperatureCelsius, overheatThreshold)
                    }
                } else if (temperatureCelsius < (overheatThreshold - 1.5f)) {
                    // Reset overheat alert latch when temperature cools down
                    hasAlertedOverheat = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error evaluating protection thresholds in service", e)
            }
        }
    }

    private fun triggerTargetChargeAlert(targetPercentage: Int) {
        serviceScope.launch {
            try {
                val prefs = BatteryPreferences(applicationContext)
                val fullChargeUri = prefs.fullChargeSoundUri.first()
                prefs.setLastFullChargeTimestamp(System.currentTimeMillis())

                // Play continuous looping target charge alarm
                SoundHelper.getInstance(applicationContext).startFullChargeAlarm(fullChargeUri)

                // Show high-priority heads-up notification with Dismiss action
                postTargetChargeHeadsUpNotification(targetPercentage)
                Log.i(TAG, "Target charge ($targetPercentage%) alert triggered successfully from ChargingService")
            } catch (e: Exception) {
                Log.e(TAG, "Error executing target charge alert in service", e)
            }
        }
    }

    private fun triggerOverheatAlert(temperatureCelsius: Float, threshold: Float) {
        serviceScope.launch {
            try {
                // Play distinct warning sound
                SoundHelper.getInstance(applicationContext).startOverheatAlarm()

                // Show high-priority heads-up thermal warning notification
                postOverheatHeadsUpNotification(temperatureCelsius, threshold)
                Log.w(TAG, "Overheat alert triggered! Temp: $temperatureCelsius°C, Threshold: $threshold°C")
            } catch (e: Exception) {
                Log.e(TAG, "Error executing overheat alert in service", e)
            }
        }
    }

    private fun postTargetChargeHeadsUpNotification(targetPercentage: Int) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = PendingIntent.getBroadcast(
            this,
            1,
            Intent(this, AlarmDismissReceiver::class.java).apply {
                action = AlarmDismissReceiver.ACTION_DISMISS_ALARM
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (targetPercentage >= 100) {
            "Battery Fully Charged (100%)"
        } else {
            "Target Charge Limit Reached ($targetPercentage%)"
        }
        val content = if (targetPercentage >= 100) {
            "Your battery has reached 100%. Unplug charger to preserve battery health."
        } else {
            "Battery reached your longevity target of $targetPercentage%. Unplug charger to extend lifespan."
        }

        val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .addAction(R.mipmap.ic_launcher, "DISMISS ALARM", dismissIntent)
            .setDeleteIntent(dismissIntent)
            .build()

        notificationManager.notify(FULL_CHARGE_NOTIFICATION_ID, notification)
    }

    private fun postOverheatHeadsUpNotification(temperatureCelsius: Float, threshold: Float) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = PendingIntent.getBroadcast(
            this,
            2,
            Intent(this, AlarmDismissReceiver::class.java).apply {
                action = AlarmDismissReceiver.ACTION_DISMISS_ALARM
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedTemp = String.format(Locale.US, "%.1f°C", temperatureCelsius)
        val formattedThreshold = String.format(Locale.US, "%.1f°C", threshold)

        val notification = NotificationCompat.Builder(this, OVERHEAT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⚠️ Battery Overheat Warning ($formattedTemp)")
            .setContentText("Battery temperature exceeded safe threshold ($formattedThreshold). Disconnect charger immediately to prevent hardware wear.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .addAction(R.mipmap.ic_launcher, "DISMISS ALARM", dismissIntent)
            .setDeleteIntent(dismissIntent)
            .build()

        notificationManager.notify(OVERHEAT_NOTIFICATION_ID, notification)
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("VoltPulse Active")
            .setContentText("Monitoring charging telemetry & battery health protection...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(launchIntent)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Silent persistent channel for foreground service
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Charging Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent status while VoltPulse monitors charging level"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(serviceChannel)

            // 2. High-priority heads-up channel for Target/Full charge alarm
            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Full & Target Charge Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when battery reaches configured target or full charge"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(alarmChannel)

            // 3. High-priority heads-up channel for Overheat warning
            val overheatChannel = NotificationChannel(
                OVERHEAT_CHANNEL_ID,
                "Battery Thermal Overheat Alert",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when battery temperature reaches hazardous levels"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(overheatChannel)
        }
    }

    private fun stopMonitoringAndSelf() {
        try {
            if (batteryReceiver != null) {
                unregisterReceiver(batteryReceiver)
                batteryReceiver = null
                Log.d(TAG, "Battery level receiver unregistered")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering battery receiver", e)
        }

        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BatteryChargingService onDestroy")
        stopMonitoringAndSelf()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "BatteryChargingService"
        const val ACTION_START_SERVICE = "com.example.voltpulse.ACTION_START_CHARGING_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.voltpulse.ACTION_STOP_CHARGING_SERVICE"

        const val SERVICE_CHANNEL_ID = "voltpulse_charging_service_channel"
        const val ALARM_CHANNEL_ID = "voltpulse_full_charge_alarm_channel"
        const val OVERHEAT_CHANNEL_ID = "voltpulse_overheat_alarm_channel"

        const val FOREGROUND_NOTIFICATION_ID = 1001
        const val FULL_CHARGE_NOTIFICATION_ID = 2001
        const val OVERHEAT_NOTIFICATION_ID = 2002

        fun startService(context: Context) {
            val intent = Intent(context, BatteryChargingService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed starting BatteryChargingService", e)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BatteryChargingService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed stopping BatteryChargingService", e)
            }
        }
    }
}
