# NewTube – Task Tracker

## Current Status

Legend: ✅ done · ⚠️ partial · ⏳ not started

| # | Task | Done |
|---|------|------|
| 1 | Project repo created on GitHub (NewTube) | ✅ |
| 2 | Repository skeleton (Gradle, CI, README, SPEC) | ✅ |
| 3 | MainActivity + NavHost + bottom navigation | ✅ |
| 4 | Compose themes (Material3, Colors, Shapes, Typography) | ✅ |
| 5 | Hilt setup (app module) | ✅ |
| 6 | Basic ViewModels for each screen | ✅ |
| 7 | Retrofit + Moshi + InvidiousService interface | ✅ |
| 8 | Domain models: Video, Channel, Short | ✅ |
| 9 | VideoRepository interface | ✅ |
|10| Data layer: Room entity, DAO, DB | ✅ |
|11| Dependency modules (Network, Database, Repository) | ✅ |
|12| VideoRepositoryImpl (mapping & caching) | ✅ |
|13| Integration of :domain and :data modules in app | ✅ |
|14| Gradle wrapper (8.6) committed, no local Gradle needed | ✅ |
|15| Build fixed end‑to‑end: AGP 8.2.2, Kotlin 1.9.24, Compose Compiler 1.5.14, compileSdk 34 | ✅ |
|16| `:data` / `:ui` converted from Kotlin/JVM to Android libraries (they consume AARs) | ✅ |
|17| AndroidManifest: launcher activity, INTERNET permission, no legacy `package=` attr | ✅ |
|18| Invidious DTOs aligned with the real payload (`videoId`, `author`, `lengthSeconds`, `liveNow`, thumbnails array, mixed search results) | ✅ |
|19| Stream URL: broken `/api/v1/streams/{id}` replaced by a chain now split by content type: live → `hlsUrl`, VOD → `formatStreams` → merged adaptive (avc1 ≤720p + `mp4a.40.2`) → `hlsUrl` | ✅ |
|20| Configurable Invidious instance via `-PinvidiousBaseUrl=` (BuildConfig field) | ✅ |
|21| ktlint wired into the build (`ktlintCheck` / `ktlintFormat`), code formatted | ✅ |
|22| Unit tests green (7 tests: repository mapping/stream selection + 4 ViewModels) | ✅ |
|23| `channelId` added to `Video`/`VideoEntity` (Room v2 + `MIGRATION_1_2`), channel navigation uses the `UC…` id | ✅ |
|24| Domain models for the watch screen: `VideoDetails`, `PlaybackSource` (HLS/progressive/merged), `CommentPage`, `ChannelVideosPage` | ✅ |
|25| Invidious endpoints: `/videos/{id}` (description, recommendations, streams), `/comments/{id}` (`continuation`, `commentCount`), channel uploads by `continuation` | ✅ |
|26| Watch screen: player on top (16:9, not full screen), title/views/date, channel row, expandable description, comments button, related videos | ✅ |
|27| Player controls: quality menu (auto / ≤ Np via `DefaultTrackSelector`), fullscreen (landscape, hidden system bars, same player instance), PIP, resume position | ✅ |
|28| Comments screen: first page, total count, pinned badge, avatars, `continuation` paging | ✅ |
|29| Shorts rewritten as a vertical swipe pager: one clip per screen, autoplay + loop, lazy stream resolution, filter `1..60s`, pads to 10 clips (max 3 page attempts) | ✅ |
|30| Home: trending + refresh button (no pull‑to‑refresh), loading/error/retry/empty states | ✅ |
|31| Search: 500 ms debounce, `page` pagination on scroll, error/retry | ✅ |
|32| Channel screen: header + uploads with `continuation` paging, error/retry | ✅ |
|33| `UiState` + shared loading/error/empty screens, `Throwable.toUserMessage()`, count/duration formatting | ✅ |
|34| Playback position persisted in DataStore (`PlaybackPositionRepository`) | ✅ |
|35| Unit tests extended to 25 (repository mapping, stream selection, livestream payloads, 6 ViewModels) | ✅ |
|36| Live API smoke check against the default instance (trending, shorts, details/recommendations, comments, channel continuation) | ✅ |
|37| Fix: trending is mostly `type=livestream`; the mapper used to drop those payloads → watch screen showed Retry. The `type` whitelist is gone, non-video entries are excluded by the missing `videoId` | ✅ |
|38| Playback errors (`PlaybackException`) are now shown in the player with a Retry button and written to logcat | ✅ |
|39| Home feed composer: parallel trending (Default + Gaming) / popular / topic-search sources, dedupe, shuffle, 60-item cap; a single source failure is ignored | ✅ |
|40| Shorts built from real `/channels/{id}/shorts` of channels discovered by name search: `1..180` s, no live, dedupe, 3 channels per page, curated fallback, reshuffle | ✅ |
|41| Home category chips (All/Music/Gaming/Movies/Sports/News/Technology) with in-memory per-category cache and stale-response protection | ✅ |
|42| Shorts refresh button; empty feed becomes an error with Retry; a page of already-shown ids no longer ends the feed | ✅ |

