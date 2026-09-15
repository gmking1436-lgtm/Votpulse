package com.example.ui.components

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.touch.InstantHapticType
import com.example.ui.touch.TouchOptimizationHelper
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Ultra-Low Latency Material 3 Snap Slider.
 *
 * Optimized for High Touch Sampling Rates (120Hz/240Hz/360Hz):
 * 1. Touch Tracking: Tracks MotionEvent input in [PointerEventPass.Main] without awaiting standard gesture delays.
 * 2. Deferred Rendering: Thumb translation and active track fill are isolated in a hardware [graphicsLayer] Draw phase,
 *    preventing parent Composable recomposition and avoiding Layout/Measure churn during fast drags.
 * 3. Haptic Snap Pacing: Automatically dispatches crisp [InstantHapticType.TICK] vibrations directly on snap threshold
 *    crossings (80%, 85%, 90%, 95%, 100%).
 * 4. Micro-Spring Physics: Smoothly animates thumb settle on gesture release with critically damped spring physics.
 */
@Composable
fun LowLatencySnapSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minValue: Int = 80,
    maxValue: Int = 100,
    stepInterval: Int = 5,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    testTag: String = "low_latency_snap_slider"
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val totalSteps = ((maxValue - minValue) / stepInterval).coerceAtLeast(1)
    val snapValues = remember(minValue, maxValue, stepInterval) {
        (minValue..maxValue step stepInterval).toList()
    }

    // Normalized progress: 0.0f (min) to 1.0f (max)
    val targetNormalized = ((value - minValue).toFloat() / (maxValue - minValue).toFloat()).coerceIn(0f, 1f)
    val animatedProgress = remember { Animatable(targetNormalized) }

    // Sync external programmatic updates when not actively dragging
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(value, isDragging) {
        if (!isDragging && targetNormalized != animatedProgress.value) {
            animatedProgress.animateTo(
                targetValue = targetNormalized,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
    }

    var lastReportedValue by remember { mutableIntStateOf(value) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag(testTag)
            .pointerInput(snapValues, totalSteps) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Main)
                    isDragging = true
                    val widthPx = size.width.toFloat()
                    val thumbRadiusPx = with(density) { 14.dp.toPx() }
                    val usableWidth = (widthPx - thumbRadiusPx * 2).coerceAtLeast(1f)

                    // Immediate jump & haptic on ACTION_DOWN tap
                    val touchX = (down.position.x - thumbRadiusPx).coerceIn(0f, usableWidth)
                    val newProgress = (touchX / usableWidth).coerceIn(0f, 1f)
                    val snappedIdx = (newProgress * totalSteps).roundToInt().coerceIn(0, snapValues.lastIndex)
                    val snappedVal = snapValues[snappedIdx]

                    if (snappedVal != lastReportedValue) {
                        lastReportedValue = snappedVal
                        TouchOptimizationHelper.performInstantHaptic(view, InstantHapticType.TICK)
                        onValueChange(snappedVal)
                    }

                    coroutineScope.launch {
                        animatedProgress.snapTo(newProgress)
                    }

                    // Continuous drag loop paced to touch sampling rate
                    var pointerId = down.id
                    drag(pointerId) { change ->
                        val dragX = (change.position.x - thumbRadiusPx).coerceIn(0f, usableWidth)
                        val dragProgress = (dragX / usableWidth).coerceIn(0f, 1f)

                        val currentSnapIdx = (dragProgress * totalSteps).roundToInt().coerceIn(0, snapValues.lastIndex)
                        val currentSnapVal = snapValues[currentSnapIdx]

                        if (currentSnapVal != lastReportedValue) {
                            lastReportedValue = currentSnapVal
                            TouchOptimizationHelper.performInstantHaptic(view, InstantHapticType.TICK)
                            onValueChange(currentSnapVal)
                        }

                        coroutineScope.launch {
                            animatedProgress.snapTo(dragProgress)
                        }

                        change.consume()
                    }

                    // Settle onto exact snap point on release
                    isDragging = false
                    val finalIdx = (animatedProgress.value * totalSteps).roundToInt().coerceIn(0, snapValues.lastIndex)
                    val finalVal = snapValues[finalIdx]
                    val finalProgress = finalIdx.toFloat() / totalSteps.toFloat()

                    coroutineScope.launch {
                        animatedProgress.animateTo(
                            targetValue = finalProgress,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    }

                    if (finalVal != lastReportedValue) {
                        lastReportedValue = finalVal
                        TouchOptimizationHelper.performInstantHaptic(view, InstantHapticType.CLICK)
                        onValueChange(finalVal)
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .graphicsLayer {
                    // Isolates slider track and thumb rendering to dedicated hardware RenderNode
                    clip = false
                }
        ) {
            val width = size.width
            val height = size.height
            val trackHeight = 8.dp.toPx()
            val thumbRadius = 13.dp.toPx()
            val usableWidth = width - thumbRadius * 2f
            val centerY = height / 2f

            val currentP = animatedProgress.value.coerceIn(0f, 1f)
            val thumbCenterX = thumbRadius + currentP * usableWidth

            // 1. Inactive Background Track
            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(thumbRadius, centerY - trackHeight / 2f),
                size = Size(usableWidth, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            )

            // 2. Discrete Snap Stop Ticks
            snapValues.forEachIndexed { index, _ ->
                val tickFraction = index.toFloat() / totalSteps.toFloat()
                val tickX = thumbRadius + tickFraction * usableWidth
                val isPassed = tickFraction <= currentP + 0.01f

                drawCircle(
                    color = if (isPassed) activeColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.35f),
                    radius = 3.dp.toPx(),
                    center = Offset(tickX, centerY)
                )
            }

            // 3. Active Illuminated Track
            if (thumbCenterX > thumbRadius) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(activeColor.copy(alpha = 0.8f), activeColor),
                        startX = thumbRadius,
                        endX = thumbCenterX
                    ),
                    topLeft = Offset(thumbRadius, centerY - trackHeight / 2f),
                    size = Size(thumbCenterX - thumbRadius, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                )
            }

            // 4. Subtle Outer Glow on Drag
            if (isDragging) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.22f),
                    radius = thumbRadius * 1.8f,
                    center = Offset(thumbCenterX, centerY)
                )
            }

            // 5. Thumb Disc
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = Offset(thumbCenterX, centerY)
            )
            drawCircle(
                color = thumbColor,
                radius = thumbRadius - 3.dp.toPx(),
                center = Offset(thumbCenterX, centerY)
            )

            // Outer border on thumb for contrast
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = thumbRadius,
                center = Offset(thumbCenterX, centerY),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}
