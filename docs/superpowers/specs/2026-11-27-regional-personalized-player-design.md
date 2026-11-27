# NewTube: Ukrainian-first feed, extended settings and player gestures

- **Date:** 2026-11-27
- **Status:** Draft for approval
- **Size:** Project (full design)
- **Owner:** NewTube

## 1. Intent

The user says the app shows "too much video and Shorts from America and some
Indians", while the YouTube app on the same phone shows more from Ukraine. The
goal is not a literal clone of YouTube's recommendation algorithm: it is to make
the feed **regionally Ukrainian** and to make it **progressively personal**
(history/subscriptions), then let the user tune it.

The user also wants "more settings", explicitly pointing at the YouTube app and
GreenTuber on their phone as the reference, and asked for player gestures
("тапи" and "свайпи") to be implemented with our best judgement rather than
asked about again. They chose **approach 1 + 3**: regional/language parameters
on the API plus a personalised feed, and said "Там подивимось як воно буде
показувати" — the first visible result will be reviewed and adjusted.

Success looks like: Home/Shorts visibly skew Ukrainian by default; a "Для вас"
feed ranks watched/subscribed channels higher; the player has an audio-track
menu, a resize control and YouTube/VLC-style tap/double-tap and swipe gestures;
all of it is configurable from a reorganized Settings screen.

## 2. Goals

1. **Settings model.** Extend `AppSettings`/DataStore with content country,
   content language, preferred audio language, autoplay-next, resume playback,
   double-tap seek length, gesture toggles and sides, resize mode, search
   suggestions, safe mode, age-restricted content and metadata visibility.
2. **Settings UI.** Reorganize `SettingsScreen` into nested sections matching
   the phone references: `Зовнішній вигляд`, `Відео та аудіо`, `Контент`,
   `Жести`, `Історія`, `Про додаток`. The user chose **A**: every new option is
   visible, writable and has real behaviour wherever the API/UI allows it —
   no store-only fake toggles. The client-side pieces are spelled out in §7.8.
3. **Regional feed.** Pass `region` and `hl` from settings into Invidious
   trending, search (including the Shorts search / shelf) and video details so
   the server itself returns Ukrainian/regional results. Default `region=UA`,
   `hl=uk`; never silently fall back to `US` unless the user chooses it. The
   user's complaint names Shorts explicitly, so this also covers the **Shorts
   tab**: its discovery queries and seed channel list become localised (Ukrainian
   queries / Ukrainian channels), including the cache and mediator work that
   this implies.
4. **Personalised feed.** Add a `Для вас` Home category that ranks the mixed
   feed using local watch history and (when signed in) YouTube subscriptions,
   with an explicit Ukrainian-language preference. Ranking is deterministic,
   explainable and unit-testable — no ML and no new network backend.
5. **Player controls.** Add an audio-track menu, and a labelled resize button
   cycling `FIT → FILL → ZOOM` with the choice persisted.
6. **Gestures.** Double-tap left/right thirds to seek (±10 s default, repeated
   taps accumulate) with feedback; single tap keeps the existing
   play/pause-or-controls behaviour per surface. In fullscreen, vertical swipes
   change brightness (left) and volume (right), both configurable, with an
   on-screen indicator.
7. Keep playback, sign-in, history and all existing unit tests working.

## 3. Non-goals

- No YouTube "recommended for you" API and no reverse-engineering of
  YouTube's ranking. The personal feed is local re-ranking of Invidious results.
- No writes to the Google account (likes, watch-later, subscriptions changes).
- No new backend service, no ML model, no cloud sync of preferences.
- No full app-wide localization project; new UI strings go to the default
  (Ukrainian) `res/values/strings.xml`.
- No change to the InnerTube playback path; no SABR/PO tokens.
- No in-player quick-settings redesign beyond the new audio/resize buttons and
  the gesture layer. The deep "advanced ExoPlayer internals" settings that
  GreenTuber has (tunneling, decoder fallback) are out of scope.
