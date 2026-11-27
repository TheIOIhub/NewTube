package com.example.newtube.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.newtube.model.AppSettings
import com.example.newtube.model.GestureSide
import com.example.newtube.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMappingTest {
    @Test
    fun `empty preferences use the documented defaults`() {
        val settings = preferencesOf().toAppSettings()
        assertEquals(AppSettings(), settings)
        assertEquals("UA", settings.contentCountry)
        assertEquals("uk", settings.contentLanguage)
        assertEquals("uk", settings.preferredAudioLanguage)
        assertEquals("system", settings.appLanguage)
        assertEquals(10, settings.doubleTapSeekSeconds)
        assertEquals(AppSettings.RESIZE_FIT, settings.resizeMode)
        assertEquals(GestureSide.RIGHT, settings.volumeGestureSide)
        assertEquals(GestureSide.LEFT, settings.brightnessGestureSide)
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
    fun `stored values override every new default`() {
        val settings =
            preferencesOf(
                stringPreferencesKey("content_country") to "US",
                stringPreferencesKey("content_language") to "en",
                stringPreferencesKey("preferred_audio_language") to "pl",
                stringPreferencesKey("app_language") to "uk",
                booleanPreferencesKey("prefer_original_audio") to false,
                booleanPreferencesKey("show_search_suggestions") to false,
                booleanPreferencesKey("age_restricted_content") to true,
                booleanPreferencesKey("safe_mode") to true,
                booleanPreferencesKey("show_metadata") to false,
                booleanPreferencesKey("autoplay_next") to false,
                booleanPreferencesKey("resume_playback") to false,
                intPreferencesKey("double_tap_seek_seconds") to 30,
                intPreferencesKey("resize_mode") to AppSettings.RESIZE_ZOOM,
                floatPreferencesKey("video_brightness") to 0.5f,
                booleanPreferencesKey("gesture_volume") to false,
                booleanPreferencesKey("gesture_brightness") to false,
                booleanPreferencesKey("gesture_zoom") to true,
                booleanPreferencesKey("gesture_speed") to true,
                stringPreferencesKey("volume_gesture_side") to "LEFT",
                stringPreferencesKey("brightness_gesture_side") to "RIGHT",
            ).toAppSettings()

        assertEquals("US", settings.contentCountry)
        assertEquals("en", settings.contentLanguage)
        assertEquals("pl", settings.preferredAudioLanguage)
        assertEquals("uk", settings.appLanguage)
        assertFalse(settings.preferOriginalAudio)
        assertFalse(settings.showSearchSuggestions)
        assertTrue(settings.ageRestrictedContent)
        assertTrue(settings.safeMode)
        assertFalse(settings.showMetadata)
        assertFalse(settings.autoplayNext)
        assertFalse(settings.resumePlayback)
        assertEquals(30, settings.doubleTapSeekSeconds)
        assertEquals(AppSettings.RESIZE_ZOOM, settings.resizeMode)
        assertEquals(0.5f, settings.videoBrightness ?: 0f, 0.001f)
        assertFalse(settings.gestureVolume)
        assertFalse(settings.gestureBrightness)
        assertTrue(settings.gestureZoom)
        assertTrue(settings.gestureSpeed)
        assertEquals(GestureSide.LEFT, settings.volumeGestureSide)
        assertEquals(GestureSide.RIGHT, settings.brightnessGestureSide)
    }

    @Test
    fun `invalid new values fall back to safe defaults`() {
        val settings =
            preferencesOf(
                stringPreferencesKey("content_country") to "XX",
                stringPreferencesKey("content_language") to "xx",
                stringPreferencesKey("preferred_audio_language") to "xx",
                stringPreferencesKey("app_language") to "klingon",
                intPreferencesKey("double_tap_seek_seconds") to 999,
                intPreferencesKey("resize_mode") to 99,
                floatPreferencesKey("video_brightness") to 3f,
                stringPreferencesKey("volume_gesture_side") to "MIDDLE",
            ).toAppSettings()

        assertEquals("UA", settings.contentCountry)
        assertEquals("uk", settings.contentLanguage)
        assertEquals("uk", settings.preferredAudioLanguage)
        assertEquals("system", settings.appLanguage)
        assertEquals(10, settings.doubleTapSeekSeconds)
        assertEquals(AppSettings.RESIZE_FIT, settings.resizeMode)
        assertEquals(null, settings.videoBrightness)
        assertEquals(GestureSide.RIGHT, settings.volumeGestureSide)
    }

    @Test
    fun `gesture side parsing is case sensitive`() {
        assertEquals(GestureSide.RIGHT, GestureSide.fromStored(null))
        assertEquals(GestureSide.LEFT, GestureSide.fromStored("LEFT"))
        assertEquals(GestureSide.RIGHT, GestureSide.fromStored("left"))
        assertEquals(GestureSide.OFF, GestureSide.fromStored("OFF"))
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
