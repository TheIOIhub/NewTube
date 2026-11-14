# NewTube

> Android client that plays YouTube videos, shorts and live streams **without ads** using the open‑source [Invidious](https://github.com/iv-org/invidious) API.

## Features

* **Home** – category chips (All, Music, Gaming, Movies, Sports, News, Technology); each feed mixes trending, popular and topic searches (`/api/v1/trending`, `/api/v1/popular`, `/api/v1/search`), deduplicated and shuffled on every load, with a refresh button and loading/error/empty states.
* **Shorts** – full-screen vertical swipe player, one autoplaying clip per page. Invidious has no Shorts feed and `#shorts` search returns landscape clips, so the tab discovers Shorts channels with `type=channel` search and plays their real vertical clips from `/api/v1/channels/{id}/shorts` (1–180 s, no live), with a refresh button to reshuffle.
* **Search** – live search over `/api/v1/search?type=video&page=N` with a 500 ms debounce and pagination on scroll.
* **Channel view** – avatar, name, subscriber count, description and uploads paged by `continuation`; navigation uses the real `UC…` channel id.
* **Watch screen** – player at the top (16:9), title, views/date, channel row, expandable description, comments button and related videos.
* **Comments** – top-level comments with avatars, total count, pinned badge and `continuation` paging.
* **Video player** – ExoPlayer: HLS manifest with progressive/merged-DASH fallbacks, quality menu (auto / ≤ Np), fullscreen, picture-in-picture and resume position.
* **Room caching** – fetched videos and channels are persisted (Room v2 with an automatic migration).
* **DataStore** – per-video playback position.
* **Material 3** UI, MVVM, Hilt DI, 3‑module Clean Architecture.

### Not implemented yet

Read‑through/offline caching, dark‑mode toggle, runtime instance switch, subscriptions, subtitles, live/DVR handling, comment replies, UI/instrumentation tests. Full list: [`TASKS.md`](TASKS.md).

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

1.0.0 – three tabs with Home category feeds, search, channel screen, watch screen with player controls and comments, plus full-screen Shorts from real vertical clips.

Next releases add bug fixes (see [`TASKS.md`](TASKS.md)) plus offline caching, subtitles, dark‑mode preference, playlists and subscriptions.

## Documentation

* [`SPEC.md`](SPEC.md) – specification, actual API contract, tech stack, known limitations.
* [`TASKS.md`](TASKS.md) – task tracker.
* [`.github/workflows/android.yml`](.github/workflows/android.yml) – CI: build, lint, tests.

## Legal

NewTube is an unofficial client for Invidious and is not affiliated with YouTube or Google. Content is streamed from public Invidious instances; use at your own risk and respect the terms of service of the instance you pick.

---

© 2026 NewTube project. All rights reserved.