- No Compose UI tests in this pass; unit-test the pure logic instead.

## 4. Anti-goals

- The app must remain usable signed out and offline (cached feed / graceful
  error) — personalisation is additive.
- Sign-in or history reads must never block Home or playback.
- Do not add a global `clickable` overlay that swallows `PlayerView`'s own
  controller taps; gestures must coexist with the controller.
- Do not make a single tap seek on Watch (YouTube/NewPipe muscle memory:
  single tap toggles controls or playback, double tap seeks).
- Do not silently override the user's chosen region/language with the device
  locale on every launch.
- Do not keep a personalised ordering that changes on every recomposition —
  ranking must be computed once per feed load.

## 5. Constraints and findings

### Codebase

- Native Android, Kotlin, Jetpack Compose (BOM 2024.03 / Material3 1.2.1),
  Hilt, `:app` / `:data` / `:domain` modules, minSdk 24, target 34.
- Media3 ExoPlayer 1.4.1 (`:app`), `DefaultTrackSelector` and `Tracks` already
  used in `ui/video/VideoPlayer.kt`; `PlayerView` supplies the controller and
  `AspectRatioFrameLayout` (`setResizeMode`).
- `VideoPlayer.kt:75` already accepts `resizeMode` with `RESIZE_MODE_FIT`;
  `:92` owns the track selector; `:172-183` already listens to `onTracksChanged`
  but only collects video heights.
- `VideoPlayer.kt:231-243` has one full-screen `clickable` overlay gated by
  `tapToTogglePlayback`; there is no `pointerInput`, no drag/tap-zone or
  volume/brightness gesture code anywhere in the repo.
- `InvidiousService.kt:15-19` declares `getTrending(region = "US")`;
  `VideoRepositoryImpl.kt:45-48,84` calls it without a region, and
  `getShorts`/`searchVideos` pass no region either. This is the direct cause of
  the US-heavy feed.
- Invidious API docs: `trending` and `search` accept `region` (ISO 3166,
  default `US`); every JSON endpoint accepts `hl`; `/popular` has no region
  parameter. `videos/:id` documents `region` too — verify in the plan before
  relying on it for recommendations.
- `AppSettings.kt` has 4 fields; `SettingsRepository` has 4 setters;
  `DataStoreSettingsRepository.kt` maps via a pure `Preferences.toAppSettings()`
  (unit-tested in `SettingsMappingTest`).
- `SettingsScreen.kt` is one scrollable column; `NavGraph.kt:66` has a single
  `"settings"` route. `SettingsComponents.kt` already has row/switch helpers.
- `HistoryRepository.observeLocal(limit)` exposes the last 500 local watches
  (`WatchHistoryEntry` has `videoId/title/channelName/channelId/watchedAt`).
- `AccountRepository.state` + `subscriptions()` give `AccountPage<RemoteVideo>`
  with `channelId` when signed in. No subscription writes.
- `getHomeFeed(category)` fetches several sources in parallel, flattens,
  `distinctBy{id}.shuffled().take(HOME_FEED_LIMIT)`; that shuffle must be
  replaced by ranking for the personalised category only.
- Home caches feeds per `HomeCategory` and Shorts is served from the Room cache
  first. Changing content country/language therefore has to **invalidate both
  caches and refetch immediately**; otherwise the user cannot see the effect of
  the setting they just changed.
- `FormatStreamDto` currently parses `url/itag/type/container/qualityLabel/
  audioQuality/bitrate` but not a per-format language; `PlaybackSource.Merged`
  carries one video URL and one audio URL.

### Device recon (TCL 6102H, Android 12) — read-only via ADB

