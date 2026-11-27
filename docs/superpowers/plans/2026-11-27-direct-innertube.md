# Direct InnerTube Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Invidious with direct YouTube InnerTube as the sole runtime data source, with own remote config, cached feeds, fast images and cached media playback.

**Architecture:** `data` gains a content client that walks a client chain (`ANDROID` → `ANDROID_VR` → `IOS` → config variants) over `youtubei/v1`, an extended tolerant parser that maps renderer JSON into the existing domain models, and a Room feed cache with stale-while-revalidate. `app` keeps MVVM/Hilt/Compose, observes cache flows, tunes Coil and wires the existing Media3 cache into Watch. `TVHTML5` remains only for signed-in account calls.

**Tech Stack:** Kotlin 1.9.24, AGP 8.2.2, JDK 17, Hilt 2.47, OkHttp 4.12, Moshi 1.15.1 + kotlinx.serialization 1.6.3, Room 2.6.1, Media3 1.4.1, Coil 2.5.0, coroutines 1.8.1.

**Spec:** `docs/superpowers/specs/2026-11-27-direct-innertube-green-tuber-design.md`

## Global Constraints

- Android API 24+, `compileSdk`/`targetSdk` 34, Compose BOM 2024.03.00, Kotlin compiler extension 1.5.14.
- No new Gradle modules; changes only in `app`, `data`, `domain`. `ui` stays empty.
- `./gradlew test`, `./gradlew ktlintCheck`, `./gradlew assembleDebug` must stay green after every task.
- Domain data classes in `domain/.../model/` keep their signatures; ViewModels may gain `isRefreshing: StateFlow<Boolean>`.
- Signed `videoplayback?...` URLs are never written to Room/DataStore.
- No Invidious call, symbol, build flag or doc reference survives in production code after Task 8.
- The remote config endpoint is ours; no dependency on `yougreentube.com` or any third-party config server.
- Ad breaks / ad renderer nodes from InnerTube are never displayed or played.

## Review Focus

- `UNPLAYABLE` / bot-check response on the device's mobile IP → the client chain advances and a cached feed stays visible; pinned by the device smoke test in Task 1 and `ContentClientChainTest` in Task 1.
- Unknown renderer shapes (`lockupViewModel`, `richItemRenderer`, missing `lengthText`) → parser returns fewer items instead of throwing; pinned by `ContentParserTest` fixtures in Task 2.
- A stream URL expired while the app was in the background → a fresh `player` response is requested; no persisted URL is ever read; pinned by `VideoRepositoryImplTest.playbackUsesFreshPlayerResponse` in Task 3.
- Cached feed plus a failing refresh → stale feed remains on screen and `isRefreshing` becomes false; pinned by `FeedCacheRepositoryTest` in Task 4 and `HomeViewModelTest.refreshFailureKeepsCachedFeed` in Task 5.
- A `player` response containing ad breaks → ignored, `PlaybackSource` comes only from `streamingData`; pinned by `ContentParserTest.adBreaksAreIgnored` in Task 2.

---

## File Structure

- `data/.../innertube/ContentClient.kt` — client descriptors and `ContentConfig` types.
- `data/.../innertube/ContentConfigRepository.kt` — bundled defaults, DataStore cache, background remote refresh.
- `data/.../innertube/YouTubeContentClient.kt` — typed `browse/search/player/next/suggestions` calls, chain and error mapping.
- `data/.../innertube/ContentParser.kt` — renderer/streamingData → domain models (extends `InnerTubeParser`).
- `data/.../database/FeedCacheEntity.kt`, `FeedCacheDao.kt` — generic JSON feed cache.
- `data/.../implementation/FeedCacheRepositoryImpl.kt` — cache read/write/clear.
- `data/.../implementation/VideoRepositoryImpl.kt` — rewritten on the new client + cache.
- `domain/.../repository/VideoRepository.kt` — adds `FeedKey` and `observeFeed`/`refreshFeed`.
- `app/.../ui/common/ThumbnailUrls.kt` — direct `i.ytimg.com` URL builder and prefetch helpers.
- `app/.../MyApplication.kt` — tuned `ImageLoader`.
- `app/.../ui/video/PlaybackSourceMapper.kt` — mapping for InnerTube `PlaybackSource`.
- `app/.../ui/video/VideoPlayer.kt` — shared `SimpleCache` and tuned `LoadControl`.

