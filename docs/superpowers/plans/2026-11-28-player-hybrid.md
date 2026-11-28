# Hybrid Player Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the player's floating text buttons with YouTube-style custom controls and a GreenTuber-style settings sheet, stop fullscreen swipes from collapsing the player, and fix the HTTP 403 that breaks playback.

**Architecture:** `data` swaps Media3's upstream to `OkHttpDataSource` with YouTube-compatible headers. `app` adds per-video UI state plus small Composable controls and a bottom sheet; `VideoPlayer` stops using Media3's controller on Watch and renders the new controls. Gestures stay in `PlayerGestureOverlay`, with fullscreen paths explicitly separated from minimize and back handling.

**Tech Stack:** Kotlin 1.9.24, AGP 8.2.2, Media3 1.4.1, Compose BOM 2024.03.00, Hilt 2.47, OkHttp 4.12, Coil 2.5.0, JDK 17.

**Spec:** `docs/superpowers/specs/2026-11-28-player-hybrid-settings-design.md`

## Global Constraints

- Android API 24+, compileSdk/targetSdk 34, no new Gradle modules or dependencies.
- No changes to `AppSettings`/DataStore/domain models: all player settings are in-memory, scoped to the current video.
- Quality is a local `DefaultTrackSelector` cap only; never re-fetch the player URL for quality.
- Shorts player and its gestures are unchanged except for shared helpers.
- All new UI strings Ukrainian; `retry` string already exists.
- `./gradlew test ktlintCheck assembleDebug` green after every task.

## Review Focus

- Direct `googlevideo` URL with a 403 → OkHttp upstream with YouTube UA/Referer/Origin; pinned by `MediaRequestHeadersTest` and device playback of the previously failing video.
- Fullscreen vertical swipe → must adjust volume/brightness only, never minimize and never let the system edge gesture exit; pinned by `GestureMathTest.edgeGuard` + device swipe.
- Error state → Ukrainian message with Retry that forces a fresh `player` request; pinned by the Retry wiring test/callback in `WatchViewModelTest` and device check.
- Settings values across players → per-video only, no DataStore writes; pinned by `PlayerUiSettingsTest` asserting defaults and no repository interaction.
- PiP availability → accessible both as a top icon and a settings row; pinned by compile + device tap.

---

## File Structure

- `data/src/main/java/com/example/newtube/di/MediaRequestHeaders.kt` — pure header provider.
- `data/src/main/java/com/example/newtube/di/PlaybackCacheModule.kt` — cached factory upstream swap.
- `app/src/main/java/com/example/newtube/ui/video/PlayerUiSettings.kt` — per-video state + pure format helpers.
- `app/src/main/java/com/example/newtube/ui/video/PlayerControls.kt` — top/center/bottom bars + settings sheet.
- `app/src/main/java/com/example/newtube/ui/video/VideoPlayer.kt` — custom controls instead of Media3 controller.
- `app/src/main/java/com/example/newtube/ui/video/WatchScreen.kt` — state, retry, back handler.
- `app/src/main/java/com/example/newtube/ui/video/PlayerGestures.kt` / `GestureMath.kt` — edge guard.
- `app/src/main/res/values/strings.xml` — Ukrainian player strings.

---

### Task 1: Fix direct media HTTP 403

**Files:**
- Create: `data/src/main/java/com/example/newtube/di/MediaRequestHeaders.kt`
- Modify: `data/src/main/java/com/example/newtube/di/PlaybackCacheModule.kt:33-41`
- Test: `data/src/test/java/com/example/newtube/di/MediaRequestHeadersTest.kt`

**Interfaces:**
- Produces: `object MediaRequestHeaders { fun userAgent(): String; fun headers(): Map<String, String> }`.
- Consumes: `CacheDataSource.Factory`, `SimpleCache`.

- [ ] **Step 1: Write the failing header test**

```kotlin
@Test
fun `media requests carry the Android client identity`() {
    val h = MediaRequestHeaders.headers()
    assertEquals("com.google.android.youtube/20.10.38 (Linux; U; Android 12; en_US) gzip", h["User-Agent"])
    assertEquals("https://www.youtube.com/", h["Referer"])
    assertEquals("https://www.youtube.com", h["Origin"])
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :data:testDebugUnitTest --tests '*MediaRequestHeadersTest'`
Expected: FAIL, object missing.

