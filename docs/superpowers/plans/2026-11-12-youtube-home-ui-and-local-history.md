# NewTube: YouTube-style UI, local history and settings — Implementation Plan 1 (Phases 1–3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make NewTube look and behave like the YouTube/GreenTuber app on the TCL 6102H: dark/light theme, five-tab shell, YouTube-style Home with avatars and a Shorts shelf, local watch history and a complete Settings screen.

**Architecture:** `:domain` gains `ThemeMode`, `AppSettings`, `SettingsRepository`, `WatchHistoryEntry`, `HistoryRepository`, and an avatar field on `Video`. `:data` gains DataStore settings, Room v3 (`authorAvatarUrl` + `watch_history` with an explicit `MIGRATION_2_3`), a local history repository, and `getShortsShelf` / `getVideoDetails(videoId, maxHeight)`. `:app` gains the theme system, shell/top bar/bottom navigation, `VideoCard`, Home shelf/skeleton, Shorts deep-link positioning, History and Settings screens. Google account work (Phases 0, 4, 5) is **not** in this plan.

**Tech Stack:** Kotlin 1.9.24, Jetpack Compose (BOM 2024.03.00 / Material 3 1.2.1), Hilt 2.47, Navigation Compose 2.7.7, Room 2.6.1, DataStore 1.1.1, Retrofit + Moshi, ExoPlayer 2.19.0, Coil 2.5.0, JUnit 4 + MockK + `kotlinx-coroutines-test`.

**Spec:** `docs/superpowers/specs/2026-11-12-youtube-home-account-design.md` (§8–§16, §20, §22, §23)

---

## Global constraints

- **Scope:** Phases 1–3 only. Do **not** add OAuth, InnerTube, encryption, account screens, remote history, subscriptions data or notifications data. The `login` route/screen belongs to Plan 2.
- **Placeholders are explicit:** in this plan the Subscriptions tab, Notifications screen and You header are signed-out informational surfaces, not dead buttons. The Google sign-in CTA is added in Plan 2. Do not add buttons that do nothing.
- **Ukrainian UI for new/changed surfaces.** Add `app/src/main/res/values/strings.xml` with Ukrainian strings as the default (no English default file). Existing English strings in untouched screens stay as they are. `HomeCategory` keeps no Android types; the app maps it to `@StringRes`.
- **No new runtime dependencies.** Only test dependencies are added: `androidx.room:room-testing`, `androidx.test:runner`, `androidx.test:core-ktx`, and JUnit/MockK/coroutines-test in `:data`.
- **`material-icons-core` only.** If an icon is not in core (`Icons.Filled.*`), add a vector drawable; do **not** add `material-icons-extended`.
- **Theme colours and tokens come from the spec §8.2/§8.3.** Purple is reserved for buttons, links, focus and the logo; chips follow YouTube's black/white selected style.
- **Local history never stores a playback position.** Resume positions stay in `DataStorePlaybackPositionRepository`. Clearing history also clears resume positions.
- **Room has no destructive fallback.** `MIGRATION_2_3` is explicit and tested.
- **Tests are JVM unit tests** (`app/src/test`, `data/src/test`) except the two Room tests in `data/src/androidTest`, which need the device. `app` already has `unitTests.isReturnDefaultValues = true`.
- **Do not stage unrelated files.** The working tree contains earlier uncommitted work; Task 0 commits it as a baseline so later task commits are isolated.
- **Run `ktlintFormat` and `ktlintCheck` in separate Gradle invocations** (the formatter rewrites files during the same run otherwise).
- **Windows commands:** use `.\gradlew.bat`, not `./gradlew`.

## Review focus

- Theme mode `SYSTEM` follows the system at runtime; `DARK`/`LIGHT` override it, including status/navigation bar icon contrast.
- Five tabs fit at 360 dp with labels always visible and no selected pill.
- Broken trending metadata (`duration = 0`, `views = 0`, `publishedText = "0 seconds ago"`) renders as channel name only, never "0 переглядів · 0 seconds ago".
- The Home Shorts shelf makes **one** `#shorts` search and never calls the heavy `getShorts(page)` channel rotation; a shelf failure hides the shelf without touching the feed.
- Home feed is not blocked by the shelf request.
- A Shorts tile opens the Shorts tab positioned at that clip; the position argument is consumed once.
- Watching a video records one local history row per video id, and only when `record_history` is on. Re-watching updates `watchedAt`.
- A Shorts page is recorded only after it has been settled for 3 seconds; swiping cancels the pending record.
- Clearing history deletes local rows **and** DataStore resume positions, and the dialog says so.
- Max quality is a stream-selection cap: 480/720/1080, default 720; an instance without a high track falls back to the best available lower track.
- Cold start with a saved non-system theme may flash the system theme for one frame; that is accepted.

---

## Execution setup

### Task 0: Baseline the existing working tree

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: Ignore generated screenshots, logs and agent state**

Append to `.gitignore`:

```gitignore
# Local verification artefacts
*-shot.png
*-log.txt
logcat*.txt
.superpowers/
```

- [ ] **Step 2: Verify the current tree builds and tests green**

```powershell
.\gradlew.bat test
```

If this fails, stop and report the failure; do not start the redesign on a broken baseline.

- [ ] **Step 3: Commit the baseline**

```powershell
git add -A
git commit -m "chore: baseline before YouTube-style home redesign"
```

The baseline intentionally includes the earlier uncommitted work (Home categories, Shorts, watch screen, comments). Later tasks must stage only the files they touch.

- [ ] **Step 4: Create the working branch**

```powershell
git switch -c feature/youtube-home-local-history
```

If the user asked to work in an isolated worktree, create it now with the `using-git-worktrees` skill from this baseline commit and run every command below from that worktree.

---

# Phase 1 — Foundations and theme

## Task 1: Settings domain model and DataStore storage

**Files:**
- Create: `domain/src/main/java/com/example/newtube/model/ThemeMode.kt`
- Create: `domain/src/main/java/com/example/newtube/model/AppSettings.kt`
- Create: `domain/src/main/java/com/example/newtube/repository/SettingsRepository.kt`
- Create: `data/src/main/java/com/example/newtube/preferences/DataStoreSettingsRepository.kt`
- Create: `data/src/main/java/com/example/newtube/di/SettingsModule.kt`
- Modify: `data/build.gradle.kts`
- Test: `data/src/test/java/com/example/newtube/preferences/SettingsMappingTest.kt`

**Interfaces:**
- Produces: `enum class ThemeMode { SYSTEM, DARK, LIGHT }` with `ThemeMode.fromStored(String?): ThemeMode` and `ThemeMode.resolveDark(systemInDarkTheme: Boolean): Boolean`.
- Produces: `data class AppSettings(themeMode, recordHistory, useRemoteHistory, defaultQuality)` and `AppSettings.DEFAULT_QUALITY = 720`, `AppSettings.QUALITY_OPTIONS = listOf(480, 720, 1080)`.
- Produces: `interface SettingsRepository { val settings: Flow<AppSettings>; suspend fun setThemeMode(...); suspend fun setRecordHistory(...); suspend fun setUseRemoteHistory(...); suspend fun setDefaultQuality(...) }`.
- Consumes: DataStore Preferences already used by `DataStorePlaybackPositionRepository`.

- [ ] **Step 1: Write the failing mapping tests**

Create `data/src/test/java/com/example/newtube/preferences/SettingsMappingTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the test and confirm it fails to compile**

```powershell
.\gradlew.bat :data:testDebugUnitTest --tests "com.example.newtube.preferences.SettingsMappingTest"
```

Expected: `unresolved reference` for `AppSettings`, `ThemeMode`, `toAppSettings`.

- [ ] **Step 3: Add the domain model and repository contract**

`ThemeMode.kt`:

```kotlin
package com.example.newtube.model

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
    ;

    fun resolveDark(systemInDarkTheme: Boolean): Boolean =
        when (this) {
            SYSTEM -> systemInDarkTheme
            DARK -> true
            LIGHT -> false
        }

    companion object {
        /** Unknown or missing values fall back to [SYSTEM]; the name is stored as-is. */
        fun fromStored(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
```

`AppSettings.kt`:

```kotlin
package com.example.newtube.model

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val recordHistory: Boolean = true,
    val useRemoteHistory: Boolean = true,
    val defaultQuality: Int = DEFAULT_QUALITY,
) {
    companion object {
        const val DEFAULT_QUALITY = 720
        val QUALITY_OPTIONS = listOf(480, 720, 1080)
    }
}
```

`SettingsRepository.kt`:

```kotlin
package com.example.newtube.repository

import com.example.newtube.model.AppSettings
import com.example.newtube.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setRecordHistory(enabled: Boolean)

    suspend fun setUseRemoteHistory(enabled: Boolean)

    /** [maxHeight] must be one of [AppSettings.QUALITY_OPTIONS]; other values reset to the default. */
    suspend fun setDefaultQuality(maxHeight: Int)
}
```

- [ ] **Step 4: Implement DataStore settings and the mapping function**

`DataStoreSettingsRepository.kt`:

```kotlin
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
```

- [ ] **Step 5: Bind the repository with Hilt**

`SettingsModule.kt`:

```kotlin
package com.example.newtube.di

import com.example.newtube.preferences.DataStoreSettingsRepository
import com.example.newtube.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}
```

- [ ] **Step 6: Add the `:data` test dependency**

In `data/build.gradle.kts` `dependencies`:

```kotlin
testImplementation("junit:junit:4.13.2")
testImplementation("io.mockk:mockk:1.13.10")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
```

`androidx.datastore:datastore-preferences:1.1.1` is already an `implementation` dependency, so `preferencesOf` is on the test classpath.

- [ ] **Step 7: Run the tests**

```powershell
.\gradlew.bat :data:testDebugUnitTest
```

- [ ] **Step 8: Commit**

```powershell
git add domain/src/main/java/com/example/newtube/model/ThemeMode.kt domain/src/main/java/com/example/newtube/model/AppSettings.kt domain/src/main/java/com/example/newtube/repository/SettingsRepository.kt data/src/main/java/com/example/newtube/preferences/DataStoreSettingsRepository.kt data/src/main/java/com/example/newtube/di/SettingsModule.kt data/src/test/java/com/example/newtube/preferences/SettingsMappingTest.kt data/build.gradle.kts
git commit -m "feat: add theme and app settings storage"
```

## Task 2: Dark/light colour schemes, typography and shapes

**Files:**
- Modify: `app/src/main/java/com/example/newtube/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/theme/Type.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/theme/Shape.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/theme/Theme.kt`
- Modify: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values-night/colors.xml`
- Create: `app/src/main/res/values-night/themes.xml`

**Interfaces:**
- Produces: `@Composable fun NewTubeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`.
- Produces: `NewTubeTheme` sets system-bar icon appearance and the navigation-bar colour from `colorScheme.surface`.
- Consumes: `ThemeMode.resolveDark(...)` from Task 1.

- [ ] **Step 1: Replace `Color.kt` with both schemes' tokens**

```kotlin
package com.example.newtube.ui.theme

import androidx.compose.ui.graphics.Color

// Dark (YouTube-like neutrals + NewTube purple)
val md_theme_dark_primary = Color(0xFFBB86FC)
val md_theme_dark_onPrimary = Color(0xFF21005D)
val md_theme_dark_background = Color(0xFF0F0F0F)
val md_theme_dark_onBackground = Color(0xFFF1F1F1)
val md_theme_dark_surface = Color(0xFF0F0F0F)
val md_theme_dark_onSurface = Color(0xFFF1F1F1)
val md_theme_dark_surfaceVariant = Color(0xFF272727)
val md_theme_dark_onSurfaceVariant = Color(0xFFAAAAAA)
val md_theme_dark_outline = Color(0xFF3F3F3F)
val md_theme_dark_secondaryContainer = Color(0xFF272727)
val md_theme_dark_onSecondaryContainer = Color(0xFFF1F1F1)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_onError = Color(0xFF690005)

// Light
val md_theme_light_primary = Color(0xFF6200EE)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_secondary = Color(0xFF03DAC5)
val md_theme_light_onSecondary = Color(0xFF000000)
val md_theme_light_background = Color(0xFFFFFFFF)
val md_theme_light_onBackground = Color(0xFF0F0F0F)
val md_theme_light_surface = Color(0xFFFFFFFF)
val md_theme_light_onSurface = Color(0xFF0F0F0F)
val md_theme_light_surfaceVariant = Color(0xFFF2F2F2)
val md_theme_light_onSurfaceVariant = Color(0xFF606060)
val md_theme_light_outline = Color(0xFFD9D9D9)
val md_theme_light_secondaryContainer = Color(0xFFF2F2F2)
val md_theme_light_onSecondaryContainer = Color(0xFF0F0F0F)
val md_theme_light_error = Color(0xFFB3261E)
val md_theme_light_onError = Color(0xFFFFFFFF)
```

- [ ] **Step 2: Add both schemes and system-bar handling in `Theme.kt`**

```kotlin
package com.example.newtube.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors =
    darkColorScheme(
        primary = md_theme_dark_primary,
        onPrimary = md_theme_dark_onPrimary,
        secondary = md_theme_dark_primary,
        onSecondary = md_theme_dark_onPrimary,
        background = md_theme_dark_background,
        onBackground = md_theme_dark_onBackground,
        surface = md_theme_dark_surface,
        onSurface = md_theme_dark_onSurface,
        surfaceVariant = md_theme_dark_surfaceVariant,
        onSurfaceVariant = md_theme_dark_onSurfaceVariant,
        secondaryContainer = md_theme_dark_secondaryContainer,
        onSecondaryContainer = md_theme_dark_onSecondaryContainer,
        outline = md_theme_dark_outline,
        error = md_theme_dark_error,
        onError = md_theme_dark_onError,
    )

private val LightColors =
    lightColorScheme(
        primary = md_theme_light_primary,
        onPrimary = md_theme_light_onPrimary,
        secondary = md_theme_light_secondary,
        onSecondary = md_theme_light_onSecondary,
        background = md_theme_light_background,
        onBackground = md_theme_light_onBackground,
        surface = md_theme_light_surface,
        onSurface = md_theme_light_onSurface,
        surfaceVariant = md_theme_light_surfaceVariant,
        onSurfaceVariant = md_theme_light_onSurfaceVariant,
        secondaryContainer = md_theme_light_secondaryContainer,
        onSecondaryContainer = md_theme_light_onSecondaryContainer,
        outline = md_theme_light_outline,
        error = md_theme_light_error,
        onError = md_theme_light_onError,
    )

@Composable
fun NewTubeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            window.navigationBarColor = colors.surface.toArgb()
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
```

