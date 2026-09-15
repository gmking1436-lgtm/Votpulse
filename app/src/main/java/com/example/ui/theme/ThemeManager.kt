package com.example.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * 4 Distinct visual theme modes supported by VoltPulse.
 *
 * Each theme provides curated primary, secondary, tertiary accents, background, surface,
 * and preview color definitions for live theme switching.
 */
enum class AppThemeMode(
    val id: String,
    val title: String,
    val subtitle: String,
    val previewPrimary: Color,
    val previewSecondary: Color,
    val previewTertiary: Color,
    val previewBackground: Color,
    val previewSurface: Color
) {
    DYNAMIC_MATERIAL_YOU(
        id = "dynamic",
        title = "Dynamic Material You",
        subtitle = "Harmonizes dynamically with system wallpaper palette",
        previewPrimary = Color(0xFF6750A4),
        previewSecondary = Color(0xFF625B71),
        previewTertiary = Color(0xFF7D5260),
        previewBackground = Color(0xFF141218),
        previewSurface = Color(0xFF1D1B20)
    ),
    AMOLED_PITCH_BLACK(
        id = "amoled",
        title = "Pure AMOLED Pitch Black",
        subtitle = "True #000000 black canvas for maximum OLED energy saving",
        previewPrimary = Color(0xFF00E676),
        previewSecondary = Color(0xFF00E5FF),
        previewTertiary = Color(0xFFFFD54F),
        previewBackground = Color(0xFF000000),
        previewSurface = Color(0xFF0D0D0D)
    ),
    CYBERPUNK_NEON(
        id = "cyberpunk",
        title = "Cyberpunk Neon",
        subtitle = "Deep dark slate with neon cyan, magenta & electric lime",
        previewPrimary = Color(0xFF00F5FF),
        previewSecondary = Color(0xFFFF007F),
        previewTertiary = Color(0xFF39FF14),
        previewBackground = Color(0xFF0D0E15),
        previewSurface = Color(0xFF161926)
    ),
    MINIMAL_FROSTED_GLASS(
        id = "frosted_glass",
        title = "Minimal Frosted Glass",
        subtitle = "Subtle glassmorphic surfaces with icy blue reflections",
        previewPrimary = Color(0xFF38BDF8),
        previewSecondary = Color(0xFF818CF8),
        previewTertiary = Color(0xFF34D399),
        previewBackground = Color(0xFF0B0F17),
        previewSurface = Color(0xFF1E293B)
    );

    companion object {
        fun fromId(id: String?): AppThemeMode {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: AMOLED_PITCH_BLACK
        }
    }
}

/**
 * CompositionLocal providing active [AppThemeMode] throughout the Compose hierarchy.
 */
val LocalAppThemeMode = compositionLocalOf { AppThemeMode.AMOLED_PITCH_BLACK }

private val Context.themePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "voltpulse_theme_preferences"
)

/**
 * Production-ready ThemeManager responsible for persisting and emitting theme selections
 * via DataStore, enabling instantaneous UI recomposition without Activity recreation.
 */
class ThemeManager(private val context: Context) {

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("voltpulse_selected_theme_mode")

        @Volatile
        private var instance: ThemeManager? = null

        fun getInstance(context: Context): ThemeManager {
            return instance ?: synchronized(this) {
                instance ?: ThemeManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Flow emitting the currently selected [AppThemeMode]. Defaults to [AppThemeMode.AMOLED_PITCH_BLACK].
     */
    val themeModeFlow: Flow<AppThemeMode> = context.themePreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val savedId = preferences[KEY_THEME_MODE]
            AppThemeMode.fromId(savedId)
        }

    /**
     * Persists the newly selected theme mode to DataStore asynchronously.
     */
    suspend fun setThemeMode(mode: AppThemeMode) {
        context.themePreferencesDataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode.id
        }
    }
}