---

### Task 1: Transport, remote config and client chain

**Files:**
- Create: `data/src/main/java/com/example/newtube/innertube/ContentClient.kt`
- Create: `data/src/main/java/com/example/newtube/innertube/ContentConfigRepository.kt`
- Create: `data/src/main/java/com/example/newtube/innertube/YouTubeContentClient.kt`
- Modify: `data/src/main/java/com/example/newtube/di/NetworkModule.kt`
- Modify: `data/build.gradle.kts:13-17`
- Test: `data/src/test/java/com/example/newtube/innertube/ContentClientChainTest.kt`
- Test: `data/src/test/java/com/example/newtube/innertube/ContentConfigRepositoryTest.kt`
- Test: `app/src/androidTest/java/com/example/newtube/InnerTubeDeviceSmokeTest.kt`

**Interfaces:**
- Consumes: `OkHttpClient`, `BuildConfig.CONTENT_CONFIG_URL`.
- Produces:
  - `data class ContentClient(val name: String, val version: String, val hl: String = "uk", val gl: String = "UA")`
  - `data class ContentConfig(val contentClients: List<ContentClient>, val playerClients: List<ContentClient>, val endpoints: Map<String, String>, val ttlMillis: Long, val fetchedAt: Long)`
  - `interface ContentConfigRepository { val config: Flow<ContentConfig>; suspend fun refresh(): Result<ContentConfig>; fun bundled(): ContentConfig; fun decodeConfig(text: String): ContentConfig? }`
  - `class YouTubeContentClient(client: OkHttpClient, config: ContentConfigRepository)` with `suspend fun browse(browseId: String, continuation: String? = null): String`, `suspend fun search(query: String, params: String? = null): String`, `suspend fun player(videoId: String): String`, `suspend fun next(continuation: String): String`, `suspend fun suggestions(query: String): List<String>`, and `class ContentSourceUnavailableException : IOException`.
  - `object ContentEndpoints` with `BROWSE`, `SEARCH`, `PLAYER`, `NEXT`, `SUGGESTIONS`.

- [ ] **Step 1: Write the failing config test**

`ContentConfigRepositoryTest` (mock `DataStore<Preferences>` and use MockWebServer) asserts: `bundled()` returns `contentClients[0].name == "ANDROID"` and version `"20.10.38"`; `decodeConfig` of `{"contentClients":[{"name":"ANDROID_VR","version":"1.60.19"}],"playerClients":[],"endpoints":{},"ttlMillis":21600000,"fetchedAt":0}` produces that client; an HTTP 500 from `refresh()` leaves the previous config unchanged.

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :data:testDebugUnitTest --tests '*ContentConfigRepositoryTest'`
Expected: FAIL, classes missing.

- [ ] **Step 3: Implement config types, JSON decoding, DataStore cache and remote refresh**

`ContentConfigRepository` reads the URL from `BuildConfig.CONTENT_CONFIG_URL`, fetches at most once per `ttlMillis`, caches in DataStore with `bundled()` as fallback. Decode with `kotlinx.serialization` `Json { ignoreUnknownKeys = true }`; validate that `contentClients` and `playerClients` are non-empty before replacing the cached config.

- [ ] **Step 4: Write the failing client-chain test**

`ContentClientChainTest` uses `MockWebServer`: enqueue a 200 `{"playabilityStatus":{"status":"UNPLAYABLE"}}` for the first `player` call, then a 200 with `"status":"OK"`; assert the second call used `ANDROID_VR` and that three consecutive UNPLAYABLE responses throw `ContentSourceUnavailableException`.

- [ ] **Step 5: Implement `YouTubeContentClient`**

Build `POST` bodies with a `context.client` object using the current `ContentClient` (`clientName`, `clientVersion`, `hl`, `gl`), `contentCheckOk`/`racyCheckOk` for `player`, and the TV user agent only for `TVHTML5`. Read `playabilityStatus.status`; treat anything other than `OK` as a chain failure. Apply OkHttp timeouts in `NetworkModule` (connect 10 s, read 15 s) and add `buildConfigField("String", "CONTENT_CONFIG_URL", ...)` with a bundled default in `data/build.gradle.kts`. **Keep** `provideInvidiousService`, `provideRetrofit` and `INVIDIOUS_BASE_URL` for now — Task 3 removes them together with the repository that consumes them, so the build stays green after every task.

- [ ] **Step 6: Add and run the device smoke test**

`InnerTubeDeviceSmokeTest` posts `player` for `dQw4w9WgXcQ`, `search` for `"test"` and `browse` for `"FEtrending"` using the chain; asserts all three return `OK` with non-empty bodies, recording the successful client name in test output. Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.newtube.InnerTubeDeviceSmokeTest` on the connected TCL device. If every client fails, stop and report before continuing.