- [ ] **Step 3: Tighten typography per spec §8.3**

Replace `Type.kt`:

```kotlin
package com.example.newtube.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography =
    Typography(
        titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
        titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
        bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
        bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp),
        labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
        labelSmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 11.sp),
    )
```

- [ ] **Step 4: Give thumbnails and chips rounded shapes**

Replace `Shape.kt`:

```kotlin
package com.example.newtube.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )
```

- [ ] **Step 5: Avoid a white launch flash in dark mode**

`app/src/main/res/values/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.NewTube" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/launch_background</item>
        <item name="android:navigationBarColor">@color/launch_background</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar">true</item>
    </style>
</resources>
```

`app/src/main/res/values-night/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.NewTube" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/launch_background</item>
        <item name="android:navigationBarColor">@color/launch_background</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar">false</item>
    </style>
</resources>
```

`app/src/main/res/values/colors.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="launch_background">#FFFFFF</color>
</resources>
```

`app/src/main/res/values-night/colors.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="launch_background">#0F0F0F</color>
</resources>
```

- [ ] **Step 6: Build and run the existing tests**

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat test
```

Expected: the app compiles but still uses the default system theme because `MainActivity` has not been updated yet. No test should change.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/example/newtube/ui/theme app/src/main/res/values/themes.xml app/src/main/res/values/colors.xml app/src/main/res/values-night/colors.xml app/src/main/res/values-night/themes.xml
git commit -m "feat: add dark and light YouTube-style themes"
```

## Task 3: Theme selection in MainActivity and a minimal Settings screen

**Files:**
- Create: `app/src/main/java/com/example/newtube/ui/shell/NewTubeTopBar.kt`
- Create: `app/src/main/java/com/example/newtube/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/example/newtube/ui/settings/SettingsScreen.kt`
- Create: `app/src/main/java/com/example/newtube/ui/settings/SettingsLabels.kt`
- Create: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/example/newtube/MainActivity.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/nav/NavGraph.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/home/HomeScreen.kt` (temporary Settings gear; removed in Task 9)
- Test: `app/src/test/java/com/example/newtube/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Produces: `SettingsViewModel.settings: StateFlow<AppSettings>` and `setThemeMode(ThemeMode)`.
- Produces: `@Composable fun DetailTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {})` and `TabTopBar(title: String, actions: @Composable RowScope.() -> Unit = {})`.
- Produces: `@StringRes fun ThemeMode.labelRes(): Int`.
- Consumes: `SettingsRepository`, `NewTubeTheme(darkTheme)`.

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
package com.example.newtube.ui.settings

import com.example.newtube.model.AppSettings
import com.example.newtube.model.ThemeMode
import com.example.newtube.repository.SettingsRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val repository: SettingsRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `settings flow is exposed with its stored values`() =
        runTest {
            every { repository.settings } returns flowOf(AppSettings(themeMode = ThemeMode.DARK))

            val viewModel = SettingsViewModel(repository)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(ThemeMode.DARK, viewModel.settings.value.themeMode)
        }

    @Test
    fun `setThemeMode delegates to the repository`() =
        runTest {
            every { repository.settings } returns flowOf(AppSettings())

            val viewModel = SettingsViewModel(repository)
            viewModel.setThemeMode(ThemeMode.LIGHT)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { repository.setThemeMode(ThemeMode.LIGHT) }
        }
}
```

- [ ] **Step 2: Run it and confirm it fails to compile**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.ui.settings.SettingsViewModelTest"
```

- [ ] **Step 3: Add the Ukrainian strings used by this task**

Create `app/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="action_back">Назад</string>
    <string name="action_cancel">Скасувати</string>
    <string name="action_refresh">Оновити</string>
    <string name="action_search">Пошук</string>
    <string name="action_settings">Налаштування</string>

    <string name="settings_title">Налаштування</string>
    <string name="settings_appearance">Вигляд</string>
    <string name="settings_theme">Тема</string>
    <string name="theme_system">Системна</string>
    <string name="theme_dark">Темна</string>
    <string name="theme_light">Світла</string>
</resources>
```

- [ ] **Step 4: Implement the ViewModel and label mapping**

`SettingsViewModel.kt`:

```kotlin
package com.example.newtube.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newtube.model.AppSettings
import com.example.newtube.model.ThemeMode
import com.example.newtube.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings(),
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }
}
```

`SettingsLabels.kt`:

```kotlin
package com.example.newtube.ui.settings

import androidx.annotation.StringRes
import com.example.newtube.R
import com.example.newtube.model.ThemeMode

@StringRes
fun ThemeMode.labelRes(): Int =
    when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.LIGHT -> R.string.theme_light
    }
```

- [ ] **Step 5: Add the shared top bars**

`NewTubeTopBar.kt`:

```kotlin
package com.example.newtube.ui.shell

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.newtube.R

@Composable
fun TabTopBar(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}
```

- [ ] **Step 6: Build the minimal Settings screen and dialog**

`SettingsScreen.kt`:

```kotlin
package com.example.newtube.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.newtube.R
import com.example.newtube.model.ThemeMode
import com.example.newtube.ui.shell.DetailTopBar

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    var themeDialogOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(
            title = stringResource(R.string.settings_title),
            onBack = { navController.popBackStack() },
        )
        Text(
            text = stringResource(R.string.settings_appearance),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        SettingsTextRow(
            title = stringResource(R.string.settings_theme),
            subtitle = stringResource(settings.themeMode.labelRes()),
            onClick = { themeDialogOpen = true },
        )
    }

    if (themeDialogOpen) {
        ThemeModeDialog(
            selected = settings.themeMode,
            onSelect = {
                viewModel.setThemeMode(it)
                themeDialogOpen = false
            },
            onDismiss = { themeDialogOpen = false },
        )
    }
}

@Composable
private fun SettingsTextRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThemeModeDialog(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(mode) }
                                .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == mode, onClick = { onSelect(mode) })
                        Text(
                            text = stringResource(mode.labelRes()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
```

- [ ] **Step 7: Wire the theme into `MainActivity` and add the route**

In `MainActivity.kt`:

```kotlin
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.newtube.model.ThemeMode
import com.example.newtube.ui.settings.SettingsViewModel

override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContent {
        val settingsViewModel: SettingsViewModel = hiltViewModel()
        val settings by settingsViewModel.settings.collectAsState()
        val darkTheme = settings.themeMode.resolveDark(isSystemInDarkTheme())
        CompositionLocalProvider(LocalPipMode provides isInPipMode) {
            NewTubeTheme(darkTheme = darkTheme) {
                MainScreen()
            }
        }
    }
}
```

`enableEdgeToEdge()` comes from `androidx.activity:activity-compose:1.9.0` (already a dependency).

Add a temporary Settings gear to the current Home header so Phase 1 is reachable (it is removed when Home is redesigned in Task 9):

```kotlin
IconButton(onClick = { navController.navigate("settings") }) {
    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
}
```

In `NavGraph.kt`:

```kotlin
composable("settings") { SettingsScreen(navController) }
```

- [ ] **Step 8: Run the tests and build**

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

- [ ] **Step 9: Manual check on the device**

Install and switch the theme in Settings. Dark mode must show `#0F0F0F` surfaces, white chips text, light status-bar icons; light mode must invert all of that. The navigation bar must follow `surface`.

- [ ] **Step 10: Commit**

```powershell
git add app/src/main/java/com/example/newtube/MainActivity.kt app/src/main/java/com/example/newtube/ui/nav/NavGraph.kt app/src/main/java/com/example/newtube/ui/home/HomeScreen.kt app/src/main/java/com/example/newtube/ui/shell/NewTubeTopBar.kt app/src/main/java/com/example/newtube/ui/settings app/src/main/res/values/strings.xml app/src/test/java/com/example/newtube/ui/settings
git commit -m "feat: persist theme mode and add minimal settings screen"
```

# Phase 2 — Shell and Home redesign

## Task 4: Room v3 — avatar column, watch history table and migration

**Files:**
- Modify: `data/build.gradle.kts`
- Create: `data/src/main/java/com/example/newtube/database/WatchHistoryEntity.kt`
- Create: `data/src/main/java/com/example/newtube/database/WatchHistoryDao.kt`
- Modify: `data/src/main/java/com/example/newtube/database/VideoEntity.kt`
- Modify: `data/src/main/java/com/example/newtube/database/NewTubeDatabase.kt`
- Modify: `data/src/main/java/com/example/newtube/di/DatabaseModule.kt`
- Create: `data/src/androidTest/java/com/example/newtube/database/MigrationTest.kt`
- Create: `data/src/androidTest/java/com/example/newtube/database/WatchHistoryDaoTest.kt`
- Generated and committed: `data/schemas/com.example.newtube.database.NewTubeDatabase/2.json`, `3.json`

**Interfaces:**
- Produces: `WatchHistoryEntity` and `WatchHistoryDao` exactly as in spec §16.2.
- Produces: `NewTubeDatabase.VERSION = 3`, `NewTubeDatabase.MIGRATION_2_3`, `watchHistoryDao()`.
- Produces: `VideoEntity.authorAvatarUrl: String = ""` (the domain/repository mapping for it lands in Task 5).
- Produces: Hilt provider `provideWatchHistoryDao`.
- Consumes: existing `VideoEntity` and `ChannelEntity`.

**Ordering is critical.** `2.json` must be generated while `VideoEntity` still has **no** avatar column, so it describes the real shipped v2 schema. Steps 1–2 therefore come before any entity change; the avatar column and `watch_history` then land together in the single 2→3 migration.

- [ ] **Step 1: Enable Room schema export at v2 (no entity changes yet)**

In `data/build.gradle.kts` add inside `android`:

```kotlin
sourceSets {
    getByName("androidTest").assets.srcDir("$projectDir/schemas")
}
```

At the top level of the file:

```kotlin
kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}
```

Add dependencies:

```kotlin
androidTestImplementation("androidx.room:room-testing:2.6.1")
androidTestImplementation("androidx.test:runner:1.5.2")
androidTestImplementation("androidx.test:core-ktx:1.5.0")
androidTestImplementation("androidx.test.ext:junit:1.2.1")
```

Set `exportSchema = true` in `NewTubeDatabase` **without** changing `VERSION = 2` and **without** touching `VideoEntity` yet.

- [ ] **Step 2: Generate and commit the true v2 schema**

```powershell
.\gradlew.bat :data:assembleDebug
```

Verify `data/schemas/com.example.newtube.database.NewTubeDatabase/2.json` exists and has **no** `authorAvatarUrl`, then:

```powershell
git add data/build.gradle.kts data/schemas
git commit -m "chore: export Room schema at version 2"
```

- [ ] **Step 3: Write the failing Room tests**

`MigrationTest.kt`:

```kotlin
package com.example.newtube.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            NewTubeDatabase::class.java,
        )

    @Test
    fun migrate2To3_addsAvatarAndWatchHistory() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO videos (id, title, channelName, channelId, thumbnailUrl, duration, views, uploadedAt, isLive) " +
                    "VALUES ('v1', 'Title', 'Channel', 'UC1', 'thumb', 10, 20, '1 day ago', 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, NewTubeDatabase.MIGRATION_2_3)

        db.query("SELECT authorAvatarUrl FROM videos WHERE id = 'v1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("", cursor.getString(0))
        }
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'watch_history'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
```

`WatchHistoryDaoTest.kt`:

```kotlin
package com.example.newtube.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchHistoryDaoTest {
    private lateinit var db: NewTubeDatabase
    private lateinit var dao: WatchHistoryDao

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                NewTubeDatabase::class.java,
            ).allowMainThreadQueries().build()
        dao = db.watchHistoryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsertReplacesTheSameVideoAndObservesNewestFirst() = runBlocking {
        dao.upsert(entry("a", 1))
        dao.upsert(entry("b", 2))
        dao.upsert(entry("a", 3))

        val rows = dao.observeRecent(500).first()

        assertEquals(listOf("a", "b"), rows.map { it.videoId })
        assertEquals(3L, rows.first().watchedAt)
    }

    @Test
    fun trimKeepsOnlyTheNewestRows() = runBlocking {
        dao.upsert(entry("a", 1))
        dao.upsert(entry("b", 2))
        dao.upsert(entry("c", 3))

        dao.trim(2)

        assertEquals(listOf("c", "b"), dao.observeRecent(500).first().map { it.videoId })
    }

    @Test
    fun clearRemovesEverything() = runBlocking {
        dao.upsert(entry("a", 1))

        dao.clear()

        assertEquals(emptyList<WatchHistoryEntity>(), dao.observeRecent(500).first())
    }

    private fun entry(id: String, watchedAt: Long) =
        WatchHistoryEntity(
            videoId = id,
            title = "Video $id",
            channelName = "Channel",
            channelId = "UC$id",
            authorAvatarUrl = "",
            thumbnailUrl = "https://example.com/$id.jpg",
            durationMs = 30_000,
            watchedAt = watchedAt,
        )
}
```

- [ ] **Step 4: Add the entity and DAO**

`WatchHistoryEntity.kt`:

```kotlin
package com.example.newtube.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history", indices = [Index("watchedAt")])
data class WatchHistoryEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val authorAvatarUrl: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val watchedAt: Long,
)
```

`WatchHistoryDao.kt`:

```kotlin
package com.example.newtube.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Upsert
    suspend fun upsert(entry: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WatchHistoryEntity>>

    @Query("DELETE FROM watch_history")
    suspend fun clear()

    @Query(
        "DELETE FROM watch_history WHERE videoId NOT IN " +
            "(SELECT videoId FROM watch_history ORDER BY watchedAt DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int)
}
```

- [ ] **Step 5: Add the avatar column to `VideoEntity`**

```kotlin
@ColumnInfo(defaultValue = "")
val authorAvatarUrl: String = "",
```

Add `import androidx.room.ColumnInfo`. The explicit default is required so `MIGRATION_2_3`'s `DEFAULT ''` matches Room's expected schema.

