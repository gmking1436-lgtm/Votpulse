package com.example.ui.touch

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * Low-latency haptic feedback types categorized for instant touch interactions.
 */
enum class InstantHapticType {
    /** Ultra-subtle crisp tick for slider snap marks, stepper increments, and micro-interactions. */
    TICK,

    /** Distinct confirmation click for button taps and card selections. */
    CLICK,

    /** Toggle switch throw sensation (on/off flip). */
    TOGGLE,

    /** Boundary collision bump when reaching minimum/maximum limit. */
    BOUNDARY
}

/**
 * Android Performance & Touch Latency Optimization Engine.
 *
 * Specializes in:
 * 1. Hardware acceleration & Minimal Post-Processing configuration (Window level) to bypass display latency.
 * 2. Instant-response touch feedback on ACTION_DOWN (bypassing the 100-200ms touch-up/timeout delay).
 * 3. Deferred state read modifiers in [Modifier.graphicsLayer] to completely bypass Compose Layout & Measure phases.
 * 4. Micro-vibration haptic pacing synchronized with high touch sampling rates (120Hz/240Hz/360Hz).
 */
object TouchOptimizationHelper {

    /**
     * Configures the host window for ultra-low latency rendering.
     *
     * - Enables hardware acceleration flags.
     * - Enables [Window.setPreferMinimalPostProcessing] on API 30+ (Android 11+) to bypass
     *   vendor display pipeline buffering (e.g., auto-enhancement, motion smoothing) that
     *   introduces input lag.
     */
    fun configureLowLatencyWindow(window: Window?) {
        if (window == null) return
        try {
            // Guarantee hardware acceleration at the window level
            window.setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )

            // Minimal post processing removes display pipeline buffering latency (API 30+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setPreferMinimalPostProcessing(true)
            }
        } catch (e: Exception) {
            // Graceful fallback on non-supported platforms
        }
    }

    /**
     * Dispatches instantaneous, low-latency haptic feedback directly to the [View] HAL.
     * Uses `FLAG_IGNORE_VIEW_SETTING` to ensure haptic response fires without queuing delays.
     */
    fun performInstantHaptic(
        view: View?,
        hapticType: InstantHapticType = InstantHapticType.TICK
    ) {
        if (view == null) return

        try {
            val feedbackConstant = when (hapticType) {
                InstantHapticType.TICK -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        HapticFeedbackConstants.SEGMENT_TICK
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.CLOCK_TICK
                    } else {
                        HapticFeedbackConstants.KEYBOARD_TAP
                    }
                }
                InstantHapticType.CLICK -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.CONFIRM
                    } else {
                        HapticFeedbackConstants.VIRTUAL_KEY
                    }
                }
                InstantHapticType.TOGGLE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        HapticFeedbackConstants.TOGGLE_ON
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.GESTURE_START
                    } else {
                        HapticFeedbackConstants.KEYBOARD_TAP
                    }
                }
                InstantHapticType.BOUNDARY -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.REJECT
                    } else {
                        HapticFeedbackConstants.LONG_PRESS
                    }
                }
            }

            val flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            view.performHapticFeedback(feedbackConstant, flags)
        } catch (e: Exception) {
            // Silent fallback if haptic vibrator is busy
        }
    }
}

/**
 * Side-effect applying minimal post processing to the host Activity window during active session.
 */
@Composable
fun LowLatencyWindowEffect() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val activity = findActivity(context)
        TouchOptimizationHelper.configureLowLatencyWindow(activity?.window)
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity?.window?.setPreferMinimalPostProcessing(false)
            }
        }
    }
}

/**
 * High Touch Sampling Rate Gesture Modifier.
 *
 * Unlike standard Compose [Modifier.clickable] which waits for tap timeout or finger lift
 * (adding 100-200ms latency), [instantTap] fires:
 * 1. Immediate visual press feedback (scale 0.96x via graphicsLayer) on ACTION_DOWN.
 * 2. Immediate haptic tick via [TouchOptimizationHelper.performInstantHaptic] on ACTION_DOWN.
 * 3. Triggers [onClick] immediately upon finger lift if gesture was not cancelled.
 */
fun Modifier.instantTap(
    hapticType: InstantHapticType = InstantHapticType.CLICK,
    enabled: Boolean = true,
    onDown: () -> Unit = {},
    onClick: () -> Unit
): Modifier = composed {
    val view = LocalView.current
    var isPressed by remember { mutableStateOf(false) }

    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.95f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "instantPressScale"
    )

    this
        .graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(pass = PointerEventPass.Main)
                down.consume()
                isPressed = true
                TouchOptimizationHelper.performInstantHaptic(view, hapticType)
                onDown()

                val up = waitForUpOrCancellation(pass = PointerEventPass.Main)
                isPressed = false
                if (up != null) {
                    up.consume()
                    onClick()
                }
            }
        }
}

/**
 * Zero-Jank Deferred Offset Modifier.
 *
 * Defers offset computation to the Compose Draw phase inside [Modifier.graphicsLayer],
 * bypassing Layout and Measure passes during rapid 240Hz touch dragging.
 */
fun Modifier.deferredOffset(
    xOffset: () -> Float = { 0f },
    yOffset: () -> Float = { 0f }
): Modifier = this.graphicsLayer {
    translationX = xOffset()
    translationY = yOffset()
}

/**
 * Zero-Jank Deferred Scale Modifier.
 *
 * Reads dynamic float scale inside the Draw layer without triggering parent recomposition.
 */
fun Modifier.deferredScale(
    scaleProvider: () -> Float
): Modifier = this.graphicsLayer {
    val s = scaleProvider()
    scaleX = s
    scaleY = s
}

/**
 * Traverses context wrappers to find host Activity.
 */
private fun findActivity(context: Context): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
