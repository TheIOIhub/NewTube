# NewTube

> Android client that plays YouTube videos, shorts and live streams **without ads** through YouTube's own InnerTube API — no third‑party backend.

## Features

* **Five-tab shell** – Головна, Shorts, Пошук, Підписки and Ви in one bottom bar (white-on-dark labels, no selected pill); the bar hides on Watch, Settings, History and About so player and detail screens get the full height.
* **Home** – YouTube-style feed: brand top bar (logo, refresh, notifications, search), category chips (All, Music, Gaming, Movies, Sports, News, Technology), a horizontal **Shorts shelf** with portrait tiles, skeleton cards while loading and video cards with a 36 dp channel avatar, duration/LIVE badge and Ukrainian view counts. Each category is one native YouTube `browse` feed (trending or a category hub), deduplicated and shuffled on every load.
* **Shorts** – full-screen vertical swipe player, one autoplaying clip per page. The tab reads YouTube's native `FEshorts` browse feed with continuation paging and plays real vertical clips (1–180 s, no live), with a refresh button to reshuffle. A shelf tap opens the tab on that exact clip.
* **Search** – live search over the InnerTube `search` endpoint with a 500 ms debounce and continuation paging on scroll.
* **Channel view** – avatar, name, subscriber count, description and uploads paged by `continuation`; navigation uses the real `UC…` channel id.
* **Watch screen** – player at the top (16:9), title, views/date, channel row, expandable description, comments button and related videos.
* **Comments** – top-level comments with avatars, total count, pinned badge and `continuation` paging.
* **Video player** – Media3 1.4.1 ExoPlayer: HLS manifest with progressive/merged-DASH fallbacks, quality menu (auto / ≤ Np), fullscreen, picture-in-picture and resume position.
* **Watch history** – every watch (a Shorts clip only after 3 seconds on the page) is upserted into a local Room table, newest first, with the channel avatar and relative watch time; tapping a row reopens the video.
* **Google account (read-only)** – TV device-code OAuth (`google.com/device`) with the client credentials scraped from YouTube's TV page, tokens encrypted with `EncryptedSharedPreferences`, and access tokens refreshed silently. The Ви tab shows the signed-in avatar, name and email with a confirmed sign-out; Settings gains an Акаунт section.
* **Subscriptions** – the signed-in account's recent uploads from the TV `FEsubscriptions` endpoint, rendered as a two-column grid with continuation paging; YouTube TV returns a video feed rather than a channel list.
* **YouTube history** – when signed in and the setting is on, the History screen shows the account's real YouTube history (videos and Shorts) with "Історія YouTube" as the subtitle and infinite paging; remote rows are never written to Room and a failed fetch falls back to the local list with an inline banner.
* **Notifications** – best-effort read of `FEnotifications`; rows open Watch, and an empty or unavailable feed shows "Немає нових сповіщень".
* **Settings** – Вигляд (system/dark/light theme, applied immediately and kept across restarts), Акаунт (Google sign-in / sign-out), Історія (record-watch switch, YouTube history switch once signed in, confirmed clear that also drops resume positions), Відтворення (maximum quality 480p/720p/1080p, default 720p) and Про застосунок.
* **Theme** – dark by default when the system is dark, full light palette, purple brand accent, matching system bars and XML splash colours.
* **Room caching** – Room v5 persists videos/channels (`channelId`, `authorAvatarUrl`), `watch_history`, the Shorts table `shorts_feed` and a `feed_cache` table (migrations `2→3→4→5`); Home, Search, Channel and Shorts appear immediately from cache and refresh in the background with a stale-while-revalidate indicator.
* **Shorts caching & preloading** – cache-first Shorts feed from Room v5 `feed_cache`/`shorts_feed` + DataStore `known_channels` (stale-while-revalidate with a refresh indicator; metadata only, never signed stream URLs); bounded source prefetch, 256 MiB LRU media disk cache, and one reused player driven by Media3 1.4.1 `DefaultPreloadManager` (radius-2 sliding window, Merged audio) with thumbnail preloading. A tested Paging 3 mediator is in place but dormant behind the existing list UI contract.
* **DataStore** – per-video playback position, settings above and the Shorts `known_channels` ids.
* **Material 3** UI, MVVM, Hilt DI, 3‑module Clean Architecture.

### Not implemented yet

True offline playback, a runtime config switch, subtitles, live/DVR handling, comment replies, UI tests and the release signing pipeline. Shorts metadata caching, media disk caching and preloading are implemented; Phase 2 device QA (Media3 preload + 15-swipe stress: 0 playback errors/crashes, ~4 MB PSS growth) passed, but Merged-audio audibility was NOT separately verified by ear. Account features are read-only: NewTube never subscribes, likes or deletes anything on YouTube. Full list: [`TASKS.md`](TASKS.md).