- [ ] **Step 6: Bump the database to v3 and add the migration**

In `NewTubeDatabase`:

```kotlin
@Database(
    entities = [VideoEntity::class, ChannelEntity::class, WatchHistoryEntity::class],
    version = NewTubeDatabase.VERSION,
    exportSchema = true,
)
abstract class NewTubeDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao

    abstract fun channelDao(): ChannelDao

    abstract fun watchHistoryDao(): WatchHistoryDao

    companion object {
        const val VERSION = 3

        val MIGRATION_1_2 = /* unchanged */

        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        "ALTER TABLE videos ADD COLUMN authorAvatarUrl TEXT NOT NULL DEFAULT ''",
                    )
                    database.execSQL(
                        "CREATE TABLE IF NOT EXISTS watch_history (" +
                            "videoId TEXT NOT NULL PRIMARY KEY, " +
                            "title TEXT NOT NULL, " +
                            "channelName TEXT NOT NULL, " +
                            "channelId TEXT NOT NULL, " +
                            "authorAvatarUrl TEXT NOT NULL, " +
                            "thumbnailUrl TEXT NOT NULL, " +
                            "durationMs INTEGER NOT NULL, " +
                            "watchedAt INTEGER NOT NULL)",
                    )
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_watch_history_watchedAt " +
                            "ON watch_history (watchedAt)",
                    )
                }
            }
    }
}
```

The SQL is byte-for-byte what Room expects: table name, column order and the index name `index_watch_history_watchedAt`.

- [ ] **Step 7: Register the migration and DAO in Hilt**

In `DatabaseModule`:

```kotlin
.addMigrations(NewTubeDatabase.MIGRATION_1_2, NewTubeDatabase.MIGRATION_2_3)
```

```kotlin
@Provides
fun provideWatchHistoryDao(db: NewTubeDatabase): WatchHistoryDao = db.watchHistoryDao()
```

- [ ] **Step 8: Generate the v3 schema, build and run the tests**

```powershell
.\gradlew.bat :data:assembleDebug
.\gradlew.bat :data:assembleDebugAndroidTest
.\gradlew.bat :data:connectedDebugAndroidTest
```

Verify `data/schemas/com.example.newtube.database.NewTubeDatabase/3.json` now exists and contains `authorAvatarUrl` and `watch_history`. The connected test needs the device; if `adb` is not available, record that in the commit message and run it at the end of the plan.

- [ ] **Step 9: Commit**

```powershell
git add data/build.gradle.kts data/src/main/java/com/example/newtube/database data/src/main/java/com/example/newtube/di/DatabaseModule.kt data/src/androidTest data/schemas
git commit -m "feat: add Room v3 migration and watch history storage"
```

## Task 5: Channel avatars from Invidious to the domain model

**Files:**
- Modify: `data/src/main/java/com/example/newtube/remote/VideoDto.kt`
- Modify: `domain/src/main/java/com/example/newtube/model/Video.kt`
- Modify: `data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt`
- Test: `app/src/test/java/com/example/newtube/repository/VideoRepositoryImplTest.kt`

**Interfaces:**
- Produces: `VideoDto.authorThumbnails: List<ThumbnailDto>`.
- Produces: `Video.authorAvatarUrl: String = ""`.
- Consumes: `VideoEntity.authorAvatarUrl` from Task 4. Do **not** change the database version or migration here.
- Rule: choose the largest avatar with `width in 1..176`; if none, the largest available; empty when the list is empty.

- [ ] **Step 1: Write the failing tests**

Add to `VideoRepositoryImplTest`:

```kotlin
@Test
fun `getTrending picks the largest avatar at or below 176 px`() =
    runBlocking {
        val dto =
            videoDto(id = "1").copy(
                authorThumbnails =
                    listOf(
                        ThumbnailDto(url = "https://example.com/32.jpg", width = 32, height = 32),
                        ThumbnailDto(url = "https://example.com/176.jpg", width = 176, height = 176),
                        ThumbnailDto(url = "https://example.com/512.jpg", width = 512, height = 512),
                    ),
            )
        coEvery { service.getTrending(any(), any()) } returns listOf(dto)
        coJustRun { dao.insertAll(any()) }

        assertEquals("https://example.com/176.jpg", repository.getTrending().single().authorAvatarUrl)
    }

@Test
fun `avatar falls back to the largest available when every size is above 176`() =
    runBlocking {
        val dto =
            videoDto(id = "1").copy(
                authorThumbnails =
                    listOf(
                        ThumbnailDto(url = "https://example.com/512.jpg", width = 512, height = 512),
                        ThumbnailDto(url = "https://example.com/900.jpg", width = 900, height = 900),
                    ),
            )
        coEvery { service.getTrending(any(), any()) } returns listOf(dto)
        coJustRun { dao.insertAll(any()) }

        assertEquals("https://example.com/900.jpg", repository.getTrending().single().authorAvatarUrl)
    }

@Test
fun `missing author thumbnails map to an empty avatar`() =
    runBlocking {
        coEvery { service.getTrending(any(), any()) } returns listOf(videoDto(id = "1"))
        coJustRun { dao.insertAll(any()) }

        assertEquals("", repository.getTrending().single().authorAvatarUrl)
    }
```

- [ ] **Step 2: Run and confirm the tests fail to compile**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.repository.VideoRepositoryImplTest"
```

- [ ] **Step 3: Add the DTO field**

In `VideoDto`:

```kotlin
val authorThumbnails: List<ThumbnailDto> = emptyList(),
```

- [ ] **Step 4: Add the domain field**

`Video.kt`:

```kotlin
data class Video(
    /* existing fields stay in this order */
    val isLive: Boolean,
    val authorAvatarUrl: String = "",
)
```

`VideoEntity` already has the matching column from Task 4; nothing to change there.

- [ ] **Step 5: Map the avatar in both directions**

In `VideoRepositoryImpl` (add `import com.example.newtube.remote.ThumbnailDto`):

```kotlin
private fun List<ThumbnailDto>.avatarUrl(): String {
    val usable = filter { it.url.isNotBlank() }
    val preferred = usable.filter { it.width in 1..176 }
    return (preferred.maxByOrNull { it.width } ?: usable.maxByOrNull { it.width })?.url.orEmpty()
}
```

In `toEntityOrNull()` add:

```kotlin
authorAvatarUrl = authorThumbnails.avatarUrl(),
```

In `VideoEntity.toDomain()` add:

```kotlin
authorAvatarUrl = authorAvatarUrl,
```

- [ ] **Step 6: Run the tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.repository.VideoRepositoryImplTest"
```

- [ ] **Step 7: Commit**

```powershell
git add data/src/main/java/com/example/newtube/remote/VideoDto.kt domain/src/main/java/com/example/newtube/model/Video.kt data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt app/src/test/java/com/example/newtube/repository/VideoRepositoryImplTest.kt
git commit -m "feat: map channel avatars onto videos"
```


## Task 6: Metadata sanity rule and `VideoCard`

**Files:**
- Create: `app/src/main/java/com/example/newtube/ui/common/VideoMetaLine.kt`
- Create: `app/src/main/java/com/example/newtube/ui/common/VideoCard.kt`
- Create: `app/src/main/java/com/example/newtube/ui/common/ChannelAvatar.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/example/newtube/ui/common/VideoMetaLineTest.kt`

**Interfaces:**
- Produces: `fun videoMetaLine(video: Video): VideoMetaLine` where `VideoMetaLine(showViews: Boolean, uploadedAt: String?)`.
- Produces: `@Composable fun VideoCard(video, onClick: (String) -> Unit, onChannelClick: (String) -> Unit = {}, modifier: Modifier = Modifier)`.
- Produces: `@Composable fun ChannelAvatar(url: String, name: String, size: Dp = 36.dp, modifier: Modifier = Modifier)`.
- Consumes: `formatCount`, `formatDuration`, `Video.authorAvatarUrl`.

- [ ] **Step 1: Write the failing metadata tests**

```kotlin
package com.example.newtube.ui.common

import com.example.newtube.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoMetaLineTest {
    @Test
    fun `broken trending metadata hides views and relative time`() {
        val meta = videoMetaLine(video(duration = 0, views = 0, uploadedAt = "0 seconds ago"))

        assertFalse(meta.showViews)
        assertNull(meta.uploadedAt)
    }

    @Test
    fun `a real new video keeps its zero views when it has a duration`() {
        val meta = videoMetaLine(video(duration = 120, views = 0, uploadedAt = "1 hour ago"))

        assertTrue(meta.showViews)
        assertEquals("1 hour ago", meta.uploadedAt)
    }

    @Test
    fun `blank upload time is omitted`() {
        val meta = videoMetaLine(video(duration = 60, views = 5, uploadedAt = ""))

        assertTrue(meta.showViews)
        assertNull(meta.uploadedAt)
    }

    @Test
    fun `a normal video keeps every segment`() {
        val meta = videoMetaLine(video(duration = 300, views = 1_200_000, uploadedAt = "2 days ago"))

        assertTrue(meta.showViews)
        assertEquals("2 days ago", meta.uploadedAt)
    }

    private fun video(duration: Long, views: Long, uploadedAt: String) =
        Video(
            id = "v1",
            title = "Video",
            channelName = "Channel",
            channelId = "UC1",
            thumbnailUrl = "https://example.com/thumb.jpg",
            duration = duration,
            views = views,
            uploadedAt = uploadedAt,
            isLive = false,
        )
}
```

- [ ] **Step 2: Run and confirm it fails**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.ui.common.VideoMetaLineTest"
```

- [ ] **Step 3: Implement the pure rule**

`VideoMetaLine.kt`:

```kotlin
package com.example.newtube.ui.common

import com.example.newtube.model.Video

data class VideoMetaLine(
    val showViews: Boolean,
    val uploadedAt: String?,
)

/**
 * Invidious' default instance returns `viewCount = 0`, `lengthSeconds = 0` and
 * `publishedText = "0 seconds ago"` for broken trending items. Hide those
 * segments, but keep a genuine zero-view upload that has a duration.
 */
fun videoMetaLine(video: Video): VideoMetaLine {
    val uploaded = video.uploadedAt.takeIf { it.isNotBlank() && it != "0 seconds ago" }
    return VideoMetaLine(
        showViews = video.duration > 0L || video.views > 0L,
        uploadedAt = uploaded,
    )
}
```

- [ ] **Step 4: Add the new strings**

```xml
<string name="views_count">%1$s переглядів</string>
<string name="live_badge">LIVE</string>
<string name="channel_avatar">Аватар каналу: %1$s</string>
```

- [ ] **Step 5: Implement `ChannelAvatar`**

```kotlin
package com.example.newtube.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.newtube.R

@Composable
fun ChannelAvatar(
    url: String,
    name: String,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            Image(
                painter = rememberAsyncImagePainter(url),
                contentDescription = stringResource(R.string.channel_avatar, name),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.6f),
            )
        }
    }
}
```

- [ ] **Step 6: Implement `VideoCard`**

```kotlin
package com.example.newtube.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.newtube.R
import com.example.newtube.model.Video

@Composable
fun VideoCard(
    video: Video,
    onClick: (String) -> Unit,
    onChannelClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val meta = videoMetaLine(video)
    val viewsLabel =
        if (meta.showViews) {
            stringResource(R.string.views_count, formatCount(video.views))
        } else {
            null
        }
    val metadata =
        listOfNotNull(
            video.channelName.takeIf { it.isNotBlank() },
            viewsLabel,
            meta.uploadedAt,
        ).joinToString(" · ")

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onClick(video.id) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Image(
                painter = rememberAsyncImagePainter(video.thumbnailUrl),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
            )
            when {
                video.isLive -> LiveBadge(Modifier.align(Alignment.BottomEnd).padding(8.dp))
                video.duration > 0L ->
                    DurationBadge(
                        text = formatDuration(video.duration),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    )
            }
        }
        Row(modifier = Modifier.padding(top = 12.dp)) {
            ChannelAvatar(
                url = video.authorAvatarUrl,
                name = video.channelName,
                modifier =
                    Modifier.clickable(enabled = video.channelId.isNotBlank()) {
                        onChannelClick(video.channelId)
                    },
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.clickable(enabled = video.channelId.isNotBlank()) {
                            onChannelClick(video.channelId)
                        },
                )
            }
        }
    }
}

@Composable
private fun DurationBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier =
            modifier
                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.live_badge),
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier =
            modifier
                .background(Color(0xFFCC0000), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
```

- [ ] **Step 7: Run the tests and build**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.ui.common.VideoMetaLineTest"
.\gradlew.bat :app:assembleDebug
```

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/java/com/example/newtube/ui/common/VideoMetaLine.kt app/src/main/java/com/example/newtube/ui/common/VideoCard.kt app/src/main/java/com/example/newtube/ui/common/ChannelAvatar.kt app/src/main/res/values/strings.xml app/src/test/java/com/example/newtube/ui/common/VideoMetaLineTest.kt
git commit -m "feat: add YouTube-style video cards with metadata sanity rule"
```

## Task 7: Lightweight Shorts shelf query and Home shelf state

**Files:**
- Modify: `domain/src/main/java/com/example/newtube/repository/VideoRepository.kt`
- Modify: `data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/home/HomeViewModel.kt`
- Test: `app/src/test/java/com/example/newtube/repository/VideoRepositoryImplTest.kt`
- Test: `app/src/test/java/com/example/newtube/ui/home/HomeViewModelTest.kt`

**Interfaces:**
- Produces: `suspend fun VideoRepository.getShortsShelf(limit: Int = 8): List<Video>`.
- Produces: `HomeViewModel.shortsShelf: StateFlow<List<Video>>`.
- Rule: one `InvidiousService.getShorts(page = 1)` request, `1..60` seconds, not live, deduped, shuffled, capped. Never calls `getShorts(page)`.

- [ ] **Step 1: Write the failing repository test**

Add to `VideoRepositoryImplTest`:

```kotlin
@Test
fun `getShortsShelf is one search and keeps only 1 to 60 second non-live clips`() =
    runBlocking {
        coEvery { service.getShorts(page = 1) } returns
            listOf(
                videoDto("short", length = 30),
                videoDto("live", length = 30).copy(liveNow = true),
                videoDto("long", length = 61),
                videoDto("zero", length = 0),
                videoDto("short", length = 30),
            )
        coJustRun { dao.insertAll(any()) }

        val shelf = repository.getShortsShelf(limit = 8)

        assertEquals(listOf("short"), shelf.map { it.id })
        coVerify(exactly = 1) { service.getShorts(page = 1) }
    }

@Test
fun `getShortsShelf honours the limit`() =
    runBlocking {
        coEvery { service.getShorts(page = 1) } returns
            (1..10).map { videoDto("s$it", length = 30) }
        coJustRun { dao.insertAll(any()) }

        assertEquals(3, repository.getShortsShelf(limit = 3).size)
    }
```

- [ ] **Step 2: Write the failing HomeViewModel tests**

Add to `HomeViewModelTest` and update `setUp()`:

```kotlin
@Before
fun setUp() {
    Dispatchers.setMain(testDispatcher)
    coEvery { repository.getShortsShelf() } returns emptyList()
}
```

```kotlin
@Test
fun `shorts shelf loads without blocking the feed`() =
    runTest {
        val shelf = CompletableDeferred<List<Video>>()
        coEvery { repository.getHomeFeed(HomeCategory.ALL) } returns listOf(video("1"))
        coEvery { repository.getShortsShelf() } coAnswers { shelf.await() }

        val viewModel = HomeViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(UiState.Success(listOf(video("1"))), viewModel.uiState.value)
        assertTrue(viewModel.shortsShelf.value.isEmpty())

        shelf.complete(listOf(video("s1")))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("s1"), viewModel.shortsShelf.value.map { it.id })
    }

