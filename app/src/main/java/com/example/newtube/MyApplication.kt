package com.example.newtube

import android.app.Application
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import coil.ImageLoader
import coil.ImageLoaderFactory
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
import java.util.Locale

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppSettingsEntryPoint {
    fun settingsRepository(): SettingsRepository
}

@HiltAndroidApp
class MyApplication : Application(), ImageLoaderFactory {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val repository =
                EntryPointAccessors
                    .fromApplication(this, AppSettingsEntryPoint::class.java)
                    .settingsRepository()
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
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this).build()
}