- YouTube → `Загальні`: `Місцезнаходження = Україна`, autoplay-in-feed, safe
  mode; `Мови → Бажані мови` is an ordered language list ("Мови, якими ви хочете
  дивитися відео"); `Налаштування відео й аудіо → Відтворення`:
  `Автовідтворення наступного відео`, `Двічі торкнутися, щоб перемотати = 10
  секунд`, always-fullscreen, PiP.
- GreenTuber (`by.green.tuber`) → `Налаштування`: `Відео та аудіо`,
  `Зовнішній вигляд`, `Історія та кеш`, `Контент`, `Повідомлення`,
  `Завантажити`. `Відео та аудіо` has default/popup/Shorts resolution,
  mobile-data cap, video/audio format, thumbnail previews, minimize behaviour,
  autoplay, auto-queue, resume, and gesture switches for **volume, brightness,
  zoom, speed**. `Контент` has app language, original-audio preference,
  **content language**, **content country**, age-restricted content, safe mode,
  search suggestions, show metadata.
- Observation is a UI inventory; we copy the *information architecture* and
  labels, not GreenTuber's code.

### Research

`docs/research/2026-11-27-newpipe-player-features.md` (cited to NewPipe/NewPipeExtractor
commits and Media3 docs) establishes:

- NewPipe recommendations are just extractor-provided `relatedItems`; no
  personalisation. It shows them as a tab/side panel plus optional auto-queue.
- NewPipe audio selection is source-level (`audioTrackId`, reload queue);
  Media3 offers `TrackSelectionOverride` as a gapless in-band alternative.
- Resize is `FIT → FILL → ZOOM` (FILL = stretch, ZOOM = crop), persisted.
- Single tap only toggles controls; thirds-based multi-double-tap seeks with a
  550 ms accumulator; fullscreen-only half-screen swipes, left brightness /
  right volume, each configurable.
- Quick settings are two rows: primary and "more"; menus are rebuilt from live
  metadata.

## 6. Approaches considered

| Approach | Trade-off | Decision |
|---|---|---|
| **A. Region/language on Invidious requests** (`region`, `hl`) | Changes what the server returns — closest to YouTube's location/preferred languages. `/popular` and `recommendedVideos` cannot be regionalized. | **Chosen** |
| **B. Local Cyrillic filter/rank only** | Cheap, but cannot create Ukrainian items the API never returned; heuristic is fragile. | Rejected as the only mechanism; kept as a small tie-breaker inside the ranker |
| **C. Personal feed from local history + subscriptions** | Real "мої" behaviour using data we already have; deterministic ranking, no backend. Bigger scope. | **Chosen (phase 2)** |
| **D. Switch to a Ukraine-local Invidious instance** | Instance health/legal risk outside our control; does not guarantee language. | Rejected |
| **Audio: in-band `TrackSelectionOverride`** | Gapless, simple, per-media-item; needs reapply on `onTracksChanged`. | **Chosen for HLS/progressive** |
| **Audio: source-level re-resolve (NewPipe)** | Needed when alternate audio is a separate URL; requires position save/restore. | **Fallback for `Merged`**, deferred if no language data exists |
| **Settings: one long list vs nested sections** | Nested matches both reference apps and keeps each screen readable. | **Chosen: nested sections** |
| **Gesture taps: single-tap zones vs double-tap thirds** | Single-tap zones conflict with `PlayerView` and YouTube muscle memory. | **Chosen: double-tap thirds** |

The user handed the gesture "how" to us ("роби як найкраще"), so those choices
are ours, not open questions.

## 7. Design

### 7.1 Settings model and storage

`domain/.../model/AppSettings.kt` gains (all with safe defaults):

```kotlin
data class AppSettings(
    // existing
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val recordHistory: Boolean = true,
    val useRemoteHistory: Boolean = true,
    val defaultQuality: Int = DEFAULT_QUALITY,
    // content (default UA / uk, matching the user's phone)
    val contentCountry: String = "UA",
    val contentLanguage: String = "uk",
    val preferredAudioLanguage: String = "uk",
    val preferOriginalAudio: Boolean = true,
    val showSearchSuggestions: Boolean = true,
    val ageRestrictedContent: Boolean = false,
    val safeMode: Boolean = false,
    val showMetadata: Boolean = true,
    // playback
    val autoplayNext: Boolean = true,
    val resumePlayback: Boolean = true,
    val doubleTapSeekSeconds: Int = 10,
    val resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT, // keep Int, no Media3 dep in :domain
    val videoBrightness: Float? = null,
    // gestures
    val gestureVolume: Boolean = true,
    val gestureBrightness: Boolean = true,
    val gestureZoom: Boolean = false,
    val gestureSpeed: Boolean = false,
    val volumeGestureSide: GestureSide = GestureSide.RIGHT,
    val brightnessGestureSide: GestureSide = GestureSide.LEFT,
)
```

- New domain enums: `GestureSide { LEFT, RIGHT, OFF }` (OFF = disabled for that
  side), `ContentCountry` / `ContentLanguage` as plain ISO strings validated by
  an allowlist kept in `domain` (ISO 3166 alpha-2 / BCP-47 language codes).
  Do not pull Android `Locale` into `:domain`.
- `SettingsRepository` keeps the four existing setters and gains one generic
  `suspend fun update(transform: (AppSettings) -> AppSettings)`; existing
  setters delegate to it so callers and tests keep compiling. `ViewModel` uses
  `update` for every new option. `DataStoreSettingsRepository` adds matching
  preference keys and extends the pure `toAppSettings()` mapping (clamp invalid
  ints, reject unknown side/country/language values, clamp brightness 0.01..1f).
- `resizeMode` is stored as an Int constant set matching Media3's values, with
  the numeric constants mirrored in `domain` so `:domain` stays pure.

### 7.2 Settings UI

Add routes in `NavGraph.kt`: `"settings/video"`, `"settings/content"`,
`"settings/gestures"`. Keep `"settings"` as the hub with clickable rows.
Existing rows are **not** removed: the account/sign-in row stays at the top of
the hub (as today), and a `Зовнішній вигляд` section with the theme row is added
before `Контент`, matching GreenTuber's structure. Then:

- `Відео та аудіо`: default quality (existing dialog), default Shorts quality,
  quality cap on mobile data, preferred audio language, original-audio switch,
  autoplay next, resume playback, double-tap seek length (5/10/15/30 s),
  fullscreen-on-rotation if cheap to read from the platform.
- `Контент`: app language (best-effort, see §7.8), **content country**,
  **content language**, search suggestions, age-restricted content, safe mode,
  show metadata — all functional, not decorative.
- `Жести`: volume gesture, brightness gesture, side mapping for each
  (ліва/права/вимкнено), zoom and speed gesture toggles (store the settings now;
  wire zoom/speed only if cheap — otherwise hide them until implemented).
- `Історія`: existing history rows.
- `Про додаток`: existing about row.

Reuse `SettingsRow`, `SettingsSwitchRow` and add a small single-choice
`SettingsChoiceDialog` in `SettingsComponents.kt` for the enums/ISO lists. All
labels via `stringResource` in Ukrainian; update `SettingsViewModel` with
`setContentCountry`, `setContentLanguage`, `setGesture*`, etc., delegating to
`SettingsRepository.update`.

### 7.3 Region/language propagation

- `InvidiousService`: add `@Query("region") region: String?` and
  `@Query("hl") hl: String?` to `getTrending`, `getShorts`, `searchVideos`,
  `getVideoDetails`; keep defaults for callers/tests that omit them. `getPopular`
  stays unchanged (no API support).
- `VideoRepositoryImpl` injects `SettingsRepository`; each network call reads
  `settings.first()` (cheap DataStore read) and passes `country`/`language`.
  For `getHomeFeed`, read once and pass to every `FeedSource.load()`.
- `FeedSource.Search` queries stay the same English topic strings, but `region`
  and `hl` now constrain the results. `getShortsShelf` and `getShorts` use the
  same preference.
- The Shorts tab's discovery queries (currently English topic strings) and the
  seed channel ids in `VideoRepositoryImpl.nextShortsChannels` become
  Ukrainian/regional: prefer Ukrainian queries and Ukrainian channels, keeping
  the old pool only as a fallback when no regional source yields results. This
  touches `ShortsDataStore` / `ShortsRemoteMediator` / `PreloadWindow`; their
  tests must stay green.
- `PlaybackSourceMapper` / player are untouched by this change; `hl` affects
  metadata only.
- Add a repository test with a fake `InvidiousService` asserting the exact
  `region`/`hl` values reach trending/search/shorts, and that changing settings
  changes the next call.

### 7.4 Personalised "Для вас" feed

- `HomeCategory` gains `FOR_YOU` as the first chip; `HomeCategoryLabels.kt`
  maps it to a new `category_for_you` string. `HomeViewModel` keeps its existing
  cache/token machinery; `FOR_YOU` simply calls a new repository method.
- `domain/.../model/FeedSignals.kt`:

```kotlin
data class FeedSignals(
  val channelAffinity: Map<String, Float>, // 0..1, recency-weighted local history
  val subscribedChannelIds: Set<String>,
  val preferredLanguage: String,
)
```

- `domain/.../usecase/RankFeed.kt` — pure function (no Android, no coroutines):

```kotlin
fun rankFeed(videos: List<Video>, signals: FeedSignals): List<Video>
```

  Score per video = `2.0f * affinity(channelId)` +
  `1.0f * if (channelId in subscriptions) 1f else 0f` +
  `1.5f * languageMatch(title/author, preferredLanguage)` +
  `0.5f * normalizedViews`. Then: stable sort by descending score, cap at 3
  items per channel, keep `HOME_FEED_LIMIT`, preserve original relative order
  for equal scores. `languageMatch` uses a Unicode-script heuristic (Cyrillic
  chars) plus an explicit title/channel token match for `uk`/`ru`; never import
  Android `Locale` into `:domain`.
- `VideoRepository.getForYouFeed()`: fetch regionalised `getHomeFeed(ALL)` via
  the existing sources, then build signals from
  `HistoryRepository.observeLocal(500).first()` (weight = `1/(1+ageDays)` per
  watch) and `AccountRepository` (only when `state.value is AccountState.SignedIn`;
  `subscriptions()` failure is ignored), apply `rankFeed`, fall back to the
  plain ranked-by-language feed if both signal sources are empty.
- Inject `HistoryRepository` and `AccountRepository` into `VideoRepositoryImpl`
  (Hilt bindings already exist). No new Room tables or migrations.
- `HomeViewModel` does not get new coroutine logic beyond choosing which
  repository method to call; the shuffle in `getHomeFeed` stays for the other
  categories.
- Setting `personalizedFeedEnabled` is **not** added: the chip itself is the
  opt-in, and the user can keep using `Усі`.

### 7.5 Player controls: audio tracks and resize

`ui/video/VideoPlayer.kt`:

- State: `availableResizeModes` is just the persisted `resizeMode` passed from
  `WatchViewModel`/settings; add `resizeMenuOpen`, `audioMenuOpen`,
  `audioTracks: List<AudioTrackOption>`, `selectedAudioKey: String?`.
- `onTracksChanged`: keep the existing height collection, and additionally
  collect `C.TRACK_TYPE_AUDIO` groups → for each `group.length`, read
  `Format.language`, `roleFlags` and `group.isTrackSelected`. Build labels as
  `Language (type)` where type is one of Original/Dubbed/Descriptive/Secondary
  (from `C.RoleFlags_*`), falling back to the raw language or "Невідома доріжка".
  Hide the button when fewer than two options (NewPipe rule).
- Selecting an option applies
  `trackSelector.setParameters(trackSelector.buildUponParameters().setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index)))`.
  Reapply the stored preference on every `onTracksChanged` / new `MediaItem`
  or it silently resets; key options by immutable `Format.language + roleFlags
  + group index`, never by index alone.
- For `PlaybackSource.Merged` with separate audio URLs: if `adaptiveFormats`
  exposes a language-tagged audio entry, extend `FormatStreamDto` with
  `language: String?` and add a parallel audio URL to `PlaybackSource.Merged`;
  switching calls `onSavePosition(currentPosition)` then rebuilds the source
  through `holder.prepare(...)`, mirroring NewPipe. If the instance gives no
  language data, hide the audio button for Merged sources and document why —
  do not invent tracks.
- Resize button in the top-end control row next to quality: a `TextButton`
  showing `Впис.` / `Заповн.` / `Обріз.`, cycling FIT → FILL → ZOOM via
  `settings.resizeMode` and calling `VideoPlayer` with the new `resizeMode`;
  `PlayerView.setResizeMode()` is applied via `AndroidView` update. Persist in
  DataStore. Use `pixelWidthHeightRatio` if any custom aspect handling is
  added; otherwise let `PlayerView` own it.

### 7.6 Gestures

New `ui/video/PlayerGestures.kt` (Compose + Media3, no domain deps):

- A `Modifier` / small composable applied over `PlayerView` inside
  `VideoPlayer`'s `Box`. It receives: `enabled`, `isFullscreen`, `player`,
  `seekSeconds`, `gesture flags/sides`, `onControlsVisibilityChange`,
  `onBrightnessChange`, `onVolumeChange`.
- `pointerInput` with a single gesture coroutine (avoid stacking two
  detectors that fight over the same pointer):
  - **Tap:** classify by x into thirds. Watch: middle single tap =
    controls/playback via existing behaviour; left/right double tap within
    550 ms = seek `∓seekSeconds` per tap, accumulating a counter shown in a
    `SeekFeedback` overlay that flips to the tapped side. Guard rewind at
    `position < 500 ms` and forward at the end. Shorts keeps its existing
    single-tap play/pause (pass a flag).
  - **Drag:** only when `isFullscreen`, only after a 40 px vertical threshold
    and `|dy| >= |dx|`; side is decided by the initial x using halves. Left →
    brightness, right → volume, unless the corresponding setting is OFF or the
    side is reassigned. Apply `AudioManager.setStreamVolume(MUSIC,
    round(max * fraction), 0)` and `Window.LayoutParams.screenBrightness` on
    the activity; persist brightness via `settings.videoBrightness`. Show a
    translucent side bar + percentage for ~600 ms after the gesture.
  - System-bar areas and non-fullscreen drags are ignored.
- If both actions end up assigned to the same side, the settings UI swaps them
  so each side has at most one action — no silent "which wins" ambiguity.
- Extract pure helpers (`classifyThird(x,width)`, `classifyHalf(x,width)`,
  `seekDelta(taps, side, seconds)`, `fractionFromDrag(dy,height,current)`) into
  an internal object so they can be unit-tested without a device.
- Keep the existing `PlayerView` controller enabled on Watch; on Shorts keep
  `useController=false` and `tapToTogglePlayback` semantics.

### 7.7 Related videos and autoplay (small, tied to goal 4)

- `WatchScreen` keeps the existing `details.related` shelf.
- `autoplayNext` (default on) shows a single `Далі: <title>` row above the
  shelf, using `related.first()`; tapping plays it, and when the current video
  ends `WatchScreen` navigates to that id unless the user disabled the setting.
  Dedup by video id. This mirrors NewPipe's auto-queue without adding a queue
  screen.

### 7.8 Content settings — real behaviour (user chose A)

- **Content country / language:** already covered by §7.3, plus immediate cache
  invalidation for Home and Shorts.
- **Preferred audio language / original audio:** passed to Media3 constraints
  (`setPreferredAudioLanguage`) and applied as the default in the audio menu
  (§7.5); original-audio preference is the tie-breaker when languages match.
- **Search suggestions:** declare `GET api/v1/search/suggestions` in
  `InvidiousService` (`q`, `region`, `hl`), add a debounced (≈250 ms)
  `suggestions: StateFlow<List<String>>` to `SearchViewModel`, and render a
  dropdown under the `OutlinedTextField` in `SearchScreen`. Tapping a
  suggestion fills the query and runs the search. A failing/empty endpoint
  simply shows no dropdown. Do not add an `icons-extended` dependency; use the
  existing core icons.
- **Age-restricted content / safe mode:** extend `VideoDto` with
  `isFamilyFriendly: Boolean = true` and filter **in the data layer before**
  `toDomainVideos()` (no Room column, no migration). When
  `ageRestrictedContent == false`, drop `isFamilyFriendly == false` items.
  When `safeMode == true`, additionally drop items whose title/author matches a
  small Ukrainian/English adult-keyword denylist kept in `:data`. Document this
  as a best-effort client-side filter — Invidious exposes no documented
  `safe`/age API parameter. Apply to Home, Shorts, Search, related videos and
  the personal feed (one shared filter helper).
- **Show metadata:** add `showMetadata: Boolean = true` to `VideoCard`,
  `ShortsTile` and related card call sites. When false, hide channel name,
  views, upload time and duration badges but keep title, thumbnail and tapping.
  Read the setting once per screen from `SettingsRepository`.
- **App language:** best-effort only. On API 33+ call
  `LocaleManager.setApplicationLocales` from `SettingsViewModel`; on older
  devices keep the system locale and show the row's subtitle explaining the
  limitation. Do **not** add AppCompat or a full localization project; strings
  stay Ukrainian by default.

## 8. Testing plan

- Update `SettingsMappingTest` for every new key/default/invalid value.
- Update `SettingsViewModelTest` for the new setters and `update` delegation.
- Extend `VideoRepositoryImplTest`: region/language reaching trending/search/
  shorts; personal feed ranking with known history/subscriptions; language tie
  breaker; channel cap; empty-signals fallback.
- New pure unit tests: `RankFeedTest`, `GestureMathTest` (thirds/halves,
  accumulating seek, fraction clamping), and a content-filter test for the
  age-restricted/safe-mode helper.
- `SearchViewModelTest`: suggestions are debounced, mapped and cleared on empty
  query; failures do not break results.
- Keep `ExoPlayerHolderTest`, `PlaybackSourceMapperTest`, `WatchViewModelTest`,
  Shorts tests and instrumented Room tests green.
- No new Compose UI tests in this pass.

## 9. Delivery order

1. Settings model, repository, mapping, UI scaffold and tests.
2. Region/language propagation + repository tests.
3. `Для вас` chip, ranker and repository/VM wiring + tests.
4. Resize cycle + audio-track menu (HLS/progressive first).
5. Gesture layer and overlays; Merged-source audio fallback only if the data
   supports it.
6. Related/autoplay-next row, final test run, update `README.md`/`SPEC.md`
   if they enumerate features.

Each step ends with the existing test suite green before the next begins.

## 10. Explicitly left to the builder

- Exact Compose pointer-event implementation (single detector vs layered
  detectors) and the overlay animation details.
- Whether to expose zoom/speed gesture toggles in UI before those gestures are
  implemented (hide them until wired, or implement them cheaply).
- Whether `videos/:id` really honours `region` on our instance — verify against
  the live API before relying on it for `recommendedVideos`.
- Field name for per-format audio language on our Invidious instance; if absent,
  take the documented fallback.
- Whether to add a Shorts-specific quality setting now (store it in phase 1,
  wire it in phase 2 if the Shorts player reads settings).

## 11. Sources

- Local research: `docs/research/2026-11-27-newpipe-player-features.md`
- Invidious API: https://docs.invidious.io/api/ (`trending`, `search`, `popular`,
  language/`hl`)
- Device recon: YouTube and GreenTuber settings screens on the connected TCL
  6102H (read-only ADB `uiautomator dump`, 2026-11-27)
- Prior design conventions: `docs/superpowers/specs/2026-11-12-youtube-home-account-design.md`
