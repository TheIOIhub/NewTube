# NewTube — прямий InnerTube як єдине джерело (дизайн, за зразком GreenTuber)

> Статус: обрано варіант **C — повний перехід на InnerTube без Invidious у робочому шляху**.
> Дата: 2026-11-27. Цей документ — вхідний для плану реалізації.

## 1. Намір і чому

Користувач порівняв NewTube з GreenTuber на телефоні: картинки й відео в GreenTuber відкриваються помітно швидше. Розслідування показало:

- Cold start не є проблемою: на пристрої TCL 6102H NewTube стартує за **1,58 с**, GreenTuber — за **2,2 с**.
- Гальмує шар даних: один пошук Invidious триває **~2,6–2,8 с** (заміри в `docs/superpowers/specs/2026-11-26-video-shorts-performance-design.md`), Головна на перше відкриття робить ~7 запитів, Shorts — до ~21 запиту по каналах.
- GreenTuber (APK `by.green.tuber`, versionCode 153) не використовує публічний Invidious як основне джерело: він звертається напряму до `youtubei/v1`, тримає локальну Room-БД, Glide-кеш, ExoPlayer із власним ABR/SABR і отримує конфігурацію ендпоінтів з віддаленого `api.php?method=getJsonForPatch`. Саме тому його фід і прев'ю з'являються швидше.
- **Живий тест 2026-11-27** (запит `POST https://www.youtube.com/youtubei/v1/player`, звичайний VOD `dQw4w9WgXcQ`):

| Клієнт | Результат |
|---|---|
| `TVHTML5` 7.20260901.15.00 (наш поточний акаунт-клієнт) | `UNPLAYABLE`, "The page needs to be reloaded", 0 форматів |
| `ANDROID` 20.10.38 | `OK`, 1 progressive 360p + 26 adaptive (H.264 720p/1080p, VP9, AV1 до 2160p), ~0,35 с, без ключа й без po-token |
| `ANDROID_VR` 1.60.19 | `OK`, той самий набір, ~0,38 с |
| `IOS` 20.10.4 | `OK`, 8 adaptive + HLS-маніфест |

URL потоку віддав `HTTP 206` і 2 КБ за 0,23 с; пошук через `ANDROID` відповів за ~0,7 с (результати в `compactVideoRenderer`).

Мета: зробити NewTube швидким як GreenTuber — прибрати повільний проміжний інстанс взагалі, а не маскувати його кешем.

## 2. Цілі / не-цілі / анти-цілі

**Цілі**
- G1. Усі користувацькі списки (Головна, Shorts, Пошук, Канал, Коментарі, Сповіщення, Підписки) і деталі відео отримуються напряму з InnerTube; жодного запиту до Invidious у рантаймі.
- G2. Перше відкриття екрана з інтернетом — <1 с замість ~2,6 с; повторне — миттєво з локального кешу з фоновою ревалідацією.
- G3. Прев'ю та аватари беруться з `i.ytimg.com` і попередньо підвантажуються; скрол без сірих плям.
- G4. Старт відео — через звичайні DASH/progressive формати з `streamingData`, без SABR/po-token; медіа-кеш 256 МБ працює і для Watch, і для Shorts.
- G5. Зміна клієнтської версії/ендпоінта не вимагає релізу додатка (віддалений конфіг, як у GreenTuber).
- G6. Зберегти без рекламу, без записів на акаунт, MVVM/Hilt/Compose і поточні доменні контракти ViewModel.

**Не-цілі**
- Не впроваджувати SABR/UMP і po-token на цьому етапі: ANDROID-клієнт поки віддає звичайні формати.
- Не додавати субтитри, офлайн-завантаження, live/DVR, Playlists, UI-тести, релізний підписний пайплайн.
- Не змінювати функції акаунта (TV device-code OAuth, підписки, історія) — вони вже працюють через InnerTube.

**Анти-цілі (заборонено)**
- **Не зберігати на диску підписані URL стрімів** (`videoplayback?...` мають обмежений строк дії). Кешуємо лише метадані.
- Не показувати рекламні блоки з InnerTube-відповідей і не відтворювати ad-слоти.
- Не залишати Invidious як "тихий" фолбек, про який користувач не знає: у налаштуваннях і документації джерело даних має бути одним.
- Не збільшувати кількість запитів на старті.

## 3. Обмеження

