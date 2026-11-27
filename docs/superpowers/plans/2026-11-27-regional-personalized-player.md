# Ukrainian-first feed, extended settings and player gestures Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make NewTube's feeds Ukrainian-first and personal, expose the extended YouTube/GreenTuber-style settings with real behaviour, and add audio-track, resize, tap and swipe controls to the player.

**Architecture:** Settings live in a single DataStore-backed `AppSettings` in `:domain`; `:data` reads them once per network call to send Invidious `region`/`hl`, applies client-side content filters, and ranks the "Для вас" feed with a pure domain function over local history + subscriptions. `:app` composes the reorganized settings screens, the Home chip, suggestions UI and a Compose gesture layer over the existing Media3 `PlayerView`. All new pure logic is unit-tested; no new Room tables or migrations.

**Tech Stack:** Kotlin 1.9.24, Android (minSdk 24, target 34), Jetpack Compose BOM 2024.03 / Material3 1.2.1, Hilt 2.47, Media3 ExoPlayer 1.4.1, Retrofit 2.11 + Moshi, Room 2.6.1, DataStore 1.1.1, Paging 3, JUnit4 + MockK + coroutines-test.

**Spec:** `docs/superpowers/specs/2026-11-27-regional-personalized-player-design.md`

## Global Constraints

- Modules: `:app` (Compose + Hilt), `:data` (Android library), `:domain` (pure Kotlin). `:domain` must not import Android, Media3 or coroutines.
- Gradle wrapper: `.\gradlew.bat`; unit tasks `:domain:test`, `:data:testDebugUnitTest`, `:app:testDebugUnitTest`.
- New UI strings go to `app/src/main/res/values/strings.xml` (default Ukrainian). Do not add `material-icons-extended`; `material-icons-core` only.
- `AppSettings()` defaults must stay: content country `UA`, language `uk`, seek `10`, volume RIGHT, brightness LEFT, resize FIT, safe mode false.
- No Room schema change, no `fallbackToDestructiveMigration`, no AppCompat dependency, no full app localization.
- Existing tests must stay green: `ExoPlayerHolderTest`, `PlaybackSourceMapperTest`, `WatchViewModelTest`, all Shorts tests, `SettingsMappingTest`, `SettingsViewModelTest`, `VideoRepositoryImplTest`, instrumented Room tests.
- Commit after every task; message format `feat: ...` / `test: ...`.

## Review Focus

- **Instance ignores `region`/`hl`** — the feed stays US even though settings say UA. Pinned in Task 3 by a MockWebServer-style query-parameter test on `InvidiousService` calls from `VideoRepositoryImpl`.
- **Shorts cache still holds US channels after the setting changes** — user sees no change. Pinned in Task 3 by a test that settings change clears/replaces `ShortsCacheRepository.knownChannelIds()`.
- **No history and no subscription data** — personal feed must still show a Ukrainian-ranked feed, never an empty screen or a crash. Pinned in Task 5 by `RankFeedTest` empty-signals case and a repository test with empty signals.
- **Both gestures assigned to the same side** — ambiguous behaviour. Pinned in Task 7 by `GestureMathTest` asserting side-collision produces one action per side.
- **Single tap conflicts with `PlayerView`'s controller** — tapping to seek must not break controls or Shorts play/pause. Pinned in Task 7 by `GestureMathTest` tap classification plus a manual on-device check step (no Compose UI tests this pass).

---

### Task 1: Settings model and DataStore storage

**Files:**
- Modify: `domain/src/main/java/com/example/newtube/model/AppSettings.kt`
- Create: `domain/src/main/java/com/example/newtube/model/GestureSide.kt`
- Modify: `domain/src/main/java/com/example/newtube/repository/SettingsRepository.kt`
- Modify: `data/src/main/java/com/example/newtube/preferences/DataStoreSettingsRepository.kt`
- Test: `data/src/test/java/com/example/newtube/preferences/SettingsMappingTest.kt`

