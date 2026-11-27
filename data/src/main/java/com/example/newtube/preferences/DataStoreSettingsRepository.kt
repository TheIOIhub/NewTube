package com.example.newtube.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.newtube.model.AppSettings
import com.example.newtube.model.GestureSide
import com.example.newtube.model.ThemeMode
import com.example.newtube.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

private val THEME_MODE = stringPreferencesKey("theme_mode")
private val RECORD_HISTORY = booleanPreferencesKey("record_history")
private val USE_REMOTE_HISTORY = booleanPreferencesKey("use_remote_history")
private val DEFAULT_QUALITY = intPreferencesKey("default_quality")
private val CONTENT_COUNTRY = stringPreferencesKey("content_country")
private val CONTENT_LANGUAGE = stringPreferencesKey("content_language")
private val PREFERRED_AUDIO_LANGUAGE = stringPreferencesKey("preferred_audio_language")
private val APP_LANGUAGE = stringPreferencesKey("app_language")
private val PREFER_ORIGINAL_AUDIO = booleanPreferencesKey("prefer_original_audio")
private val SHOW_SEARCH_SUGGESTIONS = booleanPreferencesKey("show_search_suggestions")
private val AGE_RESTRICTED_CONTENT = booleanPreferencesKey("age_restricted_content")
private val SAFE_MODE = booleanPreferencesKey("safe_mode")
private val SHOW_METADATA = booleanPreferencesKey("show_metadata")
private val AUTOPLAY_NEXT = booleanPreferencesKey("autoplay_next")
private val RESUME_PLAYBACK = booleanPreferencesKey("resume_playback")
private val DOUBLE_TAP_SEEK_SECONDS = intPreferencesKey("double_tap_seek_seconds")
private val RESIZE_MODE = intPreferencesKey("resize_mode")
private val VIDEO_BRIGHTNESS = floatPreferencesKey("video_brightness")
private val GESTURE_VOLUME = booleanPreferencesKey("gesture_volume")
private val GESTURE_BRIGHTNESS = booleanPreferencesKey("gesture_brightness")
private val GESTURE_ZOOM = booleanPreferencesKey("gesture_zoom")
private val GESTURE_SPEED = booleanPreferencesKey("gesture_speed")
private val VOLUME_GESTURE_SIDE = stringPreferencesKey("volume_gesture_side")
private val BRIGHTNESS_GESTURE_SIDE = stringPreferencesKey("brightness_gesture_side")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {
    override val settings: Flow<AppSettings> =
        context.settingsDataStore.data.map { it.toAppSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = settings.first()
        val next = transform(current)
        context.settingsDataStore.edit { prefs -> prefs.write(next) }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        update { it.copy(themeMode = mode) }
    }

    override suspend fun setRecordHistory(enabled: Boolean) {
        update { it.copy(recordHistory = enabled) }
    }

    override suspend fun setUseRemoteHistory(enabled: Boolean) {
        update { it.copy(useRemoteHistory = enabled) }
    }

    override suspend fun setDefaultQuality(maxHeight: Int) {
        update { it.copy(defaultQuality = maxHeight.validQuality()) }
    }
}

