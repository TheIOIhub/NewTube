# NewTube — прискорення завантаження відео та Shorts (дизайн)

> Статус: обрано варіант **C — поетапно** (Phase 1 без міграції плеєра, Phase 2 Media3 + PreloadManager).
> Дата: 2026-11-26. Цей документ — вхідний для плану `docs/superpowers/plans/2026-11-26-video-shorts-performance.md`.

## 1. Намір і чому

Користувач бачить три симптоми: (1) перші Shorts повільно з'являються при вході у вкладку, (2) свайп на наступний кліп затримується, (3) під час відтворення виникає буферизація. Розслідування показало **чотири незалежні корені**, і кожен потребує окремої дії:

| Симптом | Корінь (підтверджено кодом / логами) |
|---|---|
| 1 | `ShortsViewModel.reload()` (`app/.../ui/shorts/ShortsViewModel.kt:87-122`) виконує до 3 **послідовних** `getShorts(page)`; кожен виклик = discovery каналів + до 7 паралельних `/channels/{id}/shorts` (`data/.../implementation/VideoRepositoryImpl.kt:107-144,159-224`). Логи пристрою: `search?q=%23shorts` 2596/2780/2668 мс, `/videos/{id}` 324 мс, холодний старт `+4s432ms`. Плюс `HomeViewModel` паралельно тягне `getShortsShelf()`. |
| 2 | `ensurePlayback()` викликається лише на зміні `currentPage` (`ShortsScreen.kt:108-114`) — резолв URL починається **під час** свайпу. Кожен active-кліп створює новий `ExoPlayer` і звільняє його (`VideoPlayer.kt:96-108,165-169`). |
| 3 | `PlaybackSource.toMediaSource()` (`VideoPlayer.kt:343-364`) використовує `DefaultDataSource.Factory` без дискового кешу; OkHttp без `.cache()` (`NetworkModule.kt:28-40`); дефолтний `LoadControl`; VOD іде через `MergingMediaSource` (два `range`-запити). |

Мета: зробити вхід у Shorts майже миттєвим на повторному запуску, прибрати затримку свайпу та скоротити буферизацію — без руйнування поточної архітектури MVVM/Hilt/Compose і без збільшення навантаження на старті.

## 2. Цілі / не-цілі / анти-цілі

**Цілі**
- G1. Повторний вхід у Shorts показує останній кешований фід **негайно**, фон оновлює його без блокування UI.
- G2. Для поточного кліпу та ±2 сусідніх URL/джерела резолвляться заздалегідь, з обмеженням паралельності.
- G3. Один `ExoPlayer` на весь Shorts-пейджер, повторне використання декодера між свайпами.
- G4. Дисковий кеш медіа (LRU, 256 МБ) + налаштований `LoadControl` для меншої буферизації.
- G5. Phase 2: `DefaultPreloadManager` + Paging 3 з Room як source of truth — підтримка довгого фіду без OOM.

**Не-цілі**
- Не додаємо офлайн-завантаження (`DownloadManager`), субтитри, live/DVR, зміну інстанса, baseline profiles.
- Не виправляємо специфічну для тестового інстанса проблему 403 на VOD HLS-сегментах.
- Не оптимізуємо сам Cold Start за межами "не робити важку роботу на головному потоці".

**Анти-цілі (заборонено)**
- **Не зберігати на диску підписані URL стрімів** (`videoplayback?...` мають строк дії; застарілий URL = порожній плеєр). Кешуємо лише метадані фіду.
- Не збільшувати кількість запитів на першому холодному старті (сьогодні вже ~2.6 с на запит).
- Не тримати одночасно більше одного активного плеєра Shorts.
- Не показувати застарілий фід так, ніби він свіжий: потрібен ненав'язливий індикатор оновлення.

## 3. Обмеження

