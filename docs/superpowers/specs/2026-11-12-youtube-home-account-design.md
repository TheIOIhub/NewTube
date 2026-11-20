# NewTube: YouTube-style home, Google account, history and settings

- **Date:** 2026-11-12
- **Status:** Draft for approval
- **Size:** Project (full design)
- **Owner:** NewTube

## 1. Intent

NewTube works, but it does not feel like the YouTube app on the user's device:
it is light-only, has three tabs, plain card list items, no channel avatars, no
Shorts shelf, no account and no settings. The user wants NewTube to look and
behave like YouTube/GreenTuber on their TCL 6102H (Android 12, 720×1600,
density 320 → 360×800 dp, system dark mode on), and wants their **real** YouTube
watch history and subscriptions through a Google sign-in. They also asked for a
settings screen ("всілякі налаштування").

The user explicitly chose:

- **Google TV OAuth device-code login** (option A), not a WebView cookie login
  and not microG.
- **Hybrid data source:** Invidious keeps powering Home, Search, Channels and
  the ad-free player; InnerTube with the Google account is used **only** for
  account features (profile, history, subscriptions, notifications).
- **YouTube purple** stays the brand accent (not YouTube red).
- Watch history from YouTube is **read-only**; NewTube also records its own
  local history.

Success means: the app opens looking like YouTube (dark by default, five tabs,
avatars, durations, Shorts shelf), a user can sign in by entering a code at
`google.com/device`, and the Підписки/Історія screens then show their real
YouTube data. Everything continues to work signed out.

## 2. Goals

1. **Theme system.** Dark theme by default (following the system), plus an
   explicit light theme and a theme choice in Settings. Status/navigation bars
   follow the theme. No white flash on normal navigation.
2. **Five-tab shell.** Головна, Shorts, Пошук, Підписки, Ви, with YouTube-style
   iconography, labels and state preservation.
3. **Home parity.** Top bar (logo, bell, search), category chips, a horizontal
   Shorts shelf, and YouTube-style video cards with thumbnails, duration badges,
   channel avatars, title and metadata. Skeleton loading instead of a blank
   spinner. Pull-to-refresh is not required; keep the existing refresh affordance.
4. **Shorts shelf → Shorts player.** Tapping a shelf tile opens the Shorts tab
   positioned at that video.
5. **Google sign-in.** TV OAuth device-code flow, refresh-token persistence,
   account profile in the Ви tab, sign-out, session-expiry recovery.
6. **Real YouTube history.** Read-only list from InnerTube `FEhistory` with
   continuation pagination, falling back to local history.
7. **Real subscriptions.** Channel grid from InnerTube `FEsubscriptions` with
   continuation pagination; sign-in call-to-action when signed out.
8. **Notifications screen.** Bell opens a best-effort InnerTube `FEnotifications`
   list; sign-in CTA when signed out. Empty state if YouTube returns nothing
   usable; never crash or block other work.
9. **Local history.** Record watched videos in Room, show it when
   signed out or when remote history is disabled, and offer "clear" in Settings.
10. **Settings.** Theme, history behaviour, maximum stream quality, account,
    about/version/instance. Persisted in DataStore.
11. Keep the existing Invidious playback path and all current unit tests green.

## 3. Non-goals

- No InnerTube playback: no PO tokens, no signature deciphering, no SABR. The
  player stays on Invidious.
- No writes to the Google account: no "mark watched", likes, comments,
  subscriptions changes, uploads, playlists.
- No multiple-account switching UI. The first release signs into the first
  account returned by `accounts_list` (its `pageId` is stored and sent if
  present). A brand-account picker is a later feature.
- No full app localization project. New/changed UI strings go into
  `res/values/strings.xml` (default Ukrainian); untouched legacy strings may stay
  English until they are edited.
- No downloads, casting, playlists, watch-later, or channel tab redesign.
- No QR code on the login screen (code + URL + copy is enough).
- No Cast or in-player quality UI redesign beyond the persisted max-quality cap.

## 4. Anti-goals

- The app must remain fully usable without a Google account or network.
- Sign-in must never block startup, Home, Search or playback.
- Never store the Google password, never open `accounts.google.com` in a WebView.
- No token, refresh token, client secret or user email may appear in logs.
- Do not replace or destabilize the existing Invidious player while adding
  account features.
- Do not silently show fabricated metadata ("0 views • 0 seconds ago") — the
  default Invidious instance returns broken trending metadata today.

## 5. Constraints and findings from the codebase

- Modules: `:app` (Compose + Hilt), `:data` (Android library), `:domain` (pure
  Kotlin). Dependencies are declared per module, no version catalog.
- minSdk 24, compile/target 34, Kotlin 1.9.24, Compose BOM 2024.03.00
  (Material3 1.2.1), Hilt 2.47, Room 2.6.1, Retrofit 2.11 + Moshi, OkHttp 4.12,
  Coil 2.5, ExoPlayer 2.19.0, DataStore 1.1.1 in `:data`.
- Only `material-icons-core` is present. Do **not** add `material-icons-extended`;
  use the core set plus a few vector drawables (`ic_shorts`, `ic_subscriptions`,
  `ic_history`).
- Compose 1.6 / Material3 1.2 has no `PullToRefreshBox`. Do not upgrade Material3
  independently of the Compose compiler.
- Current theme is light-only (`Theme.kt` calls `lightColorScheme`); `MainActivity`
  has a 3-item `TAB_ROUTES`/`NavigationBar`.
- `VideoDto` does not parse `authorThumbnails`, although the instance returns it
  (6 sizes; choose the largest ≤ 176 px or the largest available).
- The default instance `https://yt.chocolatemoo53.com/` returns trending items
  with `viewCount=0`, `lengthSeconds=0`, `publishedText="0 seconds ago"`.
  Search results are correct. Home feed therefore needs a metadata sanity rule.
- `VideoEntity`/Room is at version 2 with a single `MIGRATION_1_2`; no
  `fallbackToDestructiveMigration`. Add a real 2→3 migration.
- Playback positions already live in DataStore
  (`DataStorePlaybackPositionRepository`). Local history is a separate concern.
- Recon proved **code issuance only**: scraping `https://www.youtube.com/tv` with
  the Fire TV UA yields a client id (`861556708454-…apps.googleusercontent.com`)
  and a 24-char secret, and `POST https://www.youtube.com/o/oauth2/device/code`
  returned a real `device_code`/`user_code` and `https://www.google.com/device`.
  The code was **not** approved by a human, so token exchange, refresh,
  `accounts_list` and an authorised `FEhistory` call are **not yet proven**.
  Phase 0 below is a mandatory spike for exactly those steps.