- [ ] **Step 7: Run unit tests and commit**

Run: `./gradlew :data:testDebugUnitTest :app:testDebugUnitTest`
Expected: PASS.
Commit: `feat: add direct InnerTube transport with own remote config`

---

### Task 2: Content parsers and fixtures

**Files:**
- Create: `data/src/main/java/com/example/newtube/innertube/ContentParser.kt`
- Create: `data/src/test/resources/innertube/browse.json`, `search.json`, `player.json`, `next.json`, `channel.json`
- Create: `data/src/test/java/com/example/newtube/innertube/ContentParserTest.kt`
- Modify: `data/src/main/java/com/example/newtube/innertube/InnerTubeParser.kt`

**Interfaces:**
- Consumes: `ParserResult<T>` from `InnerTubeParser`, `kotlinx.serialization.json.JsonObject`.
- Produces:
  - `object ContentParser` with `fun parseFeed(json: String): ParserResult<Video>`, `fun parseVideoDetails(json: String): ParsedVideoDetails?`, `fun parseComments(json: String): CommentPage`, `fun parseChannel(json: String): ParsedChannel`.
  - `data class ParsedVideoDetails(val video: Video, val description: String, val related: List<Video>, val playback: PlaybackSource)`
  - `data class ParsedChannel(val channel: Channel, val continuation: String?)`

- [ ] **Step 1: Write the failing parser tests**

`ContentParserTest` cases: `parseFeed` on `browse.json` returns ≥5 `Video` with non-blank ids and thumbnails and a non-null continuation; `parseFeed` on `search.json` also reads `compactVideoRenderer`; `parseVideoDetails` on `player.json` returns `playback` from `streamingData.formats`/`adaptiveFormats` and `related` from `contents`; `adBreaksAreIgnored` adds an `adBreaks` array to the fixture and asserts the same `PlaybackSource`; `unknownRenderersAreSkipped` adds a `lockupViewModel` node and asserts no exception and the same item count.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :data:testDebugUnitTest --tests '*ContentParserTest'`
Expected: FAIL, parser missing.

- [ ] **Step 3: Implement `ContentParser`**

Reuse `InnerTubeParser`'s tolerant `walk`/`path`/`text` helpers; read `videoId`, `title.runs[].text`, `ownerText`, `lengthText`/`lengthSeconds`, `viewCountText`, `publishedTimeText`, `thumbnails` (largest) and `channelThumbnailSupportedRenderers` for avatars. Extract first `continuationCommand.token`. For `player`, ignore `adBreaks`/`adPlacement` entirely and map `streamingData` only.

- [ ] **Step 4: Run the tests and commit**

Run: `./gradlew :data:testDebugUnitTest --tests '*ContentParserTest'`
Expected: PASS.
Commit: `feat: parse InnerTube browse, search, player and next responses`

---

### Task 3: Rewrite `VideoRepositoryImpl` on InnerTube

**Files:**
- Modify: `data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt`
- Modify: `data/src/main/java/com/example/newtube/di/RepositoryModule.kt` (if bindings change)
- Modify: `app/src/test/java/com/example/newtube/repository/VideoRepositoryImplTest.kt`
- Delete: `data/src/main/java/com/example/newtube/remote/InvidiousService.kt` and Invidious-only DTOs (`ChannelDto.kt`, `ChannelSearchDto.kt`, `ChannelVideosDto.kt`, `CommentDto.kt`, `FormatStreamDto.kt`, `SearchSuggestionsDto.kt`, `ThumbnailDto.kt`, `VideoDto.kt`)

**Interfaces:**
- Consumes: `YouTubeContentClient`, `ContentParser`, existing Room DAOs, `SettingsRepository`, `HistoryRepository`, `AccountRepository`.
- Produces: unchanged `VideoRepository` suspend methods, each backed by exactly one `browse`/`search`/`player`/`next` call per page (plus one `player` per Shorts clip).

- [ ] **Step 1: Write failing repository tests**

`VideoRepositoryImplTest` mocks `YouTubeContentClient` and asserts: `getHomeFeed(HomeCategory.ALL)` calls `browse` exactly once and never `search`; `getShorts(1)` calls `browse` once for the reel shelf and not `searchChannels`; `getComments(id)` passes `continuation` to `next`; `playbackUsesFreshPlayerResponse` prepares a cached video, calls `getVideoDetails(id)` twice and asserts two `player` calls; `searchSuggestions` uses `suggestions` and returns empty list on failure.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*VideoRepositoryImplTest'`
Expected: FAIL, constructor/service mismatch.

