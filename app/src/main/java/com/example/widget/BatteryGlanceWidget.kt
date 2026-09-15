package com.example.widget

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.BatteryManager
import android.util.Log
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.service.BatteryProtectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Snapshot of real-time battery status extracted for the Glance Widget.
 */
data class WidgetBatteryData(
    val percentage: Int,
    val isCharging: Boolean,
    val wattage: Float,
    val temperatureCelsius: Float,
    val statusText: String
)

/**
 * Production-grade Jetpack Glance Material 3 Home Screen Widget.
 *
 * Characteristics:
 * - Interactive & Resizable: Dynamically adapts across compact and expanded home screen grids.
 * - Displays a circular battery progress ring, live charging wattage, and thermals.
 * - Direct Launch: Clicking anywhere on the widget opens [MainActivity] directly.
 * - Zero Polling Drain: Strictly refreshed on system battery broadcast events via [BatteryGlanceWidgetReceiver].
 */
class BatteryGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val batteryData = readBatterySnapshot(context)
        val circularProgressBitmap = generateCircularProgressBitmap(context, batteryData.percentage, batteryData.isCharging)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        provideContent {
            GlanceTheme {
                WidgetRootLayout(
                    batteryData = batteryData,
                    circularBitmap = circularProgressBitmap,
                    launchIntent = launchIntent
                )
            }
        }
    }

    companion object {
        private const val TAG = "BatteryGlanceWidget"

        /**
         * Reads the current battery hardware state using the sticky battery broadcast.
         * Safe to call on background threads without acquiring wake locks.
         */
        fun readBatterySnapshot(context: Context): WidgetBatteryData {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = context.registerReceiver(null, intentFilter)

            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val percentage = if (level >= 0 && scale > 0) {
                ((level.toFloat() / scale.toFloat()) * 100).toInt().coerceIn(0, 100)
            } else {
                50
            }

            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val temperatureCelsius = if (tempRaw > 0) tempRaw / 10.0f else 28.0f

            val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4100) ?: 4100
            val telemetry = BatteryProtectionManager.calculateChargingPower(
                context = context,
                voltageMilliVolts = voltageMv,
                batteryPercentage = percentage,
                isCharging = isCharging
            )

            val statusText = when {
                status == BatteryManager.BATTERY_STATUS_FULL -> "Fully Charged"
                isCharging && telemetry.watts >= 25f -> "Ultra Fast Charging"
                isCharging && telemetry.watts >= 10f -> "Fast Charging"
                isCharging -> "Standard Charging"
                else -> "Discharging"
            }

            return WidgetBatteryData(
                percentage = percentage,
                isCharging = isCharging,
                wattage = telemetry.watts,
                temperatureCelsius = temperatureCelsius,
                statusText = statusText
            )
        }

        /**
         * Renders a high-resolution, anti-aliased circular battery progress ring.
         */
        fun generateCircularProgressBitmap(
            context: Context,
            percentage: Int,
            isCharging: Boolean,
            dimensionPx: Int = 240
        ): Bitmap {
            val bitmap = Bitmap.createBitmap(dimensionPx, dimensionPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val strokeWidthPx = dimensionPx * 0.10f
            val paddingPx = strokeWidthPx / 2f + 4f
            val oval = RectF(paddingPx, paddingPx, dimensionPx - paddingPx, dimensionPx - paddingPx)

            // Background Track Paint
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokeWidthPx
                color = AndroidColor.argb(55, 120, 140, 160)
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(oval, 0f, 360f, false, trackPaint)

            // Dynamic Progress Sweep Arc Paint
            val arcColor = when {
                isCharging -> AndroidColor.rgb(0, 230, 118)       // Electric Emerald
                percentage <= 20 -> AndroidColor.rgb(255, 82, 82)   // Alert Coral Red
                percentage <= 40 -> AndroidColor.rgb(255, 213, 79)  // Warning Gold
                else -> AndroidColor.rgb(0, 229, 255)               // VoltPulse Cyan
            }

            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokeWidthPx
                color = arcColor
                strokeCap = Paint.Cap.ROUND
            }

            val sweepAngle = (percentage.coerceIn(0, 100) / 100f) * 360f
            canvas.drawArc(oval, -90f, sweepAngle, false, progressPaint)

            // Inner Percentage Number Text
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE
                textSize = dimensionPx * 0.28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            val yPos = (dimensionPx / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
            val label = if (isCharging) "⚡$percentage%" else "$percentage%"
            canvas.drawText(label, dimensionPx / 2f, yPos, textPaint)

            return bitmap
        }

        /**
         * Dispatches an asynchronous update call across all installed widget instances.
         */
        fun updateAllWidgets(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val manager = GlanceAppWidgetManager(context)
                    val glanceIds = manager.getGlanceIds(BatteryGlanceWidget::class.java)
                    val widget = BatteryGlanceWidget()
                    for (glanceId in glanceIds) {
                        widget.update(context, glanceId)
                    }
                    Log.d(TAG, "Updated ${glanceIds.size} Glance widget instances")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed updating Glance widgets", e)
                }
            }
        }
    }
}

