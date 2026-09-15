package com.example.display

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

/**
 * Controller holding dynamic display refresh rate state.
 */
class DynamicRefreshRateState(
    val manager: DisplayRefreshRateManager,
    private val windowProvider: () -> Window?
) {
    var currentMode by mutableStateOf(RefreshRateMode.BALANCED)
        private set

    var currentFps by mutableStateOf(60f)
        private set

    fun requestMode(mode: RefreshRateMode) {
        currentMode = mode
        val window = windowProvider()
        manager.applyRefreshRate(window, mode)
        currentFps = manager.computeEffectiveFps(mode)
    }

    fun release() {
        val window = windowProvider()
        manager.resetToSystemDefault(window)
        currentMode = RefreshRateMode.SYSTEM_DEFAULT
        currentFps = 0f
    }
}

/**
 * Creates and remembers a [DynamicRefreshRateState] bound to the current Activity window.
 */
@Composable
fun rememberDynamicRefreshRateState(): DynamicRefreshRateState {
    val context = LocalContext.current
    val manager = remember { DisplayRefreshRateManager.getInstance(context) }

    val window = remember(context) {
        findActivity(context)?.window
    }

    return remember(manager, window) {
        DynamicRefreshRateState(manager) { window }
    }
}

/**
 * Lifecycle-aware Compose side-effect that orchestrates variable refresh rate transitions.
 *
 * Behavior:
 * - When [isUserInteracting] or [isScrolling] is true: Elevates display to [RefreshRateMode.HIGH_PERFORMANCE] (120Hz/90Hz).
 * - After [idleTimeoutMs] of inactivity: Steps down to [RefreshRateMode.IDLE_SAVER] (30Hz/60Hz) to conserve power.
 * - When the lifecycle drops below [Lifecycle.State.RESUMED]: Immediately clears the window frame rate request
 *   so the Android OS resumes system-wide display scheduling.
 */
@Composable
fun DynamicDisplayRefreshEffect(
    isUserInteracting: Boolean = false,
    isScrolling: Boolean = false,
    isAnimationRunning: Boolean = true,
    batteryPercentage: Int = 100,
    isCharging: Boolean = false,
    idleTimeoutMs: Long = 1800L,
    refreshState: DynamicRefreshRateState = rememberDynamicRefreshRateState()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastInteractionTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Notify manager of battery state for low-battery capping
    LaunchedEffect(batteryPercentage, isCharging) {
        refreshState.manager.updateBatteryState(batteryPercentage, isCharging)
    }

    // Bump interaction timer on active touch or scroll
    LaunchedEffect(isUserInteracting, isScrolling) {
        if (isUserInteracting || isScrolling) {
            lastInteractionTimestamp = System.currentTimeMillis()
        }
    }

    // Dynamic Frame Rate Arbiter
    LaunchedEffect(
        isUserInteracting,
        isScrolling,
        isAnimationRunning,
        lastInteractionTimestamp
    ) {
        if (isUserInteracting || isScrolling) {
            // Immediate elevation to maximum supported rate (120Hz/90Hz)
            refreshState.requestMode(RefreshRateMode.HIGH_PERFORMANCE)
        } else {
            // User stopped interacting: wait for idleTimeoutMs then scale down
            val elapsed = System.currentTimeMillis() - lastInteractionTimestamp
            val remaining = (idleTimeoutMs - elapsed).coerceAtLeast(0L)
            if (remaining > 0L) {
                delay(remaining)
            }

            // In idle charging or dashboard viewing: drop to 30Hz or 60Hz
            val targetMode = if (isAnimationRunning) {
                RefreshRateMode.BALANCED
            } else {
                RefreshRateMode.IDLE_SAVER
            }
            refreshState.requestMode(targetMode)
        }
    }

    // Lifecycle clean-up: release window refresh rate lock on pause/stop
    DisposableEffect(lifecycleOwner, refreshState) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    refreshState.release()
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Re-evaluate on return to foreground
                    refreshState.requestMode(RefreshRateMode.BALANCED)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            refreshState.release()
        }
    }
}

/**
 * Modifier detecting user touch interactions on any composable container,
 * continuously notifying the display refresh rate arbiter.
 */
fun Modifier.trackUserInteraction(
    onInteraction: () -> Unit
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(pass = PointerEventPass.Initial)
        onInteraction()
        do {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            if (event.changes.any { it.pressed }) {
                onInteraction()
            }
        } while (event.changes.any { it.pressed })
        onInteraction()
    }
}

/**
 * Safely traverses Context hierarchy to locate host ComponentActivity.
 */
internal fun findActivity(context: Context): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
