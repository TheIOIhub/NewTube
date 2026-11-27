package com.example.newtube.repository

import com.example.newtube.model.AppSettings
import com.example.newtube.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    /** Single write path for every preference; setters below delegate to it. */
    suspend fun update(transform: (AppSettings) -> AppSettings)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setRecordHistory(enabled: Boolean)

    suspend fun setUseRemoteHistory(enabled: Boolean)

    /** [maxHeight] must be one of [AppSettings.QUALITY_OPTIONS]; other values reset to the default. */
    suspend fun setDefaultQuality(maxHeight: Int)
}
