package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalAppThemeMode
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Immutable particle spec for charging bubble animations.
 */
private data class ChargingBubble(
    val xRatio: Float,
    val speed: Float,
    val radiusDp: Float,
    val phaseOffset: Float,
    val alpha: Float
)

/**
 * Production-grade, 60fps-optimized custom canvas liquid wave battery visualizer.
 *
 * Visual Features:
 * - Dual layered Sine wave animation (`drawPath`) smoothly interpolating water height based on battery percentage.
 * - Dynamic color adaptation based on theme mode, charge percentage, and charging state.
 * - Rising micro-bubble particles active during charging sessions.
 * - Multi-layered instrument bezel with graduation ticks and glowing progress rim.
 * - Center telemetry readout displaying percentage, estimated wattage, remaining time, and charging status.
 */
@Composable
fun LiquidWaveBatteryGauge(
    percentage: Int,
    isCharging: Boolean,
    voltageMilliVolts: Int,
    temperatureCelsius: Float,
    healthStatus: String,
    modifier: Modifier = Modifier,
    gaugeSize: Dp = 260.dp,
    isAnimationActive: Boolean = true,
    isPowerSaveMode: Boolean = false
) {
    val themeMode = LocalAppThemeMode.current
    val clampedPercentage = percentage.coerceIn(0, 100)
    val isPowerConstrained = isPowerSaveMode || (clampedPercentage <= 20 && !isCharging)

    // Dynamic wave palette selection matching theme & battery status
    val primaryWaveColor = when {
        isCharging -> MaterialTheme.colorScheme.primary
        clampedPercentage > 50 -> MaterialTheme.colorScheme.primary
        clampedPercentage > 20 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    val secondaryWaveColor = when {
        isCharging -> MaterialTheme.colorScheme.secondary
        clampedPercentage > 50 -> MaterialTheme.colorScheme.secondary
        clampedPercentage > 20 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
    }

    val animatedPrimaryColor by animateColorAsState(
        targetValue = primaryWaveColor,
        animationSpec = tween(durationMillis = 600),
        label = "primaryWaveColor"
    )

    val animatedSecondaryColor by animateColorAsState(
        targetValue = secondaryWaveColor,
        animationSpec = tween(durationMillis = 600),
        label = "secondaryWaveColor"
    )

    // Smooth progress interpolation for liquid rising
    val animatedProgress by animateFloatAsState(
        targetValue = clampedPercentage / 100f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "liquidProgress"
    )

    // Infinite transitions for wave motion and particle movement
    val infiniteTransition = rememberInfiniteTransition(label = "liquidAnimation")

    val wavePhaseFront by if (isAnimationActive) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = if (isCharging) 2000 else 2800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wavePhaseFront"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    val wavePhaseBack by if (isAnimationActive) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = if (isCharging) 2800 else 3600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wavePhaseBack"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    val particleTick by if (isCharging && isAnimationActive) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "particleTick"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    val pulseGlowAlpha by if (isCharging && isAnimationActive) {
        infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseGlowAlpha"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0.4f) }
    }

    // Reusable Path allocations to eliminate GC overhead during draw
    val frontPath = remember { Path() }
    val backPath = remember { Path() }
    val clipPath = remember { Path() }

    // Seed stable particle specifications
    val bubbles = remember {
        listOf(
            ChargingBubble(0.25f, 1.1f, 3.0f, 0.05f, 0.6f),
            ChargingBubble(0.35f, 0.8f, 4.5f, 0.25f, 0.75f),
            ChargingBubble(0.45f, 1.4f, 2.5f, 0.60f, 0.5f),
            ChargingBubble(0.55f, 0.9f, 4.0f, 0.40f, 0.8f),
            ChargingBubble(0.65f, 1.2f, 3.2f, 0.15f, 0.65f),
            ChargingBubble(0.75f, 0.7f, 5.0f, 0.80f, 0.7f),
            ChargingBubble(0.30f, 1.3f, 2.8f, 0.50f, 0.55f),
            ChargingBubble(0.70f, 1.0f, 3.8f, 0.35f, 0.6f),
            ChargingBubble(0.50f, 1.5f, 2.2f, 0.70f, 0.5f),
            ChargingBubble(0.60f, 0.85f, 4.2f, 0.90f, 0.7f)
        )
    }

    // Wattage and time remaining estimation calculations
    val estimatedWattageText = remember(voltageMilliVolts, isCharging, clampedPercentage) {
        if (isCharging) {
            val volts = if (voltageMilliVolts > 0) voltageMilliVolts / 1000f else 4.1f
            // Dynamic charge current estimation based on battery saturation curve
            val amps = when {
                clampedPercentage < 60 -> 3.6f
                clampedPercentage < 80 -> 2.4f
                clampedPercentage < 95 -> 1.4f
                else -> 0.7f
            }
            val watts = volts * amps
            String.format(Locale.US, "%.1fW", watts)
        } else {
            "0.8W"
        }
    }

    val remainingTimeText = remember(clampedPercentage, isCharging) {
        if (isCharging) {
            if (clampedPercentage >= 100) {
                "Full"
            } else {
                val remaining = 100 - clampedPercentage
                val minutes = (remaining * 1.2f).toInt().coerceAtLeast(1)
                if (minutes >= 60) {
                    val hrs = minutes / 60
                    val mins = minutes % 60
                    "~${hrs}h ${mins}m to 100%"
                } else {
                    "~${minutes}m to 100%"
                }
            }
        } else {
            val hours = (clampedPercentage / 7.2f).toInt().coerceAtLeast(1)
            "~${hours}h left"
        }
    }

    Box(
        modifier = modifier
            .size(gaugeSize)
            .testTag("liquid_wave_battery_gauge"),
        contentAlignment = Alignment.Center
    ) {
        // 1. Fluid Wave & Instrument Bezel Canvas (Hardware-accelerated isolated RenderNode)
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    // Isolates custom draw operations into a dedicated hardware RenderNode layer.
                    // Prevents invalidation of parent Composables and text nodes during 120Hz/90Hz VSYNC ticks.
                    clip = false
                }
        ) {
            val diameter = size.minDimension
            val radius = diameter / 2f
            val centerOffset = Offset(size.width / 2f, size.height / 2f)

            // Outer Instrument Bezel Ring
            drawCircle(
                color = animatedPrimaryColor.copy(alpha = pulseGlowAlpha * 0.2f),
                radius = radius - 2.dp.toPx(),
                center = centerOffset,
                style = Stroke(width = 6.dp.toPx())
            )

            // Perimeter Graduation Ticks
            val tickCount = 48
            val tickInnerRadius = radius - 14.dp.toPx()
            val tickOuterRadius = radius - 7.dp.toPx()

            for (i in 0 until tickCount) {
                val angleRad = (i * (360f / tickCount)) * (PI / 180f).toFloat()
                val isMajorTick = i % 6 == 0
                val length = if (isMajorTick) 7.dp.toPx() else 4.dp.toPx()
                val currentInner = tickOuterRadius - length

                val startX = centerOffset.x + currentInner * cos(angleRad)
                val startY = centerOffset.y + currentInner * sin(angleRad)
                val endX = centerOffset.x + tickOuterRadius * cos(angleRad)
                val endY = centerOffset.y + tickOuterRadius * sin(angleRad)

                val tickColor = if (isMajorTick) {
                    animatedPrimaryColor.copy(alpha = 0.5f)
                } else {
                    Color.White.copy(alpha = 0.12f)
                }

                drawLine(
                    color = tickColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = if (isMajorTick) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Outer Progress Indicator Arc
            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to animatedSecondaryColor,
                    0.6f to animatedPrimaryColor,
                    1.0f to animatedSecondaryColor,
                    center = centerOffset
                ),
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = Offset(centerOffset.x - radius + 4.dp.toPx(), centerOffset.y - radius + 4.dp.toPx()),
                size = Size((radius - 4.dp.toPx()) * 2, (radius - 4.dp.toPx()) * 2),
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Fluid Liquid Container Boundary
            val liquidRadius = radius - 16.dp.toPx()
            clipPath.reset()
            clipPath.addOval(
                Rect(
                    center = centerOffset,
                    radius = liquidRadius
                )
            )

            // Inner dark backdrop for liquid sphere
            drawCircle(
                color = Color(0xFF07090E),
                radius = liquidRadius,
                center = centerOffset
            )

            // Clipped Liquid Canvas
            clipPath(clipPath) {
                val liquidHeight = liquidRadius * 2
                val waterLevelY = (centerOffset.y + liquidRadius) - (liquidHeight * animatedProgress)
                val waveAmplitude = if (clampedPercentage == 0 || clampedPercentage == 100) {
                    0f
                } else {
                    (if (isCharging) 12.dp.toPx() else 7.dp.toPx()) * (1f - (clampedPercentage - 50f) * (clampedPercentage - 50f) / 2500f).coerceIn(0.3f, 1f)
                }

                val waveLength = liquidRadius * 2.2f
                // Adaptive wave geometry resolution: 4px step on high refresh rate, 8px on power constrained modes
                val waveStepX = if (isPowerConstrained) 8f else 4f

                // --- 1. Back Layer Wave (Secondary Color) ---
                backPath.reset()
                backPath.moveTo(centerOffset.x - liquidRadius, size.height)
                var x = centerOffset.x - liquidRadius
                while (x <= centerOffset.x + liquidRadius) {
                    val relativeX = x - (centerOffset.x - liquidRadius)
                    val y = waterLevelY + (waveAmplitude * 0.8f) * sin((2 * PI * (relativeX / (waveLength * 1.2f)) + wavePhaseBack).toFloat())
                    if (x == centerOffset.x - liquidRadius) {
                        backPath.moveTo(x, y)
                    } else {
                        backPath.lineTo(x, y)
                    }
                    x += waveStepX
                }
                backPath.lineTo(centerOffset.x + liquidRadius, size.height)
                backPath.lineTo(centerOffset.x - liquidRadius, size.height)
                backPath.close()

                drawPath(
                    path = backPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            animatedSecondaryColor.copy(alpha = 0.45f),
                            animatedSecondaryColor.copy(alpha = 0.20f)
                        ),
                        startY = waterLevelY - waveAmplitude,
                        endY = centerOffset.y + liquidRadius
                    )
                )

                // --- 2. Front Layer Wave (Primary Accent) ---
                frontPath.reset()
                x = centerOffset.x - liquidRadius
                while (x <= centerOffset.x + liquidRadius) {
                    val relativeX = x - (centerOffset.x - liquidRadius)
                    val y = waterLevelY + waveAmplitude * sin((2 * PI * (relativeX / waveLength) + wavePhaseFront).toFloat())
                    if (x == centerOffset.x - liquidRadius) {
                        frontPath.moveTo(x, y)
                    } else {
                        frontPath.lineTo(x, y)
                    }
                    x += waveStepX
                }
                frontPath.lineTo(centerOffset.x + liquidRadius, size.height)
                frontPath.lineTo(centerOffset.x - liquidRadius, size.height)
                frontPath.close()

                drawPath(
                    path = frontPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            animatedPrimaryColor.copy(alpha = 0.88f),
                            animatedSecondaryColor.copy(alpha = 0.50f),
                            Color(0xFF03101E).copy(alpha = 0.90f)
                        ),
                        startY = waterLevelY - waveAmplitude,
                        endY = centerOffset.y + liquidRadius
                    )
                )

                // --- 3. Charging Bubble Micro-Particles (Omitted in Low-Power mode to save GPU/CPU cycles) ---
                if (isCharging && clampedPercentage > 5 && !isPowerConstrained) {
                    val bottomY = centerOffset.y + liquidRadius
                    bubbles.forEach { bubble ->
                        val bubbleProgress = (particleTick * bubble.speed + bubble.phaseOffset) % 1f
                        val bubbleX = (centerOffset.x - liquidRadius * 0.7f) + (liquidRadius * 1.4f * bubble.xRatio)
                        val bubbleY = bottomY - bubbleProgress * (bottomY - waterLevelY)

                        if (bubbleY > waterLevelY + 4.dp.toPx()) {
                            drawCircle(
                                color = Color.White.copy(alpha = bubble.alpha * (1f - bubbleProgress * 0.5f)),
                                radius = bubble.radiusDp.dp.toPx(),
                                center = Offset(bubbleX, bubbleY)
                            )
                        }
                    }
                }

                // Inner sphere ambient glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.65f)
                        ),
                        center = centerOffset,
                        radius = liquidRadius
                    ),
                    radius = liquidRadius,
                    center = centerOffset
                )
            }
        }

        // 2. High-Contrast Center Telemetry Readout (Glassmorphic Scrim)
        Box(
            modifier = Modifier
                .size(gaugeSize * 0.68f)
                .clip(CircleShape)
                .background(Color(0x73080C14))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Top Status Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(animatedPrimaryColor.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (isCharging) Icons.Default.Bolt else Icons.Default.Speed,
                        contentDescription = null,
                        tint = animatedPrimaryColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (isCharging) "CHARGING" else "ACTIVE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = animatedPrimaryColor,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Giant Numeric Percentage Readout
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$clampedPercentage",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = (-1).sp
                        ),
                        color = Color.White,
                        fontSize = 46.sp,
                        modifier = Modifier.testTag("battery_percentage_text")
                    )
                    Text(
                        text = "%",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = animatedPrimaryColor,
                        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                    )
                }

                // Estimated Wattage & Time Remaining
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = estimatedWattageText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = animatedPrimaryColor,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "•",
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 10.sp
                    )
                    Text(
                        text = remainingTimeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Diagnostic voltage tag
                val voltageFormatted = if (voltageMilliVolts > 0) {
                    String.format(Locale.US, "%.2fV", voltageMilliVolts / 1000f)
                } else {
                    "4.10V"
                }

                Text(
                    text = "$voltageFormatted • $healthStatus",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 9.sp
                )
            }
        }
    }
}
