package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChargingSessionPoint
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Production-grade custom Canvas charging analytics chart.
 *
 * Performance Architecture:
 * - Employs [Modifier.drawWithCache] so that all Path geometry, Bézier control points, and Gradient
 *   brushes are calculated strictly when dimensions or telemetry data change—never during active draw phases.
 * - Utilizes [Modifier.graphicsLayer] to isolate chart composition onto a distinct GPU hardware layer,
 *   preventing invalidation bubbles during 60Hz-120Hz list scrolling.
 * - Highlights the critical 80% battery longevity sweet spot and 100% completion target.
 */
@Composable
fun ChargingAnalyticsChart(
    dataPoints: List<ChargingSessionPoint>,
    targetPercentage: Int = 80,
    isCharging: Boolean = true,
    currentWattage: Float = 0f,
    modifier: Modifier = Modifier
) {
    // Pulse animation for the active charging point marker
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_anim"
    )

    val points = remember(dataPoints) {
        if (dataPoints.isEmpty()) {
            val now = System.currentTimeMillis()
            listOf(
                ChargingSessionPoint(now - 15 * 60 * 1000L, 40, 18f, 30f),
                ChargingSessionPoint(now, 55, 18f, 32f)
            )
        } else if (dataPoints.size == 1) {
            val single = dataPoints.first()
            listOf(
                ChargingSessionPoint(single.timestampMs - 60 * 1000L, (single.percentage - 1).coerceAtLeast(0), single.wattage, single.temperatureCelsius),
                single
            )
        } else {
            dataPoints
        }
    }

    val startPoint = points.first()
    val latestPoint = points.last()
    val percentageGain = (latestPoint.percentage - startPoint.percentage).coerceAtLeast(0)
    val elapsedMinutes = ((latestPoint.timestampMs - startPoint.timestampMs) / 60000L).coerceAtLeast(1)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("charging_analytics_chart_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row: Title & Sweet Spot Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ShowChart,
                                contentDescription = "Charging Graph",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Charging Curve & Longevity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isCharging) "Active Telemetry Session" else "Recent Session Summary",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 80% Sweet Spot Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF00E676).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Protection",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "$targetPercentage% Target",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                    }
                }
            }

            // Metric Summary Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricChip(
                    label = "Gain",
                    value = "+$percentageGain%",
                    subtitle = "${startPoint.percentage}% → ${latestPoint.percentage}%",
                    modifier = Modifier.weight(1f)
                )
                MetricChip(
                    label = "Elapsed",
                    value = "${elapsedMinutes}m",
                    subtitle = "Time on charger",
                    modifier = Modifier.weight(1f)
                )
                MetricChip(
                    label = "Power",
                    value = if (currentWattage > 0f) "${String.format(Locale.US, "%.1f", currentWattage)}W" else "${latestPoint.wattage.roundToInt()}W",
                    subtitle = if (isCharging) "Live Delivery" else "Session Peak",
                    modifier = Modifier.weight(1f)
                )
            }

            // High-Performance Jetpack Compose Canvas with drawWithCache
            val primaryColor = MaterialTheme.colorScheme.primary
            val tertiaryColor = MaterialTheme.colorScheme.tertiary
            val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
            val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            val sweetSpotColor = Color(0xFF00E676)
            val fullChargeColor = Color(0xFFFFD54F)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .graphicsLayer {
                        clip = false
                    }
                    .drawWithCache {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val paddingLeft = 42.dp.toPx()
                        val paddingRight = 16.dp.toPx()
                        val paddingTop = 18.dp.toPx()
                        val paddingBottom = 26.dp.toPx()

                        val chartWidth = canvasWidth - paddingLeft - paddingRight
                        val chartHeight = canvasHeight - paddingTop - paddingBottom

                        // Helper to map (time, percentage) -> (x, y)
                        val minTime = points.first().timestampMs
                        val maxTime = points.last().timestampMs
                        val timeSpan = (maxTime - minTime).coerceAtLeast(1L)

                        fun mapX(timestampMs: Long): Float {
                            val fraction = ((timestampMs - minTime).toFloat() / timeSpan).coerceIn(0f, 1f)
                            return paddingLeft + fraction * chartWidth
                        }

                        fun mapY(percentage: Int): Float {
                            val fraction = percentage.coerceIn(0, 100) / 100f
                            return paddingTop + (1f - fraction) * chartHeight
                        }

                        // Compute Smooth Bezier Path
                        val linePath = Path()
                        val fillPath = Path()

                        val coords = points.map { pt ->
                            Offset(mapX(pt.timestampMs), mapY(pt.percentage))
                        }

                        if (coords.isNotEmpty()) {
                            linePath.moveTo(coords[0].x, coords[0].y)
                            fillPath.moveTo(coords[0].x, paddingTop + chartHeight)
                            fillPath.lineTo(coords[0].x, coords[0].y)

                            for (i in 0 until coords.size - 1) {
                                val current = coords[i]
                                val next = coords[i + 1]
                                val controlPointX1 = current.x + (next.x - current.x) / 2f
                                val controlPointY1 = current.y
                                val controlPointX2 = current.x + (next.x - current.x) / 2f
                                val controlPointY2 = next.y

                                linePath.cubicTo(
                                    controlPointX1, controlPointY1,
                                    controlPointX2, controlPointY2,
                                    next.x, next.y
                                )
                                fillPath.cubicTo(
                                    controlPointX1, controlPointY1,
                                    controlPointX2, controlPointY2,
                                    next.x, next.y
                                )
                            }

                            fillPath.lineTo(coords.last().x, paddingTop + chartHeight)
                            fillPath.close()
                        }

                        // Gradient Brushes
                        val fillBrush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.38f),
                                primaryColor.copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            startY = paddingTop,
                            endY = paddingTop + chartHeight
                        )

                        val lineBrush = Brush.horizontalGradient(
                            colors = listOf(
                                tertiaryColor,
                                primaryColor
                            ),
                            startX = paddingLeft,
                            endX = paddingLeft + chartWidth
                        )

                        val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)

                        onDrawBehind {
                            // 1. Grid Horizontal Guidelines (0%, 25%, 50%, 75%, 100%)
                            val gridPercentages = listOf(20, 50)
                            for (pct in gridPercentages) {
                                val y = mapY(pct)
                                drawLine(
                                    color = surfaceVariant.copy(alpha = 0.4f),
                                    start = Offset(paddingLeft, y),
                                    end = Offset(paddingLeft + chartWidth, y),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }

                            // 2. 100% Full Charge Guideline
                            val y100 = mapY(100)
                            drawLine(
                                color = fullChargeColor.copy(alpha = 0.6f),
                                start = Offset(paddingLeft, y100),
                                end = Offset(paddingLeft + chartWidth, y100),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = dashedEffect
                            )

                            // 3. 80% Sweet Spot Protection Guideline
                            val ySweetSpot = mapY(targetPercentage)
                            drawLine(
                                color = sweetSpotColor,
                                start = Offset(paddingLeft, ySweetSpot),
                                end = Offset(paddingLeft + chartWidth, ySweetSpot),
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = dashedEffect
                            )

                            // 4. Fill Area Gradient below curve
                            drawPath(
                                path = fillPath,
                                brush = fillBrush
                            )

                            // 5. Main Smooth Bézier Curve Line
                            drawPath(
                                path = linePath,
                                brush = lineBrush,
                                style = Stroke(
                                    width = 3.5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )

                            // 6. Active Pulsing Point on the Latest Telemetry Node
                            if (coords.isNotEmpty()) {
                                val latestCoord = coords.last()
                                val baseRadius = 6.dp.toPx()
                                val pulseRadius = baseRadius + (8.dp.toPx() * pulseProgress)
                                val pulseAlpha = (1.0f - pulseProgress).coerceIn(0.1f, 0.7f)

                                // Outer glow aura
                                drawCircle(
                                    color = primaryColor.copy(alpha = pulseAlpha),
                                    radius = pulseRadius,
                                    center = latestCoord
                                )
                                // Solid core node
                                drawCircle(
                                    color = primaryColor,
                                    radius = baseRadius,
                                    center = latestCoord
                                )
                                // White inner center pip
                                drawCircle(
                                    color = Color.White,
                                    radius = 2.5.dp.toPx(),
                                    center = latestCoord
                                )
                            }

                            // 7. Y-Axis Text Labels using Android Native Canvas
                            val textPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.argb(160, 200, 200, 200)
                                textSize = 10.sp.toPx()
                                isAntiAlias = true
                            }
                            val sweetSpotPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.argb(255, 0, 230, 118)
                                textSize = 10.sp.toPx()
                                isFakeBoldText = true
                                isAntiAlias = true
                            }
                            val fullChargePaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.argb(255, 255, 213, 79)
                                textSize = 10.sp.toPx()
                                isFakeBoldText = true
                                isAntiAlias = true
                            }

                            drawContext.canvas.nativeCanvas.apply {
                                drawText("100%", 4.dp.toPx(), y100 + 4.dp.toPx(), fullChargePaint)
                                drawText("$targetPercentage%", 6.dp.toPx(), ySweetSpot + 4.dp.toPx(), sweetSpotPaint)
                                drawText("50%", 10.dp.toPx(), mapY(50) + 4.dp.toPx(), textPaint)
                                drawText("20%", 10.dp.toPx(), mapY(20) + 4.dp.toPx(), textPaint)

                                // Timeline bottom markers
                                drawText("Start", paddingLeft, paddingTop + chartHeight + 18.dp.toPx(), textPaint)
                                drawText("Now (${latestPoint.percentage}%)", paddingLeft + chartWidth - 56.dp.toPx(), paddingTop + chartHeight + 18.dp.toPx(), textPaint)
                            }
                        }
                    }
            )

            // Explanatory Footer Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = sweetSpotColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Charging above $targetPercentage% elevates cell voltage and thermal stress. The green dashed threshold marks your configured longevity sweet spot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label.uppercase(Locale.ROOT),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1
            )
        }
    }
}