- Android API 24+; compileSdk/targetSdk 34; JDK 17; AGP 8.2.2; Kotlin 1.9.24; Compose BOM 2024.03.00.
- Phase 1 лишає **ExoPlayer 2.19.0**; Phase 2 — **Media3 1.4.1** (версія з `DefaultPreloadManager`, сумісна з compileSdk 34 / AGP 8.2.2; Media3 1.5+ вимагає compileSdk 35). Якщо 1.4.1 не збереться — явний fallback: підняти compileSdk до 35 і AGP до 8.6, не міняючи targetSdk 34.
- Room 2.6.1: схема v3 → **v4**, обов'язковий `MIGRATION_3_4` + новий `4.json`.
- ktlint (`./gradlew ktlintCheck`), 145 JVM unit-тестів і 4 інструментальні Room-тести мають лишатися зеленими.
- Пристрій QA: TCL 6102H (Android 12) — слабкий CPU, тому будь-яка робота в `onMeasure`/`LaunchedEffect` на main thread помітна.
- Жодних нових модулів: зміни в `app`, `data`, `domain`. Модуль `ui` лишається порожнім.

## 4. Розглянуті підходи

**A. Лише "швидкі перемоги" на ExoPlayer 2.19.** Кеш фіду, prefetch URL, один плеєр, `SimpleCache`, `LoadControl`. Дає більшість вигоди для #1/#2, помірну для #3. Не дає справжнього preload медіа — на нестабільній мережі буферизація лишиться. Вартість: 1–2 тижні.

**B. Одразу Media3 + `DefaultPreloadManager` + Paging 3.** Найкраще для #2/#3, але велика міграція (імпорти, track selection, PiP/fullscreen, керування пам'яттю preload) і 2–4 тижні з вищим ризиком регресій.

**C. Поетапно (ОБРАНО).** Phase 1 = A з швами, які не доведеться переписувати:
- `@Singleton` провайдер медіа-кешу в `data` (пізніше просто зміниться на `androidx.media3.datasource.cache`);
- стабільний контракт ViewModel (`onPageChanged(index)`, `playbackSources`, `isRefreshing`) — Phase 2 міняє лише плеєрний шар;
- окремий `PlaybackSourceMapper` замість приватного `toMediaSource()`.

Phase 2 = B поверх цих швів. Перевага: швидкий видимий результат і менший ризик; недолік: дві ітерації. Без тимчасових абстракцій: шви вводяться тільки там, де Phase 2 реально підміняє реалізацію.

## 5. Рішення, зафіксовані в дизайні

### 5.1 Phase 1 (ExoPlayer 2.19)

**F1.1 Кеш фіду Shorts (stale-while-revalidate)**
- Room v4: нова таблиця `shorts_feed` з полями `videoId` (PK), `title`, `channelName`, `channelId`, `thumbnailUrl`, `duration`, `views`, `uploadedAt`, `isLive`, `authorAvatarUrl`, `position`, `cachedAt`.
- `ShortsFeedDao`: `observeAll(): Flow<List<ShortsFeedEntity>>` (ORDER BY position), `@Transaction replaceAll(items)`, `clear()`.
- Domain: новий інтерфейс `ShortsCacheRepository` (не розширюємо `VideoRepository`, щоб Phase 2 міг залишити його незмінним):
  - `suspend fun observeFeed(): Flow<List<Video>>`
  - `suspend fun replaceFeed(videos: List<Video>)`
  - `suspend fun appendFeed(videos: List<Video>)` (position продовжується після поточного max; Phase 2)
  - `suspend fun knownChannelIds(): Set<String>`
  - `suspend fun rememberChannelIds(ids: Set<String>)`
- Реалізація в `data`: Room DAO + окремий DataStore `shorts` (`stringSetPreferencesKey("known_channels")`).
- `VideoRepositoryImpl.getShorts()` споживає `knownChannelIds()`: якщо набір не порожній — бере канали з нього (ротація без discovery); discovery виконується лише коли набору немає або він вичерпаний; знайдені id зберігаються через `rememberChannelIds()`. Кураторський fallback не змінюється.
- `ShortsViewModel`: на старті синхронно зчитує кеш (один `first()`), одразу публікує `UiState.Success(cached)` (якщо не порожній) і запускає фоновий `reload()`; після успіху `replaceFeed()`. Нова подія `isRefreshing: StateFlow<Boolean>` і `refreshError: StateFlow<String?>`. При помилці оновлення кеш **не видаляється**. Оновлений список звіряється з поточним за `videoId`: якщо поточний кліп лишився — зберігаємо його індекс і плеєр; інакше — head. TTL немає, ручний Refresh кеш не чистить.
- UI: у `ShortsScreen` — маленький індикатор "Оновлення…"/банер помилки поверх пейджера; логіка `UiState` не змінюється.