- SmartTube (MediaServiceCore) is the reference implementation for every
  InnerTube request below; exact values are recorded in §17–§18. Its captured
  `FEhistory` fixture lives in the local checkout at
  `C:\Users\umtci\AppData\Local\Temp\opencode\MediaServiceCore\youtubeapi\src\test\resources\browse\tv\history.json`
  (not in this repo yet); copy it into `data/src/test/resources/` during Phase 0.

## 6. Approaches considered

### A. Invidious only + local history (no Google)

Cheapest and most robust: keep the current stack, add local history and the UI
redesign. But it cannot show the user's real YouTube history or subscriptions,
which is an explicit requirement. Rejected.

### B. WebView Google login and cookie extraction

Sign in inside a `WebView`, scrape `SAPISID`/`__Secure-3PAPISID`, build
`SAPISIDHASH`, call InnerTube as the web client. Historically common, but Google
blocks embedded WebViews (`disallowed_useragent`), the user's password is handled
by our surface, and long-lived cookie theft is a bigger risk than an OAuth refresh
token. Rejected.

### C. Google TV OAuth device code + InnerTube for account data (chosen)

The app requests a TV OAuth device code, shows `GDF-XXX-XXX` and a link to
`google.com/device`, polls for tokens, and stores the refresh token. InnerTube
requests carry `Authorization: Bearer <access_token>` (plus `X-Goog-Pageid` for a
brand account). Invidious remains the content/playback backend.

Trade-offs accepted by the user:

- The TV client id/secret are not ours; they are scraped from YouTube's TV web app
  at runtime and cached. YouTube can change that JS at any time, so sign-in must
  degrade gracefully and never affect the rest of the app.
- Third-party use of that client is a ToS/account risk the user accepted.
- No playback through InnerTube, so no PO-token work.

## 7. Architecture

```
:domain
  model/UserAccount.kt          id-less profile: name, email, avatarUrl, pageId
  model/WatchHistoryEntry.kt    video metadata + watchedAt
  model/SubscriptionChannel.kt  channelId, title, avatarUrl, subscriberText
  model/ThemeMode.kt            SYSTEM, DARK, LIGHT
  repository/AccountRepository.kt
  repository/HistoryRepository.kt
  repository/SettingsRepository.kt
  repository/SubscriptionsRepository.kt

:data
  auth/ClientCredentialsProvider.kt   scrape/cache id+secret
  auth/DeviceAuthApi.kt               device code + token endpoints
  auth/DeviceAuthRepository.kt        LoginState flow, polling, refresh single-flight
  auth/TokenStore.kt                  EncryptedSharedPreferences
  auth/AccountRepositoryImpl.kt       accounts_list, profile, sign-out
  innertube/InnerTubeApi.kt           raw @POST endpoints
  innertube/InnerTubeContext.kt       TV context JSON builder
  innertube/InnerTubeClient.kt        auth headers, 401 retry, error mapping
  innertube/BrowseParser.kt           tolerant JsonElement → models
  innertube/HistoryRepositoryImpl.kt
  innertube/SubscriptionsRepositoryImpl.kt
  innertube/NotificationsRepositoryImpl.kt
  database/WatchHistoryEntity.kt + Dao
  preferences/DataStoreSettingsRepository.kt
  implementation/…                     wire-up, mapping, local history recorder

:app
  ui/theme/…                          light+dark schemes, ThemeMode, type, shapes
  ui/shell/NewTubeTopBar.kt, BottomNavigationBar.kt
  ui/home/…                           redesign + shelf
  ui/common/VideoCard.kt, ShortsTile.kt, ChannelAvatar.kt, skeletons
  ui/subscriptions/…                  grid + CTA
  ui/you/…                            profile + shortcuts
  ui/history/…                        local/remote history
  ui/settings/…                       settings screen
  ui/account/LoginScreen.kt           code, copy, open browser, poll status
  ui/notifications/…                  best-effort list + CTA
```

Dependency direction stays unchanged: `:app` → `:data` → `:domain`. InnerTube and
auth models stay in `:data`; `:domain` only sees plain models and repository
interfaces. `:domain` gets no Android or network dependencies.

The account session is app-scoped. `AccountRepository` exposes
`StateFlow<AccountState>` (`Unknown`, `SignedOut`, `SignedIn(UserAccount)`);
`Unknown` is a one-frame startup state, not a spinner that blocks UI.

## 8. Theme and visual system

### 8.1 Theme mode

`ThemeMode { SYSTEM, DARK, LIGHT }`, persisted in DataStore under
`theme_mode` (string, default `SYSTEM`). `MainActivity` collects the flow with
`collectAsState(initial = SYSTEM)` and calls `NewTubeTheme(darkTheme = …)`.
`darkTheme` resolves `SYSTEM` through `isSystemInDarkTheme()`.

`NewTubeTheme` also applies system-bar appearance:

- light status-bar icons in dark mode, dark icons in light mode;
- navigation bar colour = `surface`, matching theme;
- `enableEdgeToEdge()` in `MainActivity`; Scaffold handles insets, player hides
  system bars as it does today.

Initial value is `SYSTEM` so there is no flash when the saved mode is also
system. A one-frame flash is possible when the saved mode differs from the
system on a cold start; that is accepted rather than blocking on DataStore.

### 8.2 Colour tokens

YouTube-like neutrals plus the NewTube purple accent. Define in `Color.kt`:

| Role | Dark | Light |
|---|---|---|
| primary | `#BB86FC` | `#6200EE` |
| onPrimary | `#21005D` | `#FFFFFF` |
| background | `#0F0F0F` | `#FFFFFF` |
| onBackground | `#F1F1F1` | `#0F0F0F` |
| surface | `#0F0F0F` | `#FFFFFF` |
| onSurface | `#F1F1F1` | `#0F0F0F` |
| surfaceVariant | `#272727` | `#F2F2F2` |
| onSurfaceVariant | `#AAAAAA` | `#606060` |
| outline | `#3F3F3F` | `#D9D9D9` |
| secondaryContainer | `#272727` | `#F2F2F2` |
| onSecondaryContainer | `#F1F1F1` | `#0F0F0F` |
| error | `#FFB4AB` | `#B3261E` |

Chips follow YouTube, not the accent: in dark mode the selected chip is
white with black text, unselected is `#272727`; in light mode selected is black
with white text, unselected `#F2F2F2`. Purple is used for buttons, links, focus
and the logo.

### 8.3 Typography and shapes

System Roboto, tightened sizes:

| Token | Size/weight | Use |
|---|---|---|
| titleLarge | 22sp / SemiBold | screen titles, logo wordmark |
| titleMedium | 16sp / Medium | section headers ("Shorts") |
| titleSmall | 14sp / Medium | video card titles |
| bodyMedium | 14sp / Normal | primary body |
| bodySmall | 12sp / Normal | metadata |
| labelLarge | 14sp / Medium | buttons |
| labelSmall | 11sp / Normal | bottom-nav labels |

Shapes: thumbnails 12dp rounded, Shorts tiles 12dp, chips fully rounded,
avatars circle. Cards are **flat** (no elevation, no card container); spacing
between feed cards 16dp vertical, 12dp horizontal screen padding.

### 8.4 Layout sketch (360×800 dp, dark)

```
┌────────────────────────────────────┐
│ ▶ NewTube              🔔    🔍     │  56dp top bar
│ (Усі)(Музика)(Ігри)(Фільми)(Спорт) │  chips, 48dp
│ Shorts                    (Усі ›)  │  section header
│ ┌──────┐┌──────┐┌──────┐           │  9:16 tiles, 150dp wide
│ │      ││      ││      │           │  + title overlay
│ └──────┘└──────┘└──────┘           │
│ ┌────────────────────────────────┐ │
│ │        16:9 thumbnail     12:34│ │
│ └────────────────────────────────┘ │
│ (av) Title up to two lines         │
│      Channel · 1.2M views · 2 days │
└────────────────────────────────────┘
│  🏠      ▶      🔍      ☰      👤  │  80dp bottom nav
│ Головна Shorts Пошук Підписки   Ви │
└────────────────────────────────────┘
```

## 9. Shell and navigation

### 9.1 Routes

| Route | Screen | Bottom bar |
|---|---|---|
| `home` | Home | yes |
| `shorts?videoId={id}` (optional) | Shorts | yes |
| `search` | Search | yes |
| `subscriptions` | Subscriptions | yes |
| `you` | You | yes |
| `history` | History | no |
| `settings` | Settings | no |
| `login` | Google sign-in | no |
| `notifications` | Notifications | no |
| `channel/{channelId}` | Channel | no |
| `watch/{videoId}` | Watch/player | no |
| `comments/{videoId}` | Comments | no |

`TAB_ROUTES` becomes the five top-level routes. Bottom bar stays hidden in PiP
and on detail routes. Tapping the active tab is a no-op; switching tabs uses
`popUpTo(startDestination) { saveState = true }`, `launchSingleTop`,
`restoreState = true` as today.

Opening a Shorts tile navigates to `shorts?videoId=<id>` with the same tab
flags; the Shorts screen reads the argument, positions the pager at that video
(prepending it if the current page list does not contain it), and clears the
arg so re-entering the tab does not jump again.

### 9.2 Bottom navigation

`NavigationBar` with `containerColor = surface`, no selected pill
(`indicatorColor = Color.Transparent`), selected icon/label `onBackground`,
unselected `onSurfaceVariant`. `alwaysShowLabel = true`.

Icons: Home (`Icons.Filled.Home`), Shorts (custom `ic_shorts`), Search
(`Icons.Filled.Search`), Subscriptions (custom `ic_subscriptions`), You
(`Icons.Filled.Person`). Labels: **Головна, Shorts, Пошук, Підписки, Ви**.

### 9.3 Top bar

A shared `NewTubeTopBar` composable, 56dp, transparent over `background`:

- **Home:** logo (purple rounded play mark + "NewTube" wordmark), spacer,
  refresh, bell, search. Refresh keeps the existing manual reload behaviour.
- **Search:** full-width search field with leading search icon and trailing
  clear; the field is focused when the tab is opened by the top-bar search icon.
- **Subscriptions:** title "Підписки".
- **You:** title "Ви" + settings gear.
- **Shorts:** no top bar (full-bleed).
- **Detail screens:** back arrow + title.

Bell and avatar are not shown on Home: the bottom bar already exposes both You
and Subscriptions; adding an avatar would crowd 360dp. Bell is Home-only.

## 10. Home screen

### 10.1 Structure

One `LazyColumn`:

1. `item` — category chips (horizontal `LazyRow`, not sticky).
2. `item` — Shorts shelf (hidden when data is unavailable).
3. `items` — video cards.
4. `item` — bottom spacer (16dp + navigation-bar inset).

Top bar is outside the scrolling list. Category switch reloads the feed and
resets scroll to top; chips keep their selected state.

Categories (UI labels): Усі, Музика, Ігри, Фільми, Спорт, Новини, Технології.
`HomeCategory` stays a pure domain enum; the app layer maps each value to a
`@StringRes` label, so no Android types leak into `:domain`.

### 10.2 Feed

Keep `VideoRepositoryImpl.getHomeFeed()` composition (trending + popular +
topic searches, shuffled, deduped). Changes:

- Map `authorThumbnails` into `Video.authorAvatarUrl`.
- Apply a metadata sanity rule in the card formatter: if `duration <= 0 &&
  views <= 0`, show only the channel name (no "0 views" / "0 seconds ago");
  if `uploadedAt` is blank or exactly `"0 seconds ago"`, omit the time segment.
  This hides the default instance's broken trending metadata without hiding
  real zero-view new videos (which still have a non-zero duration).
- Skeleton state: five placeholder cards (grey blocks with a subtle alpha pulse)
  instead of a centred spinner. Error state: message + "Повторити". Empty state:
  "Тут поки немає відео".

### 10.3 Shorts shelf

- Data: a new lightweight `VideoRepository.getShortsShelf(limit = 8)` backed by a
  **single** Invidious `#shorts` search request (video type, 1–60 s, not live),
  shuffled and capped. Do **not** call `getShorts(page = 1)`: it performs ~4
  channel searches plus up to 7 channel-shorts requests and advances the Shorts
  tab rotation, so it would both slow Home down and change what the Shorts tab
  shows. Load the shelf in parallel with the feed; the feed must not wait for it.
  Cache the result in the ViewModel and refresh it with Home.
- Header row: custom Shorts glyph + "Shorts" `titleMedium`; optional trailing
  "Усі" is omitted (there is already a Shorts tab).
- Tiles: 150dp wide, aspect 9:16, 12dp rounded, thumbnail crop, bottom scrim
  gradient, title in white 2 lines (`titleSmall`), no avatar/duration. Tapping
  navigates to the Shorts tab at that video.
- If loading fails or returns empty: omit the shelf entirely (no error row).

### 10.4 Video card (`VideoCard`)

Replaces `VideoListItem` for Home, Search, related videos and channel videos.

- 16:9 thumbnail, 12dp rounded, Coil, `ContentScale.Crop`.
- Duration badge bottom-right: black 80% pill, white 11sp, `12:34`; hide when
  duration is 0. `LIVE` badge (red pill, white text) when `isLive`.
