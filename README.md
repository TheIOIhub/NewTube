# NewTube

> Android client that plays YouTube videos, shorts and live streams **without ads** using the open‑source [Invidious](https://github.com/iv-org/invidious) API.

## Features

* **Five-tab shell** – Головна, Shorts, Пошук, Підписки and Ви in one bottom bar (white-on-dark labels, no selected pill); the bar hides on Watch, Settings, History and About so player and detail screens get the full height.
* **Home** – YouTube-style feed: brand top bar (logo, refresh, notifications, search), category chips (All, Music, Gaming, Movies, Sports, News, Technology), a horizontal **Shorts shelf** with portrait tiles, skeleton cards while loading and video cards with a 36 dp channel avatar, duration/LIVE badge and Ukrainian view counts. Each feed mixes trending, popular and topic searches (`/api/v1/trending`, `/api/v1/popular`, `/api/v1/search`), deduplicated and shuffled on every load.
* **Shorts** – full-screen vertical swipe player, one autoplaying clip per page. Invidious has no Shorts feed and `#shorts` search returns landscape clips, so the tab discovers Shorts channels with `type=channel` search and plays their real vertical clips from `/api/v1/channels/{id}/shorts` (1–180 s, no live), with a refresh button to reshuffle. A shelf tap opens the tab on that exact clip.
* **Search** – live search over `/api/v1/search?type=video&page=N` with a 500 ms debounce and pagination on scroll.
* **Channel view** – avatar, name, subscriber count, description and uploads paged by `continuation`; navigation uses the real `UC…` channel id.
* **Watch screen** – player at the top (16:9), title, views/date, channel row, expandable description, comments button and related videos.
* **Comments** – top-level comments with avatars, total count, pinned badge and `continuation` paging.
* **Video player** – ExoPlayer: HLS manifest with progressive/merged-DASH fallbacks, quality menu (auto / ≤ Np), fullscreen, picture-in-picture and resume position.
* **Watch history** – every watch (a Shorts clip only after 3 seconds on the page) is upserted into a local Room table, newest first, with the channel avatar and relative watch time; tapping a row reopens the video.
* **Google account (read-only)** – TV device-code OAuth (`google.com/device`) with the client credentials scraped from YouTube's TV page, tokens encrypted with `EncryptedSharedPreferences`, and access tokens refreshed silently. The Ви tab shows the signed-in avatar, name and email with a confirmed sign-out; Settings gains an Акаунт section.
* **Subscriptions** – the signed-in account's recent uploads from the TV `FEsubscriptions` endpoint, rendered as a two-column grid with continuation paging; YouTube TV returns a video feed rather than a channel list.
* **YouTube history** – when signed in and the setting is on, the History screen shows the account's real YouTube history (videos and Shorts) with "Історія YouTube" as the subtitle and infinite paging; remote rows are never written to Room and a failed fetch falls back to the local list with an inline banner.
* **Notifications** – best-effort read of `FEnotifications`; rows open Watch, and an empty or unavailable feed shows "Немає нових сповіщень".
* **Settings** – Вигляд (system/dark/light theme, applied immediately and kept across restarts), Акаунт (Google sign-in / sign-out), Історія (record-watch switch, YouTube history switch once signed in, confirmed clear that also drops resume positions), Відтворення (maximum quality 480p/720p/1080p, default 720p) and Про застосунок.
* **Theme** – dark by default when the system is dark, full light palette, purple brand accent, matching system bars and XML splash colours.
* **Room caching** – fetched videos and channels are persisted (Room v3: `channelId` and `authorAvatarUrl` on videos, plus the `watch_history` table and `2→3` migration).
* **DataStore** – per-video playback position and the settings above.
* **Material 3** UI, MVVM, Hilt DI, 3‑module Clean Architecture.

### Not implemented yet

Read‑through/offline caching, runtime instance switch, subtitles, live/DVR handling, comment replies, UI tests and the release signing pipeline. Account features are read-only: NewTube never subscribes, likes or deletes anything on YouTube. Full list: [`TASKS.md`](TASKS.md).

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
```

Run the unit tests and the linter (same commands as CI):

```bash
./gradlew test
./gradlew ktlintCheck            # ./gradlew ktlintFormat to auto‑fix
```

### Choosing an Invidious instance

Public Invidious instances get shut down or rate‑limited regularly, so the base URL is a build config field rather than a hardcoded constant. The default is `https://yt.chocolatemoo53.com/`; override it without touching the code (the trailing slash is required):

```bash
./gradlew assembleDebug -PinvidiousBaseUrl=https://yewtu.be/
```

A current list of working instances: <https://api.invidious.io/>. If the feed stays empty while the instance is up, check `adb logcat` – HTTP logging is enabled for debug builds.

## Project structure

```
app/     Compose UI, ViewModels, navigation, theme (Android application)
data/    Retrofit + Moshi client, DTOs, Room + migrations, DataStore, Hilt modules, VideoRepositoryImpl
domain/  Domain models and the VideoRepository / PlaybackPositionRepository contracts (pure Kotlin)
ui/      Reserved Android library for shared Compose components (empty)
```

`src/` in the repository root is a stale copy from the single‑module days and is not part of the build.

## Roadmap

1.0.0 – five tabs (Головна, Shorts, Пошук, Підписки, Ви) with YouTube-style Home cards and a Shorts shelf, search, channel screen, watch screen with player controls and comments, full-screen Shorts from real vertical clips, local watch history, dark/light theme and a complete Settings screen.

1.1.0 – read-only Google account: TV device-code OAuth, encrypted tokens, account header and sign-out, the subscriptions recent-video feed, YouTube history and best-effort notifications.

Next releases add offline caching, subtitles, playlists, a runtime Invidious switcher and the release pipeline (see [`TASKS.md`](TASKS.md)).

## Documentation

* [`SPEC.md`](SPEC.md) – specification, actual API contract, tech stack, known limitations.
* [`TASKS.md`](TASKS.md) – task tracker.
* [`.github/workflows/android.yml`](.github/workflows/android.yml) – CI: build, lint, tests.

## Legal

NewTube is an unofficial client for Invidious and is not affiliated with YouTube or Google. Content is streamed from public Invidious instances; use at your own risk and respect the terms of service of the instance you pick.

---

© 2026 NewTube project. All rights reserved.