**F1.2 Prefetch вікно джерел**
- `ShortsViewModel.onPageChanged(index: Int)`: для `index-1..index+2` (не більше 4, межі списку обрізаються) викликає резолв у `coroutineScope { async }` з `Semaphore(2)`. Дедуп — наявний `requestedSources`. `ensurePlayback(id, force)` залишається для Retry.
- `ShortsScreen`: `LaunchedEffect(currentPage, videos.size)` викликає `onPageChanged` замість прямого `ensurePlayback`; `loadMore()` — як раніше (threshold 2).

**F1.3 Один плеєр і кеш медіа**
- `data/di/PlaybackCacheModule.kt`: `@Singleton SimpleCache` (`StandaloneDatabaseProvider`, директорія `filesDir/"media_cache"`, `LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024)`) і `@Singleton CacheDataSource.Factory` з upstream `DefaultDataSource.Factory(context)`.
- `data/di/PlaybackCacheEntryPoint.kt`: `@EntryPoint @InstallIn(SingletonComponent::class)` з методом `cacheDataSourceFactory(): CacheDataSource.Factory` (Compose не має прямого DI для параметрів).
- `app/ui/video/PlaybackSourceMapper.kt`: перенести `PlaybackSource.toMediaSource(...)` з `VideoPlayer.kt` у публічну `internal fun PlaybackSource.toMediaSource(context: Context, dataSourceFactory: DataSource.Factory): MediaSource`.
- `app/ui/video/ExoPlayerHolder.kt`: `class ExoPlayerHolder(context, factory, loadControl)` з `val player: ExoPlayer`, `fun prepare(source: PlaybackSource)`, `fun stop()`, `fun release()`. `stop()` викликається, коли активна сторінка ще не має джерела. `prepare()` = `player.stop()` → `setMediaSource(...)` → `prepare()` → `playWhenReady = true`. `LoadControl`: `setBufferDurationsMs(1000, 5000, 1000, 1500)`, `setPrioritizeTimeOverSizeThresholds(true)`.
- `VideoPlayer.kt`: новий параметр `holder: ExoPlayerHolder? = null`. Якщо `null` — створюємо й звільняємо як зараз (Watch-екран не змінюється); якщо передано — використовуємо спільний плеєр і **не** викликаємо `release()` у `onDispose`, а повторно готуємо джерело при зміні `playback`.
- `ShortsPager` створює один `ExoPlayerHolder` (`remember`), звільняє в `DisposableEffect`, і на `LaunchedEffect(currentPage, sources)` викликає `holder.prepare(sources[video.id])` для активного кліпу. `ShortPage` передає `holder` у `VideoPlayer`.

**F1.4 Мініатюри**
- `MyApplication : Application(), ImageLoaderFactory` — один `ImageLoader` на процес.
- `ShortsScreen`: `LaunchedEffect(videos.take(4))` → `context.imageLoader.enqueue(ImageRequest(...))` для перших 4 мініатюр.

### 5.2 Phase 2 (Media3)

