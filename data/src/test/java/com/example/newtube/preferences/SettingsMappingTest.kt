package com.example.newtube.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.newtube.model.AppSettings
import com.example.newtube.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMappingTest {
    @Test
    fun `empty preferences use the documented defaults`() {
        assertEquals(AppSettings(), preferencesOf().toAppSettings())
    }

    @Test
    fun `stored values override every default`() {
        val settings =
            preferencesOf(
                stringPreferencesKey("theme_mode") to ThemeMode.DARK.name,
                booleanPreferencesKey("record_history") to false,
                booleanPreferencesKey("use_remote_history") to false,
                intPreferencesKey("default_quality") to 1080,
            ).toAppSettings()

        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertFalse(settings.recordHistory)
        assertFalse(settings.useRemoteHistory)
        assertEquals(1080, settings.defaultQuality)
    }

    @Test
    fun `unknown theme and quality fall back to safe defaults`() {
        val settings =
            preferencesOf(
                stringPreferencesKey("theme_mode") to "SEPIA",
                intPreferencesKey("default_quality") to 999,
            ).toAppSettings()

        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(720, settings.defaultQuality)
    }

    @Test
    fun `theme mode parsing is case sensitive and falls back to system`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("dark"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStored("LIGHT"))
    }

    @Test
    fun `theme mode resolves against the system setting`() {
        assertTrue(ThemeMode.SYSTEM.resolveDark(true))
        assertFalse(ThemeMode.SYSTEM.resolveDark(false))
        assertTrue(ThemeMode.DARK.resolveDark(false))
        assertFalse(ThemeMode.LIGHT.resolveDark(true))
    }
}
