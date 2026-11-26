# Video & Shorts Loading Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Прибрати три симптоми повільної роботи Shorts (повільний перший кліп, затримка свайпу, буферизація) спочатку на ExoPlayer 2.19, потім перейти на Media3 з `DefaultPreloadManager`.

**Architecture:** Phase 1 додає кеш фіду (Room v4 + DataStore), prefetch джерел сусідніх кліпів, один спільний `ExoPlayerHolder` для пейджера та дисковий `SimpleCache` для медіа. Усі шви (контракт ViewModel, mapper джерела, провайдер кешу) зроблені так, щоб Phase 2 змінив лише плеєрний шар: `DefaultPreloadManager` + Paging 3 `RemoteMediator`.

**Tech Stack:** Kotlin 1.9.24, Jetpack Compose (BOM 2024.03.00), Hilt 2.47, Room 2.6.1, DataStore 1.1.1, Retrofit/OkHttp 4.12.0, Coil 2.5.0, ExoPlayer 2.19.0 → Media3 1.4.1, JUnit4 + MockK + coroutines-test, ktlint 12.0.2.

**Spec:** `docs/superpowers/specs/2026-11-26-video-shorts-performance-design.md`

## Global Constraints

- API 24+, compileSdk/targetSdk 34, JDK 17, AGP 8.2.2, Kotlin 1.9.24, Compose BOM 2024.03.00.
- Phase 1 залишає ExoPlayer **2.19.0**; Phase 2 — Media3 **1.4.1** (пін у build.gradle: `DefaultPreloadManager` уже є, compileSdk 34/AGP 8.2.2 лишаються; Media3 1.5+ вимагає compileSdk 35). Fallback якщо 1.4.1 не збереться — compileSdk 35 + AGP 8.6, targetSdk 34 без змін.
- `specifiedRangeCached()` не використовується (API 1.9): у 1.4.1 preload іде в пам'ять через `specifiedRangeLoaded()`, а повторні відтворення обслуговує `CacheDataSource`.
- Room **v3 → v4** з `MIGRATION_3_4` та export schema `4.json` (обов'язково).
- Не зберігати підписані URL стрімів у **наших** Room/DataStore моделях: кешуємо лише метадані `Video`. Приватний SQLite-індекс `SimpleCache` є винятком (LRU 256 МБ) — кастомний `CacheKeyFactory` не вводимо, бо зрізані query зіткнуться на всіх `/videoplayback`.
- Не більше **4** резолвів джерел одночасно і не більше **2** паралельних запити у Phase 1.
- Дисковий кеш медіа: **256 MiB**, LRU; preload-пам'ять Phase 2: **96 MiB**, вікно **±2**.
- Буфери: `min=1000, max=5000, forPlayback=1000, afterRebuffer=1500` мс; `setPrioritizeTimeOverSizeThresholds(true)`.
- `./gradlew test`, `./gradlew ktlintCheck`, `./gradlew assembleDebug` мають проходити після кожного task.
- 145 наявних JVM-тестів і 4 інструментальні Room-тести не регресують.
- Модуль `ui` не використовувати; кореневий `src/` не чіпати.

## Review Focus

| Ризик | Очікувана поведінка | Де закріплено тестом |
|---|---|---|
| Строк дії підписаних URL | У `shorts_feed` ніколи немає полів stream URL; кеш відновлює лише `Video` | Task 1, `ShortsFeedMappingTest` (entity має лише метадані) |
| Помилка оновлення на застарілому фіду | Кешований фід лишається на екрані, `isRefreshing=false`, банер помилки | Task 3, `ShortsViewModelTest.cachedFeedSurvivesFailedRefresh` |
| Виснаження/збій кешованих каналів | Якщо всі відомі канали FAIL — повертається discovery + кураторський fallback | Task 2, `VideoRepositoryImplTest.fallsBackToDiscoveryWhenCachedChannelsFail` |
| Швидкий свайп між кліпами | Один плеєр, жодного `release()` між сторінками, `prepare()` тільки для активного джерела | Task 6, device QA (logcat: count `Preparing …` ≠ count свайпів) + `ExoPlayerHolderTest.prepareSequence` |
| Preload-вікно та пам'ять | Не більше 4 джерел на подію, не більше 2 паралельних запити; LRU ≤ 256 MiB | Task 4, `ShortsViewModelTest.prefetchWindowIsBounded`; Task 5, `PlaybackCacheModuleTest.maxBytesIs256MiB` |

---

## Phase 1 — ExoPlayer 2.19 (без міграції)

### Task 1: Shorts feed cache (Room v4 + DataStore + repository)

**Files:**
- Create: `domain/src/main/java/com/example/newtube/repository/ShortsCacheRepository.kt`
- Create: `data/src/main/java/com/example/newtube/database/ShortsFeedEntity.kt`
- Create: `data/src/main/java/com/example/newtube/database/ShortsFeedDao.kt`
- Create: `data/src/main/java/com/example/newtube/preferences/ShortsDataStore.kt`
- Create: `data/src/main/java/com/example/newtube/implementation/ShortsCacheRepositoryImpl.kt`
- Modify: `data/src/main/java/com/example/newtube/database/NewTubeDatabase.kt:8-27`
- Modify: `data/src/main/java/com/example/newtube/di/DatabaseModule.kt:16-36`
- Modify: `data/src/main/java/com/example/newtube/di/RepositoryModule.kt`
- Test: `data/src/test/java/com/example/newtube/implementation/ShortsCacheRepositoryImplTest.kt`
- Test: `data/src/test/java/com/example/newtube/database/ShortsFeedMappingTest.kt`
- Test: `data/src/androidTest/java/com/example/newtube/database/MigrationTest.kt`

**Interfaces:**
- Consumes: `domain` model `Video` (fields: id, title, channelName, channelId, thumbnailUrl, duration, views, uploadedAt, isLive, authorAvatarUrl).
- Produces:
  - `interface ShortsCacheRepository { fun observeFeed(): Flow<List<Video>>; suspend fun replaceFeed(videos: List<Video>); suspend fun knownChannelIds(): Set<String>; suspend fun rememberChannelIds(ids: Set<String>) }`
  - `internal fun ShortsFeedEntity.toVideo(): Video`
  - `internal fun Video.toShortsFeedEntity(position: Int, cachedAt: Long): ShortsFeedEntity`

- [ ] **Step 1: Write failing mapping test**

```kotlin
class ShortsFeedMappingTest {
    @Test fun `entity carries metadata only and round-trips to Video`() {
        val video = Video("id1", "Title", "Chan", "UC1", "https://thumb", 42L, 100L, "2 d", false, "avatar")
        val entity = video.toShortsFeedEntity(position = 3, cachedAt = 1234L)
        assertEquals(3, entity.position)
        assertEquals(1234L, entity.cachedAt)
        assertEquals(video, entity.toVideo())
        assertFalse(entity::class.members.any { it.name.contains("url", true) && it.name.contains("stream", true) })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :data:test --tests "*ShortsFeedMappingTest"`
Expected: FAIL — unresolved `toShortsFeedEntity` / `ShortsFeedEntity`.

- [ ] **Step 3: Create entity, DAO, mapping helpers and Room v4 migration**

`ShortsFeedEntity` — table `shorts_feed`, PK `videoId`; columns exactly: `videoId, title, channelName, channelId, thumbnailUrl, duration, views, uploadedAt, isLive, authorAvatarUrl, position, cachedAt`. No stream fields.
`ShortsFeedDao` — `@Query("SELECT * FROM shorts_feed ORDER BY position ASC") fun observeAll(): Flow<List<ShortsFeedEntity>>`, `@Transaction @Insert/onConflict=REPLACE + @Query("DELETE FROM shorts_feed") suspend fun replaceAll(items)`, `suspend fun clear()`.
`NewTubeDatabase` — `VERSION = 4`, add entity + `abstract fun shortsFeedDao()`, `MIGRATION_3_4` creates the table exactly as the entity declares and adds `index_shorts_feed_position`. Register in `DatabaseModule` and export `4.json` (Room kapt generates it).

- [ ] **Step 4: Create DataStore and repository implementation**

`ShortsDataStore.kt`: `private val Context.shortsDataStore by preferencesDataStore(name = "shorts")`, key `stringSetPreferencesKey("known_channels")`, plus `@Provides @Singleton fun provideShortsDataStore(@ApplicationContext context: Context): DataStore<Preferences>`.
`ShortsCacheRepositoryImpl @Inject constructor(dao: ShortsFeedDao, dataStore: DataStore<Preferences>)`: `observeFeed()` maps DAO flow to `Video`; `replaceFeed()` maps with `position = index`, `cachedAt = System.currentTimeMillis()`; `knownChannelIds()/rememberChannelIds()` read/write the string set. Bind in `RepositoryModule` with `@Binds @Singleton`.
Keep mapping in `internal` top-level functions so Step 1's test stays pure JVM.

- [ ] **Step 5: Write failing repository tests**

```kotlin
@Test fun `replaceFeed preserves order and cachedAt`() = runTest { /* MockK dao, verify replaceAll with positions 0,1 */ }
@Test fun `empty DataStore yields empty channel set`() = runTest { /* stub data Flow with empty preferences */ }
@Test fun `rememberChannelIds writes the set`() = runTest { /* MockK dataStore.edit, capture key/value */ }
```

- [ ] **Step 6: Run tests**

Run: `./gradlew :data:test --tests "*ShortsCacheRepositoryImplTest" --tests "*ShortsFeedMappingTest"`
Expected: PASS.

- [ ] **Step 7: Add instrumented migration test**

In `MigrationTest.kt`, add `migration3To4CreatesShortsFeed`: run 3→4, insert one row into `shorts_feed`, read it back and assert columns.

Run: `./gradlew :data:connectedAndroidTest` (device required)
Expected: PASS.

- [ ] **Step 8: Commit**

`git add domain/src/main/java/com/example/newtube/repository/ShortsCacheRepository.kt data/src/main/java/com/example/newtube/database data/src/main/java/com/example/newtube/preferences/ShortsDataStore.kt data/src/main/java/com/example/newtube/implementation/ShortsCacheRepositoryImpl.kt data/src/main/java/com/example/newtube/di data/src/test data/src/androidTest`
`git commit -m "feat(shorts): cache feed metadata in Room v4"`

### Task 2: Feed discovery reuses cached channel ids

**Files:**
- Modify: `data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt:33-41,107-144,159-224`
- Test: `app/src/test/java/com/example/newtube/repository/VideoRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `ShortsCacheRepository` from Task 1.
- Produces: unchanged `VideoRepository.getShorts(page: Int): List<Video>` signature.

- [ ] **Step 1: Write failing tests** in the existing `VideoRepositoryImplTest`:
  - `knownChannelsSkipDiscovery` — stub `knownChannelIds()` with 3 ids, stub `getChannelShorts` for them; assert `searchChannels` was **never** called.
  - `fallsBackToDiscoveryWhenCachedChannelsFail` — all `getChannelShorts` for known ids fail; assert `searchChannels` called and discovered ids saved via `rememberChannelIds`.
  - `preservesCuratedFallback` — empty discovery result still returns the curated list (existing behavior).

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "*VideoRepositoryImplTest"`
Expected: FAIL on the two new assertions.

- [ ] **Step 3: Inject the cache and change `nextShortsChannels()`**

Add `private val shortsCache: ShortsCacheRepository` to the constructor. At the start of `nextShortsChannels()` (still under `shortsChannelMutex`), read `knownChannelIds()`. If it has ≥ `SHORTS_DISCOVERY_QUERIES_PER_PAGE` ids, select from it by rotating `usedShortsChannels` as today and return — no `searchChannels` call. Otherwise run the existing discovery code, then **merge** found ids with the stored set (`distinct`, cap 42) via `rememberChannelIds(...)`; curated fallback ids are **not** persisted. When every known id has been used in a cycle, clear `usedShortsChannels` and start a new cycle. Keep the curated fallback itself untouched.

- [ ] **Step 4: Run tests and full verification**

Run: `./gradlew :app:test --tests "*VideoRepositoryImplTest" && ./gradlew test ktlintCheck`
Expected: PASS.

- [ ] **Step 5: Commit**

`git commit -m "feat(shorts): reuse discovered channels from cache"`

### Task 3: Stale-while-revalidate in ShortsViewModel

**Files:**
- Modify: `app/src/main/java/com/example/newtube/ui/shorts/ShortsViewModel.kt:28-55,87-122`
- Modify: `app/src/main/java/com/example/newtube/ui/shorts/ShortsScreen.kt:51-84`
- Test: `app/src/test/java/com/example/newtube/ui/shorts/ShortsViewModelTest.kt`

**Interfaces:**
- Consumes: `ShortsCacheRepository.observeFeed()/replaceFeed()`.
- Produces: `val isRefreshing: StateFlow<Boolean>`, `val refreshError: StateFlow<String?>`, unchanged `uiState`.

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun `cached feed is published before network responds`() = runTest {
    // shortsCache.observeFeed() -> flowOf(listOf(cached)); repository.getShorts() suspends on a gate
    val vm = ShortsViewModel(...)
    advanceUntilIdle()
    assertEquals(UiState.Success(listOf(cached)), vm.uiState.value)
    assertTrue(vm.isRefreshing.value)
}
@Test fun `cachedFeedSurvivesFailedRefresh`() = runTest {
    // cached feed present, repository.getShorts throws
    advanceUntilIdle()
    assertEquals(listOf(cached), (vm.uiState.value as UiState.Success).data)
    assertFalse(vm.isRefreshing.value)
    assertNotNull(vm.refreshError.value)
}
@Test fun `successful refresh replaces cached feed`() = runTest { /* verify shortsCache.replaceFeed(fresh) */ }
@Test fun `refresh keeps current clip index when id survives`() = runTest {
    // cached list [a,b,c], current index 1, fresh list [x,b,y]
    // assert UiState.Success(fresh) and requestedStartId == "b" (pager restored to index 1)
}
@Test fun `manual refresh does not clear cache`() = runTest { /* verify no shortsCache.clear() */ }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "*ShortsViewModelTest"`
Expected: FAIL — unresolved `isRefreshing` / wrong initial state.

- [ ] **Step 3: Implement cache-first reload**

In `init`, launch one job: `shortsCache.observeFeed().first()` → if non-empty set `UiState.Success(cached)`; then call the existing network loop; on success `replaceFeed(result)` and publish network result; on failure set `refreshError` while keeping cached state (if no cache, keep existing `UiState.Error`). Set `isRefreshing` true/false around the network part. Reconcile by `videoId`: remember the current id from `onPageChanged`, and after publishing the fresh list emit it as `requestedStartId` so `ShortsPager` restores that index; if the id is gone, emit `null`. A manual `reload()` from Retry/Refresh uses the same path and must never call `clear()`. Do not change `UiState` shape and do not add a TTL in Phase 1.

- [ ] **Step 4: Add the refresh indicator in `ShortsScreen`**

Collect `isRefreshing`/`refreshError`; show a small top-center `LinearProgressIndicator` when refreshing and a dismissible text banner when `refreshError != null`. Keep `LoadingScreen` for the truly empty case.

- [ ] **Step 5: Run tests and lint**

Run: `./gradlew :app:test --tests "*ShortsViewModelTest" && ./gradlew ktlintCheck`
Expected: PASS.

- [ ] **Step 6: Commit**

`git commit -m "feat(shorts): show cached feed instantly and refresh in background"`

### Task 4: Bounded source prefetch window

**Files:**
- Modify: `app/src/main/java/com/example/newtube/ui/shorts/ShortsViewModel.kt:41,161-180`
- Modify: `app/src/main/java/com/example/newtube/ui/shorts/ShortsScreen.kt:108-114`
- Test: `app/src/test/java/com/example/newtube/ui/shorts/ShortsViewModelTest.kt`

**Interfaces:**
- Consumes: `playbackSources`, `requestedSources` (existing).
- Produces: `fun onPageChanged(index: Int)`.

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun prefetchWindowIsBounded() = runTest {
    // 10 videos, onPageChanged(4) -> ids at 3,4,5,6 resolved; ids 0,1,2,7,8,9 not
    assertEquals(setOf("v3","v4","v5","v6"), vm.playbackSources.value.keys)
}
@Test fun prefetchNeverExceedsTwoConcurrentRequests() = runTest {
    // gate getVideoDetails with a counter; assert max concurrent == 2
}
@Test fun repeatedPageChangeDoesNotRefetch() = runTest { /* existing requestedSources dedupe */ }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "*ShortsViewModelTest"`
Expected: FAIL — unresolved `onPageChanged`.

- [ ] **Step 3: Implement `onPageChanged`**

Compute `(index - 1)..(index + 2)`, clamp to list indices (max 4). For each id not in `requestedSources`, mark it and launch in `coroutineScope` guarded by `Semaphore(2)` using the same `getVideoDetails(videoId, maxHeight)` call as `ensurePlayback`. Factor that call into one private `resolveSource(videoId)` used by both. Keep `force = true` behavior for Retry.

- [ ] **Step 4: Wire the pager**

In `ShortsScreen`, replace the `viewModel.ensurePlayback(video.id)` call inside the `currentPage` effect with `viewModel.onPageChanged(pagerState.currentPage)`; keep the `loadMore()` condition unchanged.

- [ ] **Step 5: Run tests and commit**

Run: `./gradlew :app:test --tests "*ShortsViewModelTest" && ./gradlew test ktlintCheck`
`git commit -m "feat(shorts): prefetch source window around active clip"`

### Task 5: Media caching DI and extracted source mapper

**Files:**
- Create: `data/src/main/java/com/example/newtube/di/PlaybackCacheModule.kt`
- Create: `data/src/main/java/com/example/newtube/di/PlaybackCacheEntryPoint.kt`
- Create: `app/src/main/java/com/example/newtube/ui/video/PlaybackSourceMapper.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/video/VideoPlayer.kt:343-364`
- Test: `app/src/test/java/com/example/newtube/ui/video/PlaybackSourceMapperTest.kt`
- Test: `data/src/test/java/com/example/newtube/di/PlaybackCacheModuleTest.kt`

**Interfaces:**
- Consumes: `PlaybackSource` sealed interface (`Hls(url)`, `Progressive(url)`, `Merged(videoUrl, audioUrl)`).
- Produces:
  - `internal fun PlaybackSource.mediaItem(): MediaItem`
  - `internal fun PlaybackSource.toMediaSource(context: Context, dataSourceFactory: DataSource.Factory): MediaSource`
  - `@EntryPoint interface PlaybackCacheEntryPoint { fun cacheDataSourceFactory(): CacheDataSource.Factory }`

- [ ] **Step 1: Write failing mapper test**

```kotlin
@Test fun `mediaItem uses the source uri`() {
    assertEquals("https://x/a.m3u8", PlaybackSource.Hls("https://x/a.m3u8").mediaItem().uri.toString())
}
@Test fun `merged source keeps video and audio uris`() {
    val s = PlaybackSource.Merged("https://x/v", "https://x/a")
    assertEquals("https://x/v", s.videoUrl); assertEquals("https://x/a", s.audioUrl)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:test --tests "*PlaybackSourceMapperTest"`
Expected: FAIL — unresolved mapper.

- [ ] **Step 3: Create the mapper and remove the private copy from VideoPlayer**

Move `toMediaSource` out of `VideoPlayer.kt` into `PlaybackSourceMapper.kt`; add `mediaItem()` and build HLS/Progressive/Merged sources from it. `VideoPlayer` imports the new function and passes `DefaultDataSource.Factory(context)` for now (Task 6 replaces it with the cached factory).

- [ ] **Step 4: Create the cache module and entry point**

`PlaybackCacheModule`: declare `internal const val PLAYBACK_CACHE_MAX_BYTES = 268_435_456L` (256 MiB). `@Provides @Singleton fun provideSimpleCache(@ApplicationContext context: Context): SimpleCache` using `StandaloneDatabaseProvider(context)`, `File(context.filesDir, "media_cache")`, `LeastRecentlyUsedCacheEvictor(PLAYBACK_CACHE_MAX_BYTES)`. `provideCacheDataSourceFactory(cache: SimpleCache, context: Context): CacheDataSource.Factory` with upstream `DefaultDataSource.Factory(context)`.
`PlaybackCacheEntryPoint`: `@EntryPoint @InstallIn(SingletonComponent::class)` exposing `cacheDataSourceFactory()`.

- [ ] **Step 5: Write and run the cache-size test**

```kotlin
@Test fun maxBytesIs256MiB() {
    // assert the evictor constant used by the module equals 268435456L
    assertEquals(268_435_456L, PLAYBACK_CACHE_MAX_BYTES)
}
```

Run: `./gradlew :data:test --tests "*PlaybackCacheModuleTest" && ./gradlew :app:test --tests "*PlaybackSourceMapperTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

`git commit -m "feat(playback): provide singleton media cache and source mapper"`

### Task 6: One shared player for the Shorts pager

**Files:**
- Create: `app/src/main/java/com/example/newtube/ui/video/ExoPlayerHolder.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/video/VideoPlayer.kt:71-110,126-170`
- Modify: `app/src/main/java/com/example/newtube/ui/shorts/ShortsScreen.kt:87-142,161-193`
- Test: `app/src/test/java/com/example/newtube/ui/video/ExoPlayerHolderTest.kt`

**Interfaces:**
- Consumes: `CacheDataSource.Factory` from Task 5, `PlaybackSource.toMediaSource` from Task 5.
- Produces: `class ExoPlayerHolder(context: Context, dataSourceFactory: DataSource.Factory, loadControl: LoadControl)` with `val player: ExoPlayer`, `fun prepare(source: PlaybackSource)`, `fun stop()`, `fun release()`; `internal fun shortsLoadControl(): DefaultLoadControl`.

- [ ] **Step 1: Write failing test**

```kotlin
@Test fun `buffer durations are tuned for short clips`() {
    val lc = shortsLoadControl()
    assertEquals(1_000, lc.minBufferMs); assertEquals(5_000, lc.maxBufferMs)
    assertEquals(1_000, lc.bufferForPlaybackMs); assertEquals(1_500, lc.bufferForPlaybackAfterRebufferMs)
}
@Test fun `prepareSequence stops before replacing the source`() {
    // fake ExoPlayer via MockK; verify order: stop -> setMediaSource -> prepare -> playWhenReady = true
}
@Test fun `stop clears the media source`() = runTest { /* verify player.stop() and clearMediaItems() */ }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:test --tests "*ExoPlayerHolderTest"`
Expected: FAIL — unresolved holder.

- [ ] **Step 3: Implement holder and make `VideoPlayer` accept it**

`ExoPlayerHolder` builds `ExoPlayer.Builder(context).setLoadControl(shortsLoadControl()).build()`, `repeatMode = REPEAT_MODE_ONE`. `prepare()` performs exactly the four calls in the test, on the player's application looper; `stop()` calls `player.stop()` + `clearMediaItems()`.
Add `holder: ExoPlayerHolder? = null` to `VideoPlayer`: when `null`, keep today's `remember(playback) { ... }` + `release()`; when non-null, use `holder.player`, skip creation and skip release, run `holder.prepare(playback)` in `LaunchedEffect(playback)`, and make the built-in Retry call `holder.prepare(playback)` instead of `exoPlayer.prepare()` / `play()`.

- [ ] **Step 4: Use one holder in `ShortsPager`**

Obtain `CacheDataSource.Factory` once via `EntryPointAccessors.fromApplication(context.applicationContext, PlaybackCacheEntryPoint::class.java).cacheDataSourceFactory()`. Create `val holder = remember { ExoPlayerHolder(context, factory, shortsLoadControl()) }`; `DisposableEffect(Unit) { onDispose { holder.release() } }`. In the `currentPage` effect: `sources[videos[currentPage].id]?.let(holder::prepare) ?: holder.stop()`. Pass `holder` into `ShortPage` → `VideoPlayer`.

- [ ] **Step 5: Device QA (the part unit tests cannot cover)**

Run: `./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`
Steps on TCL 6102H: open Shorts, swipe 10 clips quickly, then back 3; assert (a) no black frame between clips, (b) `adb logcat -d | findstr "VideoPlayer.*Preparing"` shows preparation per active clip only, (c) no `Player is accessed on the wrong thread` errors. Record result in the commit message.

- [ ] **Step 6: Run full verification and commit**

Run: `./gradlew test ktlintCheck assembleDebug`
`git commit -m "feat(shorts): reuse a single ExoPlayer with disk-cached sources"`

### Task 7: Thumbnail preloading

**Files:**
- Modify: `app/src/main/java/com/example/newtube/MyApplication.kt:1-7`
- Modify: `app/src/main/java/com/example/newtube/ui/shorts/ShortsScreen.kt:51-67`

**Interfaces:** none new.

- [ ] **Step 1: Make `MyApplication` implement `ImageLoaderFactory`**

Return `ImageLoader.Builder(this).build()` (one loader per process — Coil's default already shares caches, but the explicit singleton makes preloading effective).

- [ ] **Step 2: Preload the first four thumbnails**

In `ShortsScreen`, `LaunchedEffect(uiState)` when `Success`: `videos.take(4).forEach { context.imageLoader.enqueue(ImageRequest.Builder(context).data(it.thumbnailUrl).build()) }`.

- [ ] **Step 3: Verify on device**

Open Shorts after a fresh launch; the first four pages must show the thumbnail immediately instead of a blank box. Run `./gradlew test ktlintCheck`.

- [ ] **Step 4: Commit**

`git commit -m "feat(shorts): preload first thumbnails via shared ImageLoader"`

### Phase 1 checkpoint

- [ ] Run `./gradlew test ktlintCheck assembleDebug` and the device QA from Tasks 6–7. Ask the human partner to confirm the three symptoms improved before starting Phase 2. Do not merge the Media3 migration into this checkpoint.

---

## Phase 2 — Media3 (обмеження пам'яті та preload)

> Версія **1.4.1** фіксована: `DefaultPreloadManager` доступний уже з 1.4.0, а 1.5+ вимагає compileSdk 35. Не оновлювати AGP/Gradle без окремого рішення.

### Task 8: Migrate playback to Media3 1.4.1

**Files:**
- Modify: `app/build.gradle.kts:68-70`, `data/build.gradle.kts`
- Modify: `app/src/main/java/com/example/newtube/ui/video/VideoPlayer.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/video/PlaybackSourceMapper.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/video/ExoPlayerHolder.kt`
- Modify: `data/src/main/java/com/example/newtube/di/PlaybackCacheModule.kt`
- Modify: `data/src/main/java/com/example/newtube/di/PlaybackCacheEntryPoint.kt`

**Interfaces:** all signatures from Tasks 5–6 kept; only package names change (`com.google.android.exoplayer2.*` → `androidx.media3.*`).

- [ ] **Step 1: Swap dependencies**

Replace ExoPlayer 2.19 artifacts with `androidx.media3:media3-exoplayer:1.4.1`, `media3-exoplayer-hls:1.4.1`, `media3-ui:1.4.1`, `media3-datasource-cache:1.4.1` (cache provider moves to `data`). Do **not** change compileSdk/targetSdk/AGP — if the build reports `minCompileSdk 35`, stop and ask before bumping the build tools.

- [ ] **Step 2: Migrate imports and MediaSource factories**

Use IDE-assisted package rename; verify `DefaultTrackSelector.Parameters`, `Tracks`, `PlayerView`, `HlsMediaSource.Factory`, `MergingMediaSource`, `CacheDataSource.Factory`, `StandaloneDatabaseProvider` all resolve. Keep `RESUME_TOLERANCE_MS = 5_000L` and the same quality-cap logic (`setMaxVideoSize`).

- [ ] **Step 3: Verify**

Run: `./gradlew test ktlintCheck assembleDebug`
Expected: PASS with all existing tests unchanged. Device: Watch screen quality menu, fullscreen and PiP still work.

- [ ] **Step 4: Commit**

`git commit -m "refactor(playback): migrate ExoPlayer 2.19 to Media3 1.9"`

### Task 9: DefaultPreloadManager controller

**Files:**
- Create: `app/src/main/java/com/example/newtube/ui/shorts/Media3ShortsPlaybackController.kt`
- Create: `app/src/main/java/com/example/newtube/ui/shorts/PreloadWindow.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/shorts/ShortsScreen.kt`
- Test: `app/src/test/java/com/example/newtube/ui/shorts/PreloadWindowTest.kt`

**Interfaces:**
- Consumes: `ShortsCacheRepository`, `CacheDataSource.Factory`, `PlaybackSource.mediaItem()`.
- Produces: `fun preloadWindow(index: Int, size: Int, radius: Int = 2): IntRange`; `class Media3ShortsPlaybackController` with `fun setItems(items: List<MediaItem>)`, `fun setCurrentIndex(index: Int)`, `fun release()`.

- [ ] **Step 1: Write failing pure test for the sliding window**

```kotlin
@Test fun windowIsClamped() { assertEquals(0..4, preloadWindow(2, 10)) }
@Test fun windowAtStart() { assertEquals(0..2, preloadWindow(0, 10)) }
@Test fun windowAtEnd() { assertEquals(7..9, preloadWindow(9, 10)) }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:test --tests "*PreloadWindowTest"`
Expected: FAIL — unresolved function.

- [ ] **Step 3: Implement the controller**

Create one `DefaultPreloadManager.Builder`; build **both** manager and player from it so `BandwidthMeter`, `LoadControl`, `TrackSelectorFactory`, `MediaSourceFactory` and Looper are shared. `TargetPreloadStatusControl<Int, PreloadStatus>` returns `specifiedRangeLoaded(3_000L)` for indices in `preloadWindow(current, size)` and `null` otherwise (Media3 1.4.1 treats null as "do not preload"). `setItems()` calls `remove` for indices outside the window then `addMediaItems(items, rankingIndices)`; `setCurrentIndex()` calls `setCurrentPlayingIndex`. Apply `DefaultLoadControl.Builder().setBufferDurationsMsForStreaming(1000,5000,1000,1500).setPrioritizeTimeOverSizeThresholdsForStreaming(true).setPlayerTargetBufferBytes("preload", 96*1024*1024)`. Release manager **before** player.

- [ ] **Step 4: Wire it into `ShortsPager`**

Replace the `ExoPlayerHolder` from Task 6 with the controller. On `settledPage` call `setCurrentIndex`; on the `currentPage` effect call `setItems` with the current `videos` list and then `player.setMediaSource(preloadManager.getMediaSource(item))` / `prepare()` / `play()` for the active index. Keep `VideoPlayer`'s UI for Watch only.

- [ ] **Step 5: Device QA**

Swipe 20 clips; assert no OOM, no `Player is accessed on the wrong thread`, and first-frame time visibly shorter than the Phase 1 checkpoint. Run `./gradlew test ktlintCheck assembleDebug`.

- [ ] **Step 6: Commit**

`git commit -m "feat(shorts): preload adjacent clips with Media3 PreloadManager"`

### Task 10: Paging 3 RemoteMediator for the Shorts feed

**Files:**
- Create: `app/src/main/java/com/example/newtube/ui/shorts/ShortsRemoteMediator.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/shorts/ShortsViewModel.kt`
- Modify: `app/build.gradle.kts` (add `androidx.paging:paging-runtime-ktx:3.2.1`)
- Modify: `domain/build.gradle.kts` (add `androidx.paging:paging-common:3.2.1`)
- Modify: `domain/src/main/java/com/example/newtube/repository/ShortsCacheRepository.kt`
- Modify: `data/src/main/java/com/example/newtube/implementation/ShortsCacheRepositoryImpl.kt`
- Modify: `data/src/main/java/com/example/newtube/database/ShortsFeedDao.kt`
- Modify: `data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt` (expose paged loads via existing `getShorts(page)`)
- Test: `app/src/test/java/com/example/newtube/ui/shorts/ShortsRemoteMediatorTest.kt`

**Interfaces:**
- Consumes: `ShortsCacheRepository.replaceFeed()/appendFeed()/pagingSource()`, `VideoRepository.getShorts(page)`.
- Produces: `class ShortsRemoteMediator @Inject constructor(...) : RemoteMediator<Int, Video>()`; `val pagingData: StateFlow<PagingData<Video>>` on the ViewModel.

- [ ] **Step 1: Write failing mediator tests**

```kotlin
@Test fun refreshWritesFirstPageToCache() = runTest { /* REFRESH -> replaceFeed called with 10 videos, append=false */ }
@Test fun appendUsesNextPageAndSetsEndOfPagination() = runTest { /* empty page -> endOfPaginationReached = true */ }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "*ShortsRemoteMediatorTest"`
Expected: FAIL — unresolved mediator.

- [ ] **Step 3: Implement mediator and ViewModel paging**

`RemoteMediator.load(REFRESH, state)` loads page 1 and calls `replaceFeed`; `APPEND` loads `state.pages.last().nextPage` and calls `appendFeed`; failures return `LoadResult.Error` without clearing cached data. In `ShortsViewModel`, replace the manual list with `Pager(PagingConfig(pageSize=10, prefetchDistance=2, initialLoadSize=10, maxSize=40), remoteMediator, pagingSourceFactory = shortsCache::pagingSource).flow.cachedIn(viewModelScope)`. Keep `onPageChanged`, `playbackSources`, `isRefreshing` and `refreshError` unchanged so Tasks 3–4 and 9 keep working.

- [ ] **Step 4: Add the paging source without leaking the DAO into UI**

`ShortsFeedDao`: `@Query("SELECT * FROM shorts_feed ORDER BY position ASC") fun pagingSource(): PagingSource<Int, ShortsFeedEntity>`. `ShortsCacheRepositoryImpl.pagingSource()` returns a `PagingSource<Int, Video>` that delegates to it and maps every loaded entity with `toVideo()`. `appendFeed()` computes `max(position) + 1` before inserting.

- [ ] **Step 5: Verify and commit**

Run: `./gradlew test ktlintCheck assembleDebug`; device QA: scroll 5 pages, no duplicates, no memory growth.
`git commit -m "feat(shorts): page the feed with Paging 3 and Room mediator"`

### Task 11: Documentation and final verification

**Files:**
- Modify: `README.md:26-28`, `SPEC.md:29,118,149-157`, `TASKS.md:86-104`

**Interfaces:** none.

- [ ] **Step 1: Update docs**

README: media/short caching and preloading now implemented (remove from "Not implemented yet"). SPEC: ExoPlayer row → Media3 **1.4.1**; §6 known limitations — remove "cache only on write" for Shorts; note disk cache budget; explicitly record that the Home Shorts shelf's `#shorts` search stays out of scope (landscape results would pollute the vertical feed cache). TASKS: add phase-7 checklist entries with ✅ and the measured device result.

- [ ] **Step 2: Full verification**

Run: `./gradlew test ktlintCheck assembleDebug && ./gradlew :data:connectedAndroidTest`
Expected: 145+ tests green, ktlint clean, debug APK built, migration tests pass.

- [ ] **Step 3: Commit**

`git commit -m "docs: record Shorts performance work and verification"`

---

## Execution handoff

План збережено. Перед початком реалізації потрібно обрати метод виконання: subagent-driven (свіжий агент + рев'юер на кожен task) або native (я виконую task-и в цій сесії). Рекомендую **subagent-driven**: task-и залежать один від одного через інтерфейси, а помилка в спільному плеєрі або міграції Room коштує дорожче, ніж свіжий контекст на кожен крок.