/** Pure mapping, unit-testable without an Android Context. */
internal fun Preferences.toAppSettings(): AppSettings =
    AppSettings(
        themeMode = ThemeMode.fromStored(this[THEME_MODE]),
        recordHistory = this[RECORD_HISTORY] ?: true,
        useRemoteHistory = this[USE_REMOTE_HISTORY] ?: true,
        defaultQuality = (this[DEFAULT_QUALITY] ?: AppSettings.DEFAULT_QUALITY).validQuality(),
        contentCountry = this[CONTENT_COUNTRY].validCountry(),
        contentLanguage = this[CONTENT_LANGUAGE].validLanguage(),
        preferredAudioLanguage = this[PREFERRED_AUDIO_LANGUAGE].validLanguage(),
        appLanguage = this[APP_LANGUAGE].validAppLanguage(),
        preferOriginalAudio = this[PREFER_ORIGINAL_AUDIO] ?: true,
        showSearchSuggestions = this[SHOW_SEARCH_SUGGESTIONS] ?: true,
        ageRestrictedContent = this[AGE_RESTRICTED_CONTENT] ?: false,
        safeMode = this[SAFE_MODE] ?: false,
        showMetadata = this[SHOW_METADATA] ?: true,
        autoplayNext = this[AUTOPLAY_NEXT] ?: true,
        resumePlayback = this[RESUME_PLAYBACK] ?: true,
        doubleTapSeekSeconds = (this[DOUBLE_TAP_SEEK_SECONDS] ?: AppSettings.DEFAULT_SEEK_SECONDS).validSeek(),
        resizeMode = (this[RESIZE_MODE] ?: AppSettings.RESIZE_FIT).validResize(),
        videoBrightness = this[VIDEO_BRIGHTNESS]?.validBrightness(),
        gestureVolume = this[GESTURE_VOLUME] ?: true,
        gestureBrightness = this[GESTURE_BRIGHTNESS] ?: true,
        gestureZoom = this[GESTURE_ZOOM] ?: false,
        gestureSpeed = this[GESTURE_SPEED] ?: false,
        volumeGestureSide = GestureSide.fromStored(this[VOLUME_GESTURE_SIDE], GestureSide.RIGHT),
        brightnessGestureSide = GestureSide.fromStored(this[BRIGHTNESS_GESTURE_SIDE], GestureSide.LEFT),
    )

private fun androidx.datastore.preferences.core.MutablePreferences.write(settings: AppSettings) {
    this[THEME_MODE] = settings.themeMode.name
    this[RECORD_HISTORY] = settings.recordHistory
    this[USE_REMOTE_HISTORY] = settings.useRemoteHistory
    this[DEFAULT_QUALITY] = settings.defaultQuality
    this[CONTENT_COUNTRY] = settings.contentCountry
    this[CONTENT_LANGUAGE] = settings.contentLanguage
    this[PREFERRED_AUDIO_LANGUAGE] = settings.preferredAudioLanguage
    this[APP_LANGUAGE] = settings.appLanguage
    this[PREFER_ORIGINAL_AUDIO] = settings.preferOriginalAudio
    this[SHOW_SEARCH_SUGGESTIONS] = settings.showSearchSuggestions
    this[AGE_RESTRICTED_CONTENT] = settings.ageRestrictedContent
    this[SAFE_MODE] = settings.safeMode
    this[SHOW_METADATA] = settings.showMetadata
    this[AUTOPLAY_NEXT] = settings.autoplayNext
    this[RESUME_PLAYBACK] = settings.resumePlayback
    this[DOUBLE_TAP_SEEK_SECONDS] = settings.doubleTapSeekSeconds
    this[RESIZE_MODE] = settings.resizeMode
    val brightness = settings.videoBrightness
    if (brightness != null) {
        this[VIDEO_BRIGHTNESS] = brightness
    } else {
        remove(VIDEO_BRIGHTNESS)
    }
    this[GESTURE_VOLUME] = settings.gestureVolume
    this[GESTURE_BRIGHTNESS] = settings.gestureBrightness
    this[GESTURE_ZOOM] = settings.gestureZoom
    this[GESTURE_SPEED] = settings.gestureSpeed
    this[VOLUME_GESTURE_SIDE] = settings.volumeGestureSide.name
    this[BRIGHTNESS_GESTURE_SIDE] = settings.brightnessGestureSide.name
}

private fun Int.validQuality(): Int =
    if (this in AppSettings.QUALITY_OPTIONS) this else AppSettings.DEFAULT_QUALITY

private fun Int.validSeek(): Int =
    if (this in AppSettings.SEEK_OPTIONS) this else AppSettings.DEFAULT_SEEK_SECONDS

private fun Int.validResize(): Int =
    if (this in AppSettings.RESIZE_OPTIONS) this else AppSettings.RESIZE_FIT

private fun String?.validCountry(): String =
    if (this != null && this in AppSettings.VALID_COUNTRIES) this else "UA"

private fun String?.validLanguage(): String =
    if (this != null && this in AppSettings.VALID_LANGUAGES) this else "uk"

private fun String?.validAppLanguage(): String =
    if (this != null && this in AppSettings.VALID_APP_LANGUAGES) this else "system"

private fun Float.validBrightness(): Float? =
    if (this in 0.01f..1f) this else null