- Android API 24+, compileSdk/targetSdk 34, JDK 17, AGP 8.2.2, Kotlin 1.9.24, Compose BOM 2024.03.00, Media3 1.4.1, Coil 2.5.0, Room 2.6.1, Hilt 2.47.
- Модулі: зміни в `app`, `data`, `domain`; `ui` лишається порожнім. Жодних нових модулів.
- `ktlintCheck`, ~145 JVM unit-тестів і 4 інструментальні Room-тести мають лишатися зеленими.
- Пристрій QA: TCL 6102H (Android 12, слабкий CPU) — важка робота в `LaunchedEffect`/`onMeasure` помітна.
- InnerTube — непублічний API: Google може змінити версію клієнта, renderer-и або вимагати po-token. Це головний ризик, його пом'якшуєremote config + ланцюжок клієнтів + локальний кеш.
- Тест робився з IP робочого комп'ютера; на мобільному IP відповідь може відрізнятись — перший крок реалізації: повторити живий тест з телефона.

## 4. Розглянуті підходи

**A. Invidious + агресивне кешування.** HTTP-кеш, кеш фідів, R8. Не міняє джерело: перше відкриття все одно чекає ~2,6 с. Відхилено — користувач прямо попросив прибрати Invidious.

**B. Гібрид: InnerTube основний, Invidious fallback.** Надійніший, але лишає повільний інстанс у коді, конфігурації й документації; не дає відчуття "як GreenTuber". Відхилено.

**C. Повний перехід на InnerTube (ОБРАНО).** Один шар даних на базі `youtubei/v1`, віддалений конфіг як у GreenTuber, локальний кеш усіх списків, прямі медіа-URL, Coil-кеш. Найбільший виграш у швидкості; ціна — залежність від непублічного API та більший обсяг парсерів/тестів.

## 5. Рішення, зафіксовані в дизайні

### 5.1 Джерело даних

- Новий `YouTubeContentService` у `data` (або розширення наявного `YouTubeInnerTubeService`) для ендпоінтів: `browse`, `search`, `player`, `next`, `search/suggestions` (або `suggestqueries.google.com`), плюс наявні акаунтні виклики.
- Клієнт для контенту: `ANDROID` / `20.10.38` (`hl`/`gl` з налаштувань контенту), запасні `ANDROID_VR` / `1.60.19` і `IOS` / `20.10.4`. `TVHTML5` лишається **тільки** для авторизованих підписок/історії/профілю.
- Віддалений конфіг (за зразком GreenTuber, але **власний**): JSON із полями `contentClients`, `playerClients`, `endpoints`, `flags`, TTL ~6 год. Сервер і схему задаємо самі — жодної залежності від `yougreentube.com` чи іншого чужого ендпоінта. URL конфігу задається build-config полем; у репо лежать bundled-defaults, які працюють при недоступності сервера.
- Усі запити — на `Dispatchers.IO`, з таймаутами OkHttp (connect 10 с, read 15 с).
- **Ланцюжок клієнтів при `UNPLAYABLE`/bot-помилці:** `ANDROID` → `ANDROID_VR` → `IOS` → додаткові варіанти з конфігу (різні `clientVersion`/`hl`/`gl`), перш ніж вважати джерело недоступним. Invidious і SABR у ланцюжок не входять.
- Якщо вичерпано весь ланцюжок: показати останній кешований фід і явний банер "джерело недоступне" з кнопкою повтору; прихованого фолбеку немає.

### 5.2 Парсинг і домен

- Нові DTO InnerTube для renderer-ів (`videoRenderer`/`compactVideoRenderer`/`richItemRenderer`/`reelItemRenderer`/`continuationItemRenderer`) з `@JsonClass(generateAdapter = true)` (Moshi, як зараз).
- Мапери в доменні `Video`, `Channel`, `Comment`, `VideoDetails`, `PlaybackSource`, `CommentPage`, `ChannelVideosPage` — ViewModel-контракти не міняються.
- **Курацію фідів замінюємо на рідну YouTube:** Головна будується з власних вкладок YouTube (`browse`, наприклад `FEtrending`), Shorts — з reel-shelf/Shorts-табу каналу; прибираємо тематичні мікси, `shuffled()`, `distinctBy` як список джерел і обертання каналів (`usedShortsChannels`, `nextShortsChannels`). Зберігаємо лише локальну "Для вас" **ранкінг**-логіку поверх отриманого фіду (вона не витрачає запити).
- Тести-фікстури: зберегти реальні JSON-відповіді `player`, `search`, `browse`, `next` у `data/src/test/resources` і перевірити парсери/мапери.