- Below: 36dp circular channel avatar on the left (generic person icon on a
  `surfaceVariant` circle when URL is empty), then a column:
  - title, `titleSmall`, max 2 lines, ellipsis;
  - metadata, `bodySmall`, `onSurfaceVariant`, max 1 line:
    `Channel · 1.2M views · 2 days ago`, segments omitted per §10.2.
- Whole card opens Watch. Avatar and channel name open Channel. No overflow
  menu until there are real actions; do not add dead buttons.
- Existing `formatCount` is reused; add "views" → Ukrainian "переглядів" only in
  new formatted strings, keeping the number formatting logic.

## 11. Shorts tab

Keep the existing vertical pager and playback logic. Changes:

- Optional `videoId` argument positions the pager at the requested short. If the
  loaded page does not contain it, fetch `getVideoById`, prepend to the list and
  start at index 0.
- Clear the navigation argument after applying it.
- Restyle overlays/chrome to theme colours so the dark/light switch is coherent.
- History recording: a short is recorded once its page has been the settled
  pager page for **3 seconds** (no position data, no repeat row). This reuses
  `HistoryRepository.record(video)` and respects `record_history`; it does not
  create a second recording path for every swipe.

No other Shorts redesign in this project.

## 12. Search tab

Keep `SearchViewModel` and Invidious search. Replace the card list with
`VideoCard`, keep the search field in the top bar, skeleton rows while loading
and the existing error/empty states. Search history is out of scope.

## 13. Subscriptions tab

- **Signed out:** full-screen CTA: icon, "Увійдіть у Google, щоб бачити свої
  підписки", primary button "Увійти", secondary text explaining that sign-in is
  only used for account data. Button opens `login`.
- **Signed in:** two-column `LazyVerticalGrid` of channels (72dp circular
  avatar, name 2 lines, optional subscriber text), 16dp gaps. Tap opens
  `channel/{channelId}` (Invidious data). Initial page from
  `FEsubscriptions`; append via continuation on scroll.
- **Loading:** skeleton grid. **Error/empty:** message + retry / "Підписок
  немає". **Session expired:** inline banner with "Увійти знову".