- [ ] **Step 3: Implement the header provider and swap the upstream**

Create `MediaRequestHeaders` with the three exact values. In `PlaybackCacheModule.provideCacheDataSourceFactory`, replace `DefaultDataSource.Factory(context)` with `OkHttpDataSource.Factory(OkHttpClient.Builder().build())` and call `.setDefaultRequestProperties(MediaRequestHeaders.headers())`. Use a new client instance in the module (do not inject or modify the global/InnerTube clients). Keep the 256 MiB cache and `PLAYBACK_CACHE_MAX_BYTES` unchanged.

- [ ] **Step 4: Run tests and commit**

Run: `./gradlew test ktlintCheck assembleDebug`
Expected: PASS.
Commit: `fix: send YouTube client headers on media requests`

---

### Task 2: Per-video player state and format helpers

**Files:**
- Create: `app/src/main/java/com/example/newtube/ui/video/PlayerUiSettings.kt`
- Create: `app/src/test/java/com/example/newtube/ui/video/PlayerUiSettingsTest.kt`

**Interfaces:**
- Produces:
  - `data class PlayerUiSettings(val quality: Int = Int.MAX_VALUE, val speed: Float = 1f, val repeat: Boolean = false, val resize: Int = AppSettings.RESIZE_FIT, val audioLabel: String = "Авто", val gestureZoom: Boolean = true, val pipEnabled: Boolean = true)`
  - `fun PlayerUiSettings.qualityLabel(): String` (`Авто` when `quality == Int.MAX_VALUE`, else `"${quality}p"`)
  - `fun PlayerUiSettings.speedLabel(): String` (`1x`, `1.5x`, `2x`, `3x`)
  - `fun PlayerUiSettings.nextResize(): Int` (FIT→FILL→ZOOM→FIT)
  - `fun formatPlayerTime(ms: Long): String` (`m:ss`, `h:mm:ss` when ≥1 h)

- [ ] **Step 1: Write failing tests for the four helpers**

`PlayerUiSettingsTest` cases: default label `Авто`; `quality=720` → `720p`; `speed=3f` → `3x`; `nextResize()` cycles FIT→FILL→ZOOM→FIT; `formatPlayerTime(27_000) == "0:27"`; `formatPlayerTime(3_663_000) == "1:01:03"`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*PlayerUiSettingsTest'`
Expected: FAIL, class missing.

- [ ] **Step 3: Implement state and helpers**

Use `AppSettings.RESIZE_*` constants for resize values; no DataStore/repository access anywhere in this file.

- [ ] **Step 4: Run tests and commit**

Run: `./gradlew test ktlintCheck assembleDebug`
Expected: PASS.
Commit: `feat: add per-video player settings state`

---

### Task 3: Custom controls and settings sheet

**Files:**
- Create: `app/src/main/java/com/example/newtube/ui/video/PlayerControls.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/example/newtube/ui/video/PlayerUiSettingsTest.kt` (extra assertions for labels used by the sheet)

**Interfaces:**
- Consumes: `PlayerUiSettings` and its helpers from Task 2.
- Produces:
  - `@Composable fun PlayerTopBar(isFullscreen: Boolean, qualityLabel: String, showPip: Boolean, onBack: () -> Unit, onPip: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun PlayerCenterControls(isPlaying: Boolean, onPlayPause: () -> Unit, onSeek: (Int) -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun PlayerBottomBar(positionMs: Long, durationMs: Long, isLive: Boolean, isFullscreen: Boolean, onSeekTo: (Long) -> Unit, onFullscreen: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun PlayerSettingsSheet(settings: PlayerUiSettings, heights: List<Int>, audioOptions: List<String>, onSettingsChange: (PlayerUiSettings) -> Unit, onPip: () -> Unit, onDismiss: () -> Unit)`

- [ ] **Step 1: Write failing label tests**

Add assertions for Ukrainian row labels exactly: `Якість`, `Швидкість`, `Аудіодоріжка`, `Повторювати`, `Екран`, `Жест яскравості`, `Жест збільшення`, `Швидкість під час утримання`, `PiP`, `Субтитри`, `Таймер`, `Кастинг`, `Браузер`, and `Недоступно` for non-functional rows.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*PlayerUiSettingsTest'`
Expected: FAIL.

