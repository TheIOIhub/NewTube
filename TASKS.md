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
|43| Theme: dark/light palettes, system bars, XML splash colours, `values-night`, purple accent | ✅ |
|44| DataStore settings (`ThemeMode`, record/remote history, default quality 480/720/1080) + `SettingsRepository` | ✅ |
|45| Settings screen with theme picker; `NewTubeTopBar` variants and Ukrainian strings | ✅ |
|46| Room v3: `watch_history` table, `videos.authorAvatarUrl`, `MIGRATION_2_3`, schemas `2.json`/`3.json` | ✅ |
|47| Channel avatars mapped from `authorThumbnails` (largest ≤176 px) | ✅ |
|48| YouTube-style `VideoCard`, `ChannelAvatar`, metadata line; shared across Home/Search/Channel/Watch | ✅ |
|49| Home Shorts shelf from one lightweight `#shorts` search | ✅ |
|50| Five-tab shell (Головна, Shorts, Пошук, Підписки, Ви); You/Subscriptions/Notifications/History/About screens; bar hidden on detail screens | ✅ |
|51| Home redesign: brand top bar, category chips, Shorts shelf, skeletons, empty/error states | ✅ |
|52| Open the Shorts tab at a specific clip from a shelf tile (`shorts?videoId=…`) | ✅ |
|53| Local watch history repository (upsert, 500-row trim, clear) + `clearAll()` for resume positions | ✅ |
|54| Record watches from Watch and Shorts (3 s settle, cancel on tab exit), gated by the setting | ✅ |
|55| History screen backed by Room with relative Ukrainian timestamps | ✅ |
|56| Complete Settings: history switches, confirmed clear (also resume positions), maximum quality, About | ✅ |
|57| Maximum-quality cap applied to stream selection (`getVideoDetails(videoId, maxHeight)`) | ✅ |
|58| Formatter edge-case tests, docs update and final device QA pass | ✅ |
|59| TV device-code OAuth spike: scrape `clientId`/`clientSecret` from the TV base JS, confirm `returnPersonaAccounts`, 401 on signed-out `FEhistory` | ✅ |
|60| `YouTubeOAuthApi`: device-code request, token polling (`authorization_pending`/`slow_down`/`access_denied`/`expired_token`), refresh with `invalid_grant` mapping | ✅ |
|61| Tolerant InnerTube parser: `tileRenderer`/`reelWatchEndpoint`, run titles, durations, dedupe by id, TV continuation, account parser | ✅ |
|62| `AccountRepositoryImpl` + `EncryptedSharedPreferences` token store: cold login flow, shared refresh mutex, expiry/back-off, `SignedOut`/`SignedIn`/`Expired` | ✅ |
|63| Login screen: device code, countdown, open `google.com/device`, copy code, cancel/retry, Hilt `login` route | ✅ |
|64| You tab account header and confirmed sign-out; Settings Акаунт section (sign in / name+email / sign out) | ✅ |
|65| Subscriptions as the TV recent-video feed: two-column grid, continuation paging, skeletons, CTA, expired banner | ✅ |
|66| History screen switches to read-only YouTube history when enabled; remote rows never persisted; local fallback banners | ✅ |
|67| Best-effort notifications screen from `FEnotifications` with empty state and retry | ✅ |
|68| Account strings, README/SPEC/TASKS update, 145 JVM tests and device QA on the TCL 6102H | ✅ |

## Phase 1–3 plan

Tasks 43–58 were executed from [`docs/superpowers/plans/2026-11-12-youtube-home-ui-and-local-history.md`](docs/superpowers/plans/2026-11-12-youtube-home-ui-and-local-history.md); the binding design is [`docs/superpowers/specs/2026-11-12-youtube-home-account-design.md`](docs/superpowers/specs/2026-11-12-youtube-home-account-design.md). Progress notes and task-by-task rulings live in `.superpowers/sdd/2026-11-12-youtube-home-ui-and-local-history/progress.md`.

## Phase 4–5 plan

Tasks 59–68 were executed from [`docs/superpowers/plans/2026-11-20-google-account-phases-4-5.md`](docs/superpowers/plans/2026-11-20-google-account-phases-4-5.md); the same design document is binding (spike corrections are folded into §13/§15.3/§18.2/§22). Progress notes live in `.superpowers/sdd/2026-11-20-google-account-phases-4-5/progress.md`.

## Next Steps (planned)

1. **Read‑through caching** – serve lists from Room when offline, add `NetworkBoundResource`‑style repository methods.
2. **Runtime instance switch** – choose/validate an Invidious base URL in Settings instead of a build flag.
3. **Live stream metadata** – surface `liveNow`, live badges and DVR handling.
4. **Comment interactions** – replies, likes, sorting.
5. **UI tests** for navigation flows and player controls (Compose test rule); keep the manual device pass.
6. **Cleanup** – remove the stale root `src/` folder and decide whether the empty `ui` module is still needed.
7. **Release** – add `LICENSE` (MIT), signing config, `assembleRelease`, tagged 1.0.0.

## Phase 6 checklist

- [ ] Read‑through/offline cache for Home, Search, Channel and History lists.
- [ ] Runtime Invidious instance picker with validation and a health check.
- [ ] Live stream metadata, DVR window and a live badge on the Watch screen.
- [ ] Comment replies, likes and sorting.
- [ ] Compose UI tests for the five tabs, login and the player controls.
- [ ] Remove the stale root `src/` and the empty `ui` module.
- [ ] Release: LICENSE, signing config, `assembleRelease`, tag.

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