@Test
fun `a shelf failure is silent and keeps an empty shelf`() =
    runTest {
        coEvery { repository.getHomeFeed(HomeCategory.ALL) } returns listOf(video("1"))
        coEvery { repository.getShortsShelf() } throws IOException("offline")

        val viewModel = HomeViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(UiState.Success(listOf(video("1"))), viewModel.uiState.value)
        assertTrue(viewModel.shortsShelf.value.isEmpty())
    }

@Test
fun `refresh reloads the shelf too`() =
    runTest {
        coEvery { repository.getHomeFeed(HomeCategory.ALL) } returns listOf(video("1"))
        coEvery { repository.getShortsShelf() } returnsMany
            listOf(listOf(video("s1")), listOf(video("s2")))

        val viewModel = HomeViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("s1"), viewModel.shortsShelf.value.map { it.id })

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("s2"), viewModel.shortsShelf.value.map { it.id })
    }
```

These tests also need `import kotlinx.coroutines.CompletableDeferred` and `import java.io.IOException`.

- [ ] **Step 3: Run and confirm the tests fail**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.repository.VideoRepositoryImplTest" --tests "com.example.newtube.ui.home.HomeViewModelTest"
```

- [ ] **Step 4: Add the repository method**

`VideoRepository`:

```kotlin
/** One lightweight `#shorts` search for the Home shelf; does not touch the Shorts tab rotation. */
suspend fun getShortsShelf(limit: Int = 8): List<Video>
```

`VideoRepositoryImpl`:

```kotlin
override suspend fun getShortsShelf(limit: Int): List<Video> =
    withContext(Dispatchers.IO) {
        service
            .getShorts(page = 1)
            .asSequence()
            .filter { it.lengthSeconds in 1..SHORTS_SHELF_MAX_SECONDS && !it.liveNow }
            .distinctBy { it.videoId }
            .shuffled()
            .take(limit.coerceAtLeast(0))
            .toList()
            .toDomainVideos()
    }
```

Add to the companion:

```kotlin
const val SHORTS_SHELF_MAX_SECONDS = 60L
```

- [ ] **Step 5: Add the shelf state to `HomeViewModel`**

```kotlin
private val _shortsShelf = MutableStateFlow<List<Video>>(emptyList())
val shortsShelf: StateFlow<List<Video>> = _shortsShelf

private var shelfJob: Job? = null

init {
    load()
    loadShortsShelf()
}

fun refresh() {
    fetch(showLoading = false)
    loadShortsShelf()
}

private fun loadShortsShelf() {
    shelfJob?.cancel()
    shelfJob =
        viewModelScope.launch {
            _shortsShelf.value =
                runCatching { repository.getShortsShelf() }.getOrDefault(emptyList())
        }
}
```

Add `import kotlinx.coroutines.Job`.

- [ ] **Step 6: Run the tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.repository.VideoRepositoryImplTest" --tests "com.example.newtube.ui.home.HomeViewModelTest"
```

- [ ] **Step 7: Commit**

```powershell
git add domain/src/main/java/com/example/newtube/repository/VideoRepository.kt data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt app/src/main/java/com/example/newtube/ui/home/HomeViewModel.kt app/src/test/java/com/example/newtube/repository/VideoRepositoryImplTest.kt app/src/test/java/com/example/newtube/ui/home/HomeViewModelTest.kt
git commit -m "feat: load a lightweight Shorts shelf for Home"
```

## Task 8: Five-tab shell, bottom navigation and signed-out placeholders

**Files:**
- Create: `app/src/main/res/drawable/ic_home.xml`
- Create: `app/src/main/res/drawable/ic_shorts.xml`
- Create: `app/src/main/res/drawable/ic_search.xml`
- Create: `app/src/main/res/drawable/ic_subscriptions.xml`
- Create: `app/src/main/res/drawable/ic_person.xml`
- Create: `app/src/main/res/drawable/ic_history.xml`
- Create: `app/src/main/res/drawable/ic_settings.xml`
- Create: `app/src/main/res/drawable/ic_info.xml`
- Create: `app/src/main/java/com/example/newtube/ui/shell/BottomNavigationBar.kt`
- Create: `app/src/main/java/com/example/newtube/ui/you/YouScreen.kt`
- Create: `app/src/main/java/com/example/newtube/ui/subscriptions/SubscriptionsScreen.kt`
- Create: `app/src/main/java/com/example/newtube/ui/notifications/NotificationsScreen.kt`
- Create: `app/src/main/java/com/example/newtube/ui/history/HistoryScreen.kt` (placeholder, replaced in Task 13)
- Create: `app/src/main/java/com/example/newtube/ui/about/AboutScreen.kt`
- Modify: `app/src/main/java/com/example/newtube/MainActivity.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/nav/NavGraph.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/search/SearchScreen.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/channel/ChannelScreen.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/video/WatchScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: routes `home`, `shorts`, `search?focus={focus}`, `subscriptions`, `you`, `history`, `settings`, `about`, `notifications`, plus the existing detail routes.
- Produces: `BottomNavItem(@StringRes labelRes, route, @DrawableRes iconRes)`.
- Consumes: `VideoCard`, `DetailTopBar`, `TabTopBar`.

- [ ] **Step 1: Add the navigation vector icons**

All are 24×24 viewport vectors with `android:fillColor="#FF000000"` (Compose tints them).

`ic_home.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF000000"
        android:pathData="M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z" />
</vector>
```

`ic_shorts.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF000000"
        android:pathData="M16,3H8C6.9,3 6,3.9 6,5v14c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V5C18,3.9 17.1,3 16,3zM10,16V8l5,4L10,16z" />
</vector>
```

`ic_search.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF000000"
        android:pathData="M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z" />
</vector>
```

`ic_subscriptions.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF000000"
        android:pathData="M20,8H4V6h16v2zM18,2H6v2h12V2zM22,12v8c0,1.1 -0.9,2 -2,2H4c-1.1,0 -2,-0.9 -2,-2v-8c0,-1.1 0.9,-2 2,-2h16c1.1,0 2,0.9 2,2zM14,17l-6,-4v8l6,-4z" />
</vector>
```

`ic_person.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF000000"
        android:pathData="M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z" />
</vector>
```

`ic_history.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF000000"
        android:pathData="M13,3c-4.97,0 -9,4.03 -9,9H1l3.89,3.89 0.07,0.14L9,12H6c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08V8H12z" />
</vector>
```

`ic_settings.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF000000"
        android:pathData="M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6 3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z" />
</vector>
```

`ic_info.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF000000"
        android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2V7h2v2z" />
</vector>
```

- [ ] **Step 2: Add the new strings**

```xml
<string name="tab_home">Головна</string>
<string name="tab_shorts">Shorts</string>
<string name="tab_search">Пошук</string>
<string name="tab_subscriptions">Підписки</string>
<string name="tab_you">Ви</string>

<string name="you_guest">Гість</string>
<string name="you_guest_subtitle">Історія та підписки з’являться після входу в Google</string>
<string name="you_history">Історія переглядів</string>
<string name="you_subscriptions">Підписки</string>
<string name="you_settings">Налаштування</string>
<string name="you_about">Про застосунок</string>

<string name="subscriptions_title">Підписки</string>
<string name="subscriptions_signed_out_title">Підписки з’являться після входу в Google</string>
<string name="subscriptions_signed_out_body">Вхід у Google буде додано в наступному оновленні.</string>

<string name="notifications_title">Сповіщення</string>
<string name="notifications_signed_out">Сповіщення з’являться після входу в Google.</string>

<string name="history_title">Історія переглядів</string>
<string name="history_empty">Історія порожня</string>
<string name="history_empty_body">Відео, які ви переглянули, з’являться тут.</string>

<string name="about_title">Про NewTube</string>
<string name="about_version">Версія %1$s</string>
<string name="about_instance">Invidious: %1$s</string>
<string name="about_legal">NewTube не пов’язаний із YouTube чи Google. Відео відтворюються через публічні інстанси Invidious.</string>

<string name="search_hint">Пошук на YouTube</string>
<string name="search_clear">Очистити</string>
<string name="search_empty">Нічого не знайдено</string>
```

- [ ] **Step 3: Build the bottom navigation**

`BottomNavigationBar.kt`:

```kotlin
package com.example.newtube.ui.shell

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.newtube.R

data class BottomNavItem(
    @StringRes val labelRes: Int,
    val route: String,
    @DrawableRes val iconRes: Int,
)

val BOTTOM_NAV_ITEMS =
    listOf(
        BottomNavItem(R.string.tab_home, "home", R.drawable.ic_home),
        BottomNavItem(R.string.tab_shorts, "shorts", R.drawable.ic_shorts),
        BottomNavItem(R.string.tab_search, "search", R.drawable.ic_search),
        BottomNavItem(R.string.tab_subscriptions, "subscriptions", R.drawable.ic_subscriptions),
        BottomNavItem(R.string.tab_you, "you", R.drawable.ic_person),
    )

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val currentRoute =
        navController.currentBackStackEntryAsState().value?.destination?.route?.substringBefore("?")
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        BOTTOM_NAV_ITEMS.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = stringResource(item.labelRes),
                    )
                },
                label = {
                    Text(
                        text = stringResource(item.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                selected = currentRoute == item.route,
                colors =
                    NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent,
                        selectedIconColor = MaterialTheme.colorScheme.onBackground,
                        selectedTextColor = MaterialTheme.colorScheme.onBackground,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                alwaysShowLabel = true,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        }
    }
}
```

- [ ] **Step 4: Add the signed-out screens**

`YouScreen.kt`:

```kotlin
package com.example.newtube.ui.you

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.newtube.R
import com.example.newtube.ui.common.ChannelAvatar
import com.example.newtube.ui.shell.TabTopBar

@Composable
fun YouScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize()) {
        TabTopBar(title = stringResource(R.string.tab_you)) {
            IconButton(onClick = { navController.navigate("settings") }) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelAvatar(url = "", name = stringResource(R.string.you_guest), size = 48.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.you_guest), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.you_guest_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        YouRow(R.drawable.ic_history, stringResource(R.string.you_history)) { navController.navigate("history") }
        YouRow(R.drawable.ic_subscriptions, stringResource(R.string.you_subscriptions)) {
            navController.navigate("subscriptions")
        }
        YouRow(R.drawable.ic_settings, stringResource(R.string.you_settings)) {
            navController.navigate("settings")
        }
        YouRow(R.drawable.ic_info, stringResource(R.string.you_about)) { navController.navigate("about") }
    }
}