- Замінити залежності `com.google.android.exoplayer:*` на `androidx.media3:*` 1.4.1; оновити `VideoPlayer`, `ExoPlayerHolder`, `PlaybackSourceMapper`, `PlaybackCacheModule/EntryPoint` (пакет Media3 `datasource.cache`), Watch-екран. `PlaybackSource` domain-модель не змінюється.
- `app/ui/shorts/Media3ShortsPlaybackController.kt`: `DefaultPreloadManager` + один `ExoPlayer` з **одного** `DefaultPreloadManager.Builder` (спільні `LoadControl`, `BandwidthMeter`, `TrackSelectorFactory`, `MediaSourceFactory`, той самий Looper). `TargetPreloadStatusControl<Int, PreloadStatus>`: `specifiedRangeLoaded(3000L)` для індексів у вікні ±2, `null` для решти (1.4.1 підтримує null як «не preload»); `setCurrentPlayingIndex()` на `settledPage`; sliding window `addMediaItems/remove` щоб уникнути OOM. Реліз: менеджер — перед плеєром. `specifiedRangeCached()` не використовується — це API 1.9; повторні відтворення обслуговує `CacheDataSource`.
- Пам'ять: `DefaultLoadControl.Builder().setPlayerTargetBufferBytes("preload", 96 * 1024 * 1024)` + `setBufferDurationsMsForStreaming(1000, 5000, 1000, 1500)` + `setPrioritizeTimeOverSizeThresholdsForStreaming(true)`.
- Paging 3: `ShortsRemoteMediator` (REFRESH → `replaceFeed`, APPEND → `appendFeed`), `PagingConfig(pageSize = 10, prefetchDistance = 2, initialLoadSize = 10, maxSize = 40)`; `ShortsViewModel` споживає `PagingData<Video>` замість власного списку, але зберігає `onPageChanged`/`playbackSources` контракт. `ShortsCacheRepository` розширюється методом `fun pagingSource(): PagingSource<Int, Video>` (потрібен `androidx.paging:paging-common:3.2.1` у `domain`; мапінг entity→Video відбувається у `data`, DAO в UI не протікає).

## 6. Що лишається на виконавця

- Точні назви файлів/пакетів і сигнатури — у плані; відхилення від них мають бути явними.
- Конкретні значення `initialLoadSize`/preload window можна підкрутити після вимірювань на пристрої, але межі (≤4 джерела й ≤2 паралельні запити у Phase 1) — фіксовані.
- Phase 2 виконується окремим проходом після приймання Phase 1; не змішувати міграцію з першими чотирма задачами.

## 7. Розв'язки builder-check (рішення виконавця)

1. **Версія Media3 / compileSdk** — див. обмеження вище: 1.4.1, compileSdk 34, AGP 8.2.2 без змін.
2. **Ключі дискового кешу** — заборона на підписані URL стосується **наших** Room/DataStore моделей. Внутрішній SQLite-індекс `SimpleCache` є приватним для застосунку й обмежений LRU 256 МБ. Індекс лишається стандартним: кастомний `CacheKeyFactory`, що зрізає query, зіткнеться на всіх `videoplayback?...` (шлях один — `/videoplayback`) і дасть хибні попадання.
3. **Оновлення фіду й пейджер** — після refresh список звіряється за `videoId`: якщо поточний кліп лишився, відновлюємо його індекс і плеєр не перезапускаємо; якщо зник — переходимо на head. Ручний Refresh **не** чистить кеш. TTL у Phase 1 немає: кеш показується завжди з індикатором оновлення.
4. **Ротація каналів** — знайдені id **об'єднуються** зі збереженими (`distinct`, кап 42). Кураторські fallback-ід не зберігаються. Коли всі відомі id відвідані — `usedShortsChannels` скидається й починається новий цикл.
5. **Догрузка** — `loadMore()` тепер пише в `shorts_feed` через `appendFeed()` (position продовжується), інакше відновлювався б лише перший набір.
6. **Полиця Shorts на Головній** — залишається поза Phase 1: результати `#shorts` search горизонтальні, засідання ними кешу вертикального фіду його зіпсувало б; це окремий запит на старті, який треба міряти окремо.
7. **Переходи між джерелами** — до `ExoPlayerHolder` додано `stop()`; якщо для активної сторінки джерела ще немає, плеєр зупиняється (показуємо мініатюру + spinner), а не грає попередній кліп під новою сторінкою. Retry у `VideoPlayer` з спільним holder викликає `holder.prepare(playback)`, а не `exoPlayer.prepare()`.

## 8. Джерела (первинні)

- https://developer.android.com/media/media3/exoplayer/network-stacks#caching
- https://developer.android.com/media/media3/exoplayer/preloading-media/preloadmanager/concepts
- https://developer.android.com/blog/posts/elevating-media-playback-introducing-preloading-with-media3-part-1
- https://developer.android.com/blog/posts/elevating-media-playback-a-deep-dive-into-media3-s-preload-manager-part-2
- https://developer.android.com/reference/androidx/media3/exoplayer/DefaultLoadControl.Builder
- https://github.com/androidx/media/issues/2165
- https://developer.android.com/topic/libraries/architecture/paging/v3-network-db
- https://coil-kt.github.io/coil/image_loaders/