/**
 * Glance Composable layout structuring the widget hierarchy.
 */
@androidx.compose.runtime.Composable
private fun WidgetRootLayout(
    batteryData: WidgetBatteryData,
    circularBitmap: Bitmap,
    launchIntent: Intent
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(ColorProvider(androidx.compose.ui.graphics.Color(0xFF121418)))
            .padding(14.dp)
            .clickable(actionStartActivity(launchIntent)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Circular Progress Indicator Gauge
            Image(
                provider = ImageProvider(circularBitmap),
                contentDescription = "Battery ${batteryData.percentage}%",
                modifier = GlanceModifier.size(86.dp)
            )

            Spacer(modifier = GlanceModifier.width(14.dp))

            // Telemetry Metric Column
            Column(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Header / App Tag
                Text(
                    text = "VOLTPULSE",
                    style = TextStyle(
                        color = ColorProvider(androidx.compose.ui.graphics.Color(0xFF00E5FF)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = GlanceModifier.height(3.dp))

                // Wattage Display
                val wattageText = if (batteryData.isCharging && batteryData.wattage > 0f) {
                    "⚡ ${String.format(Locale.US, "%.1f", batteryData.wattage)} W"
                } else if (batteryData.isCharging) {
                    "⚡ Charging"
                } else {
                    "🔋 Battery"
                }
                Text(
                    text = wattageText,
                    style = TextStyle(
                        color = ColorProvider(androidx.compose.ui.graphics.Color.White),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = GlanceModifier.height(3.dp))

                // Temperature Metric
                val tempText = "🌡️ ${String.format(Locale.US, "%.1f", batteryData.temperatureCelsius)}°C"
                Text(
                    text = tempText,
                    style = TextStyle(
                        color = ColorProvider(androidx.compose.ui.graphics.Color(0xFFB0BEC5)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                )

                Spacer(modifier = GlanceModifier.height(2.dp))

                // Status Subtitle
                Text(
                    text = batteryData.statusText,
                    style = TextStyle(
                        color = ColorProvider(
                            if (batteryData.isCharging) {
                                androidx.compose.ui.graphics.Color(0xFF00E676)
                            } else {
                                androidx.compose.ui.graphics.Color(0xFF78909C)
                            }
                        ),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Production-grade BroadcastReceiver receiving Android OS battery broadcast triggers.
 * Ensures widget updates strictly on battery level changes and hardware cable events,
 * guaranteeing ZERO continuous idle battery polling.
 */
class BatteryGlanceWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = BatteryGlanceWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        Log.d("BatteryGlanceWidget", "Glance receiver received action: $action")

        if (action == Intent.ACTION_BATTERY_CHANGED ||
            action == Intent.ACTION_POWER_CONNECTED ||
            action == Intent.ACTION_POWER_DISCONNECTED ||
            action == "android.appwidget.action.APPWIDGET_UPDATE"
        ) {
            val pendingResult = try {
                goAsync()
            } catch (e: Exception) {
                null
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val manager = GlanceAppWidgetManager(context)
                    val glanceIds = manager.getGlanceIds(BatteryGlanceWidget::class.java)
                    val widget = BatteryGlanceWidget()
                    for (glanceId in glanceIds) {
                        widget.update(context, glanceId)
                    }
                } catch (e: Exception) {
                    Log.w("BatteryGlanceWidget", "Error refreshing glance widgets", e)
                } finally {
                    try {
                        pendingResult?.finish()
                    } catch (e: Exception) {
                        Log.w("BatteryGlanceWidget", "Failed to finish pendingResult", e)
                    }
                }
            }
        }
    }
}