## Requirements

* **JDK 17** (`JAVA_HOME` pointing at a 17 JDK).
* **Android SDK Platform 34** + **Build‑Tools 34.0.0**, either via `ANDROID_HOME` / `ANDROID_SDK_ROOT` or a `local.properties` file:

  ```properties
  sdk.dir=C\:\\Android\\Sdk
  ```

* No local Gradle installation needed – the Gradle 8.6 wrapper is committed.

## Build & run

```bash
# debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleDebug          # Windows: .\gradlew.bat assembleDebug

# install on a connected device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk

# minified release APK -> app/build/outputs/apk/release/app-release-unsigned.apk
./gradlew assembleRelease          # Windows: .\gradlew.bat assembleRelease
```

The `release` build type enables R8 minification (`isMinifyEnabled = true`). It is unsigned until a signing config is added; debug builds remain the QA target.

Run the unit tests and the linter (same commands as CI):

```bash
./gradlew test
./gradlew ktlintCheck            # ./gradlew ktlintFormat to auto‑fix
```

### Remote content config

Content and playback requests go straight to YouTube's `youtubei/v1` endpoints — no third‑party API host. The client chain (`ANDROID`, `ANDROID_VR`, `IOS`) and endpoint paths are bundled with the app. The build property defaults to empty (`-PcontentConfigUrl=`), which means the bundled config is used. To serve your own JSON config instead, pass its URL at build time:

```bash
./gradlew assembleDebug -PcontentConfigUrl=https://example.com/youtube-config.json
```

The JSON can define `contentClients`, `playerClients`, `endpoints`, `ttlMillis` and `fetchedAt`. A valid response is cached in DataStore and reused for `ttlMillis` (6 hours by default) before a refetch; a failed or invalid fetch keeps the cached/bundled config. Logs are BASIC-level in debug builds, so check `adb logcat` if a feed stays empty.

## Project structure

```
app/     Compose UI, ViewModels, navigation, theme (Android application)
data/    OkHttp InnerTube client + tolerant JSON parser, Room + migrations, DataStore, Hilt modules, VideoRepositoryImpl
domain/  Domain models and the VideoRepository / PlaybackPositionRepository contracts (pure Kotlin)
ui/      Reserved Android library for shared Compose components (empty)
```

## Roadmap

1.0.0 – five tabs (Головна, Shorts, Пошук, Підписки, Ви) with YouTube-style Home cards and a Shorts shelf, search, channel screen, watch screen with player controls and comments, full-screen Shorts from real vertical clips, local watch history, dark/light theme and a complete Settings screen.

1.1.0 – read-only Google account: TV device-code OAuth, encrypted tokens, account header and sign-out, the subscriptions recent-video feed, YouTube history and best-effort notifications.

Next releases add read‑through offline caching for the other lists, subtitles, playlists, a runtime content-config switcher and release signing (see [`TASKS.md`](TASKS.md)).

## Documentation

* [`SPEC.md`](SPEC.md) – specification, actual API contract, tech stack, known limitations.
* [`TASKS.md`](TASKS.md) – task tracker.
* [`.github/workflows/android.yml`](.github/workflows/android.yml) – CI: build, lint, tests.

## Legal

NewTube is an unofficial YouTube client and is not affiliated with YouTube or Google. Content is streamed from YouTube's own endpoints; use at your own risk and respect YouTube's terms of service.

---

© 2026 NewTube project. All rights reserved.

## Regional feed and extended player (2026-11-27)

- **Ukrainian-first content:** `contentCountry` (default `UA`) and `contentLanguage` (default `uk`) are sent as InnerTube client `gl`/`hl` in the request context on browse, search, suggestions and player. Changing either refreshes Home and Shorts immediately; the For You chip also prefers Ukrainian-language results.
- **"Для вас" chip:** the first Home category ranks the regionalised feed by local watch history and (when signed in) subscriptions, with an explicit Ukrainian-language preference. Ranking is deterministic and local-only — no backend.
- **Content settings with real behaviour:** search suggestions from InnerTube `search/suggestions` (250 ms debounce), best-effort age-restricted/safe-mode filtering before persistence, and a "show metadata" switch for cards. App language follows `LocaleManager` on Android 13+.
- **Player:** audio-track menu from live Media3 tracks (with source reload for language-tagged separate audio), persisted FIT → FILL → ZOOM cycle, double-tap thirds to seek (±10 s by default) and fullscreen vertical swipes (left brightness, right volume, configurable sides), plus an autoplay-next row.
- Detailed design: `docs/superpowers/specs/2026-11-27-regional-personalized-player-design.md`; NewPipe research: `docs/research/2026-11-27-newpipe-player-features.md`.
