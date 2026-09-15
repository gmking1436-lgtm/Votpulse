package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ThemeManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemeManagerTest {

    @Test
    fun `verify all 4 theme modes exist and have valid properties`() {
        assertEquals(4, AppThemeMode.entries.size)

        AppThemeMode.entries.forEach { mode ->
            assertNotNull(mode.id)
            assertNotNull(mode.title)
            assertNotNull(mode.subtitle)
            assertNotNull(mode.previewPrimary)
            assertNotNull(mode.previewSecondary)
            assertNotNull(mode.previewTertiary)
            assertNotNull(mode.previewBackground)
            assertNotNull(mode.previewSurface)
        }
    }

    @Test
    fun `verify fromId fallback behavior`() {
        assertEquals(AppThemeMode.DYNAMIC_MATERIAL_YOU, AppThemeMode.fromId("dynamic"))
        assertEquals(AppThemeMode.AMOLED_PITCH_BLACK, AppThemeMode.fromId("amoled"))
        assertEquals(AppThemeMode.CYBERPUNK_NEON, AppThemeMode.fromId("cyberpunk"))
        assertEquals(AppThemeMode.MINIMAL_FROSTED_GLASS, AppThemeMode.fromId("frosted_glass"))
        // Unknown falls back to AMOLED Pitch Black
        assertEquals(AppThemeMode.AMOLED_PITCH_BLACK, AppThemeMode.fromId("non_existent_theme"))
        assertEquals(AppThemeMode.AMOLED_PITCH_BLACK, AppThemeMode.fromId(null))
    }

    @Test
    fun `verify theme mode persistence via ThemeManager`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val themeManager = ThemeManager.getInstance(context)

        themeManager.setThemeMode(AppThemeMode.CYBERPUNK_NEON)
        val mode = themeManager.themeModeFlow.first()
        assertEquals(AppThemeMode.CYBERPUNK_NEON, mode)

        themeManager.setThemeMode(AppThemeMode.MINIMAL_FROSTED_GLASS)
        val updatedMode = themeManager.themeModeFlow.first()
        assertEquals(AppThemeMode.MINIMAL_FROSTED_GLASS, updatedMode)
    }
}
