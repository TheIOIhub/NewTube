package com.example.newtube

import android.app.Application
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.newtube.innertube.ContentConfigRepository
import com.example.newtube.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppSettingsEntryPoint {
    fun settingsRepository(): SettingsRepository

    fun contentConfigRepository(): ContentConfigRepository
}

@HiltAndroidApp
class MyApplication : Application(), ImageLoaderFactory {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val entryPoint = EntryPointAccessors.fromApplication(this, AppSettingsEntryPoint::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val repository = entryPoint.settingsRepository()
            appScope.launch {
                repository.settings
                    .distinctUntilChanged { old, new -> old.appLanguage == new.appLanguage }
                    .collect { settings ->
                        val locales =
                            if (settings.appLanguage == "system") {
                                LocaleList.getEmptyLocaleList()
                            } else {
                                LocaleList(Locale.forLanguageTag(settings.appLanguage))
                            }
                        getSystemService(LocaleManager::class.java).applicationLocales = locales
                    }
            }
        }
        // The remote client config is optional; a failed or absent config URL
        // simply leaves the bundled defaults in place. IO keeps the cold start
        // off the main thread and DataStore keeps the last good config on disk.
        appScope.launch(Dispatchers.IO) {
            runCatching { entryPoint.contentConfigRepository().refresh() }
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader
            .Builder(this)
            .memoryCache {
                MemoryCache.Builder(this).maxSizePercent(0.25).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(filesDir, "image_cache"))
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()
}
