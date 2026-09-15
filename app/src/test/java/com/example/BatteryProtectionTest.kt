package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.BatteryPreferences
import com.example.service.BatteryProtectionManager
import com.example.service.ChargingSpeedCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatteryProtectionTest {

    @Test
    fun `verify target charge limit evaluation`() {
        // 80% target limit
        assertFalse(BatteryProtectionManager.isTargetReached(79, 80))
        assertTrue(BatteryProtectionManager.isTargetReached(80, 80))
        assertTrue(BatteryProtectionManager.isTargetReached(85, 80))

        // 90% target limit
        assertFalse(BatteryProtectionManager.isTargetReached(89, 90))
        assertTrue(BatteryProtectionManager.isTargetReached(90, 90))

        // 100% target limit
        assertFalse(BatteryProtectionManager.isTargetReached(99, 100))
        assertTrue(BatteryProtectionManager.isTargetReached(100, 100))
    }

    @Test
    fun `verify overheat temperature evaluation`() {
        val threshold = 42.0f

        assertFalse(BatteryProtectionManager.isOverheating(37.5f, threshold))
        assertFalse(BatteryProtectionManager.isOverheating(41.9f, threshold))
        assertTrue(BatteryProtectionManager.isOverheating(42.0f, threshold))
        assertTrue(BatteryProtectionManager.isOverheating(44.2f, threshold))
    }

    @Test
    fun `verify charging power calculation logic and speed categories`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Not charging -> DISCHARGING category
        val dischargingTelemetry = BatteryProtectionManager.calculateChargingPower(
            context = context,
            voltageMilliVolts = 3850,
            batteryPercentage = 50,
            isCharging = false
        )
        assertEquals(ChargingSpeedCategory.DISCHARGING, dischargingTelemetry.speedCategory)
        assertEquals(0f, dischargingTelemetry.watts, 0.01f)

        // 2. Charging active -> Fallback or hardware model calculates wattage & speed category
        val chargingTelemetry = BatteryProtectionManager.calculateChargingPower(
            context = context,
            voltageMilliVolts = 4100,
            batteryPercentage = 50,
            isCharging = true
        )
        assertTrue("Calculated wattage must be greater than 0 while charging", chargingTelemetry.watts > 0f)
        assertTrue(chargingTelemetry.speedCategory != ChargingSpeedCategory.DISCHARGING)
    }

    @Test
    fun `verify battery preferences persistence for protection settings`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = BatteryPreferences(context)

        // Target charge percentage
        prefs.setTargetChargePercentage(85)
        assertEquals(85, prefs.targetChargePercentage.first())

        prefs.setTargetChargePercentage(90)
        assertEquals(90, prefs.targetChargePercentage.first())

        // Overheat alert toggle
        prefs.setOverheatAlertEnabled(false)
        assertEquals(false, prefs.isOverheatAlertEnabled.first())

        prefs.setOverheatAlertEnabled(true)
        assertEquals(true, prefs.isOverheatAlertEnabled.first())

        // Overheat temperature threshold
        prefs.setOverheatTemperatureThreshold(45.0f)
        assertEquals(45.0f, prefs.overheatTemperatureThreshold.first(), 0.01f)

        prefs.setOverheatTemperatureThreshold(40.0f)
        assertEquals(40.0f, prefs.overheatTemperatureThreshold.first(), 0.01f)
    }
}