- [ ] **Step 3: Implement the repository against the new contract**

Replace all `service.get*` calls with `contentClient.*` + `ContentParser.*`. Delete `FeedSource`, `ALL_TOPIC_QUERIES`, `usedShortsChannels`, `nextShortsChannels`, `pickQueries` and the Invidious channel-rotation path. Keep `clearFeedCaches()`, the local "Для вас" ranking and the existing content filters. Keep `VideoEntity` write-through for video/channel metadata.

- [ ] **Step 4: Run the tests and commit**

Run: `./gradlew :app:testDebugUnitTest --tests '*VideoRepositoryImplTest'`
Expected: PASS.
Commit: `refactor: serve all feeds from InnerTube instead of Invidious`

---

### Task 4: Room feed cache v5 with stale-while-revalidate

**Files:**
- Create: `data/src/main/java/com/example/newtube/database/FeedCacheEntity.kt`
- Create: `data/src/main/java/com/example/newtube/database/FeedCacheDao.kt`
- Create: `data/src/main/java/com/example/newtube/implementation/FeedCacheRepositoryImpl.kt`
- Modify: `data/src/main/java/com/example/newtube/database/NewTubeDatabase.kt`
- Modify: `data/src/main/java/com/example/newtube/di/DatabaseModule.kt`
- Modify: `domain/src/main/java/com/example/newtube/repository/VideoRepository.kt`
- Create: `data/schemas/com.example.newtube.database.NewTubeDatabase/5.json`
- Create: `data/src/androidTest/java/com/example/newtube/database/FeedCacheMigrationTest.kt`
- Create: `data/src/test/java/com/example/newtube/implementation/FeedCacheRepositoryTest.kt`

**Interfaces:**
- Consumes: Room 2.6.1, `ContentParser` for mapping cached JSON.
- Produces:
  - `sealed interface FeedKey` in `domain/.../repository/VideoRepository.kt` with `data class Home(val category: HomeCategory)`, `data object Shorts`, `data class Search(val query: String, val page: Int)`, `data class Channel(val channelId: String)`, `data object Notifications`.
  - `fun observeFeed(key: FeedKey): Flow<List<Video>>` and `suspend fun refreshFeed(key: FeedKey): Result<List<Video>>` added to `VideoRepository`.
  - `interface FeedCacheRepository { fun observe(key: String): Flow<String?>; suspend fun put(key: String, json: String); suspend fun clearAll() }`

- [ ] **Step 1: Write the failing cache test**