## Next Steps (planned)

1. **Read‑through caching** – serve lists from Room when offline, add `NetworkBoundResource`‑style repository methods.
2. **DataStore preferences** – dark mode toggle, default quality, instance URL override at runtime.
3. **Live stream metadata** – surface `liveNow`, live badges and DVR handling.
4. **Comment interactions** – replies, likes, sorting.
5. **UI tests** for navigation flows and player controls (Compose test rule); manual device pass on a real APK.
6. **Cleanup** – remove the stale root `src/` folder and decide whether the empty `ui` module is still needed.
7. **Release** – add `LICENSE` (MIT), signing config, `assembleRelease`, tagged 1.0.0.
8. **Feature: channel subscriptions** – database for the subscription list.

> All tasks will be tracked in this markdown and updated as they complete.

## Recently fixed bugs

* `NetworkModule.BASE_URL` pointed at `invidious.snopyta.org`, dead since 2022 → now a `BuildConfig` field with a working default.
* `InvidiousService.getStream` declared `String` for a JSON endpoint that does not exist at all → playback URL is derived from the single‑video endpoint.
* `./gradlew ktlintCheck` (used by CI) failed with “task not found” → ktlint plugin applied to all modules, `.editorconfig` tuned, sources formatted.
* DTOs didn’t match the Invidious payload, so every list request failed at parse time with `JsonDataException: Required value 'videoId' missing` → DTOs rewritten, non‑video search results filtered out.
* Missing trailing commas / import order / `BottomNavigation` (Material 2 in a Material 3 app) / missing `getValue`, `clip`, `padding`, `AndroidView` imports → code compiles again.
* Channel navigation passed the channel *name* to `channel/{id}`, which the API rejects with a 500 → `Video.channelId` is now mapped from `authorId` (Room v1→v2 migration) and every list item navigates with the `UC…` id.
* Shorts tab was a plain list of mixed search results → now a vertical swipe pager with one autoplaying clip per screen, `1..60s` filter and lazy stream resolution.
* Shorts showed horizontal videos because `#shorts` search on the instance returns landscape clips → the feed is now built from `/channels/{id}/shorts` of discovered Shorts channels, filtered to `1..180s` and non-live.
* Watch screen used the player as the whole screen → player is now 16:9 at the top with title, channel, description, comments and related videos below.
* Player had no controls → quality menu (auto/≤Np), fullscreen (landscape, hidden bars, same ExoPlayer instance), PIP and resume position.
* ViewModels swallowed exceptions and showed empty lists → `UiState` + shared loading/error/empty screens with a retry button.
* Search fired a request per keystroke and never paged → 500 ms debounce plus `page` pagination; channel uploads and comments page through `continuation`.
* Room schema change without a migration would crash on upgrade → `MIGRATION_1_2` adds `videos.channelId` with a default value.
* Every video opened from Home showed “Retry”: the trending feed mostly returns `type=livestream` while the mapper accepted only `type=video` and threw `IllegalStateException`. Fixed by filtering on `videoId` instead of `type` (unit tests added).