@Composable
private fun YouRow(iconRes: Int, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.width(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null)
    }
}
```

`SubscriptionsScreen.kt` and `NotificationsScreen.kt` are centred informational surfaces:

```kotlin
@Composable
fun SubscriptionsScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        TabTopBar(title = stringResource(R.string.subscriptions_title))
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.subscriptions_signed_out_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.subscriptions_signed_out_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
```

`NotificationsScreen` uses `DetailTopBar` (back arrow) plus `notifications_signed_out` text. `HistoryScreen` placeholder uses `DetailTopBar` + `EmptyScreen(stringResource(R.string.history_empty))`. `AboutScreen` uses `DetailTopBar`, reads the version with `LocalContext.current.packageManager.getPackageInfo(LocalContext.current.packageName, 0).versionName` (the `:app` module does not enable `buildConfig`, so there is no app `BuildConfig.VERSION_NAME`), and reads the instance URL from `com.example.newtube.data.BuildConfig.INVIDIOUS_BASE_URL`.

- [ ] **Step 5: Rewrite `MainActivity`'s shell**

Remove the old `TAB_ROUTES`, `BottomNavigationBar` and `BottomNavItem` from `MainActivity.kt`. Keep `onCreate`/PiP from Task 3. `MainScreen` becomes:

```kotlin
private val TAB_ROUTES = setOf("home", "shorts", "search", "subscriptions", "you")

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val baseRoute = backStackEntry?.destination?.route?.substringBefore("?")
    val inPip = LocalPipMode.current
    val showBottomBar = !inPip && baseRoute in TAB_ROUTES

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) BottomNavigationBar(navController)
        },
    ) { innerPadding ->
        NavGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
```

Import `BottomNavigationBar` from `com.example.newtube.ui.shell`.

- [ ] **Step 6: Extend `NavGraph`**

```kotlin
composable("home") { HomeScreen(navController) }
composable("shorts") { ShortsScreen(navController) }
composable(
    route = "search?focus={focus}",
    arguments = listOf(navArgument("focus") { type = NavType.BoolType; defaultValue = false }),
) { entry ->
    SearchScreen(
        navController = navController,
        focus = entry.arguments?.getBoolean("focus") ?: false,
    )
}
composable("subscriptions") { SubscriptionsScreen() }
composable("you") { YouScreen(navController) }
composable("history") { HistoryScreen(navController) }
composable("settings") { SettingsScreen(navController) }
composable("about") { AboutScreen(navController) }
composable("notifications") { NotificationsScreen(navController) }
```

Keep `channel`, `watch` and `comments` routes unchanged.

- [ ] **Step 7: Restyle Search and swap the card component**

`SearchScreen(navController: NavController, focus: Boolean = false)`: put the existing `OutlinedTextField` in a `Row` at the top with `Modifier.fillMaxWidth().padding(horizontal = 12.dp)`, add a `FocusRequester` and `LaunchedEffect(focus) { if (focus) focusRequester.requestFocus() }`, leading `Icons.Filled.Search`, trailing `Icons.Filled.Close` when the query is not blank. Replace `VideoListItem` with `VideoCard` and show five skeleton placeholders while loading (component arrives in Task 9; for this task use `CircularProgressIndicator` as before, then Task 9 swaps in skeletons).

In `ChannelScreen` and `WatchScreen`, replace every `VideoListItem(...)` with `VideoCard(...)`.

- [ ] **Step 8: Build and run the tests**

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat test
```

Manual check: five tabs are visible at 360 dp, labels fit, no selected pill, tapping You/Subscriptions/Notifications works, and no route crashes.

- [ ] **Step 9: Commit**

```powershell
git add app/src/main/res/drawable app/src/main/res/values/strings.xml app/src/main/java/com/example/newtube/MainActivity.kt app/src/main/java/com/example/newtube/ui/nav/NavGraph.kt app/src/main/java/com/example/newtube/ui/shell/BottomNavigationBar.kt app/src/main/java/com/example/newtube/ui/you app/src/main/java/com/example/newtube/ui/subscriptions app/src/main/java/com/example/newtube/ui/notifications app/src/main/java/com/example/newtube/ui/history app/src/main/java/com/example/newtube/ui/about app/src/main/java/com/example/newtube/ui/search/SearchScreen.kt app/src/main/java/com/example/newtube/ui/channel/ChannelScreen.kt app/src/main/java/com/example/newtube/ui/video/WatchScreen.kt
git commit -m "feat: add five-tab shell and signed-out account placeholders"
```

## Task 9: Home redesign — top bar, Ukrainian chips, shelf, skeletons and cards

**Files:**
- Create: `app/src/main/java/com/example/newtube/ui/shell/HomeTopBar.kt`
- Create: `app/src/main/java/com/example/newtube/ui/home/HomeCategoryLabels.kt`
- Create: `app/src/main/java/com/example/newtube/ui/common/ShortsTile.kt`
- Create: `app/src/main/java/com/example/newtube/ui/common/Skeletons.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/home/HomeScreen.kt`
- Modify: `domain/src/main/java/com/example/newtube/model/HomeCategory.kt`
- Delete: `app/src/main/java/com/example/newtube/ui/common/VideoListItem.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: `HomeTopBar(isRefreshing, onRefresh, onNotifications, onSearch)`.
- Produces: `@StringRes fun HomeCategory.labelRes(): Int`.
- Consumes: `VideoCard`, `VideoCardSkeleton`, `ShortsTile`, `HomeViewModel.shortsShelf`.

- [ ] **Step 1: Add the strings**

```xml
<string name="action_notifications">Сповіщення</string>
<string name="section_shorts">Shorts</string>
<string name="home_empty">Тут поки немає відео</string>
<string name="retry">Повторити</string>

<string name="category_all">Усі</string>
<string name="category_music">Музика</string>
<string name="category_gaming">Ігри</string>
<string name="category_movies">Фільми</string>
<string name="category_sports">Спорт</string>
<string name="category_news">Новини</string>
<string name="category_technology">Технології</string>
```

- [ ] **Step 2: Move category labels to the app layer**

Replace `HomeCategory.kt` with a pure enum:

```kotlin
package com.example.newtube.model

/** Top-level feed tabs shown on the Home screen; labels live in :app. */
enum class HomeCategory {
    ALL,
    MUSIC,
    GAMING,
    MOVIES,
    SPORTS,
    NEWS,
    TECHNOLOGY,
}
```

Create `HomeCategoryLabels.kt`:

```kotlin
package com.example.newtube.ui.home

import androidx.annotation.StringRes
import com.example.newtube.R
import com.example.newtube.model.HomeCategory

@StringRes
fun HomeCategory.labelRes(): Int =
    when (this) {
        HomeCategory.ALL -> R.string.category_all
        HomeCategory.MUSIC -> R.string.category_music
        HomeCategory.GAMING -> R.string.category_gaming
        HomeCategory.MOVIES -> R.string.category_movies
        HomeCategory.SPORTS -> R.string.category_sports
        HomeCategory.NEWS -> R.string.category_news
        HomeCategory.TECHNOLOGY -> R.string.category_technology
    }
```

Update `HomeScreen`'s chip label to `stringResource(category.labelRes())`.

- [ ] **Step 3: Add the Home top bar and logo**

`HomeTopBar.kt`:

```kotlin
package com.example.newtube.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.newtube.R

@Composable
fun HomeTopBar(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onNotifications: () -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp).weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "NewTube",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        IconButton(onClick = onRefresh, enabled = !isRefreshing) {
            if (isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
            }
        }
        IconButton(onClick = onNotifications) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = stringResource(R.string.action_notifications),
            )
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
        }
    }
}
```

If `Notifications` or `PlayArrow` is missing from `material-icons-core` at compile time, add a vector drawable with the Material path and use `painterResource`; do not add the extended icons artifact.

- [ ] **Step 4: Add the Shorts tile**

`ShortsTile.kt`:

```kotlin
package com.example.newtube.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.newtube.model.Video

@Composable
fun ShortsTile(
    video: Video,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .width(150.dp)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick(video.id) },
    ) {
        Image(
            painter = rememberAsyncImagePainter(video.thumbnailUrl),
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        ),
                    ),
        )
        Text(
            text = video.title,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
    }
}
```

- [ ] **Step 5: Add skeleton placeholders**

`Skeletons.kt`:

```kotlin
package com.example.newtube.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

@Composable
fun VideoCardSkeleton(modifier: Modifier = Modifier) {
    val alpha = skeletonAlpha()
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
        )
        Row(modifier = Modifier.padding(top = 12.dp)) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                SkeletonLine(widthFraction = 0.9f, height = 14.dp, alpha = alpha)
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonLine(widthFraction = 0.6f, height = 12.dp, alpha = alpha)
            }
        }
    }
}

@Composable
private fun SkeletonLine(widthFraction: Float, height: androidx.compose.ui.unit.Dp, alpha: Float) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth(widthFraction)
                .height(height)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
    )
}