`FeedCacheRepositoryTest` with a fake DAO asserts: `observe` emits the stored JSON; `put` overwrites the same key; `clearAll` empties the table; a JSON string that cannot be parsed does not throw from `put`.

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :data:testDebugUnitTest --tests '*FeedCacheRepositoryTest'`
Expected: FAIL, classes missing.

- [ ] **Step 3: Add Room v5, migration and schema**

`FeedCacheEntity(key: String PRIMARY KEY, json: String, cachedAt: Long)`. `MIGRATION_4_5` creates `feed_cache` and its `cachedAt` index; bump `VERSION` to 5, register the entity and DAO, export `5.json`. Add a Room instrumentation test that migrates a v4 database and inserts/observes one row.

- [ ] **Step 4: Implement `FeedCacheRepositoryImpl` and `observeFeed`/`refreshFeed`**

Serialize `FeedKey` to a stable string via `when` (e.g. `home:ALL`, `shorts`, `search:query:page`). `refreshFeed` fetches from InnerTube, stores the raw JSON and returns parsed videos; `observeFeed` maps stored JSON through `ContentParser.parseFeed` and emits `emptyList()` when absent. Wire into `VideoRepositoryImpl` so `clearFeedCaches()` calls `clearAll()`.

- [ ] **Step 5: Run tests and commit**

Run: `./gradlew :data:testDebugUnitTest :app:testDebugUnitTest`
Expected: PASS.
Commit: `feat: add Room feed cache with stale-while-revalidate`

---

### Task 5: ViewModel wiring and refresh indicators

**Files:**
- Modify: `app/src/main/java/com/example/newtube/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/shorts/ShortsViewModel.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/search/SearchViewModel.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/channel/ChannelViewModel.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/comments/CommentsViewModel.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/notifications/NotificationsViewModel.kt`
- Test: `app/src/test/java/com/example/newtube/ui/home/HomeViewModelTest.kt`
- Test: `app/src/test/java/com/example/newtube/ui/search/SearchViewModelTest.kt`

**Interfaces:**
- Consumes: `VideoRepository.observeFeed(FeedKey)`, `refreshFeed(FeedKey)`.
- Produces: `isRefreshing: StateFlow<Boolean>` on Search, Channel, Comments and Notifications ViewModels; existing `isRefreshing` on Home/Shorts unchanged.

- [ ] **Step 1: Write the failing ViewModel tests**

`HomeViewModelTest.refreshFailureKeepsCachedFeed`: emit a cached feed through `observeFeed`, make `refreshFeed` fail, assert `uiState` still holds the cached list and `isRefreshing.value == false`. `SearchViewModelTest` asserts a query change emits `isRefreshing = true` then `false` and that cached results are emitted before the network result.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*HomeViewModelTest' --tests '*SearchViewModelTest'`
Expected: FAIL, no flow observation in ViewModels.

- [ ] **Step 3: Wire flows and refresh state**

Collect `observeFeed` into `_uiState` on init and on key change; keep `refresh()`/`load()` as `refreshFeed` triggers; on failure leave the cached `UiState.Success` intact and clear `_isRefreshing` in `finally`. Add the same pair of flows/state to the four screens that lack it.

- [ ] **Step 4: Run tests and commit**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.
Commit: `feat: show cached feeds immediately with background refresh`

---

### Task 6: Fast thumbnails and avatars

**Files:**
- Create: `app/src/main/java/com/example/newtube/ui/common/ThumbnailUrls.kt`
- Modify: `app/src/main/java/com/example/newtube/MyApplication.kt:55`
- Modify: `app/src/main/java/com/example/newtube/ui/common/VideoCard.kt:65`
- Modify: `app/src/main/java/com/example/newtube/ui/common/RemoteVideoCard.kt:58`
- Modify: `app/src/main/java/com/example/newtube/ui/common/ShortsTile.kt:43`
- Modify: `app/src/main/java/com/example/newtube/ui/common/ChannelAvatar.kt:41`
- Modify: `app/src/main/java/com/example/newtube/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/shorts/ShortsScreen.kt:81-92`
- Test: `app/src/test/java/com/example/newtube/ui/common/ThumbnailUrlsTest.kt`

**Interfaces:**
- Consumes: Coil 2.5.0, direct `i.ytimg.com` URLs.
- Produces: `object ThumbnailUrls { fun hq(videoId: String): String; fun mq(videoId: String): String; fun prefetch(context: Context, urls: List<String>, sizePx: Int) }`.

- [ ] **Step 1: Write the failing URL test**

`ThumbnailUrlsTest` asserts `hq("abc") == "https://i.ytimg.com/vi/abc/hqdefault.jpg"`, `mq("abc") == "https://i.ytimg.com/vi/abc/mqdefault.jpg"` and that `prefetch` enqueues one `ImageRequest` per URL with `size(sizePx)`.

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*ThumbnailUrlsTest'`
Expected: FAIL.

- [ ] **Step 3: Configure Coil and use sized requests**

`MyApplication.newImageLoader()` sets `MemoryCache` (25 % app memory), `DiskCache` (128 MiB, `filesDir/image_cache`), `respectCacheHeaders(false)` and no crossfade. Replace raw `rememberAsyncImagePainter(url)` with `rememberAsyncImagePainter(ImageRequest.Builder(context).data(url).size(...).build())` using `ThumbnailUrls` in the five card/avatar files; call `prefetch` for the first 6–8 Home cards and for visible Shorts tiles only once per distinct list.

- [ ] **Step 4: Run tests and commit**