**Interfaces:**
- Consumes: existing `AppSettings(themeMode, recordHistory, useRemoteHistory, defaultQuality)`, `ThemeMode`, `Preferences.toAppSettings()`.
- Produces: `AppSettings` with fields `contentCountry: String = "UA"`, `contentLanguage: String = "uk"`, `preferredAudioLanguage: String = "uk"`, `preferOriginalAudio: Boolean = true`, `showSearchSuggestions: Boolean = true`, `ageRestrictedContent: Boolean = false`, `safeMode: Boolean = false`, `showMetadata: Boolean = true`, `autoplayNext: Boolean = true`, `resumePlayback: Boolean = true`, `doubleTapSeekSeconds: Int = 10`, `resizeMode: Int = 0` (FIT; mirror Media3's **verified** constants in `:domain`: `RESIZE_FIT = 0`, `RESIZE_FILL = 3`, `RESIZE_ZOOM = 4`; do not use 1/2/3), `videoBrightness: Float? = null`, `gestureVolume: Boolean = true`, `gestureBrightness: Boolean = true`, `gestureZoom: Boolean = false`, `gestureSpeed: Boolean = false`, `volumeGestureSide: GestureSide = GestureSide.RIGHT`, `brightnessGestureSide: GestureSide = GestureSide.LEFT`.
- Produces: `enum class GestureSide { LEFT, RIGHT, OFF }` with `fromStored(String?): GestureSide`.
- Produces: `SettingsRepository.update(transform: (AppSettings) -> AppSettings)`; four existing setters delegate to it.
- Produces: `internal fun Preferences.toAppSettings()` extended with all keys; invalid enums/strings fall back to defaults, `doubleTapSeekSeconds` clamped to `5..30`, brightness clamped to `0.01f..1f`, unknown resize int → FIT.

- [ ] **Step 1: Write failing mapping tests**

In `SettingsMappingTest.kt`, add:
- `empty preferences use the documented defaults` already asserts `AppSettings()` — extend it with explicit asserts for the new defaults (`"UA"`, `"uk"`, `10`, RIGHT/LEFT, FIT).
- `stored values override every new default` using keys `content_country`, `content_language`, `preferred_audio_language`, `prefer_original_audio`, `show_search_suggestions`, `age_restricted_content`, `safe_mode`, `show_metadata`, `autoplay_next`, `resume_playback`, `double_tap_seek_seconds`, `resize_mode`, `video_brightness`, `gesture_volume`, `gesture_brightness`, `gesture_zoom`, `gesture_speed`, `volume_gesture_side`, `brightness_gesture_side` — assert each maps to the set value.
- `invalid values fall back to safe defaults`: unknown country/language string, seek `999`, resize `99`, brightness `3f`, side `"MIDDLE"` → documented defaults.

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :data:testDebugUnitTest --tests "com.example.newtube.preferences.SettingsMappingTest"`
Expected: compile failure — new `AppSettings` members and `GestureSide` do not exist.

- [ ] **Step 3: Implement the model, enum and mapping**

Add the fields and constants to `AppSettings.kt`, create `GestureSide.kt` with strict `fromStored` (same case-sensitive pattern as `ThemeMode`), add `update` to `SettingsRepository`, add keys and mapping branches in `DataStoreSettingsRepository.kt`. Existing setters call `update { copy(...) }`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :data:testDebugUnitTest --tests "com.example.newtube.preferences.SettingsMappingTest"`
Expected: PASS.

- [ ] **Step 5: Run the whole existing suite**

Run: `.\gradlew.bat test`
Expected: PASS (no regressions in `:app` / `:domain`).

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/java/com/example/newtube/model/AppSettings.kt domain/src/main/java/com/example/newtube/model/GestureSide.kt domain/src/main/java/com/example/newtube/repository/SettingsRepository.kt data/src/main/java/com/example/newtube/preferences/DataStoreSettingsRepository.kt data/src/test/java/com/example/newtube/preferences/SettingsMappingTest.kt
git commit -m "feat: extend settings model with content, playback and gesture preferences"
```

---

### Task 2: Reorganized Settings screens

**Files:**
- Modify: `app/src/main/java/com/example/newtube/ui/nav/NavGraph.kt:66`
- Modify: `app/src/main/java/com/example/newtube/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/settings/SettingsComponents.kt`
- Create: `app/src/main/java/com/example/newtube/ui/settings/SettingsChoiceDialog.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/example/newtube/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `AppSettings` fields and `SettingsRepository.update` from Task 1.
- Produces: `SettingsViewModel` functions `setContentCountry(String)`, `setContentLanguage(String)`, `setPreferredAudioLanguage(String)`, `setPreferOriginalAudio(Boolean)`, `setShowSearchSuggestions(Boolean)`, `setAgeRestrictedContent(Boolean)`, `setSafeMode(Boolean)`, `setShowMetadata(Boolean)`, `setAutoplayNext(Boolean)`, `setResumePlayback(Boolean)`, `setDoubleTapSeekSeconds(Int)`, `setResizeMode(Int)`, `setVideoBrightness(Float?)`, `setGestureVolume(Boolean)`, `setGestureBrightness(Boolean)`, `setGestureZoom(Boolean)`, `setGestureSpeed(Boolean)`, `setVolumeGestureSide(GestureSide)`, `setBrightnessGestureSide(GestureSide)` — each launches `settingsRepository.update { it.copy(...) }`.
- Produces: `SettingsChoiceDialog<T>(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, onDismiss: () -> Unit)`.
- Produces: composables `AppearanceSettingsScreen`, `VideoAudioSettingsScreen`, `ContentSettingsScreen`, `GestureSettingsScreen` (internal to the settings package), each taking `viewModel: SettingsViewModel`, `settings: AppSettings`, `onBack: () -> Unit`.
- Produces: nav routes `"settings/appearance"`, `"settings/video"`, `"settings/content"`, `"settings/gestures"`.

- [ ] **Step 1: Write failing ViewModel tests**

Add tests to `SettingsViewModelTest.kt`: `setContentCountry writes UA→US through update`, `setDoubleTapSeekSeconds writes 30`, `setVolumeGestureSide swaps a colliding brightness side` (call a new helper `SettingsViewModel.assignGestureSide(side, forVolume, current): Pair<GestureSide, GestureSide>` that returns new volume/brightness sides; assert assigning volume LEFT when brightness is LEFT yields volume LEFT / brightness RIGHT), `setVideoBrightness clamps through repository mapping (stored value 0.5f)`. Use the existing fake repository style.

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.ui.settings.SettingsViewModelTest"`
Expected: FAIL — new methods missing.

- [ ] **Step 3: Implement ViewModel setters and collision helper**

Add the functions above; `assignGestureSide` is a pure `internal fun` in `SettingsViewModel.kt` so the test can call it without a ViewModel.

- [ ] **Step 4: Build the settings screens**

In `SettingsScreen.kt`, keep the account row at the top of the hub, then section rows: `Зовнішній вигляд` (theme), `Відео та аудіо`, `Контент`, `Жести`, `Історія` (existing), `Про додаток`. Each new row navigates to its route. Implement the four sub-screens with `SettingsRow`, `SettingsSwitchRow`, `SettingsChoiceDialog` and the existing `ThemeModeDialog` / `QualityDialog`. App-language row subtitle on <API 33: use `settings_app_language_limited`. Add all new strings in Ukrainian to `strings.xml`; register the four routes in `NavGraph.kt`.

- [ ] **Step 5: Run tests and compile**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.ui.settings.SettingsViewModelTest" assembleDebug`
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/newtube/ui/nav/NavGraph.kt app/src/main/java/com/example/newtube/ui/settings app/src/main/res/values/strings.xml app/src/test/java/com/example/newtube/ui/settings/SettingsViewModelTest.kt
git commit -m "feat: reorganize settings into nested screens with functional controls"
```

---

### Task 3: Region/language on Invidious and immediate cache invalidation

**Files:**
- Modify: `data/src/main/java/com/example/newtube/remote/InvidiousService.kt`
- Modify: `data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/shorts/ShortsViewModel.kt`
- Test: `app/src/test/java/com/example/newtube/repository/VideoRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository.settings: Flow<AppSettings>`; `AppSettings.contentCountry/contentLanguage`.
- Produces: `InvidiousService.getTrending(region: String? = null, type: String = "Default", hl: String? = null)`, `getShorts(query: String = "#shorts", type: String = "video", duration: String = "short", page: Int = 1, region: String? = null, hl: String? = null)`, `searchVideos(query: String, type: String = "video", page: Int = 1, region: String? = null, hl: String? = null)`, `getVideoDetails(videoId: String, region: String? = null, hl: String? = null)`. Existing 2-arg calls must keep compiling through defaults.
- Produces: `VideoRepositoryImpl` constructor `(service, dao, channelDao, shortsCache, settingsRepository, historyRepository, accountRepository)`; `clearFeedCaches()` internal method clearing Home cache and calling `shortsCache.clearKnownChannelIds()` — add that method to `ShortsCacheRepository` and its implementation (in-memory/DataStore known-channel set only, no Room).
- Produces: `HomeViewModel.onContentPreferencesChanged()` clears its `cache` and calls `load()` + `loadShortsShelf()`; `ShortsViewModel.onContentPreferencesChanged()` clears the pager source and reloads.

- [ ] **Step 1: Write failing repository tests**

In `VideoRepositoryImplTest.kt`, add:
- `getTrending passes UA and uk from settings` — mock settings flow returns `AppSettings(contentCountry="US", contentLanguage="en")`; `coVerify { service.getTrending(region = "US", type = "Default", hl = "en") }`.
- `getHomeFeed passes region and hl to every source` — assert at least one `searchVideos(..., region="US", hl="en")` and one `getTrending(region="US", ...)`.
- `getShorts passes region and hl to the shorts search` where applicable.
- `settings change clears shorts channel cache` — call `repository.clearFeedCaches()` and verify `shortsCache.clearKnownChannelIds()`.
- `shorts discovery uses Ukrainian queries` — with no cached channels, verify `service.searchChannels` is called at least once with a Ukrainian query string from the new `UKRAINIAN_SHORTS_CHANNEL_QUERIES` pool.

Update the `setUp` constructor call with the three new mocks and stub `settingsRepository.settings` as `MutableStateFlow(AppSettings())`. Existing `coEvery { service.getTrending(any(), any()) }` must be changed to 3-arg matchers.

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.repository.VideoRepositoryImplTest"`
Expected: FAIL — new signatures / constructor params missing.

- [ ] **Step 3: Implement service params and repository wiring**

Add the optional `@Query` params, inject settings into `VideoRepositoryImpl`, read `settingsRepository.settings.first()` once per public method and pass values into every source call. Add `clearFeedCaches()` and `ShortsCacheRepository.clearKnownChannelIds()`. Replace `SHORTS_CHANNEL_QUERIES` / `FALLBACK_SHORTS_CHANNELS` with Ukrainian-first pools (`UKRAINIAN_SHORTS_CHANNEL_QUERIES`, `UKRAINIAN_FALLBACK_SHORTS_CHANNELS`), keeping the old pools as a second-step fallback only. No changes to `PlaybackSourceMapper`.

- [ ] **Step 4: Wire immediate invalidation in both ViewModels**

In `HomeViewModel` / `ShortsViewModel`, collect `settingsRepository.settings` with `distinctUntilChangedBy { it.contentCountry to it.contentLanguage }` and, after the first emission, call `onContentPreferencesChanged()`; inject `SettingsRepository` via Hilt. Keep the existing request-token guard.

- [ ] **Step 5: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.repository.VideoRepositoryImplTest" :app:testDebugUnitTest`
Expected: PASS for both commands.

- [ ] **Step 6: Commit**

```bash
git add data/src/main/java/com/example/newtube/remote/InvidiousService.kt data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt data/src/main/java/com/example/newtube/repository/ShortsCacheRepository.kt app/src/main/java/com/example/newtube/ui/home/HomeViewModel.kt app/src/main/java/com/example/newtube/ui/shorts/ShortsViewModel.kt app/src/test/java/com/example/newtube/repository/VideoRepositoryImplTest.kt
git commit -m "feat: send content region and language to Invidious and refresh on change"
```

---

### Task 4: Content settings with real behaviour

**Files:**
- Create: `data/src/main/java/com/example/newtube/remote/SearchSuggestionsDto.kt`
- Modify: `data/src/main/java/com/example/newtube/remote/VideoDto.kt`
- Modify: `data/src/main/java/com/example/newtube/remote/InvidiousService.kt`
- Create: `data/src/main/java/com/example/newtube/implementation/VideoContentFilter.kt`
- Modify: `domain/src/main/java/com/example/newtube/repository/VideoRepository.kt`
- Modify: `data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/search/SearchViewModel.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/search/SearchScreen.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/common/VideoCard.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/common/ShortsTile.kt`
- Modify: `app/src/main/java/com/example/newtube/MyApplication.kt`
- Test: `data/src/test/java/com/example/newtube/implementation/VideoContentFilterTest.kt`
- Test: `app/src/test/java/com/example/newtube/ui/search/SearchViewModelTest.kt`

**Interfaces:**
- Consumes: `AppSettings.showSearchSuggestions/ageRestrictedContent/safeMode/showMetadata`; `SettingsRepository`.
- Produces: `SearchSuggestionsDto(val query: String = "", val suggestions: List<String> = emptyList())`; `InvidiousService.getSearchSuggestions(q: String, region: String? = null, hl: String? = null): SearchSuggestionsDto`; `VideoRepository.searchSuggestions(query: String): List<String>`.
- Produces: `VideoDto.isFamilyFriendly: Boolean = true` (no Room column; filtering before mapping).
- Produces: `object VideoContentFilter { fun List<VideoDto>.applyContentSettings(settings: AppSettings): List<VideoDto> }` — drops `isFamilyFriendly == false` when `ageRestrictedContent == false`; when `safeMode == true`, also drops titles/authors matching a private Ukrainian/English keyword denylist.
- Produces: `SearchViewModel.suggestions: StateFlow<List<String>>`; `fun acceptSuggestion(value: String)` sets the query and searches immediately.
- Produces: `VideoCard(..., showMetadata: Boolean = true)`, `ShortsTile(..., showMetadata: Boolean = true)`; when false hide channel/views/time/duration.

- [ ] **Step 1: Write failing filter tests**

Create `VideoContentFilterTest.kt` in `:data`:
- `age restricted content is dropped by default` — DTO with `isFamilyFriendly=false` removed.
- `age restricted content is kept when enabled`.
- `safe mode drops denylisted titles even when age restricted content is enabled`.
- `safe mode keeps neutral Ukrainian titles`.

Add `SearchViewModelTest` cases: `suggestions are mapped and cleared on blank query`, `suggestion endpoint failure leaves results intact`, `acceptSuggestion searches immediately`. Use 500 ms `MainDispatcher` advance or 250 ms delay from step 3.

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :data:testDebugUnitTest --tests "com.example.newtube.implementation.VideoContentFilterTest" :app:testDebugUnitTest --tests "com.example.newtube.ui.search.SearchViewModelTest"`
Expected: FAIL — classes/members missing.

- [ ] **Step 3: Implement filtering, suggestions and metadata flag**

Implement the DTO field, filter helper and its use in `toDomainVideos()` / `getVideoDetails` related mapping / search / shorts / home. Add the suggestions endpoint, repository method and a 250 ms debounced suggestions job in `SearchViewModel` (parallel to the existing 500 ms search job). Render a dropdown in `SearchScreen` under the text field; tapping a suggestion calls `acceptSuggestion`. Add `showMetadata` params and read the setting once in Home/Search/Watch/Shorts call sites. In `MyApplication`, on API 33+ apply `LocaleManager.setApplicationLocales` when the stored app-language preference changes; on older APIs leave system locale.

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :data:testDebugUnitTest --tests "com.example.newtube.implementation.VideoContentFilterTest" :app:testDebugUnitTest --tests "com.example.newtube.ui.search.SearchViewModelTest" :app:assembleDebug`
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add data/src/main/java/com/example/newtube/remote data/src/main/java/com/example/newtube/implementation/VideoContentFilter.kt data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt domain/src/main/java/com/example/newtube/repository/VideoRepository.kt app/src/main/java/com/example/newtube/ui/search app/src/main/java/com/example/newtube/ui/common/VideoCard.kt app/src/main/java/com/example/newtube/ui/common/ShortsTile.kt app/src/main/java/com/example/newtube/MyApplication.kt data/src/test/java/com/example/newtube/implementation/VideoContentFilterTest.kt app/src/test/java/com/example/newtube/ui/search/SearchViewModelTest.kt
git commit -m "feat: make content settings filter feeds, search suggestions and card metadata"
```

---

### Task 5: "Для вас" personalised feed

**Files:**
- Create: `domain/src/main/java/com/example/newtube/model/FeedSignals.kt`
- Create: `domain/src/main/java/com/example/newtube/usecase/RankFeed.kt`
- Modify: `domain/src/main/java/com/example/newtube/model/HomeCategory.kt`
- Modify: `domain/src/main/java/com/example/newtube/repository/VideoRepository.kt`
- Modify: `data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/home/HomeCategoryLabels.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `domain/src/test/java/com/example/newtube/usecase/RankFeedTest.kt`
- Test: `app/src/test/java/com/example/newtube/repository/VideoRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `Video(id, title, channelName, channelId, views, ...)`; `WatchHistoryEntry(channelId, watchedAt)`; `AccountRepository.state/subscriptions()`.
- Produces: `data class FeedSignals(val channelAffinity: Map<String, Float>, val subscribedChannelIds: Set<String>, val preferredLanguage: String)`.
- Produces: `fun rankFeed(videos: List<Video>, signals: FeedSignals): List<Video>` — score `2f*affinity + 1f*subscribed + 1.5f*languageMatch + 0.5f*normalizedViews`, stable descending sort, max 3 items per channel, keep `HOME_FEED_LIMIT`.
- Produces: `HomeCategory.FOR_YOU`, `VideoRepository.getForYouFeed(): List<Video>`, `HomeCategory.labelRes()` mapping to `category_for_you`.

- [ ] **Step 1: Write failing ranker tests**

Create `RankFeedTest.kt` asserting:
- a watched+subscribed Ukrainian channel outranks an unwatched US channel;
- equal scores preserve input order;
- at most 3 videos per channel survive;
- empty signals still return a Ukrainian-language ordered list and never empty when input is non-empty;
- affinity from an older watch is weaker than a recent one (pass precomputed maps; age weighting is tested at repository level).

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :domain:test --tests "com.example.newtube.usecase.RankFeedTest"`
Expected: FAIL — class missing.

- [ ] **Step 3: Implement the pure ranker and model**

Implement `FeedSignals`, `RankFeed.kt` with a Cyrillic script heuristic (`title`/`channelName` contains Cyrillic for `uk`/`ru`) and a stable `sortedWith` comparator. No Android/Coroutines imports.

- [ ] **Step 4: Add repository method and Home wiring**

In `VideoRepositoryImpl.getForYouFeed()`: call the already regionalised `getHomeFeed(ALL)`, build `FeedSignals` from `historyRepository.observeLocal(500).first()` (`1/(1+ageDays)` per watch) and `accountRepository` when `AccountState.SignedIn` (ignore subscription failure), apply `rankFeed`, fall back to language-only ranking when both signal sources are empty. `HomeViewModel.fetch()` calls `getForYouFeed()` for `FOR_YOU`, otherwise the existing method. Add the chip as the first category.

- [ ] **Step 5: Run tests**

Run: `.\gradlew.bat :domain:test :app:testDebugUnitTest --tests "com.example.newtube.repository.VideoRepositoryImplTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/java/com/example/newtube/model/FeedSignals.kt domain/src/main/java/com/example/newtube/usecase/RankFeed.kt domain/src/main/java/com/example/newtube/model/HomeCategory.kt domain/src/main/java/com/example/newtube/repository/VideoRepository.kt data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt app/src/main/java/com/example/newtube/ui/home app/src/main/res/values/strings.xml domain/src/test/java/com/example/newtube/usecase/RankFeedTest.kt app/src/test/java/com/example/newtube/repository/VideoRepositoryImplTest.kt
git commit -m "feat: add personalised Для вас home feed"
```

---

### Task 6: Player resize cycle and audio-track menu

**Files:**
- Modify: `app/src/main/java/com/example/newtube/ui/video/VideoPlayer.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/video/WatchViewModel.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/video/WatchScreen.kt`
- Modify: `domain/src/main/java/com/example/newtube/model/PlaybackSource.kt`
- Modify: `data/src/main/java/com/example/newtube/remote/FormatStreamDto.kt`
- Test: `app/src/test/java/com/example/newtube/ui/video/PlaybackSourceMapperTest.kt`

**Interfaces:**
- Consumes: `AppSettings.resizeMode`, `preferredAudioLanguage`, `preferOriginalAudio`; `TrackSelectionOverride`; `DefaultTrackSelector`.
- Produces: `VideoPlayer(...)` new optional params `preferredAudioLanguage: String = "uk"`, `onResizeModeChange: (Int) -> Unit = {}`, `onSavePosition: (Long) -> Unit` (existing), `showAudioControl: Boolean = true`.
- Produces: internal `data class AudioTrackOption(val key: String, val label: String, val group: Tracks.Group, val index: Int)`; `fun Tracks.audioOptions(): List<AudioTrackOption>` in a new `app/.../ui/video/AudioTrackOptions.kt`.
- Produces: `PlaybackSource.Merged(videoUrl, audioUrl, audioAlternatives: List<String> = emptyList())`; `FormatStreamDto.language: String? = null`.

- [ ] **Step 1: Write failing mapper test**

In `PlaybackSourceMapperTest.kt`, add `merged source keeps language-tagged audio alternatives` — an `adaptiveFormats` list with two `audio/` entries with different `language` values maps to `PlaybackSource.Merged` with both URLs in `audioAlternatives`, preserving existing equality for the single-audio case.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.ui.video.PlaybackSourceMapperTest"`
Expected: FAIL — new field/member missing.

- [ ] **Step 3: Implement alternatives mapping**

Extend the DTO, sealed interface and mapper; keep the default empty list so all existing assertions compile.

- [ ] **Step 4: Implement resize and audio UI**

In `VideoPlayer.kt`: collect audio options in `onTracksChanged`, apply the stored preferred language via `setPreferredAudioLanguage` in the existing `LaunchedEffect`, apply `TrackSelectionOverride` on selection and reapply after every `onTracksChanged`; hide the audio button when fewer than two options; for `Merged` with alternatives, save position then `holder.prepare(...)` with the chosen URL (NewPipe-style reload), otherwise hide it. Add a resize `TextButton` showing `Впис./Заповн./Обріз.` that calls `onResizeModeChange(next)`; apply it to `PlayerView` through `AndroidView` update. `WatchViewModel` writes resize changes via `SettingsRepository.update`; `WatchScreen` collects `settings` and passes values down.

- [ ] **Step 5: Verify**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS / BUILD SUCCESSFUL; manually verify on the connected device that quality, PiP and fullscreen still work.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/newtube/ui/video domain/src/main/java/com/example/newtube/model/PlaybackSource.kt data/src/main/java/com/example/newtube/remote/FormatStreamDto.kt app/src/test/java/com/example/newtube/ui/video/PlaybackSourceMapperTest.kt
git commit -m "feat: add audio track menu and persisted resize cycle to the player"
```

---

### Task 7: Player gestures (double-tap seek, volume and brightness swipes)

**Files:**
- Create: `app/src/main/java/com/example/newtube/ui/video/PlayerGestures.kt`
- Create: `app/src/main/java/com/example/newtube/ui/video/GestureMath.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/video/VideoPlayer.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/shorts/ShortsScreen.kt`
- Test: `app/src/test/java/com/example/newtube/ui/video/GestureMathTest.kt`

**Interfaces:**
- Consumes: `AppSettings.doubleTapSeekSeconds/gestureVolume/gestureBrightness/volumeGestureSide/brightnessGestureSide/videoBrightness`.
- Produces: `internal object GestureMath` with `fun classifyThird(x: Int, width: Int): Int` (0 left, 1 middle, 2 right), `fun classifyHalf(x: Int, width: Int): Int` (0 left, 1 right), `fun seekDelta(taps: Int, side: Int, seconds: Int): Long`, `fun dragFraction(dy: Int, height: Int, current: Float): Float` clamped `0.01f..1f`, `fun resolveSides(volume: GestureSide, brightness: GestureSide): Pair<GestureSide, GestureSide>` swapping collisions.
- Produces: `@Composable fun PlayerGestureOverlay(player: Player, isFullscreen: Boolean, isShorts: Boolean, settings: AppSettings, onBrightness: (Float) -> Unit, onVolume: (Float) -> Unit, content: @Composable () -> Unit)`.

- [ ] **Step 1: Write failing gesture-math tests**

`GestureMathTest.kt`: thirds boundaries (`width/3`, `2*width/3`), halves boundary, three right taps at 10 s → `+30_000L`, two left taps → `-20_000L`, middle side → `0L`, drag fraction clamped, collided sides swapped.

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.ui.video.GestureMathTest"`
Expected: FAIL — class missing.

- [ ] **Step 3: Implement GestureMath**

Pure functions only; no Android/Compose imports except the `GestureSide` domain enum.

- [ ] **Step 4: Implement the Compose gesture layer**

One `pointerInput` block in `PlayerGestureOverlay`: double tap within 550 ms on left/right thirds seeks by `seekDelta` and shows a `SeekFeedback` overlay; single tap keeps existing behaviour (`Shorts` play/pause; Watch lets `PlayerView` controller handle it); vertical drag only when fullscreen, after 40 px and `|dy| >= |dx|`, initial x half decides side; volume via `AudioManager.setStreamVolume`, brightness via `Window.LayoutParams.screenBrightness`, both with a translucent side indicator. Ignore status/nav bar start. Remove the blanket `clickable` overlay from `VideoPlayer.kt` when the gesture layer is enabled. Wire settings from `WatchScreen`/`ShortsScreen` into `VideoPlayer`/overlay.

- [ ] **Step 5: Verify**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS / BUILD SUCCESSFUL.
Manual on-device check (no Compose UI tests this pass): open a video, double-tap both sides (counter appears, seek works), tap middle (controls still work), enter fullscreen, swipe right (volume) and left (brightness), exit fullscreen, open Shorts (single tap still pauses).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/newtube/ui/video/PlayerGestures.kt app/src/main/java/com/example/newtube/ui/video/GestureMath.kt app/src/main/java/com/example/newtube/ui/video/VideoPlayer.kt app/src/main/java/com/example/newtube/ui/shorts/ShortsScreen.kt app/src/test/java/com/example/newtube/ui/video/GestureMathTest.kt
git commit -m "feat: add double-tap seek and fullscreen volume/brightness gestures"
```

---

### Task 8: Autoplay-next row, final verification and docs

**Files:**
- Modify: `app/src/main/java/com/example/newtube/ui/video/WatchScreen.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/video/WatchViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `README.md`
- Modify: `SPEC.md`
- Test: `app/src/test/java/com/example/newtube/ui/video/WatchViewModelTest.kt`

**Interfaces:**
- Consumes: `AppSettings.autoplayNext`, `VideoDetails.related`, `WatchUiState`.
- Produces: `WatchUiState.nextVideo: Video?` derived from `related.firstOrNull()` and `autoplayNext`; `WatchViewModel.onPlaybackEnded()` navigating through an existing `onNavigateNext` callback supplied by `WatchScreen`.

- [ ] **Step 1: Write failing ViewModel test**

`WatchViewModelTest.kt`: `nextVideo is null when autoplay is disabled`, `nextVideo is first related item when enabled`, `onPlaybackEnded does nothing without a next video`.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.ui.video.WatchViewModelTest"`
Expected: FAIL — new member missing.

- [ ] **Step 3: Implement the row and endpoint handling**

Add `nextVideo` to `WatchUiState` and populate it in `WatchViewModel` using `settingsRepository.settings.first()`. Render a single `Далі: <title>` row above «Схожі відео» in `WatchScreen`; pass `onNavigateNext` from the screen. Add an end-of-playback listener (`Player.STATE_ENDED`) in `VideoPlayer` that calls the callback, or navigate from `WatchScreen` when notified — keep one path only.

- [ ] **Step 4: Full verification**

Run: `.\gradlew.bat clean test assembleDebug`
Expected: BUILD SUCCESSFUL, all unit tests pass. Then on-device smoke: Home `Для вас` chip, settings changes refresh feeds, audio/resize buttons, gestures, related autoplay.

- [ ] **Step 5: Update docs and commit**

Document the new settings and gestures in `README.md`/`SPEC.md` where features are enumerated.

```bash
git add app/src/main/java/com/example/newtube/ui/video app/src/main/res/values/strings.xml README.md SPEC.md app/src/test/java/com/example/newtube/ui/video/WatchViewModelTest.kt
git commit -m "feat: add autoplay-next row and document regional player features"
```
