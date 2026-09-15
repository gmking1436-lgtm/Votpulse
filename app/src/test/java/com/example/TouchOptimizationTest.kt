package com.example

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.example.ui.touch.InstantHapticType
import com.example.ui.touch.TouchOptimizationHelper
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TouchOptimizationTest {

    @Test
    fun `verify window hardware acceleration and minimal post processing configuration`() {
        val controller: ActivityController<MainActivity> = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val window: Window? = activity.window

        assertNotNull(window)
        TouchOptimizationHelper.configureLowLatencyWindow(window)

        val flags = window?.attributes?.flags ?: 0
        val isHwAccelerated = (flags and WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED) != 0
        assertTrue("Hardware acceleration flag should be enabled on window", isHwAccelerated)
    }

    @Test
    fun `verify instant haptic feedback execution does not crash across types`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testView = View(context)

        // Ensure each haptic category can be executed safely
        for (type in InstantHapticType.entries) {
            TouchOptimizationHelper.performInstantHaptic(testView, type)
        }
    }
}