Run: `./gradlew :app:testDebugUnitTest --tests '*ThumbnailUrlsTest' :app:ktlintCheck`
Expected: PASS.
Commit: `perf: tune Coil caches and prefetch thumbnails`

---

### Task 7: Cached playback for Watch and Shorts

**Files:**
- Modify: `app/src/main/java/com/example/newtube/ui/video/PlaybackSourceMapper.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/video/VideoPlayer.kt:116-147`
- Modify: `app/src/main/java/com/example/newtube/ui/video/VideoPlayer.kt:283-294`
- Test: `app/src/test/java/com/example/newtube/ui/video/PlaybackSourceMapperTest.kt`

**Interfaces:**
- Consumes: `PlaybackSource` from `VideoDetails`, `CacheDataSource.Factory` from `PlaybackCacheModule`, existing `DefaultPreloadManager` for Shorts.
- Produces: unchanged `PlaybackSource.toMediaSource(context, factory)` signature; Watch uses the shared cache factory and a tuned `DefaultLoadControl`.

- [ ] **Step 1: Write the failing mapper tests**

Add cases to `PlaybackSourceMapperTest`: a `Merged` source with three adaptive audio tracks produces a `MergingMediaSource` with the chosen audio first and two alternatives; a `Progressive` source produces `ProgressiveMediaSource`; a `Hls` source produces `HlsMediaSource`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*PlaybackSourceMapperTest'`
Expected: FAIL for the new cases.

- [ ] **Step 3: Wire the cache into Watch**

In `VideoPlayer.kt`, build `ExoPlayer` with `DefaultLoadControl` values already used by `Media3ShortsPlaybackController` (`1500/5000/1000/1500` ms, `setTargetBufferBytes(96 MiB)`), keep `SHOW_BUFFERING_NEVER`, and pass the Hilt-provided `CacheDataSource.Factory` for every prepare/retry/audio-switch path. Keep signed URLs in memory only.

- [ ] **Step 4: Run tests and commit**

Run: `./gradlew :app:testDebugUnitTest --tests '*PlaybackSourceMapperTest'`
Expected: PASS.
Commit: `perf: use media cache and tuned buffers in Watch player`

---

### Task 8: Remove Invidious completely and ship a release build

**Files:**
- Modify: `README.md`, `SPEC.md`, `TASKS.md`
- Modify: `app/build.gradle.kts`
- Delete: remaining Invidious-only tests/fixtures and stale `src/` mirrors
- Create: `app/proguard-rules.pro` (only if R8 needs keep rules)

**Interfaces:**
- Consumes: nothing new.
- Produces: zero `Invidious`/`api/v1`/`INVIDIOUS_BASE_URL` matches in `app`, `data`, `domain`; a `release` build type with `minifyEnabled = true`.

- [ ] **Step 1: Write the failing grep check**

Add a shell check step with exact command below; it must return no output.

- [ ] **Step 2: Run it to see it fail**

Run: `grep -RniE 'Invidious|api/v1|INVIDIOUS_BASE_URL' app data domain README.md SPEC.md TASKS.md --include='*.kt' --include='*.kts' --include='*.md'`
Expected: FAIL with matches in docs/build files.

- [ ] **Step 3: Clean up and add the release build type**

Delete leftover Invidious code/docs, update README/SPEC/TASKS to describe InnerTube as the sole source and the remote config URL property (`-PcontentConfigUrl=...`), and add an `android.buildTypes.release` block with `isMinifyEnabled = true` and `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`.

- [ ] **Step 4: Run the full verification suite**

Run: `grep -RniE 'Invidious|api/v1|INVIDIOUS_BASE_URL' app data domain README.md SPEC.md TASKS.md --include='*.kt' --include='*.kts' --include='*.md'` → no output; `./gradlew test ktlintCheck assembleDebug` → PASS.

- [ ] **Step 5: Commit**

Commit: `chore: remove Invidious and enable release minification`

---

## Final Verification

1. `./gradlew test ktlintCheck assembleDebug` — green.
2. Device smoke test from Task 1 — all three endpoints `OK` on the mobile network.
3. On-device timings: first Home frame, Shorts entry, video first frame; compare against the 2.6 s baseline from the spec.
4. Manual QA: 15 Shorts swipes, 10 video opens, search, channel, comments — no playback errors, no ads, cached feed appears instantly.