@Composable
private fun skeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "skeleton-alpha",
        )
    return alpha
}
```

- [ ] **Step 6: Rewrite `HomeScreen`**

Structure:

```kotlin
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val shelf by viewModel.shortsShelf.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(selectedCategory) {
        listState.scrollToItem(0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HomeTopBar(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            onNotifications = { navController.navigate("notifications") },
            onSearch = { navController.navigate("search?focus=true") },
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item(key = "categories") {
                CategoryChips(
                    selected = selectedCategory,
                    onSelect = viewModel::selectCategory,
                )
            }
            if (shelf.isNotEmpty()) {
                item(key = "shorts-shelf") {
                    ShortsShelf(
                        videos = shelf,
                        onOpen = {
                            // Task 10 upgrades this to a positioned deep link.
                            navController.navigate("shorts") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }
            when (val state = uiState) {
                UiState.Loading -> items(count = 5) { VideoCardSkeleton() }
                is UiState.Error ->
                    item(key = "error") {
                        HomeMessage(message = state.message, onRetry = viewModel::load)
                    }
                is UiState.Success ->
                    if (state.data.isEmpty()) {
                        item(key = "empty") {
                            HomeMessage(message = stringResource(R.string.home_empty))
                        }
                    } else {
                        items(state.data, key = { it.id }) { video ->
                            VideoCard(
                                video = video,
                                onClick = { navController.navigate("watch/$it") },
                                onChannelClick = { navController.navigate("channel/$it") },
                            )
                        }
                    }
            }
            item(key = "bottom-spacer") { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
```

`CategoryChips` uses `LazyRow` with `contentPadding = PaddingValues(horizontal = 12.dp)`, `horizontalArrangement = Arrangement.spacedBy(8.dp)` and:

```kotlin
FilterChip(
    selected = category == selected,
    onClick = { onSelect(category) },
    label = { Text(stringResource(category.labelRes())) },
    shape = CircleShape,
    border = null,
    colors =
        FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.onBackground,
            selectedLabelColor = MaterialTheme.colorScheme.background,
        ),
)
```

`ShortsShelf`:

```kotlin
@Composable
private fun ShortsShelf(videos: List<Video>, onOpen: (String) -> Unit) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shorts),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.section_shorts),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(videos, key = { it.id }) { video ->
                ShortsTile(video = video, onClick = onOpen)
            }
        }
    }
}
```

`HomeMessage` is a centred `Column` with the message and an optional `TextButton` labelled `R.string.retry`. Remove the temporary Settings gear from Home; Settings is reachable from You now.

- [ ] **Step 7: Delete the old card component**

```powershell
Remove-Item app\src\main\java\com\example\newtube\ui\common\VideoListItem.kt
```

Verify no references remain:

```powershell
rg "VideoListItem" app domain data
```

- [ ] **Step 8: Build, test and manually check**

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat test
```

On the device: dark Home with logo, chips scroll horizontally, the shelf appears only when it has clips, five skeleton cards replace the spinner, cards show avatar/duration/metadata, and a broken trending item shows only the channel name.

- [ ] **Step 9: Commit**

```powershell
git add app/src/main/java/com/example/newtube/ui/shell/HomeTopBar.kt app/src/main/java/com/example/newtube/ui/home app/src/main/java/com/example/newtube/ui/common/ShortsTile.kt app/src/main/java/com/example/newtube/ui/common/Skeletons.kt app/src/main/java/com/example/newtube/ui/common/VideoListItem.kt domain/src/main/java/com/example/newtube/model/HomeCategory.kt app/src/main/res/values/strings.xml
git commit -m "feat: redesign Home with shelf, skeletons and YouTube-style cards"
```

## Task 10: Open the Shorts tab at a specific clip

**Files:**
- Modify: `app/src/main/java/com/example/newtube/ui/shorts/ShortsViewModel.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/shorts/ShortsScreen.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/nav/NavGraph.kt`
- Test: `app/src/test/java/com/example/newtube/ui/shorts/ShortsViewModelTest.kt`

**Interfaces:**
- Produces: `ShortsViewModel.startFrom(videoId: String)`, `requestedStartId: StateFlow<String?>`, `consumeStartRequest()`.
- Consumes: `VideoRepository.getVideoById(videoId)`.
- Route: `shorts?videoId={videoId}` with a nullable string argument and `defaultValue = null`, so a plain `"shorts"` navigation still matches.

- [ ] **Step 1: Write the failing ViewModel tests**

```kotlin
@Test
fun `startFrom prepends a clip that is missing from the feed`() =
    runTest {
        val feed = video("feed1")
        val requested = video("requested")
        coEvery { repository.getShorts(1) } returns listOf(feed)
        coEvery { repository.getShorts(2) } returns emptyList()
        coEvery { repository.getVideoById("requested") } returns requested

        val viewModel = ShortsViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.startFrom("requested")
        testDispatcher.scheduler.advanceUntilIdle()

        val data = (viewModel.uiState.value as UiState.Success).data
        assertEquals("requested", data.first().id)
        assertEquals(listOf("requested", "feed1"), data.map { it.id })
    }

@Test
fun `startFrom does not fetch a clip that is already in the feed`() =
    runTest {
        coEvery { repository.getShorts(1) } returns listOf(video("feed1"))
        coEvery { repository.getShorts(2) } returns emptyList()

        val viewModel = ShortsViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.startFrom("feed1")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { repository.getVideoById(any()) }
    }

@Test
fun `startFrom while the feed is loading is applied after reload`() =
    runTest {
        val firstPage = CompletableDeferred<List<Video>>()
        coEvery { repository.getShorts(1) } coAnswers { firstPage.await() }
        coEvery { repository.getVideoById("requested") } returns video("requested")

        val viewModel = ShortsViewModel(repository)
        viewModel.startFrom("requested")
        firstPage.complete(listOf(video("feed1")))
        testDispatcher.scheduler.advanceUntilIdle()

        val data = (viewModel.uiState.value as UiState.Success).data
        assertEquals("requested", data.first().id)
    }
```

Add `import kotlinx.coroutines.CompletableDeferred` and `io.mockk.coVerify`.

- [ ] **Step 2: Run and confirm the tests fail**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.ui.shorts.ShortsViewModelTest"
```

- [ ] **Step 3: Implement `startFrom` and apply it during reload**

In `ShortsViewModel`:

```kotlin
private val _requestedStartId = MutableStateFlow<String?>(null)
val requestedStartId: StateFlow<String?> = _requestedStartId

/** The last id handed to the pager; keeps a restored tab from jumping twice. */
private var handledStartId: String? = null

/**
 * A Home shelf tap asks the tab to open at this clip. If the clip is not in
 * the loaded feed yet, fetch it once and prepend it, so the pager can start at
 * index 0 without waiting for the whole Shorts rotation.
 */
fun startFrom(videoId: String) {
    if (videoId == handledStartId) return
    if (_requestedStartId.value == videoId) return
    _requestedStartId.value = videoId
    val current = _uiState.value as? UiState.Success ?: return
    if (current.data.any { it.id == videoId }) return
    viewModelScope.launch { prependRequested() }
}

/** Called by the pager after it jumped to [requestedStartId]. */
fun consumeStartRequest() {
    handledStartId = _requestedStartId.value
    _requestedStartId.value = null
}

private suspend fun prependRequested() {
    val id = _requestedStartId.value ?: return
    val current = _uiState.value as? UiState.Success ?: return
    if (current.data.any { it.id == id }) return
    val requested = runCatching { repository.getVideoById(id) }.getOrNull() ?: return
    val latest = _uiState.value as? UiState.Success ?: return
    _uiState.value =
        UiState.Success(listOf(requested) + latest.data.filterNot { it.id == id })
}
```

In `reload()`, after `collected` is deduped and before assigning success, apply the requested clip:

```kotlin
var result = collected.distinctBy { it.id }
val requestedId = _requestedStartId.value
if (requestedId != null && result.none { it.id == requestedId }) {
    runCatching { repository.getVideoById(requestedId) }.getOrNull()?.let {
        result = listOf(it) + result
    }
}
_uiState.value = UiState.Success(result)
```

- [ ] **Step 4: Wire the route and the pager**

`NavGraph.kt`:

```kotlin
composable(
    route = "shorts?videoId={videoId}",
    arguments =
        listOf(
            navArgument("videoId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
) { entry ->
    ShortsScreen(
        navController = navController,
        startVideoId = entry.arguments?.getString("videoId"),
    )
}
```

Keep the bottom-nav route string `"shorts"`; `defaultValue = null` makes the plain route match.

`ShortsScreen`:

```kotlin
@Composable
fun ShortsScreen(
    navController: NavController,
    startVideoId: String? = null,
    viewModel: ShortsViewModel = hiltViewModel(),
) {
    LaunchedEffect(startVideoId) {
        if (startVideoId != null) viewModel.startFrom(startVideoId)
    }
    /* existing state switch passes viewModel to ShortsPager as before */
}
```

`ShortsPager` observes the request rather than the navigation argument, so the jump is consumed once and survives feed reloads:

```kotlin
val requestedStartId by viewModel.requestedStartId.collectAsState()
val targetIndex = videos.indexOfFirst { it.id == requestedStartId }
val pagerState =
    rememberPagerState(
        initialPage = targetIndex.coerceAtLeast(0),
        pageCount = { videos.size },
    )

LaunchedEffect(requestedStartId, targetIndex, videos.size) {
    if (requestedStartId != null && targetIndex >= 0) {
        pagerState.scrollToPage(targetIndex)
        viewModel.consumeStartRequest()
    }
}
```

A restored tab entry re-passes the same navigation argument, but `startFrom` ignores it once `handledStartId` matches, so the pager does not jump again.

- [ ] **Step 5: Run the tests and build**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.ui.shorts.ShortsViewModelTest"
.\gradlew.bat :app:assembleDebug
```

Manual check: tapping a Home shelf tile opens Shorts on that clip; going back and returning to the Shorts tab does not jump again.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/example/newtube/ui/shorts app/src/main/java/com/example/newtube/ui/nav/NavGraph.kt app/src/test/java/com/example/newtube/ui/shorts/ShortsViewModelTest.kt
git commit -m "feat: open Shorts at a requested clip from the Home shelf"
```

# Phase 3 — Local history and complete settings

## Task 11: Local history repository and clearing resume positions

**Files:**
- Create: `domain/src/main/java/com/example/newtube/model/WatchHistoryEntry.kt`
- Create: `domain/src/main/java/com/example/newtube/repository/HistoryRepository.kt`
- Create: `data/src/main/java/com/example/newtube/implementation/HistoryRepositoryImpl.kt`
- Create: `data/src/main/java/com/example/newtube/di/HistoryModule.kt`
- Modify: `domain/src/main/java/com/example/newtube/repository/PlaybackPositionRepository.kt`
- Modify: `data/src/main/java/com/example/newtube/preferences/DataStorePlaybackPositionRepository.kt`
- Test: `data/src/test/java/com/example/newtube/implementation/HistoryRepositoryImplTest.kt`

**Interfaces:**
- Produces: `data class WatchHistoryEntry(videoId, title, channelName, channelId, authorAvatarUrl, thumbnailUrl, durationMs, watchedAt)`.
- Produces: `interface HistoryRepository { fun observeLocal(limit: Int = 500): Flow<List<WatchHistoryEntry>>; suspend fun record(video: Video); suspend fun clearLocal() }`.
- Produces: `suspend fun PlaybackPositionRepository.clearAll()`.
- Consumes: `WatchHistoryDao` and `WatchHistoryEntity` from Task 4.

- [ ] **Step 1: Write the failing repository tests**

`data/src/test/java/com/example/newtube/implementation/HistoryRepositoryImplTest.kt`:

```kotlin
package com.example.newtube.implementation

import com.example.newtube.database.WatchHistoryDao
import com.example.newtube.database.WatchHistoryEntity
import com.example.newtube.model.Video
import com.example.newtube.model.WatchHistoryEntry
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRepositoryImplTest {
    private val dao: WatchHistoryDao = mockk(relaxed = true)
    private val repository = HistoryRepositoryImpl(dao)

    @Test
    fun `observeLocal maps rows to domain entries`() =
        runTest {
            every { dao.observeRecent(500) } returns flowOf(listOf(entity("v1", watchedAt = 42)))

            assertEquals(
                listOf(
                    WatchHistoryEntry(
                        videoId = "v1",
                        title = "Video v1",
                        channelName = "Channel",
                        channelId = "UC1",
                        authorAvatarUrl = "https://example.com/avatar.jpg",
                        thumbnailUrl = "https://example.com/thumb.jpg",
                        durationMs = 30_000,
                        watchedAt = 42,
                    ),
                ),
                repository.observeLocal(500).first(),
            )
        }

    @Test
    fun `record converts seconds to millis and trims to 500 rows`() =
        runTest {
            val before = System.currentTimeMillis()
            val captured = slot<WatchHistoryEntity>()
            coJustRun { dao.upsert(capture(captured)) }
            coJustRun { dao.trim(any()) }

            repository.record(video("v1", duration = 120))

            val entity = captured.captured
            assertEquals("v1", entity.videoId)
            assertEquals(120_000L, entity.durationMs)
            assertTrue(entity.watchedAt in before..System.currentTimeMillis())
            coVerify { dao.trim(500) }
        }

    @Test
    fun `clearLocal deletes every row`() =
        runTest {
            coJustRun { dao.clear() }

            repository.clearLocal()

            coVerify { dao.clear() }
        }

    private fun entity(id: String, watchedAt: Long) =
        WatchHistoryEntity(
            videoId = id,
            title = "Video $id",
            channelName = "Channel",
            channelId = "UC1",
            authorAvatarUrl = "https://example.com/avatar.jpg",
            thumbnailUrl = "https://example.com/thumb.jpg",
            durationMs = 30_000,
            watchedAt = watchedAt,
        )

    private fun video(id: String, duration: Long) =
        Video(
            id = id,
            title = "Video $id",
            channelName = "Channel",
            channelId = "UC1",
            thumbnailUrl = "https://example.com/thumb.jpg",
            duration = duration,
            views = 10,
            uploadedAt = "1 day ago",
            isLive = false,
            authorAvatarUrl = "https://example.com/avatar.jpg",
        )
}
```

- [ ] **Step 2: Run and confirm it fails to compile**

```powershell
.\gradlew.bat :data:testDebugUnitTest --tests "com.example.newtube.implementation.HistoryRepositoryImplTest"
```

- [ ] **Step 3: Add the domain model and contract**

`WatchHistoryEntry.kt`:

```kotlin
package com.example.newtube.model

/** One locally watched video. No playback position: DataStore owns resume state. */
data class WatchHistoryEntry(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val authorAvatarUrl: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val watchedAt: Long,
)
```

`HistoryRepository.kt`:

```kotlin
package com.example.newtube.repository

import com.example.newtube.model.Video
import com.example.newtube.model.WatchHistoryEntry
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun observeLocal(limit: Int = 500): Flow<List<WatchHistoryEntry>>

    /** Upserts one row per video id and trims the table to the newest 500 rows. */
    suspend fun record(video: Video)

    suspend fun clearLocal()
}
```

- [ ] **Step 4: Implement the repository**

`HistoryRepositoryImpl.kt`:

```kotlin
package com.example.newtube.implementation

import com.example.newtube.database.WatchHistoryDao
import com.example.newtube.database.WatchHistoryEntity
import com.example.newtube.model.Video
import com.example.newtube.model.WatchHistoryEntry
import com.example.newtube.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val dao: WatchHistoryDao,
) : HistoryRepository {
    override fun observeLocal(limit: Int): Flow<List<WatchHistoryEntry>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun record(video: Video) {
        dao.upsert(
            WatchHistoryEntity(
                videoId = video.id,
                title = video.title,
                channelName = video.channelName,
                channelId = video.channelId,
                authorAvatarUrl = video.authorAvatarUrl,
                thumbnailUrl = video.thumbnailUrl,
                durationMs = video.duration * 1_000L,
                watchedAt = System.currentTimeMillis(),
            ),
        )
        dao.trim(HISTORY_LIMIT)
    }

    override suspend fun clearLocal() {
        dao.clear()
    }

    private fun WatchHistoryEntity.toDomain(): WatchHistoryEntry =
        WatchHistoryEntry(
            videoId = videoId,
            title = title,
            channelName = channelName,
            channelId = channelId,
            authorAvatarUrl = authorAvatarUrl,
            thumbnailUrl = thumbnailUrl,
            durationMs = durationMs,
            watchedAt = watchedAt,
        )

    private companion object {
        const val HISTORY_LIMIT = 500
    }
}
```

- [ ] **Step 5: Bind it with Hilt**

`HistoryModule.kt`:

```kotlin
package com.example.newtube.di

import com.example.newtube.implementation.HistoryRepositoryImpl
import com.example.newtube.repository.HistoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HistoryModule {
    @Binds
    @Singleton
    abstract fun bindHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository
}
```

- [ ] **Step 6: Add `clearAll()` to playback positions**

`PlaybackPositionRepository.kt`:

```kotlin
/** Deletes every stored resume position; called when the user clears history. */
suspend fun clearAll()
```

`DataStorePlaybackPositionRepository.kt`:

```kotlin
override suspend fun clearAll() {
    context.playbackDataStore.edit { it.clear() }
}
```

- [ ] **Step 7: Run the data tests**

```powershell
.\gradlew.bat :data:testDebugUnitTest
```

- [ ] **Step 8: Commit**

```powershell
git add domain/src/main/java/com/example/newtube/model/WatchHistoryEntry.kt domain/src/main/java/com/example/newtube/repository/HistoryRepository.kt domain/src/main/java/com/example/newtube/repository/PlaybackPositionRepository.kt data/src/main/java/com/example/newtube/implementation/HistoryRepositoryImpl.kt data/src/main/java/com/example/newtube/di/HistoryModule.kt data/src/main/java/com/example/newtube/preferences/DataStorePlaybackPositionRepository.kt data/src/test/java/com/example/newtube/implementation/HistoryRepositoryImplTest.kt
git commit -m "feat: add local watch history repository"
```

## Task 12: Record watches from Watch and Shorts

**Files:**
- Modify: `app/src/main/java/com/example/newtube/ui/video/WatchViewModel.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/shorts/ShortsViewModel.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/shorts/ShortsScreen.kt`
- Test: `app/src/test/java/com/example/newtube/ui/video/WatchViewModelTest.kt`
- Test: `app/src/test/java/com/example/newtube/ui/shorts/ShortsViewModelTest.kt`

**Interfaces:**
- Produces: `WatchViewModel` records on successful load when `record_history` is on.
- Produces: `ShortsViewModel.onPageSettled(video: Video)` which records after a 3-second settled delay and cancels on the next call.
- Consumes: `HistoryRepository`, `SettingsRepository`.

- [ ] **Step 1: Update the Watch tests first**

Extend the existing `@Before setUp()` (do not add a second one) with the mocks and default stubs:

```kotlin
private val history: HistoryRepository = mockk(relaxed = true)
private val settings: SettingsRepository = mockk()

@Before
fun setUp() {
    Dispatchers.setMain(testDispatcher)
    every { settings.settings } returns flowOf(AppSettings())
    coJustRun { history.record(any()) }
}
```

Every `WatchViewModel(repository, playbackPositions)` becomes `WatchViewModel(repository, playbackPositions, history, settings)`.

Add:

```kotlin
@Test
fun `a successful load records the video once`() =
    runTest {
        val details = details()
        coEvery { repository.getVideoDetails("v1") } returns details
        coEvery { playbackPositions.positionFor("v1") } returns 0L

        val viewModel = WatchViewModel(repository, playbackPositions, history, settings)
        viewModel.load("v1")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { history.record(details.video) }
    }

@Test
fun `recording is skipped when the setting is off`() =
    runTest {
        every { settings.settings } returns flowOf(AppSettings(recordHistory = false))
        coEvery { repository.getVideoDetails("v1") } returns details()
        coEvery { playbackPositions.positionFor("v1") } returns 0L

        val viewModel = WatchViewModel(repository, playbackPositions, history, settings)
        viewModel.load("v1")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { history.record(any()) }
    }

@Test
fun `a history failure does not break the watch screen`() =
    runTest {
        coEvery { repository.getVideoDetails("v1") } returns details()
        coEvery { playbackPositions.positionFor("v1") } returns 0L
        coEvery { history.record(any()) } throws IllegalStateException("db")

        val viewModel = WatchViewModel(repository, playbackPositions, history, settings)
        viewModel.load("v1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is UiState.Success)
    }
```

Imports: `AppSettings`, `HistoryRepository`, `SettingsRepository`, `coJustRun`, `coVerify`, `every`, `flowOf`.

- [ ] **Step 2: Run and confirm the Watch tests fail**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.ui.video.WatchViewModelTest"
```

- [ ] **Step 3: Record in `WatchViewModel`**

Constructor:

```kotlin
class WatchViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val playbackPositions: PlaybackPositionRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel()
```

In `loadInternal`, after publishing `UiState.Success`, add:

```kotlin
runCatching {
    if (settingsRepository.settings.first().recordHistory) {
        historyRepository.record(details.video)
    }
}
```

Imports: `HistoryRepository`, `SettingsRepository`, `kotlinx.coroutines.flow.first`.

- [ ] **Step 4: Update the Shorts tests**

The constructor becomes `ShortsViewModel(repository, settings, history)`; replace every existing `ShortsViewModel(repository)` call. Extend the existing `@Before setUp()` (do not add a second one) and add the mocks:

```kotlin
private val history: HistoryRepository = mockk(relaxed = true)
private val settings: SettingsRepository = mockk()

@Before
fun setUp() {
    Dispatchers.setMain(testDispatcher)
    every { settings.settings } returns flowOf(AppSettings())
    coJustRun { history.record(any()) }
}
```

Imports: `AppSettings`, `HistoryRepository`, `SettingsRepository`, `coJustRun`, `every`, `flowOf`.

Add:

```kotlin
@Test
fun `a settled short is recorded after three seconds`() =
    runTest {
        coEvery { repository.getShorts(1) } returns listOf(video("1"))
        coEvery { repository.getShorts(2) } returns emptyList()
        val short = video("1")

        val viewModel = ShortsViewModel(repository, settings, history)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPageSettled(short)
        testDispatcher.scheduler.advanceTimeBy(2_999)
        coVerify(exactly = 0) { history.record(any()) }

        testDispatcher.scheduler.advanceTimeBy(2)
        coVerify(exactly = 1) { history.record(short) }
    }

@Test
fun `swiping before three seconds cancels the pending record`() =
    runTest {
        coEvery { repository.getShorts(1) } returns listOf(video("1"), video("2"))
        coEvery { repository.getShorts(2) } returns emptyList()
        val first = video("1")
        val second = video("2")

        val viewModel = ShortsViewModel(repository, settings, history)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPageSettled(first)
        testDispatcher.scheduler.advanceTimeBy(2_000)
        viewModel.onPageSettled(second)
        testDispatcher.scheduler.advanceTimeBy(2_000)
        coVerify(exactly = 0) { history.record(any()) }

        testDispatcher.scheduler.advanceTimeBy(1_001)
        coVerify(exactly = 1) { history.record(second) }
    }
```

- [ ] **Step 5: Implement the 3-second recording rule**

In `ShortsViewModel`:

```kotlin
private var historyJob: Job? = null

fun onPageSettled(video: Video) {
    historyJob?.cancel()
    historyJob =
        viewModelScope.launch {
            delay(SHORTS_HISTORY_DELAY_MS)
            runCatching {
                if (settingsRepository.settings.first().recordHistory) {
                    historyRepository.record(video)
                }
            }
        }
}

/** Leaving the Shorts tab must not record a clip that was only passing by. */
fun cancelPendingRecord() {
    historyJob?.cancel()
}

private companion object {
    const val SHORTS_HISTORY_DELAY_MS = 3_000L
}
```

Add `delay`, `Job`, `first`, `HistoryRepository`, `SettingsRepository` imports. Keep the existing companion constants in the same companion object.

- [ ] **Step 6: Call `onPageSettled` from the pager**

Use the settled page, not `currentPage`: `currentPage` changes mid-swipe and would start a record for a page the user is only passing through. Compose Foundation 1.6 (BOM 2024.03.00) exposes `pagerState.settledPage`.

```kotlin
LaunchedEffect(pagerState.settledPage, videos.size) {
    val video = videos.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
    viewModel.onPageSettled(video)
}
```

Keep `ensurePlayback(video.id)` on the existing `currentPage` effect (it preloads the incoming clip), and add this second effect for history.

In `ShortsScreen`, cancel a pending record when the tab leaves composition:

```kotlin
DisposableEffect(Unit) {
    onDispose { viewModel.cancelPendingRecord() }
}
```

Import `androidx.compose.runtime.DisposableEffect`.

- [ ] **Step 7: Run the tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.ui.video.WatchViewModelTest" --tests "com.example.newtube.ui.shorts.ShortsViewModelTest"
```

Manual check: open a video, back out, open History; the row appears. Re-open the same video; there is still one row with a newer time. Clear history and confirm the resume position is gone (the player starts at 0).

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/java/com/example/newtube/ui/video/WatchViewModel.kt app/src/main/java/com/example/newtube/ui/shorts app/src/test/java/com/example/newtube/ui/video/WatchViewModelTest.kt app/src/test/java/com/example/newtube/ui/shorts/ShortsViewModelTest.kt
git commit -m "feat: record local watch history from Watch and Shorts"
```

## Task 13: History screen backed by Room

**Files:**
- Create: `app/src/main/java/com/example/newtube/ui/history/HistoryViewModel.kt`
- Replace: `app/src/main/java/com/example/newtube/ui/history/HistoryScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/example/newtube/ui/history/HistoryViewModelTest.kt`

**Interfaces:**
- Produces: `HistoryViewModel.uiState: StateFlow<UiState<List<WatchHistoryEntry>>>`.
- Consumes: `HistoryRepository.observeLocal()`.

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
package com.example.newtube.ui.history

import com.example.newtube.model.WatchHistoryEntry
import com.example.newtube.repository.HistoryRepository
import com.example.newtube.ui.common.UiState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val repository: HistoryRepository = mockk()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `history starts loading and then exposes rows`() =
        runTest {
            val flow = MutableStateFlow<List<WatchHistoryEntry>>(emptyList())
            every { repository.observeLocal() } returns flow

            val viewModel = HistoryViewModel(repository)
            assertTrue(viewModel.uiState.value is UiState.Loading)

            flow.value = listOf(entry("v1"))
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                UiState.Success(listOf(entry("v1"))),
                viewModel.uiState.first { it is UiState.Success },
            )
        }

    private fun entry(id: String) =
        WatchHistoryEntry(
            videoId = id,
            title = "Video $id",
            channelName = "Channel",
            channelId = "UC1",
            authorAvatarUrl = "",
            thumbnailUrl = "https://example.com/$id.jpg",
            durationMs = 30_000,
            watchedAt = 1,
        )
}
```

- [ ] **Step 2: Run and confirm it fails to compile**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.ui.history.HistoryViewModelTest"
```