### 5.3 Медіа

- `PlaybackSourceMapper` розширюється: `streamingData.formats` → progressive; `adaptiveFormats` → merged video+audio (підбір пари за itag); `hlsManifestUrl` → HLS для live; вибір якості згідно з наявним `maxQuality`.
- Підписані URL зберігаються тільки в пам'яті (in-memory map з TTL), при повторному вході у відео робиться свіжий `player`-запит.
- `SimpleCache` (256 МБ) підключається до Watch-плеєра і Shorts; `DefaultPreloadManager` лишається для Shorts, додається легкий prefetch перших секунд наступного відео у Watch.
- `LoadControl` з налаштованими буферами (як у Shorts) для обох плеєрів.

### 5.4 Картинки

- Один `ImageLoader` у `MyApplication` із явними memory/disk кешами, `size()` під конкретну картку, `error`/placeholder, без crossfade на слабкому CPU.
- Прямі URL `i.ytimg.com/vi/{id}/hqdefault.jpg` (або `mqdefault` для маленьких аватарів).
- Prefetch перших 6–8 карток Головної/пошуку + усіх видимих Shorts-тайлів; не пере-enqueue на кожну емісію `uiState`.

### 5.5 Кешування даних

- Room v5: нові таблиці/колонки для кешу фідів із `fetchedAt` (Home за категоріями, search query+page, channel videos, shorts, comments, notifications) або узагальнення існуючої `shorts_feed`. `MIGRATION_4_5` + `5.json`.
- Стратегія stale-while-revalidate для всіх списків: спочатку Room, потім фоновий InnerTube-запит; помилка оновлення не затирає робочий кеш; ненав'язливий індикатор оновлення.
- Уточнення до G6: "контракти ViewModel не міняються" означає незмінні **доменні моделі** (`Video`, `Channel`, `Comment`, `PlaybackSource`). Допускається розширення UI-стану viewmodel полем `isRefreshing` там, де його ще немає (Search, Channel, Comments, Notifications), — це частина вимоги про індикатор оновлення.
- `clearFeedCaches()` при зміні регіону/мови (вже існує) поширюється на всі нові таблиці.

### 5.6 Видалення Invidious

- Видалити `InvidiousService`, DTO-ти Invidious, `NetworkModule`-прив'язку Retrofit для нього, `BuildConfig.INVIDIOUS_BASE_URL`, `-PinvidiousBaseUrl`, відповідні тести та розділи README.
- Залишити Retrofit/Moshi для InnerTube (або перейти на типізований OkHttp-клієнт — рішення реалізатора, головне: один стиль).
- В `Settings`/`About` не показувати вибір інстанса; якщо користувач бачить "джерело даних" — це InnerTube.

### 5.7 Що лишається на реалізатора

- Точний вибір бібліотеки для парсингу (Moshi codegen vs kotlinx.serialization — у репо вже є обидві; бажано один стиль).
- Конкретні `browseId`/params для вкладок Головної й Shorts-shelf; перевірити на телефоні.
- Як саме міняти Room-схему (нова таблиця `feed_cache` vs розширення `shorts_feed`) — вибрати менший дизайн, що покриває всі списки.

## 6. Як перевіримо успіх

1. Живий smoke-тест з телефона: `player`, `search`, `browse` через `ANDROID` — `OK`, без po-token; якщо мобільний IP блокується — послідовно перевірити весь ланцюжок `ANDROID_VR` → `IOS` → варіанти з конфігу й зафіксувати результат у документі.
2. Заміри до/після на TCL 6102H: час до першого кадру Головної, Shorts, старт відео, свайп Shorts; ціль G2.
3. `./gradlew test`, `./gradlew ktlintCheck`, `./gradlew assembleDebug` — зелені.
4. Grepping `Invidious`/`api/v1`/`INVIDIOUS_BASE_URL` у `app`, `data`, `domain` — 0 входжень у виробничому коді.
5. Ручний QA: 15 свайпів Shorts, 10 відкриттів відео, пошук, канал, коментарі — 0 помилок відтворення, реклама не відтворюється, cached фід показується миттєво.