Subscriptions data comes from the account; no local cache is required. If the
channel screen fails (Invidious doesn't know the id), show the standard channel
error with retry.

## 14. You tab

- **Signed out:** row with generic avatar, "Увійти в Google", subtitle
  "Історія та підписки", tapping opens `login`. Below: Історія переглядів,
  Налаштування, Про застосунок.
- **Signed in:** avatar (48dp), account name, email; a "Вийти" row with a
  confirmation dialog. Below: Історія переглядів, Підписки (shortcut),
  Налаштування, Про застосунок.
- Rows use 24dp leading icon, 16dp padding, chevron via `Icons.Filled.
  KeyboardArrowRight`.

Sign-out clears tokens, profile and remote caches. Local history is kept unless
the user clears it in Settings.

## 15. History

### 15.1 Screen behaviour

`HistoryScreen` chooses its source:

1. **Signed in and `use_remote_history` on:** InnerTube `FEhistory`. Show
   "Історія YouTube" as a subtitle. Paginate with continuation (30 per page,
   append on scroll). If the first page fails with an auth error → trigger the
   session-expiry state and show the local list as fallback with a banner.
2. **Otherwise:** local Room history, newest first.

Rows are compact: 160×90 thumbnail, title 2 lines, channel, watched-time/views.
Tap opens Watch. Remote rows are not written to Room.

### 15.2 Local recording

- Table `watch_history` (§16). One row per `videoId`; re-watching updates
  `watchedAt` instead of duplicating. Cap 500 rows: after an insert, delete ids
  older than the 500th newest.
- `WatchViewModel` records on successful load with the video metadata. Only if
  `record_history` is true. Shorts record via the 3-second rule in §11.
- Local history stores **no playback position**. Resume position remains owned
  by `DataStorePlaybackPositionRepository`; the player keeps writing it exactly
  as today. This avoids two competing sources of truth and duplicate writes.
- Clearing history deletes all local rows **and** all DataStore resume positions
  (`PlaybackPositionRepository.clearAll()`), because keeping "resume" state after
  the user clears the watch history would contradict the promise made in §21. The
  confirmation dialog says that resume positions are cleared too.

### 15.3 Remote parsing

InnerTube `FEhistory` with the TV client returns
`contents.tvBrowseRenderer.content…gridRenderer.items[].gridVideoRenderer`
(confirmed in MediaServiceCore's captured fixture). Older/newer responses may
use `videoRenderer`, `tileRenderer` or `tvhtml5VideoRenderer`. Parse
**tolerantly** by walking the JSON tree for any object that has a `videoId`,
rather than by binding one rigid Moshi shape:

- title: `title.simpleText` or `title.runs[].text` joined;
- thumbnail: highest-resolution entry of `thumbnail.thumbnails`;
- channel name: `shortBylineText` / `longBylineText` / `ownerText`;
- channel id: first `browseEndpoint.browseId` starting with `UC` in the item;
- views: `viewCountText.simpleText` (raw string, shown as-is);
- published: `publishedTimeText.simpleText`;
- duration: `lengthText.simpleText` parsed `h:mm:ss`/`m:ss`;
- live: `badges[].metadataBadgeRenderer.style == "BADGE_STYLE_TYPE_LIVE_NOW"`
  or presence of `isLive`;
- continuation: first `continuationCommand.token` found in
  `continuations[]` or at the end of the content tree.

Deduplicate by `videoId`. Ignore items without a video id. This one parser also
serves notifications (§19) and any future browse list.

## 16. Data layer changes

### 16.1 Domain models

```kotlin
data class Video(
    …existing fields…,
    val authorAvatarUrl: String = "",
)

data class UserAccount(
    val name: String,
    val email: String,
    val avatarUrl: String,
    val pageId: String? = null,
)

data class WatchHistoryEntry(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val authorAvatarUrl: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val watchedAt: Long,
)

data class SubscriptionChannel(
    val channelId: String,
    val title: String,
    val avatarUrl: String,
    val subscriberText: String? = null,
)

enum class ThemeMode { SYSTEM, DARK, LIGHT }
```

### 16.2 Room v3

`VideoEntity` gains `authorAvatarUrl: String` (default `""` in the entity for
safe inserts). New table:

```kotlin
@Entity(
    tableName = "watch_history",
    indices = [Index("watchedAt")],
)
data class WatchHistoryEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val authorAvatarUrl: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val watchedAt: Long,
)
```

`NewTubeDatabase.VERSION = 3`, entities add `WatchHistoryEntity`, DAO:

```kotlin
@Upsert suspend fun upsert(entry: WatchHistoryEntity)
@Query("SELECT * FROM watch_history ORDER BY watchedAt DESC LIMIT :limit")
fun observeRecent(limit: Int = 500): Flow<List<WatchHistoryEntity>>
@Query("DELETE FROM watch_history") suspend fun clear()
@Query("""DELETE FROM watch_history WHERE videoId NOT IN
          (SELECT videoId FROM watch_history ORDER BY watchedAt DESC LIMIT :keep)""")
suspend fun trim(keep: Int = 500)
```

`MIGRATION_2_3` (registered in `DatabaseModule` next to `MIGRATION_1_2`):

```sql
ALTER TABLE videos ADD COLUMN authorAvatarUrl TEXT NOT NULL DEFAULT '';
CREATE TABLE IF NOT EXISTS watch_history (
    videoId TEXT NOT NULL PRIMARY KEY,
    title TEXT NOT NULL,
    channelName TEXT NOT NULL,
    channelId TEXT NOT NULL,
    authorAvatarUrl TEXT NOT NULL,
    thumbnailUrl TEXT NOT NULL,
    durationMs INTEGER NOT NULL,
    watchedAt INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS index_watch_history_watchedAt
    ON watch_history (watchedAt);
```

Add `room-testing` as `androidTestImplementation` and a migration test that
creates v2, inserts a video, migrates, and checks both the new column and the
history table.

### 16.3 DataStore settings

New file `settings.preferencesDataStore`:

| Key | Type | Default | Meaning |
|---|---|---|---|
| `theme_mode` | String | `SYSTEM` | `ThemeMode.name` |
| `record_history` | Boolean | true | write local watch history |
| `use_remote_history` | Boolean | true | prefer YouTube history when signed in |
| `default_quality` | Int | 720 | maximum stream height (480/720/1080) |

`SettingsRepository` exposes a `Flow<AppSettings>` and suspend setters.
`default_quality` is a **max height for stream selection**, not just a player
cosmetic: `VideoRepository.getVideoDetails(videoId, maxHeight)` replaces the
hard-coded `PREFERRED_MAX_HEIGHT = 720` in `VideoRepositoryImpl`, and Watch and
Shorts pass the setting through. The existing `PlaybackSource` model is
unchanged (single HLS/progressive source, or one merged adaptive video+audio
pair), so 1080p works only when the instance offers such a track; otherwise the
app picks the best track at or below the limit. The in-player quality menu keeps
its current session-only behaviour and can only lower the selected track's
constraint (the source itself is still single-track).

Why not "Auto": with one resolved source there is nothing for Auto to choose
between. The player menu still has Auto for track constraints, but Settings
answers "what is the maximum quality NewTube should request".

## 17. Google account (device-code OAuth)

### 17.1 UX flow

`LoginScreen` states, driven by `AccountRepository.loginFlow()`:

1. **Requesting code** — spinner, "Отримуємо код…" (1–3 s).
2. **Waiting** — big monospace `GDF-LMZ-WBP` (auto-formatted), text
   "Відкрийте google.com/device на телефоні або комп'ютері та введіть код",
   buttons: **Відкрити сайт**, **Копіювати код**, **Скасувати**; countdown from
   `expires_in`; subtle "Перевіряємо…" indicator each poll.
3. **Success** — brief check, then `popBackStack()`; the You tab shows the
   profile.
4. **Error** — human message + "Спробувати ще"; expired/denied codes return to
   step 1.
5. **Cancelled** — polling stops on `onCleared`/button, no token writes.

"Відкрити сайт" uses an `ACTION_VIEW` intent for the returned
`verification_url` (currently `https://www.google.com/device`) with
`FLAG_ACTIVITY_NEW_TASK`. If no browser resolves, show a dialog with the URL and
code to type on another device. Do not add Custom Tabs as a hard dependency; a
plain browser intent is enough on this device and keeps the dependency set small.
"Копіювати код" uses `ClipboardManager` and shows a snackbar.

### 17.2 Endpoints and payloads

Requests are JSON (`Content-Type: application/json`), Fire TV Cobalt UA:

```
POST https://www.youtube.com/o/oauth2/device/code
{
  "client_id": "<scraped>",
  "device_id": "<stable random hex>",
  "model_name": "ytlr::",
  "scope": "http://gdata.youtube.com https://www.googleapis.com/auth/youtube-paid-content"
}
→ { device_code, user_code, expires_in: 1800, interval: 5, verification_url }

POST https://www.youtube.com/o/oauth2/token
{
  "code": "<device_code>",
  "client_id": "<scraped>",
  "client_secret": "<scraped>",
  "grant_type": "http://oauth.net/grant_type/device/1.0"
}
→ { access_token, refresh_token, expires_in, token_type }

POST https://www.youtube.com/o/oauth2/token
{
  "refresh_token": "<stored>",
  "client_id": "<scraped>",
  "client_secret": "<scraped>",
  "grant_type": "refresh_token"
}
→ { access_token, expires_in, token_type }
```

Polling errors are JSON `{"error": "..."}`:

- `authorization_pending` → keep polling at `interval`;
- `slow_down` → add 5 s to the interval and continue;
- `access_denied`, `expired_token` → terminal, show message, allow retry;
- network/5xx → keep polling with capped backoff (max 30 s) until expiry.

Use a stable `device_id`: 16 random bytes as hex, generated once and stored in
the encrypted token store. Never use `ANDROID_ID` (privacy) and never log it.

### 17.3 Client id/secret discovery

`ClientCredentialsProvider`:

1. Return a cached pair from EncryptedSharedPreferences unless it is older than
   7 days.
2. Otherwise `GET https://www.youtube.com/tv` with the Fire TV UA (below) and
   `Accept-Language: en-US,en;q=0.9`, parse the base script URL in this order:
   - `id="base-js" src="…"`,
   - Cobalt: `.src = '<…m=base>'`,
   - Cobalt: `.src = '<…>'; .id = 'base-js'`.
   Resolve it against `https://www.youtube.com/tv` (relative URLs are normal).
3. Fetch the JS with the same UA; take the **first** match of
   `clientId:"([-\w]+\.apps\.googleusercontent\.com)",\s*\w+:"(\w+)"`.
4. Store `{clientId, clientSecret, fetchedAt}` encrypted.

UA proven on this setup (2026-11-09):

```
Mozilla/5.0 (Linux armeabi-v7a; Android 7.1.2; Fire OS 6.0)
Cobalt/22.lts.3.306369-gold (unlike Gecko) v8/8.8.278.8-jit gles Starboard/13,
Amazon_ATV_mediatek8695_2019/NS6294 (Amazon, AFTMM, Wireless)
com.amazon.firetv.youtube/22.3.r2.v66.0
```

(one line, no wrapping in code). The same UA is used for the device-code
request; it already produced a valid code.

Failure handling: no cached pair and scraping fails → show "Не вдалося
підготувати вхід. Перевірте з'єднання" with retry. Never ship a hardcoded
client secret in source; it would rot and put the secret in every APK.

On `401 invalid_client` from token endpoints, clear the cached pair, re-scrape
once and retry. On `invalid_grant` during refresh, treat the session as expired:
clear tokens, emit `SignedOut(reason = Expired)`.

### 17.4 Token storage and lifecycle

- `EncryptedSharedPreferences` (`androidx.security:security-crypto:1.1.0-alpha06`)
  file `account_tokens`, `MasterKey` AES256_GCM. Keys: `refresh_token`,
  `access_token`, `expires_at`, `device_id`, `client_id`, `client_secret`,
  `client_fetched_at`, `account_json`.
- Access token lifetime is **not** ~60 min on this setup: the Phase 0 spike received
  `expires_in` values of 64 001 s (~17.8 h) and, after refresh, 67–85 k s
  (~18.6–23.7 h). Still refresh when `now >= expires_at − 5 min` so the logic is
  correct for any issued lifetime.
- Refreshing does **not** rotate the refresh token (spike: no `refresh_token` in
  the refresh response). Keep the stored one and only overwrite it when the
  response actually contains a new value.
- A single `Mutex` guards refresh; concurrent InnerTube calls wait for one
  refresh and reuse the result.
- InnerTube `401` → force one refresh and retry the request once; second `401`
  → expired session.
- Separate OkHttp clients:
  - **token client**: no logging interceptor at all (bodies contain the refresh
    token);
  - **InnerTube client**: `HttpLoggingInterceptor` at `BASIC` in debug with
    `redactHeader("Authorization")`, `redactHeader("X-Goog-Pageid")`;
  - **Invidious client**: unchanged.
- Backups: `android:allowBackup` is currently true. Add
  `res/xml/backup_rules.xml` + `data_extraction_rules.xml` excluding
  `account_tokens` from cloud/device backup (Keystore keys do not transfer and a
  restored token file would be unreadable anyway).
- Sign-out clears `account_tokens` and any in-memory profile/remote caches.

### 17.5 Profile (`accounts_list`)

After a successful token exchange:

```
POST https://www.youtube.com/youtubei/v1/account/accounts_list
headers: Authorization: Bearer …, TV UA, Referer: https://www.youtube.com/tv
body: TV context + {"accountReadMask":{
        "returnOwner":true,"returnBrandAccounts":true,"returnPersonaAccounts":true}}
```

`returnPersonaAccounts` must be **true**: the 2026-11-20 spike got HTTP 200 with an
empty `contents` when it was `false`, and the full account item when it was `true`.

Parse the first account with `hasChannel == true`:

- `accountName.simpleText` → name (a Text object, not a bare string);
- `accountByline.simpleText` → email (also a Text object);
- `accountPhoto.thumbnails` → largest avatar;
- optional `serviceEndpoint.selectActiveIdentityEndpoint.supportedTokens[]
  .pageIdToken.pageId` → `pageId`. The spike's owner account had **no**
  `pageIdToken` (only `accountStateToken`, `offlineCacheKeyToken`,
  `datasyncIdToken`), so treat `pageId` as optional and never fail sign-in
  without it; brand accounts may provide it.

If there are multiple accounts (owner + brand accounts), phase 1 uses the first
`isSelected` account, else the first. Store the profile JSON encrypted; the You
tab renders from it before any network call.

## 18. InnerTube client

### 18.1 Base request

`POST https://www.youtube.com/youtubei/v1/browse` with:

```
Content-Type: application/json
User-Agent: <Fire TV Cobalt UA>
Referer: https://www.youtube.com/tv
Authorization: Bearer <access token>        (when signed in)
X-Goog-Pageid: <pageId>                     (only if stored)
```

Body is built as a `JsonObject`:

```json
{
  "context": {
    "client": {
      "clientName": "TVHTML5",
      "clientVersion": "7.20260901.15.00",
      "clientScreen": "WATCH",
      "userAgent": "<Fire TV UA>",
      "acceptLanguage": "uk",
      "acceptRegion": "UA",
      "utcOffsetMinutes": "120",
      "visitorData": "<visitor id or omitted>"
    },
    "user": { "enableSafetyMode": false, "lockedSafetyMode": false },
    "tvAppInfo": { "appQuality": "TV_APP_QUALITY_FULL_ANIMATION", "zylonLeftNav": true },
    "webpSupport": false,
    "animatedWebpSupport": true
  },
  "racyCheckOk": true,
  "contentCheckOk": true,
  "browseId": "FEhistory"
}
```

Notes:

- `acceptLanguage`/`acceptRegion` from `Locale.getDefault()` with `en`/`US`
  fallbacks; `utcOffsetMinutes` from the current offset.
- Do not add a `visitorData` unless one is available; omitting it is valid.
- Client version is a constant. If YouTube starts rejecting it, bump the
  constant; no runtime config fetch is required for phase 1.
- Continuation requests send `"continuation": "<token>"` instead of `browseId`
  (same context).
- Do **not** fetch or send PO tokens. Browse endpoints do not need them.

### 18.2 Endpoints used

| Feature | Body | Notes |
|---|---|---|
| History | `browseId: "FEhistory"` | paginate via continuation |
| Subscriptions | `browseId: "FEsubscriptions"` | channel grid + continuation |
| Notifications | `browseId: "FEnotifications"` | best effort, first page only |
| Profile | `/account/accounts_list` | see §17.5 |

Both endpoints return `contents.tvBrowseRenderer` — **not** the web renderers
(`videoRenderer`, `lockupViewModel`, `gridChannelRenderer`). Observed on
2026-11-20:

- `FEhistory`: `tvBrowseRenderer` → `tvSurfaceContentRenderer` →
  `sectionListRenderer` → `shelfRenderer` → `horizontalListRenderer` →
  `tileRenderer` items (16 on the first page, 2 continuation markers).
- `FEsubscriptions`: `tvBrowseRenderer` → `tvSecondaryNavRenderer` →
  `sections[].tvSecondaryNavSectionRenderer.tabs[].tabRenderer.content` →
  `tvSurfaceContentRenderer` → `sectionListRenderer` → `shelfRenderer` →
  `horizontalListRenderer` → `tileRenderer` (30 tiles, 29 `UC…` browse
  endpoints, 54 continuation markers).

Use the tolerant walker for both (channel items key off
`browseEndpoint.browseId` starting with `UC` and a `title`). A signed-out
`browse` with `FEhistory` answers **401**, so the error mapping in §18.3 matters
even before sign-in UI work.

### 18.3 Errors

Map to a small sealed result (`Success`, `NeedsSignIn`, `Network`, `Server`,
`ParseEmpty`):

- 401/403 → refresh once (§17.4); still failing → `NeedsSignIn` and update
  account state to `SignedOut(Expired)`.
- 429 → `Server` with retry-after honoured for the next manual retry; no
  automatic loop.
- 5xx/timeout → `Network`/`Server`; UI shows retry.
- Parse yields zero items → `ParseEmpty`; UI shows empty state, logged once at
  debug level with the response's top-level keys (never the full body, which
  may contain account data).

## 19. Notifications screen

Best-effort, explicitly fenced:

- Signed out → same CTA as Subscriptions.
- Signed in → one `FEnotifications` request, parse with the tolerant walker,
  render rows (thumbnail if present, title, channel, published text). Tapping a
  row with a video id opens Watch; rows without one are non-clickable.
- Empty/unparsable → "Немає нових сповіщень".
- No pagination, no badges/unread counts. This screen must not block the release:
  if the endpoint shape proves unusable during implementation, ship it with the
  empty state and record the limitation in the plan.

## 20. Settings screen

Sections (Material3 list rows, no preference library):

**Вигляд**
- Тема: Системна / Темна / Світла (segmented or radio dialog; default Системна).

**Історія**
- Записувати перегляди (switch, default on).
- Показувати історію YouTube (switch, default on; disabled with explanatory
  subtitle when signed out).
- Очистити історію переглядів (destructive text button + confirm dialog; only
  enabled when local history exists). The dialog states that resume positions
  are also cleared.

**Відтворення**
- Максимальна якість: 480p / 720p / 1080p (radio dialog, default 720p). This is
  the max stream height passed into `getVideoDetails(videoId, maxHeight)`; if the
  instance has no track that high, the best available lower track is used.

**Акаунт**
- Signed out: "Увійти в Google" → `login`.
- Signed in: name/email, "Вийти" with confirmation.

**Про застосунок**
- Version (`BuildConfig.VERSION_NAME`), Invidious base URL (build config),
  one-line note that the app is not affiliated with YouTube.

Changing a setting applies immediately. No "save" button.

## 21. Security and privacy

- Refresh/access tokens, client credential cache and the profile live in
  `EncryptedSharedPreferences`; nothing token-related in DataStore/Room/logs.
- Auth HTTP goes through a non-logging client. Headers are redacted on the
  InnerTube client even in debug.
- Emails/avatars are personal data: no analytics, no third-party upload; only
  rendered and stored locally.
- Sign-out and "clear watch history" (which also clears resume positions) are the
  two user-facing data-deletion paths.
- Scraped client credentials are cached encrypted; no secret in source or logs.
- The account is optional; the app never asks for the Google password.
- Note in the About section that account features use Google's TV OAuth and that
  YouTube can invalidate the session at any time.

## 22. Phased delivery

Each phase builds and is shippable on its own; do not start a phase before the
previous one compiles, passes unit tests and is manually checked on the device.

### Phase 0 — Auth feasibility spike (gate for phases 4–5)

The design is based on code issuance only (§5). Before the login UI and account
screens are planned in detail, prove the whole chain once, outside the app (a
throwaway PowerShell/Node script or a temporary JVM test; not product code):

1. Scrape client id/secret from `youtube.com/tv`.
2. Request a device code, show the user code, have the **user** approve it at
   `google.com/device`.
3. Poll `/o/oauth2/token` until a real `access_token` + `refresh_token` arrive.
4. Refresh once and confirm a second `access_token`.
5. Call `accounts_list`, then `browse` with `browseId: "FEhistory"` and
   `FEsubscriptions`, using `Authorization: Bearer …` and the TV context.
6. Save redacted response fixtures into `data/src/test/resources/`, and copy the
   MediaServiceCore `history.json` fixture into the repo.

**Gate:** if Bearer-authenticated browse is rejected or the token cannot be
refreshed, stop and revisit this design with the user before Phase 4. Phases 1–3
(UI, local history) are unaffected and may proceed while the spike is pending.
If the spike succeeds, record any request/header corrections in this document
before planning phases 4–5.

**Result (2026-11-20): PASSED.** Throwaway script:
`.superpowers/sdd/2026-11-12-youtube-home-ui-and-local-history/spike/phase0.ps1`
(gitignored, never product code).

1. Scraping worked on the first try with the documented UA: `youtube.com/tv`
   served `id="base-js" src="…/kabuki.base_cr.en_US…/m=base"` and the JS carried
   the `clientId:"….apps.googleusercontent.com",<key>:"…"` pair.
2. Device code issued (`expires_in 1800`, `interval 5`,
   `verification_url https://www.google.com/device`) and approved by the user.
3. Token exchange returned `access_token` + `refresh_token` (`expires_in 64001`).
4. Refresh worked twice; no `refresh_token` rotation, lifetime 67–85 k s.
5. `accounts_list` returned the account (name, email, 216 px avatar,
   `hasChannel`, `isSelected`) once `returnPersonaAccounts` was `true`.
6. Bearer `browse` returned real data: `FEhistory` 200 with 16 history tiles,
   `FEsubscriptions` 200 with 30 channel tiles; the same request without
   `Authorization` returns 401.

Redacted fixtures are committed under `data/src/test/resources/spike/`
(`accounts_list.json`, `browse_FEhistory.json`, `browse_FEsubscriptions.json`);
profile name/email/avatar/gaia id were replaced with placeholders, tokens and
`visitorData` are `REDACTED`. Corrections found by the spike are folded into
§17.2, §17.4, §17.5 and §18.2 above. The MediaServiceCore `history.json` fixture
mentioned in step 6 is not available in this workspace; the captured
`browse_FEhistory.json` replaces it.

### Phase 1 — Foundations and theme
- `ThemeMode`, dark/light schemes, `NewTubeTheme(darkTheme)`, system bars,
  edge-to-edge, typography/shapes, settings DataStore.
- Minimal Settings screen wired to the theme (other rows can land in phase 3).
- Unit tests: ThemeMode parsing/defaults, settings flow.

### Phase 2 — Shell and Home redesign
- Five-tab shell, routes, top bar, icons, bottom nav.
- Home: keys/labels, chips, skeleton cards, `VideoCard`, metadata sanity rule.
- Avatars: `authorThumbnails` in DTO → domain/entity → Room v3 migration
  (§16.2) and `VideoCard`.
- `getShortsShelf` + shelf UI + `shorts?videoId=` positioning.
- You tab shell (signed-out state), Search restyle.

### Phase 3 — Local history and settings complete
- `watch_history` storage and recording from `WatchViewModel`/Shorts, History
  screen (local source), Settings rows for history/quality/clear, About.
- `getVideoDetails(videoId, maxHeight)` plumbing for the quality setting and
  updated playback tests.
- Unit tests: DAO/trim logic, history repository mapping, formatter edge cases.

### Phase 4 — Google sign-in (after the Phase 0 gate)
- `security-crypto`, token store, credential scraper, device-code flow,
  polling, refresh single-flight, profile fetch, Login screen, You tab signed-in
  state, sign-out, session-expiry handling.
- Unit tests with fixtures: base-JS regex (proven HTML/JS snapshot), device-code
  and token JSON, pending/slow_down/denied error mapping, refresh single-flight
  (fake clock), `accounts_list` parsing.

### Phase 5 — Account content (after the Phase 0 gate)
- InnerTube client + context + tolerant parser, remote History with pagination,
  Subscriptions grid, notifications best effort, `use_remote_history` wiring,
  signed-out CTAs, expired-session banners.
- Unit tests: parser against the Phase 0 fixtures (history, subscriptions,
  notifications), continuation extraction, dedupe, 401 → refresh → retry.

### Phase 6 — QA and polish
- Manual pass on the TCL device (signed out/in, dark/light/system, rotation,
  PiP, back stack, offline, expired token, empty feeds, 500-item history trim).
- `./gradlew :app:testDebugUnitTest :app:assembleDebug` green.
- Screenshot comparison against YouTube/GreenTuber; fix spacing/contrast.

## 23. Testing strategy

- **JVM unit tests** for every parser and state machine: credential regex,
  OAuth responses, polling transitions, InnerTube JSON walker, continuation,
  dedupe, metadata formatter, history trim, theme/settings parsing.
- **Fixtures** in `data/src/test/resources/` (HTML/JS excerpt, device/token JSON,
  `FEhistory` from the Phase 0 spike or the MediaServiceCore checkout, and
  Phase 0 captures for subscriptions/notifications). Redact tokens, emails and
  names. No live network in tests.
- **Instrumented** Room migration test (2→3), plus an end-to-end manual checklist.
- Compose UI tests are optional; if added, cover the five-tab shell and
  signed-out CTAs only.
- Manual device checks use the connected TCL (`adb`) and compare screenshots with
  YouTube/GreenTuber, as done during recon.

## 24. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| YouTube changes TV JS or rotates client credentials | Sign-in fails | Runtime scrape + encrypted cache + 401 re-scrape once + clear error; app works signed out; bump logic in one provider. |
| TV OAuth client revoked | Sign-in fails | Same; account features degrade, no crash. Documented limitation. |
| InnerTube response shape changes | Empty history/subs | Tolerant tree walker, fixtures, `ParseEmpty` empty state, parser isolated in one file. |
| ToS/account risk of third-party TV client | Account action | User accepted; About note; no writes, read-only. |
| EncryptedSharedPreferences library alpha | Crash on some devices | Pin `1.1.0-alpha06` (API 23+ fixes), verify on Android 12; fallback plan: Keystore AES-GCM blob in DataStore. |
| Broken trending metadata on default instance | Ugly cards | Metadata sanity rule; feed still mixes search/popular. |
| Extra Shorts shelf request | Slower Home | Single `#shorts` search (not the channel-discovery feed), loaded in parallel, 8-item cap, hidden on failure. |
| Room migration breaks existing data | Lost cache/history | Explicit `MIGRATION_2_3` + instrumented migration test; no destructive fallback. |
| 5 tabs cramped at 360dp | Truncated labels | 11sp labels, `alwaysShowLabel`, icons 24dp; verify on device. |
| Compose M3 1.2 lacks newer components | Build/visual compromises | No Material3 upgrade; skeleton/pull-to-refresh implemented manually. |
| Max-quality setting conflicts with stream availability | Playback error | Treated as a cap in stream selection; always fall back to the best available track at or below it. |
| Local history grows unbounded | Storage | 500-row cap, single row per video. |
| Phase 0 finds Bearer browse rejected | Account half collapses | Gate stops phases 4–5; UI/local-history phases still ship; revisit design with the user. |

## 25. Decisions made for the builder

These are my calls, not the user's; change them only for a concrete reason.

- Ukrainian UI strings for new/changed surfaces; "Shorts" stays "Shorts".
- Purple accent for actions/logo; YouTube-style white/black selected chips.
- Flat cards, 12dp thumbnails, 36dp avatars, 150dp Shorts tiles.
- Bell is Home-only; avatar is not duplicated in the top bar.
- Manual refresh icon on Home instead of pull-to-refresh (M3 1.2 limitation).
- Notifications is best effort and may ship with an empty state.
- Local history keeps one row per video and is capped at 500; it stores no
  playback position, and clearing it also clears DataStore resume positions.
- Shorts are recorded after 3 seconds as the settled pager page; no per-swipe
  spam.
- Remote history is never persisted to Room.
- The Home Shorts shelf uses a single `#shorts` search (`getShortsShelf`), not
  the heavier Shorts-tab feed.
- "Максимальна якість" is a stream-selection cap (480/720/1080, default 720),
  not a cosmetic player preference.
- Single account (first selected) in phase 1; `pageId` support is included.
- Plain browser intent for `google.com/device`, no Custom Tabs dependency.
- `EncryptedSharedPreferences` for secrets; no hardcoded client credentials.
- `kotlinx.serialization` `JsonElement` for InnerTube responses in `:data`.

## 26. What is left to the builder

- Exact shimmer animation, skeleton block sizes and colour alphas.
- Precise metadata date formatting (Invidious gives `publishedText`; only the
  broken-value rule is fixed).
- Error-message wording and snackbar vs inline banner choices.
- Whether to add a tiny `SettingsViewModel` or inject `SettingsRepository`
  directly into each screen.
- The exact `SettingsRepository` → `getVideoDetails(maxHeight)` plumbing and its
  parameter name.
- File/class names inside the packages sketched in §7.
- The exact vector paths for `ic_shorts`, `ic_subscriptions`, `ic_history`.
- Whether the tolerant parser exposes typed intermediates or maps directly to
  domain models.
- Exact polling backoff constants beyond the fixed rules in §17.2.
- Whether the Phase 0 spike is a script or a temporary JVM test (it must not
  become product code).
