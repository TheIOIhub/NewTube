# NewTube

> Android application that lets you watch YouTube videos, shorts, and live streams **without ads** by using the open‑source [Invidious](https://github.com/iv-org/invidious) API.

## Features

* **Home** – Browse popular & recommended videos.
* **Shorts** – Infinite scroll of short‑form content.
* **Search** – Query videos, channels, or live streams with autocomplete.
* **Channel view** – See all uploads, shorts, and live streams of a channel.
* **Video Player** – ExoPlayer based player that supports:
  * Multiple quality levels (HLS/DASH).
  * Audio track selection.
  * Full‑screen toggle and system UI hide.
  * Bookmark/continue‑where‑you‑left‑off.
* **Offline caching** of thumbnails and metadata in Room.
* **Dark / Light themes**.
* **Clean Architecture** (Domain, Data, UI) with MVVM and Hilt DI.
* **Unit + UI tests**.
* **CI** – GitHub Actions build, lint, tests.

## Roadmap

1.0.0 – Core functionality, 3 main tabs and basic video playback.

Subsequent minor releases add bug fixes, UI polish, and new features such as:
* PIP mode
* Subtitles support
* Dark‑mode preference
* Custom playlists
* Analytics for user engagement

## Getting Started

```bash
# Clone the repository
git clone https://github.com/<your-username>/NewTube.git
cd NewTube

# Build and run via Android Studio or
./gradlew assembleDebug
```

## Contributing

Feel free to submit PRs! Please run `./gradlew ktlintCheck` and `./gradlew test` before pushing.

---

© 2026 NewTube project. All rights reserved.
