package com.example.newtube.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.newtube.model.AppSettings
import com.example.newtube.model.ThemeMode
import com.example.newtube.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

private val THEME_MODE = stringPreferencesKey("theme_mode")
private val RECORD_HISTORY = booleanPreferencesKey("record_history")
private val USE_REMOTE_HISTORY = booleanPreferencesKey("use_remote_history")
private val DEFAULT_QUALITY = intPreferencesKey("default_quality")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {
    override val settings: Flow<AppSettings> =
        context.settingsDataStore.data.map { it.toAppSettings() }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[THEME_MODE] = mode.name }
    }

    override suspend fun setRecordHistory(enabled: Boolean) {
        context.settingsDataStore.edit { it[RECORD_HISTORY] = enabled }
    }

    override suspend fun setUseRemoteHistory(enabled: Boolean) {
        context.settingsDataStore.edit { it[USE_REMOTE_HISTORY] = enabled }
    }

    override suspend fun setDefaultQuality(maxHeight: Int) {
        context.settingsDataStore.edit {
            it[DEFAULT_QUALITY] = maxHeight.validQuality()
        }
    }
}

/** Pure mapping, unit-testable without an Android Context. */
internal fun Preferences.toAppSettings(): AppSettings =
    AppSettings(
        themeMode = ThemeMode.fromStored(this[THEME_MODE]),
        recordHistory = this[RECORD_HISTORY] ?: true,
        useRemoteHistory = this[USE_REMOTE_HISTORY] ?: true,
        defaultQuality = (this[DEFAULT_QUALITY] ?: AppSettings.DEFAULT_QUALITY).validQuality(),
    )

private fun Int.validQuality(): Int =
    if (this in AppSettings.QUALITY_OPTIONS) this else AppSettings.DEFAULT_QUALITY