- [ ] **Step 3: Implement Composables and strings**

`PlayerSettingsSheet` is a `ModalBottomSheet` with a drag handle and X; rows show current value on the right in accent color; tapping a value row opens a second list inside the same sheet with a back arrow. Put non-functional rows (`Швидкість під час утримання`, `Таймер`, `Блокування екрана`, `Кастинг`, `Браузер`, `Субтитри`) behind `Недоступно`; no PiP row is disabled — `onPip` is supplied. Add missing Ukrainian strings to `strings.xml`.

- [ ] **Step 4: Run tests and commit**

Run: `./gradlew test ktlintCheck assembleDebug`
Expected: PASS.
Commit: `feat: add custom player controls and settings sheet`

---

### Task 4: Wire player, remove old buttons, fix fullscreen/back/PiP

**Files:**
- Modify: `app/src/main/java/com/example/newtube/ui/video/VideoPlayer.kt:74-506`
- Modify: `app/src/main/java/com/example/newtube/ui/video/WatchScreen.kt:105-179`
- Modify: `app/src/main/java/com/example/newtube/ui/video/PlayerGestures.kt:50-278`
- Modify: `app/src/main/java/com/example/newtube/ui/video/GestureMath.kt`
- Test: `app/src/test/java/com/example/newtube/ui/video/GestureMathTest.kt`

**Interfaces:**
- Consumes: Task 2 `PlayerUiSettings`, Task 3 Composables, `onRetry`.
- Produces: `VideoPlayer` gains `settings: PlayerUiSettings`, `onSettingsChange: (PlayerUiSettings) -> Unit`, `onPip: () -> Unit`, `onRetry: () -> Unit`; `GestureMath.canStartFullscreenGesture(x: Float, y: Float, width: Float, height: Float, edgePx: Float): Boolean`.

- [ ] **Step 1: Write failing edge-guard tests**

`GestureMathTest`: start at `(200f, 40f)` in a 720×1280 area with `edgePx = 48f` → false; `(200f, 400f)` → true; horizontal delta dominance must not start a fullscreen gesture.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*GestureMathTest'`
Expected: FAIL.

- [ ] **Step 3: Implement gesture and wiring changes**

In `GestureMath`, add the pure `canStartFullscreenGesture` using the 48 px edge threshold and vertical dominance. In `PlayerGestures`, call it before entering volume/brightness drag; keep `onMinimize` unreachable whenever `isFullscreen`. In `VideoPlayer`, set `PlayerView.useController = false` for Watch (keep Shorts behavior via `isShorts`), delete the entire old `TopEnd` Row (`:373-503`), render the three new bars plus `PlayerSettingsSheet`, show `onRetry` on a Ukrainian error message, and auto-hide controls after 3 s of no tap. In `WatchScreen`, hold `PlayerUiSettings` with `remember`, add `BackHandler(enabled = isFullscreen) { isFullscreen = false }`, wire non-fullscreen back to `onMinimize`, pass `onRetry = viewModel::retry`, and replace fullscreen inset behavior with `hide(Type.systemBars())` (no transient-bars-by-swipe).

- [ ] **Step 4: Run tests and commit**

Run: `./gradlew test ktlintCheck assembleDebug`
Expected: PASS.
Commit: `feat: wire hybrid player controls and safe fullscreen gestures`

---

### Task 5: Device verification and final polish

**Files:** none mandatory; fix only what verification exposes.

- [ ] **Step 1: Full verification**

Run: `./gradlew test ktlintCheck assembleDebug assembleRelease`
Expected: PASS.

- [ ] **Step 2: Install and verify on the connected device**

Steps: install debug APK; open a video; confirm the previously failing video plays; tap to show controls; open settings sheet and scroll all rows; verify PiP icon and settings row; enter fullscreen and swipe vertically — volume/brightness change, player stays fullscreen; press system back — exits fullscreen only; in normal mode press top-left — minimizes to mini player.

- [ ] **Step 3: Commit any fixes found**

Commit message prefix `fix:`.