- [ ] **Step 3: Implement the ViewModel**

```kotlin
package com.example.newtube.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newtube.model.WatchHistoryEntry
import com.example.newtube.repository.HistoryRepository
import com.example.newtube.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    historyRepository: HistoryRepository,
) : ViewModel() {
    val uiState: StateFlow<UiState<List<WatchHistoryEntry>>> =
        historyRepository
            .observeLocal()
            .map<List<WatchHistoryEntry>, UiState<List<WatchHistoryEntry>>> { UiState.Success(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UiState.Loading,
            )
}
```

- [ ] **Step 4: Build the local history list**

Replace the placeholder screen:

```kotlin
@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(
            title = stringResource(R.string.history_title),
            onBack = { navController.popBackStack() },
        )
        when (val current = state) {
            UiState.Loading -> LoadingScreen()
            // The local Room flow does not fail; render an error defensively
            // without a dead "retry" button.
            is UiState.Error -> EmptyScreen(message = current.message)
            is UiState.Success ->
                if (current.data.isEmpty()) {
                    EmptyScreen(
                        message = stringResource(R.string.history_empty),
                        hint = stringResource(R.string.history_empty_body),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(current.data, key = { it.videoId }) { entry ->
                            HistoryRow(
                                entry = entry,
                                onClick = { navController.navigate("watch/${entry.videoId}") },
                            )
                        }
                    }
                }
        }
    }
}
```

`HistoryRow`:

```kotlin
@Composable
private fun HistoryRow(entry: WatchHistoryEntry, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Image(
            painter = rememberAsyncImagePainter(entry.thumbnailUrl),
            contentDescription = entry.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(160.dp).height(90.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.channelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    DateUtils.getRelativeTimeSpanString(
                        entry.watchedAt,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

`StateScreens.kt`'s `EmptyScreen` currently takes only `message`. Extend it with an optional hint and keep every existing call site valid:

```kotlin
@Composable
fun EmptyScreen(
    message: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (hint != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
```

`HistoryRow` needs `import android.text.format.DateUtils`.

- [ ] **Step 5: Run tests and build**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.ui.history.HistoryViewModelTest"
.\gradlew.bat :app:assembleDebug
```

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/example/newtube/ui/history app/src/main/res/values/strings.xml app/src/test/java/com/example/newtube/ui/history app/src/main/java/com/example/newtube/ui/common/StateScreens.kt
git commit -m "feat: add local watch history screen"
```

## Task 14: Complete the Settings screen

**Files:**
- Modify: `app/src/main/java/com/example/newtube/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/settings/SettingsScreen.kt`
- Create: `app/src/main/java/com/example/newtube/ui/settings/SettingsComponents.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/settings/SettingsLabels.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/example/newtube/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Produces: `SettingsViewModel.hasLocalHistory: StateFlow<Boolean>`, `setRecordHistory`, `setUseRemoteHistory`, `setDefaultQuality`, `clearHistory()`.
- Consumes: `HistoryRepository`, `PlaybackPositionRepository`.

- [ ] **Step 1: Extend the failing ViewModel tests**

Extend the existing `@Before setUp()` (do not add a second one) with the mocks and default stubs:

```kotlin
private val history: HistoryRepository = mockk(relaxed = true)
private val positions: PlaybackPositionRepository = mockk(relaxed = true)

@Before
fun setUp() {
    Dispatchers.setMain(testDispatcher)
    every { history.observeLocal(1) } returns flowOf(emptyList())
}

private fun viewModel() = SettingsViewModel(repository, history, positions)
```

Replace existing `SettingsViewModel(repository)` with `viewModel()`.

Add:

```kotlin
@Test
fun `history flag is exposed`() =
    runTest {
        every { history.observeLocal(1) } returns flowOf(listOf(entry()))
        every { repository.settings } returns flowOf(AppSettings())

        val viewModel = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.hasLocalHistory.value)
    }

@Test
fun `clearing history also clears resume positions`() =
    runTest {
        every { repository.settings } returns flowOf(AppSettings())

        val viewModel = viewModel()
        viewModel.clearHistory()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { history.clearLocal() }
        coVerify { positions.clearAll() }
    }

@Test
fun `every settings setter delegates`() =
    runTest {
        every { repository.settings } returns flowOf(AppSettings())

        val viewModel = viewModel()
        viewModel.setRecordHistory(false)
        viewModel.setUseRemoteHistory(false)
        viewModel.setDefaultQuality(1080)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.setRecordHistory(false) }
        coVerify { repository.setUseRemoteHistory(false) }
        coVerify { repository.setDefaultQuality(1080) }
    }

private fun entry() =
    WatchHistoryEntry(
        videoId = "v1",
        title = "Video",
        channelName = "Channel",
        channelId = "UC1",
        authorAvatarUrl = "",
        thumbnailUrl = "thumb",
        durationMs = 1,
        watchedAt = 1,
    )
```

Imports: `PlaybackPositionRepository`, `HistoryRepository`, `WatchHistoryEntry`, `coVerify`, `flowOf`, `assertTrue`.

- [ ] **Step 2: Run and confirm the tests fail**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.ui.settings.SettingsViewModelTest"
```

- [ ] **Step 3: Extend `SettingsViewModel`**

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val playbackPositions: PlaybackPositionRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = /* unchanged */

    val hasLocalHistory: StateFlow<Boolean> =
        historyRepository
            .observeLocal(limit = 1)
            .map { it.isNotEmpty() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )

    fun setThemeMode(mode: ThemeMode) { /* unchanged */ }

    fun setRecordHistory(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRecordHistory(enabled) }
    }

    fun setUseRemoteHistory(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUseRemoteHistory(enabled) }
    }

    fun setDefaultQuality(maxHeight: Int) {
        viewModelScope.launch { settingsRepository.setDefaultQuality(maxHeight) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearLocal()
            playbackPositions.clearAll()
        }
    }
}
```

- [ ] **Step 4: Add the remaining strings**

```xml
<string name="settings_history">Історія</string>
<string name="settings_record_history">Записувати перегляди</string>
<string name="settings_record_history_summary">Додавати переглянуті відео до локальної історії</string>
<string name="settings_use_remote_history">Показувати історію YouTube</string>
<string name="settings_use_remote_history_disabled">Доступно після входу в Google</string>
<string name="settings_clear_history">Очистити історію переглядів</string>
<string name="settings_clear_history_title">Очистити історію?</string>
<string name="settings_clear_history_body">Буде видалено локальну історію переглядів, а також збережені позиції відтворення.</string>
<string name="settings_clear">Очистити</string>

<string name="settings_playback">Відтворення</string>
<string name="settings_max_quality">Максимальна якість</string>
<string name="quality_480">480p</string>
<string name="quality_720">720p</string>
<string name="quality_1080">1080p</string>

<string name="settings_about">Про застосунок</string>
<string name="about_account_note">Функції акаунта використовують TV OAuth Google; YouTube може будь-коли скасувати сесію.</string>
```

`settings_record_history` etc. are used by the switch rows.

Add the quality label mapping to `SettingsLabels.kt`:

```kotlin
@StringRes
fun qualityLabelRes(maxHeight: Int): Int =
    when (maxHeight) {
        480 -> R.string.quality_480
        1080 -> R.string.quality_1080
        else -> R.string.quality_720
    }
```

- [ ] **Step 5: Add reusable rows and switches**

`SettingsComponents.kt`:

```kotlin
@Composable
fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
```

- [ ] **Step 6: Build the full Settings layout**

Keep the `DetailTopBar`. Sections:

1. **Вигляд** — theme row (existing dialog).
2. **Історія** — `SettingsSwitchRow` for `recordHistory`; `SettingsSwitchRow` for `useRemoteHistory` with `enabled = false` and subtitle `settings_use_remote_history_disabled`; destructive `TextButton` for clear history, enabled only when `hasLocalHistory`, opening a confirm dialog. On confirm call `viewModel.clearHistory()`.
3. **Відтворення** — `SettingsRow(settings_max_quality, subtitle = stringResource(qualityLabelRes(settings.defaultQuality)))` opening a single-choice dialog over `AppSettings.QUALITY_OPTIONS`, each option labelled with `qualityLabelRes(it)`. On select call `viewModel.setDefaultQuality(it)`.
4. **Про застосунок** — `SettingsRow(settings_about) { navController.navigate("about") }`.

The clear dialog body must use `settings_clear_history_body` (it explicitly mentions resume positions). Use `AlertDialog` with a destructive `TextButton`.

- [ ] **Step 7: Run tests, build and manually check**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.ui.settings.SettingsViewModelTest"
.\gradlew.bat :app:assembleDebug
```

Manual: toggling record history stops new history rows; the quality dialog shows 480/720/1080; clear is disabled with empty history and clears both tables/positions when used; About shows version and instance URL.

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/java/com/example/newtube/ui/settings app/src/main/res/values/strings.xml app/src/test/java/com/example/newtube/ui/settings
git commit -m "feat: complete settings for history, quality and about"
```

## Task 15: Plumb the maximum-quality cap through playback

**Files:**
- Modify: `domain/src/main/java/com/example/newtube/repository/VideoRepository.kt`
- Modify: `data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/video/WatchViewModel.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/shorts/ShortsViewModel.kt`
- Test: `app/src/test/java/com/example/newtube/repository/VideoRepositoryImplTest.kt`
- Test: `app/src/test/java/com/example/newtube/ui/video/WatchViewModelTest.kt`
- Test: `app/src/test/java/com/example/newtube/ui/shorts/ShortsViewModelTest.kt`

**Interfaces:**
- Produces: `suspend fun VideoRepository.getVideoDetails(videoId: String, maxHeight: Int = AppSettings.DEFAULT_QUALITY): VideoDetails`.
- Consumes: `AppSettings.defaultQuality` from Settings.
- Rule: choose the best `avc1` track at or below `maxHeight`; if none exists, the best available lower track; progressive tracks above the cap are skipped only when an adaptive/HLS alternative exists.

- [ ] **Step 1: Write the failing repository cap tests**

Add to `VideoRepositoryImplTest`:

```kotlin
@Test
fun `getVideoDetails respects a 480p cap`() =
    runBlocking {
        val dto =
            videoDto(id = "1").copy(
                adaptiveFormats =
                    listOf(
                        adaptiveVideo("https://example.com/360.mp4", "360p", "avc1.4D401E"),
                        adaptiveVideo("https://example.com/480.mp4", "480p", "avc1.4D401F"),
                        adaptiveVideo("https://example.com/720.mp4", "720p", "avc1.4D401F"),
                        adaptiveAudio("https://example.com/audio.m4a", "130668", "mp4a.40.2"),
                    ),
            )
        coEvery { service.getVideoDetails("1") } returns dto
        coJustRun { dao.insertAll(any()) }

        assertEquals(
            PlaybackSource.Merged(
                videoUrl = "https://example.com/480.mp4",
                audioUrl = "https://example.com/audio.m4a",
            ),
            repository.getVideoDetails("1", maxHeight = 480).playback,
        )
    }

@Test
fun `getVideoDetails allows 1080p when the cap is 1080`() =
    runBlocking {
        val dto =
            videoDto(id = "1").copy(
                adaptiveFormats =
                    listOf(
                        adaptiveVideo("https://example.com/720.mp4", "720p", "avc1.4D401F"),
                        adaptiveVideo("https://example.com/1080.mp4", "1080p", "avc1.640028"),
                        adaptiveAudio("https://example.com/audio.m4a", "130668", "mp4a.40.2"),
                    ),
            )
        coEvery { service.getVideoDetails("1") } returns dto
        coJustRun { dao.insertAll(any()) }

        assertEquals(
            PlaybackSource.Merged(
                videoUrl = "https://example.com/1080.mp4",
                audioUrl = "https://example.com/audio.m4a",
            ),
            repository.getVideoDetails("1", maxHeight = 1080).playback,
        )
    }

@Test
fun `a progressive track above the cap is skipped when adaptive is available`() =
    runBlocking {
        val dto =
            videoDto(id = "1").copy(
                formatStreams =
                    listOf(FormatStreamDto(url = "https://example.com/progressive-720.mp4", type = "video/mp4", qualityLabel = "720p")),
                adaptiveFormats =
                    listOf(
                        adaptiveVideo("https://example.com/adaptive-360.mp4", "360p", "avc1.4D401E"),
                        adaptiveAudio("https://example.com/audio.m4a", "130668", "mp4a.40.2"),
                    ),
            )
        coEvery { service.getVideoDetails("1") } returns dto
        coJustRun { dao.insertAll(any()) }

        assertEquals(
            PlaybackSource.Merged(
                videoUrl = "https://example.com/adaptive-360.mp4",
                audioUrl = "https://example.com/audio.m4a",
            ),
            repository.getVideoDetails("1", maxHeight = 480).playback,
        )
    }
```

- [ ] **Step 2: Run and confirm the tests fail**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.repository.VideoRepositoryImplTest"
```

- [ ] **Step 3: Change the repository contract and implementation**

`VideoRepository`:

```kotlin
suspend fun getVideoDetails(
    videoId: String,
    maxHeight: Int = AppSettings.DEFAULT_QUALITY,
): VideoDetails
```

Import `com.example.newtube.model.AppSettings`.

`VideoRepositoryImpl`:

```kotlin
override suspend fun getVideoDetails(videoId: String, maxHeight: Int): VideoDetails =
    withContext(Dispatchers.IO) {
        val dto = service.getVideoDetails(videoId)
        val video = dto.toDomainVideo()
        val playback =
            dto.toPlaybackSource(maxHeight)
                ?: throw IllegalStateException("No playable stream returned for video $videoId")
        VideoDetails(
            video = video,
            description = dto.description.trim(),
            related = dto.recommendedVideos.mapNotNull { it.toDomainOrNull() },
            playback = playback,
        )
    }

private fun VideoDto.toPlaybackSource(maxHeight: Int): PlaybackSource? {
    val cap = maxHeight.takeIf { it > 0 } ?: AppSettings.DEFAULT_QUALITY
    val isLive = liveNow || type == "livestream"
    if (isLive) {
        hlsUrl?.takeIf { it.isNotBlank() }?.let { return PlaybackSource.Hls(it) }
    }

    formatStreams.firstPlayableUrl(cap)?.let { return PlaybackSource.Progressive(it) }

    val videoUrl = adaptiveFormats.bestVideoUrl(cap)
    val audioUrl = adaptiveFormats.bestAudioUrl()
    if (videoUrl != null && audioUrl != null) return PlaybackSource.Merged(videoUrl, audioUrl)

    // Last resort: accept a progressive track above the cap, then HLS.
    formatStreams.firstPlayableUrl()?.let { return PlaybackSource.Progressive(it) }
    return hlsUrl?.takeIf { it.isNotBlank() }?.let { PlaybackSource.Hls(it) }
}

private fun List<FormatStreamDto>.bestVideoUrl(maxHeight: Int): String? {
    val candidates = filter { !it.url.isNullOrBlank() && it.type?.startsWith("video/mp4") == true }
    if (candidates.isEmpty()) return null

    val capped = candidates.filter { (it.heightOrNull() ?: Int.MAX_VALUE) <= maxHeight }
    val pool = capped.ifEmpty { candidates }
    val byQuality =
        if (capped.isNotEmpty()) {
            compareByDescending<FormatStreamDto> { it.isAvc() }
                .thenByDescending { it.heightOrNull() ?: 0 }
        } else {
            compareByDescending<FormatStreamDto> { it.isAvc() }
                .thenBy { it.heightOrNull() ?: Int.MAX_VALUE }
        }
    return pool.sortedWith(byQuality).firstOrNull()?.url
}

private fun List<FormatStreamDto>.firstPlayableUrl(maxHeight: Int? = null): String? =
    firstOrNull { format ->
        val height = format.heightOrNull()
        !format.url.isNullOrBlank() &&
            (maxHeight == null || height == null || height <= maxHeight)
    }?.url
```

Remove the old `PREFERRED_MAX_HEIGHT` constant. Keep the existing private helpers `FormatStreamDto.heightOrNull()` (parses the leading digits of `qualityLabel`; the DTO has no numeric height field) and `FormatStreamDto.isAvc()`; only `bestVideoUrl` and `firstPlayableUrl` gain the `maxHeight` parameter.

- [ ] **Step 4: Pass the setting from Watch and Shorts**

`WatchViewModel.loadInternal`:

```kotlin
val maxHeight =
    runCatching { settingsRepository.settings.first().defaultQuality }
        .getOrDefault(AppSettings.DEFAULT_QUALITY)
val details = repository.getVideoDetails(videoId, maxHeight)
```

`ShortsViewModel.ensurePlayback`:

```kotlin
val maxHeight =
    runCatching { settingsRepository.settings.first().defaultQuality }
        .getOrDefault(AppSettings.DEFAULT_QUALITY)
val playback = repository.getVideoDetails(videoId, maxHeight).playback
```

- [ ] **Step 5: Update the ViewModel test stubs**

Every `coEvery { repository.getVideoDetails("v1") }` in `WatchViewModelTest` becomes `coEvery { repository.getVideoDetails("v1", 720) }`. Add a cap test:

```kotlin
@Test
fun `the saved maximum quality is passed to the repository`() =
    runTest {
        every { settings.settings } returns flowOf(AppSettings(defaultQuality = 1080))
        coEvery { repository.getVideoDetails("v1", 1080) } returns details()
        coEvery { playbackPositions.positionFor("v1") } returns 0L

        val viewModel = WatchViewModel(repository, playbackPositions, history, settings)
        viewModel.load("v1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is UiState.Success)
    }
```

In `ShortsViewModelTest`, every `getVideoDetails("1")` becomes `getVideoDetails("1", 720)`, plus one test with `AppSettings(defaultQuality = 480)` verifying `getVideoDetails("1", 480)`.

**Important:** an override cannot declare default arguments, so `VideoRepositoryImpl.getVideoDetails` requires both parameters. Update every existing `VideoRepositoryImplTest` call from `repository.getVideoDetails("x")` to `repository.getVideoDetails("x", AppSettings.DEFAULT_QUALITY)`. Add `import com.example.newtube.model.AppSettings` to that test file.

- [ ] **Step 6: Run the full unit test suite**

```powershell
.\gradlew.bat test
```

- [ ] **Step 7: Commit**

```powershell
git add domain/src/main/java/com/example/newtube/repository/VideoRepository.kt data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt app/src/main/java/com/example/newtube/ui/video/WatchViewModel.kt app/src/main/java/com/example/newtube/ui/shorts/ShortsViewModel.kt app/src/test/java/com/example/newtube/repository/VideoRepositoryImplTest.kt app/src/test/java/com/example/newtube/ui/video/WatchViewModelTest.kt app/src/test/java/com/example/newtube/ui/shorts/ShortsViewModelTest.kt
git commit -m "feat: apply the maximum-quality setting to stream selection"
```

## Task 16: Formatter tests, documentation and final device pass

**Files:**
- Test: `app/src/test/java/com/example/newtube/ui/common/FormatTest.kt`
- Modify: `README.md`
- Modify: `SPEC.md`
- Modify: `TASKS.md`

**Interfaces:** none new.

- [ ] **Step 1: Add formatter edge-case tests**

```kotlin
package com.example.newtube.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun `counts keep one decimal at every boundary`() {
        assertEquals("0", formatCount(0))
        assertEquals("999", formatCount(999))
        assertEquals("1.0K", formatCount(1_000))
        assertEquals("1.2K", formatCount(1_200))
        assertEquals("1.0M", formatCount(1_000_000))
        assertEquals("1.2M", formatCount(1_200_000))
        assertEquals("1.0B", formatCount(1_000_000_000))
    }

    @Test
    fun `durations switch to hours only after 3600 seconds`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:59", formatDuration(59))
        assertEquals("1:00", formatDuration(60))
        assertEquals("59:59", formatDuration(3_599))
        assertEquals("1:00:00", formatDuration(3_600))
    }
}
```

- [ ] **Step 2: Run the full verification**

```powershell
.\gradlew.bat ktlintFormat
.\gradlew.bat ktlintCheck
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

`ktlintFormat` and `ktlintCheck` must be separate invocations. Fix every ktlint issue before proceeding.

- [ ] **Step 3: Run the Room instrumented tests**

```powershell
.\gradlew.bat :data:connectedDebugAndroidTest
```

Expected: migration and DAO tests pass on the TCL 6102H.

- [ ] **Step 4: Install and run the device QA checklist**

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am force-stop com.example.newtube
adb shell monkey -p com.example.newtube -c android.intent.category.LAUNCHER 1
```

Check each item:

- [ ] Dark theme by default when the system is dark; Settings → Тема → Світла switches immediately; the app stays light after a cold start.
- [ ] Five bottom tabs fit at 360 dp, labels are legible, no selected pill, and the bar is hidden on Watch/Settings/History.
- [ ] Home top bar shows the purple logo, refresh spinner, bell and search; search opens the Search tab with the field focused.
- [ ] Chips show Ukrainian labels and selecting one reloads the feed and scrolls to the top.
- [ ] Shorts shelf appears only when it has clips; tapping a tile opens Shorts at that clip.
- [ ] Cards show a 16:9 thumbnail, duration/LIVE badge, 36 dp avatar, title and metadata; broken trending items show only the channel name.
- [ ] Search results, related videos and channel uploads use the same card design.
- [ ] Watching a video adds exactly one history row; re-watching updates the time instead of duplicating.
- [ ] A Shorts clip is recorded only after 3 seconds; swiping quickly does not add rows.
- [ ] History shows newest first and opens the video on tap.
- [ ] Settings → Максимальна якість → 480p actually caps adaptive playback at 480p on a video that offers it.
- [ ] Clear history empties the list, clears resume positions (the player restarts at 0) and asks for confirmation mentioning positions.
- [ ] Subscriptions and Notifications show honest signed-out notes with no dead buttons.
- [ ] About shows the app version, the Invidious base URL and the legal note.

- [ ] **Step 5: Update the docs**

- `README.md`: change the feature list from three tabs to five, mention dark/light theme, Shorts shelf, local history, settings, Room v3, and remove "dark-mode toggle" from "Not implemented yet".
- `SPEC.md`: update the screen table/navigation and Room version; leave account/OAuth items as planned.
- `TASKS.md`: add the completed Phase 1–3 items and point to this plan; keep Phases 4–5 as next steps.

- [ ] **Step 6: Final commit**

```powershell
git add app/src/test/java/com/example/newtube/ui/common/FormatTest.kt README.md SPEC.md TASKS.md
git commit -m "test: cover formatters and document the YouTube-style local release"
```

- [ ] **Step 7: Report the evidence**

Paste the final output of `test`, `ktlintCheck`, `assembleDebug`, `:data:connectedDebugAndroidTest` and the QA checklist into the completion message. Do not claim success without the command output.

---

## Plan 2 hand-off

Do not start this in Plan 1. Plan 2 covers spec Phases 0, 4 and 5 (Google device-code OAuth, encrypted token store, InnerTube account client, remote history, subscriptions, notifications, login/You signed-in states). It is gated on the Phase 0 spike being approved by the user. The placeholders created here (`SubscriptionsScreen`, `NotificationsScreen`, You header) are the exact surfaces Plan 2 replaces.

