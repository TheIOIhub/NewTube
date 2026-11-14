# NewTube — watch screen, shorts feed, player controls, pagination

Дата: 2026-11-07
Статус: затверджено усно («роби, а там подивимось»), реалізується.

## 1. Намір і чому

Застосунок збирається і тягне дані, але користуватись ним незручно:

* натиск на відео відкриває голий плеєр на весь екран — без назви, опису, коментарів і схожих відео;
* таб «Шортси» показує звичайні відео (Invidious не має стрічки шортсів, а поточний запит `q=#shorts&duration=short` повертає переважно 1–4-хвилинні ролики);
* немає керування плеєром (якість, fullscreen, PIP, продовження з місця);
* будь-яка помилка мережі перетворюється на порожній екран без можливості повторити;
* списки не гортаються далі першої сторінки;
* перехід на канал передає назву замість `UC…id` → API віддає 500.

Мета: довести застосунок до стану «можна користуватись як YouTube-клієнтом» у межах того, що реально віддає Invidious.

## 2. Цілі / не-цілі

Цілі:

1. `channelId` у моделі відео + міграція Room v1→v2, робоча навігація на канал.
2. Екран перегляду: плеєр зверху (16:9), назва, метадані, канал, опис, кнопка коментарів, схожі відео.
3. Окремий екран коментарів з пагінацією.
4. Шортси: вертикальний свайп-плеєр (один ролик на екран, автоплей поточного).
5. Контроли плеєра: вибір якості, fullscreen, PIP, продовження з останньої позиції.
6. Стан помилки + «Повторити» на всіх екранах.
7. Пагінація там, де її підтримує API.

Не-цілі (свідомо поза скоупом): підписки, playlists, субтитри, історія переглядів, автокомпліт пошуку, коментарі-відповіді, лайки/дизлайки, офлайн-режим, DataStore-перемикач теми.

Анти-цілі: фейкова пагінація головної, дублювання відео між сторінками, важкі залежності (Paging 3, Media3) заради самого факту.

## 3. Обмеження, знайдені в API (перевірено на живому інстансі)

| Що | Факт |
|---|---|
| `/api/v1/trending`, `/api/v1/popular` | `page` ігнорується → пагінації немає |
| `/api/v1/search` | `page` працює, сторінки не перетинаються |
| `/api/v1/channels/{id}/videos` | `page` ігнорується, працює `continuation` |
| `/api/v1/comments/{id}` | `continuation` працює, `commentCount` є |
| `/api/v1/videos/{id}` | є `recommendedVideos` (12), `hlsUrl`, `adaptiveFormats`; `description` порожній на доступному інстансі |
| `formatStreams` | на інстансі порожній → якість є лише в HLS-варіантах / adaptive-треках |
| Шортси | окремого feed немає; `#shorts&duration=short` → ~5 із 20 роликів ≤60 с |

## 4. Підходи, які розглядались

### 4.1. Якість відео

* **A. Парсинг HLS master-playlist** (regex по `RESOLUTION=`), окремий запит маніфесту. Точно, але зайвий мережевий виклик, крихкий парсер і не працює для progressive.
* **B. MergedMediaSource** (окремо video-only + audio-only adaptive). Дає «справжній» список висот, але губить HLS-автоперемикання і ламається на VP9/AV1.
* **C. TrackSelector-обмеження (`setMaxVideoSize`) + меню «Авто / ≤ Np», висоти з `player.currentTrackGroups`.** Працює і для HLS, і для adaptive, без парсингу. **Обрано.**

### 4.2. Шортси

* **A.** Лише відфільтрувати ≤60 с у наявному списку — дешево, але таб і далі схожий на головну.
* **B.** Вертикальний `VerticalPager`: один ролик на екран, автоплей активного, решта — постери; плеєр створюється/знищується при зміні сторінки. **Обрано (погоджено з користувачем).**
* **C.** Один спільний `ExoPlayer` + переміщення `PlayerView` між сторінками — економніше, але крихко в Compose.

### 4.3. Пагінація

* **A. Paging 3** — надлишково для 4 екранів, тягне нову архітектуру.
* **B. Ручні `page`/`continuation` у ViewModel + «догрузити на межі списку».** **Обрано.**

## 5. Дизайн

### 5.1. Домен (`:domain`)

