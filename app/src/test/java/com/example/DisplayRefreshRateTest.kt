package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.display.DisplayRefreshRateManager
import com.example.display.RefreshRateMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DisplayRefreshRateTest {

    @Test
    fun `verify display refresh rate manager initialization and query`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = DisplayRefreshRateManager.getInstance(context)

        assertTrue("Max supported refresh rate should be at least 60Hz", manager.maxSupportedRefreshRate >= 59.0f)
        assertTrue("Min supported refresh rate should be positive", manager.minSupportedRefreshRate > 0f)
    }

    @Test
    fun `verify standard rate computation across modes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = DisplayRefreshRateManager.getInstance(context)

        // Reset any battery constraints
        manager.updateBatteryState(percentage = 80, isCharging = true)

        // System default should return 0f to hand control to OS
        assertEquals(0f, manager.computeEffectiveFps(RefreshRateMode.SYSTEM_DEFAULT), 0.01f)

        // Balanced mode should default to 60fps
        assertEquals(60f, manager.computeEffectiveFps(RefreshRateMode.BALANCED), 0.01f)

        // High performance mode should request maximum supported refresh rate
        val highPerf = manager.computeEffectiveFps(RefreshRateMode.HIGH_PERFORMANCE)
        assertTrue(highPerf in 60f..120f)
    }

    @Test
    fun `verify low battery throttles high performance mode to 60Hz`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = DisplayRefreshRateManager.getInstance(context)

        // 15% battery and not charging -> Low battery constraint active
        manager.updateBatteryState(percentage = 15, isCharging = false)

        val throttledHigh = manager.computeEffectiveFps(RefreshRateMode.HIGH_PERFORMANCE)
        assertEquals(60f, throttledHigh, 0.01f)

        val throttledBalanced = manager.computeEffectiveFps(RefreshRateMode.BALANCED)
        assertEquals(60f, throttledBalanced, 0.01f)

        // When plugged into charger, constraint should release
        manager.updateBatteryState(percentage = 15, isCharging = true)
        val unconstrainedHigh = manager.computeEffectiveFps(RefreshRateMode.HIGH_PERFORMANCE)
        assertTrue(unconstrainedHigh >= 60f)
    }

    @Test
    fun `verify idle saver mode computes valid low power rate`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = DisplayRefreshRateManager.getInstance(context)

        val idleRate = manager.computeEffectiveFps(RefreshRateMode.IDLE_SAVER)
        assertTrue("Idle rate should be 30Hz or 60Hz", idleRate == 30f || idleRate == 48f || idleRate == 60f)
    }
}
