package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.touch.InstantHapticType
import com.example.ui.touch.TouchOptimizationHelper
import kotlinx.coroutines.launch

/**
 * Ultra-Low Latency Material 3 Switch.
 *
 * Engineered for Zero-Jank Touch Response:
 * 1. Immediate Action Down Trigger: Reverses state and dispatches instant haptic toggle pulse on touch contact.
 * 2. Deferred Draw Layer: Uses [graphicsLayer] hardware rendering for thumb displacement without triggering layout passes.
 * 3. Critically Damped Spring: Physical spring animation mimics real mechanical switch throw.
 */
@Composable
fun LowLatencySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    checkedThumbColor: Color = MaterialTheme.colorScheme.primary,
    checkedTrackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
    uncheckedThumbColor: Color = MaterialTheme.colorScheme.outline,
    uncheckedTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    testTag: String = "low_latency_switch"
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    // 0.0f = unchecked (left), 1.0f = checked (right)
    val thumbPosition = remember { Animatable(if (checked) 1f else 0f) }

    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (thumbPosition.targetValue != target) {
            thumbPosition.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    Box(
        modifier = modifier
            .size(width = 52.dp, height = 30.dp)
            .testTag(testTag)
            .pointerInput(checked) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Main)
                    down.consume()

                    // Instant trigger on finger contact
                    val newChecked = !checked
                    TouchOptimizationHelper.performInstantHaptic(view, InstantHapticType.TOGGLE)
                    onCheckedChange(newChecked)

                    coroutineScope.launch {
                        thumbPosition.animateTo(
                            targetValue = if (newChecked) 1f else 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessHigh
                            )
                        )
                    }

                    waitForUpOrCancellation(pass = PointerEventPass.Main)
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .size(width = 52.dp, height = 30.dp)
                .graphicsLayer { clip = false }
        ) {
            val width = size.width
            val height = size.height
            val trackHeight = height
            val cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

            val p = thumbPosition.value.coerceIn(0f, 1f)

            // Dynamic track color interpolation
            val currentTrackColor = androidx.compose.ui.graphics.lerp(
                start = uncheckedTrackColor,
                stop = checkedTrackColor,
                fraction = p
            )

            // Draw track
            drawRoundRect(
                color = currentTrackColor,
                topLeft = Offset(0f, 0f),
                size = Size(width, trackHeight),
                cornerRadius = cornerRadius
            )

            // Track border
            drawRoundRect(
                color = if (p > 0.5f) checkedThumbColor.copy(alpha = 0.4f) else uncheckedThumbColor.copy(alpha = 0.25f),
                topLeft = Offset(0f, 0f),
                size = Size(width, trackHeight),
                cornerRadius = cornerRadius,
                style = Stroke(width = 1.dp.toPx())
            )

            // Calculate thumb position
            val thumbPadding = 3.dp.toPx()
            val thumbDiameter = height - (thumbPadding * 2f)
            val thumbRadius = thumbDiameter / 2f
            val travelDistance = width - thumbDiameter - (thumbPadding * 2f)
            val thumbX = thumbPadding + thumbRadius + (p * travelDistance)
            val thumbY = height / 2f

            val currentThumbColor = androidx.compose.ui.graphics.lerp(
                start = uncheckedThumbColor,
                stop = checkedThumbColor,
                fraction = p
            )

            // Subtle glow when checked
            if (p > 0.1f) {
                drawCircle(
                    color = checkedThumbColor.copy(alpha = 0.25f * p),
                    radius = thumbRadius * 1.4f,
                    center = Offset(thumbX, thumbY)
                )
            }

            // Draw Thumb Circle
            drawCircle(
                color = currentThumbColor,
                radius = thumbRadius,
                center = Offset(thumbX, thumbY)
            )

            // Inner highlight for 3D metallic feel
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = thumbRadius - 1.5.dp.toPx(),
                center = Offset(thumbX, thumbY),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}