```kotlin
data class Video(..., val channelName: String, val channelId: String, ...)

data class VideoDetails(
    val video: Video,
    val description: String,
    val related: List<Video>,
    val playback: PlaybackSource,
)

sealed interface PlaybackSource {
    data class Hls(val url: String) : PlaybackSource
    data class Progressive(val url: String) : PlaybackSource
    data class Merged(val videoUrl: String, val audioUrl: String) : PlaybackSource
}

data class Comment(id, author, authorChannelId, authorAvatarUrl, content, likeCount, publishedText, isPinned)
data class CommentPage(val comments: List<Comment>, val continuation: String?, val totalCount: Long)
data class ChannelVideosPage(val videos: List<Video>, val continuation: String?)

interface VideoRepository {
    suspend fun getTrending(): List<Video>                              // без пагінації
    suspend fun getShorts(page: Int = 1): List<Video>                   // тільки 1..60 с
    suspend fun search(query: String, page: Int = 1): List<Video>
    suspend fun getVideoById(videoId: String): Video
    suspend fun getVideoDetails(videoId: String): VideoDetails
    suspend fun getChannelDetails(channelId: String): Channel
    suspend fun getChannelVideos(channelId: String, continuation: String? = null): ChannelVideosPage
    suspend fun getComments(videoId: String, continuation: String? = null): CommentPage
}

interface PlaybackPositionRepository {
    suspend fun positionFor(videoId: String): Long
    suspend fun savePosition(videoId: String, positionMs: Long)
}
```

`getStreamUrl` видаляється: джерело відтворення тепер частина `VideoDetails`.

### 5.2. Дані (`:data`)

* `VideoDto`: + `authorId`, `description`, `recommendedVideos`. Мапінг `channelId = authorId`.
* Нові `CommentDto`, `CommentsPageDto`.
* `InvidiousService`: `getShorts(query, page)`, `getChannelVideos(channelId, continuation)`, `getComments(videoId, continuation)`; `getStream` не повертається.
* `VideoRepositoryImpl`:
  * шортси фільтруються `lengthSeconds in 1..60`;
  * `getVideoDetails` → `description`, `related` (мапінг `recommendedVideos`), `playback` за пріоритетом `hlsUrl` → `formatStreams` → `Merged(adaptive video/mp4 + audio/m4a)`;
  * `getComments` мапить `authorThumbnail` → `authorAvatarUrl`.
* Room: `VideoEntity.channelId`, `NewTubeDatabase` version = 2, `Migration(1, 2)` = `ALTER TABLE videos ADD COLUMN channelId TEXT NOT NULL DEFAULT ''`.
* `DataStorePlaybackPositionRepository` (`datastore-preferences`) + Hilt-модуль.

### 5.3. Застосунок (`:app`)

`UiState<T>` = `Loading | Success(data) | Error(message)`; кожен екран має `Retry`.

| Екран | Поведінка |
|---|---|
| Home | trending + pull-to-refresh, помилка+retry; пагінації немає (API не дає) |
| Shorts | `VerticalPager`, автоплей активної сторінки, догрузка коли до кінця ≤2 сторінки, тап по екрану — пауза, кнопка «відкрити» → watch screen |
| Search | debounce 500 мс, `page++` при доскролі до кінця |
| Channel | шапка (аватар/назва/підписники/опис) + стрічка завантажень з `continuation` |
| Watch | плеєр 16:9 закріплений зверху; нижче title, «views • published», канал (→ channel), кнопка «Коментарі (N)», опис (згорнутий, якщо порожній — не показуємо), схожі відео |
| Comments | список + «Завантажити ще» за `continuation` |
| Player | play/pause, seek, «Авто/≤ Np», fullscreen (landscape + приховані системні бари), PIP (API 26+, `supportsPictureInPicture`), збереження/відновлення позиції |

Навігація: `videoPlayer/{videoId}`, `comments/{videoId}`, `channel/{channelId}` (тепер справжній id).

### 5.4. Що лишається будівнику

* Точні відступи/типографіка — за існуючою темою.
* Формат «views» (компактний, напр. `1.2M`) — проста утиліта.
* Кількість сторінок для догрузки шортсів — 1 сторінка за раз, доки не набереться 10 роликів або 3 сторінки.

## 6. Перевірка

* Unit-тести: мапінг `channelId`, фільтр шортсів, `PlaybackSource` (HLS/progressive/merged), коментарі, пагінація каналу, `PlaybackPositionRepository` (fake in-memory), ViewModel-и (success/error/loadMore).
* `./gradlew ktlintCheck test assembleDebug` — зелені.
* Тимчасовий smoke-тест проти живого інстансу для нових ендпоінтів (деталі, коментарі, continuation), потім видаляється.
* Ручна перевірка APK на пристрої — за користувачем.

## 7. Ризики

* `description` порожній на інстансі → блок опису ховається; якщо інстанс почне віддавати — з’явиться сам.
* PIP/fullscreen не тестуються юніт-тестами — тільки збірка + ручна перевірка.
* Міграція Room не покрита інструментальним тестом (немає androidTest-інфраструктури) — перевіряється інсталяцією на пристрій.
