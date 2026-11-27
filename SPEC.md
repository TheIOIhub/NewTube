# NewTube – Специфікація та План Розробки

## 1. Загальний опис

**Назва:** *NewTube* (версія 1.0.0)

**Мета проекту:** Створити Android‑додаток, що дозволяє переглядати відео, шортси і прямі трансляції із YouTube без реклами, використовуючи відкритий API [Invidious](https://github.com/iv-org/invidious).

## 2. Функціональні вимоги

| Вкладка | Функція | Опис | Стан |
|---------|---------|------|------|
| **Оболонка** | П'ять вкладок | Головна, Shorts, Пошук, Підписки, Ви в одному `NavigationBar` (підписи 10 sp, без «пігулки» вибраного); на Watch/Settings/History/About панель ховається | ✅ реалізовано |
| **Головна** | Стрічка за категоріями | брендовий top bar (лого, оновлення, дзвіночок, пошук), чипси All/Music/Gaming/Movies/Sports/News/Technology, горизонтальна **полиця Shorts** із вертикальними плитками, скелетони під час завантаження; кожна категорія — суміш trending, popular і тематичних пошуків, перемішана на кожен запит | ✅ реалізовано |
| **Картки** | YouTube-стиль | 16:9 мініатюра, бейдж тривалості/LIVE, аватар каналу 36 dp, назва, українські перегляди й дата; та сама картка у пошуку, схожих відео та завантаженнях каналу | ✅ реалізовано |
| **Шортси** | Вертикальні відео | справжні Shorts із `/api/v1/channels/{id}/shorts` каналів, знайдених пошуком; фільтр `1..180` с без live, вертикальний свайп‑плеєр на весь екран + перемішування; тайл із полиці відкриває вкладку на конкретному кліпі (`shorts?videoId=…`) | ✅ реалізовано |
| **Пошук** | Пошук відео | `GET /api/v1/search?type=video&page=N`, живий пошук з debounce 500 мс і догрузкою сторінок | ✅ реалізовано (без автокомпліту/історії) |
| **Канали** | Профіль каналу | аватар, назва, підписники, опис + стрічка відео з `continuation` | ✅ реалізовано (навігація за `UC…id`) |
| **Відео** | Екран перегляду | плеєр 16:9 зверху, назва/перегляди/дата, канал, опис, коментарі, схожі відео | ✅ реалізовано |
| **Коментарі** | Список коментарів | `GET /api/v1/comments/{videoId}`, перша сторінка + `continuation`, лічильник, закріплені | ✅ реалізовано (без відповідей/лайків) |
| **Плеєр** | Media3 1.4.1 (ExoPlayer) | live → HLS, VOD → progressive → merged adaptive (avc1 ≤ вибраної якості) → HLS як запасний; меню якості, fullscreen, PIP, resume; Shorts — один плеєр із `DefaultPreloadManager` (радіус 2), 256 MiB дисковий кеш і передзавантаження мініатюр | ✅ реалізовано (Phase 2 device QA — 15 свайпів, див. §6) |
| **Історія** | Локальна історія | Room-таблиця `watch_history`: один рядок на відео (upsert, ліміт 500), найновіші зверху, відносний час українською; Shorts пишеться лише після 3 с на сторінці; тап відкриває відео | ✅ реалізовано |
| **Історія YouTube** | Віддалена історія | після входу і ввімкненого перемикача екран показує справжню історію акаунта (відео й Shorts, підзаголовок «Історія YouTube») з пагінацією; віддалені рядки не пишуться в Room; збій або прострочена сесія повертає локальний список із банером | ✅ реалізовано |
| **Налаштування** | Повний екран | Акаунт (вхід/вихід), Вигляд (системна/темна/світла тема), Історія (запис переглядів, перемикач історії YouTube, підтверджене очищення разом із позиціями), Відтворення (480/720/1080p, типово 720p), Про застосунок | ✅ реалізовано |
| **Вхід** | Google TV OAuth | device-code вхід на `google.com/device`: код, зворотний відлік, «Відкрити сайт», копіювання коду, скасування й повтор; токени у `EncryptedSharedPreferences`, оновлення access-токена без участі користувача | ✅ реалізовано |
| **Підписки** | Стрічка підписок | останні відео акаунта з TV-ендпоінта `FEsubscriptions` як сітка 2×N із `continuation`; CTA для входу, банер простроченої сесії; це стрічка відео, а не список каналів, бо TV-ендпоінт не повертає `gridChannelRenderer` | ✅ реалізовано |
| **Сповіщення** | Best-effort | один запит `FEnotifications`, рядки відкривають Watch; порожня або недоступна відповідь показує «Немає нових сповіщень» (без пагінації й бейджів) | ✅ реалізовано |
| **Ви / Акаунт** | Стан акаунта | гість бачить CTA входу; після входу — аватар 48 dp, ім'я, email і підтверджений вихід; вихід не чіпає локальну історію | ✅ реалізовано |
| **Кешування** | Метадані відео/каналів і Shorts | Room v4: write‑through для `getVideoById`/`getChannelDetails`; Shorts — cache-first з `shorts_feed` + DataStore `known_channels` (stale‑while‑revalidate з індикатором оновлення, лише метадані — підписані URL стрімів не зберігаються) | ⚠️ частково (Shorts ✅; офлайн‑стрічок інших вкладок немає) |
| **Тема** | Темна/світла | Material3, темна типово за системою, перемикач у Налаштуваннях зберігається в DataStore | ✅ реалізовано |

### 2.1. Мережевий шар (фактичний контракт)

Усі запити стрічок, пошуку, каналів і плеєра йдуть на Invidious‑інстанс, базова адреса задається збіркою (див. §4.3). Функції акаунта (історія, підписки, сповіщення) ходять напряму в YouTube InnerTube з Bearer‑токеном.

| Призначення | Запит | Модель |
|---|---|---|
| Головна (trending) | `GET api/v1/trending?region=US&type=Default` / `type=Gaming` | `List<VideoDto>` |
| Головна (popular) | `GET api/v1/popular` | `List<VideoDto>` |
| Головна (тематичні пошуки) | `GET api/v1/search?q=…&type=video&page=1` | `List<VideoDto>` |
| Шортси (канали) | `GET api/v1/search?q=…&type=channel&page=1` | `List<ChannelSearchDto>` |
| Шортси (стрічка каналу) | `GET api/v1/channels/{channelId}/shorts` | `ChannelVideosDto { videos, continuation }` |
| Пошук | `GET api/v1/search?q=…&type=video&page=N` | `List<VideoDto>` |
| Відео каналу | `GET api/v1/channels/{channelId}/videos?continuation=…` | `ChannelVideosDto { videos, continuation }` |
| Канал | `GET api/v1/channels/{channelId}` | `ChannelDto` |
| Деталі + стрім | `GET api/v1/videos/{videoId}` | `VideoDto` (+`hlsUrl`, `formatStreams`, `adaptiveFormats`, `recommendedVideos`) |
| Коментарі | `GET api/v1/comments/{videoId}?continuation=…` | `CommentsPageDto { comments, continuation, commentCount }` |

Акаунт (YouTube InnerTube, лише читання):

| Призначення | Запит | Особливості |
|---|---|---|
| Device code | `POST https://www.youtube.com/o/oauth2/device/code` | `client_id`/`client_secret` витягуються з TV base JS і кешуються в зашифрованому сховищі; scope як у TV-клієнта |
| Токени | `POST https://www.youtube.com/o/oauth2/token` | `authorization_pending`, `slow_down`, `access_denied`, `expired_token`; refresh без ротації токена, `invalid_grant` = вихід, `invalid_client` = одноразовий повторний скрейп пари |
| Профіль | `POST /youtubei/v1/account/accounts_list` | `returnPersonaAccounts: true`; власник акаунта не має `pageIdToken` |
| Історія | `POST /youtubei/v1/browse` з `browseId=FEhistory` | TVHTML5-контекст + Bearer; відповідь — `tileRenderer`, зокрема Shorts через `reelWatchEndpoint` |
| Підписки | `POST /youtubei/v1/browse` з `browseId=FEsubscriptions` | стрічка останніх відео, а не список каналів |
| Сповіщення | `POST /youtubei/v1/browse` з `browseId=FEnotifications` | best-effort: порожня або нерозпізнана відповідь = порожній стан |

Пагінація InnerTube береться з `continuations[].nextContinuationData.continuation` (не з `continuationCommand.token`); підписаний користувач отримує 401 на `FEhistory` без токена, тому віддалені функції активні лише після входу. Парсер терпимий до невідомих вузлів: невідомі форми дають менше елементів, а не помилку.

Особливості, які враховані в мапінгу:

* поля Invidious мають інші назви, ніж у доменній моделі: `videoId`, `author`, `authorId`, `lengthSeconds`, `publishedText`, `liveNow`;
* відповідь `/api/v1/search` змішана — канали та плейлисти приходять у тому ж масиві без `videoId`, тому записи без `videoId` відфільтровуються; поле `type` для фільтра не використовується, бо Invidious позначає прямі трансляції `livestream`, а рекомендації приходять без `type`;
* ендпоінта `/api/v1/streams/{id}` **не існує** — URL відтворення береться з `/api/v1/videos/{id}`: для live (`liveNow`/`type=livestream`) першим іде `hlsUrl`; для VOD — `formatStreams` (progressive, уже з аудіо) у межах обраної максимальної якості, далі merged-пара з `adaptiveFormats` (окремі `video/mp4` + `audio/mp4` склеюються `MergingMediaSource`; перевага `avc1`, висота ≤ `AppSettings.defaultQuality` — 480/720/1080, аудіо `mp4a.40.2`), потім progressive вище ліміту як останній запасний варіант, і лише потім `hlsUrl`;
* `/api/v1/trending` не підтримує пагінацію — один запит повертає всю стрічку, тому «догрузка» на головній замінена кнопкою refresh;
* `/api/v1/channels/{id}/videos` і `/api/v1/comments/{id}` пагінуються лише непрозорим `continuation`, а не номером сторінки;
* пошук `#shorts` на тестовому інстансі повертає лише **горизонтальні** ролики (11/11 кандидатів ≤60 с мали 1920×1080), тому шортси будуються з `/channels/{id}/shorts`: канали знаходяться пошуком `type=channel`, ротуються по 3 на сторінку, фільтр `lengthSeconds in 1..180` і `!liveNow`, дедуп і shuffle, а після вичерпання пулу додається курований список каналів;
* `duration=short` у пошуку гарантує «короткість» лише приблизно і не гарантує вертикальний формат; фільтр за тривалістю лишається як додаткова перевірка;
* головна збирає до 60 відео з кількох джерел паралельно: збій одного джерела ігнорується, помилка показується лише коли впали всі;
* `description` і `recommendedVideos` повертає лише single‑video ендпоінт; на деяких інстансах `description` порожній, і тоді блок опису не показується.

## 3. Нефункціональні вимоги

- Підтримка Android API 24+ (compileSdk/targetSdk 34).
- UI на Jetpack Compose (Material 3).
- Тестованість: JUnit4 + MockK + kotlinx‑coroutines‑test (399 JVM unit‑тестів у debug / 408 разом із release, 0 failures: репозиторії, OAuth/InnerTube, мапінги налаштувань/історії, 12 ViewModel‑класів, форматери, Shorts-кеш/медіа/preload/Paging) + **5 інструментальних тестів Room** (міграції та DAO `watch_history`, включно з `3→4`) на реальному пристрої; `./gradlew.bat :data:connectedAndroidTest --no-daemon` — **5/5 PASS**.
- Локальне збереження: Room v4 (міграції `1→2` додає `videos.channelId`, `2→3` — `videos.authorAvatarUrl` і таблицю `watch_history`, `3→4` — таблицю `shorts_feed`), DataStore Preferences для позиції відтворення, налаштувань і `known_channels`, `EncryptedSharedPreferences` (`androidx.security:security-crypto`) для OAuth-токенів, клієнтських даних і профілю; файл `account_tokens` виключений із backup.
- Лінтинг: ktlint (плагін `org.jlleitschuh.gradle.ktlint`), `ktlintCheck` проходить.
- CI/CD: GitHub Actions `.github/workflows/android.yml` (`assembleDebug`, `ktlintCheck`, `test`).
- Ліцензія MIT (файл `LICENSE` ще не доданий).

## 4. Архітектура та збірка

### 4.1. Модулі

```
app      Android application: Compose UI (п'ять вкладок, Home/Shorts/Search/Subscriptions/You/Login/History/Settings/Notifications), ViewModels, навігація, тема, Hilt entry points
data     Android library: Retrofit/Moshi сервіс, Room (entities/DAO/db/migrations v1→v2→v3→v4, включно з shorts_feed), DataStore (позиції + налаштування + known_channels), Media3 disk cache (256 MiB LRU), encrypted token store, YouTube TV OAuth + InnerTube-клієнт, Hilt‑модулі, VideoRepositoryImpl / HistoryRepositoryImpl / AccountRepositoryImpl
domain   Kotlin/JVM: доменні моделі (Video, VideoDetails, PlaybackSource, Channel, Comment, CommentPage, ChannelVideosPage, AppSettings, WatchHistoryEntry, RemoteVideo, UserAccount, AccountState, LoginState, AccountResult) та інтерфейси VideoRepository / PlaybackPositionRepository / HistoryRepository / SettingsRepository / AccountRepository
ui       Android library‑заглушка (порожня, зарезервована під винесення Compose‑компонентів)
```

> У корені репозиторію залишився каталог `src/` — це застаріла копія коду з часів однопроєктної збірки, він **не входить** до жодного сорс‑сету і не використовується.

Потік даних: `domain` → `data` (`VideoRepositoryImpl` + кеш + мапінг DTO→Entity→Domain) → `app` (`ViewModel` + `Compose`). DI — Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, `SingletonComponent`).

### 4.2. Технічний стек (фактичні версії)

| Компонент | Версія |
|-----------|--------|
| Gradle (wrapper) | 8.6 |
| Android Gradle Plugin | 8.2.2 |
| Kotlin | 1.9.24 |
| Compose Compiler Extension | 1.5.14 |
| Jetpack Compose BOM | 2024.03.00 |
| Material 3 | з BOM |
| Navigation Compose | 2.7.7 |
| Lifecycle | 2.8.0 |
| Hilt | 2.47 (kapt) |
| Room | 2.6.1 (kapt) |
| DataStore Preferences | 1.1.1 |
| Security Crypto | 1.1.0-alpha06 (`EncryptedSharedPreferences`) |
| Retrofit + converter‑moshi | 2.11.0 |
| Moshi (kotlin + codegen) | 1.15.1 |
| OkHttp + logging‑interceptor | 4.12.0 |
| Media3 (ExoPlayer) | 1.4.1 (`media3-exoplayer`, `-hls`, `-ui`, `-datasource` + явні `media3-common` / `media3-database`) |
| Paging 3 | 3.2.1 (Shorts RemoteMediator — працює, але не підключений до UI) |
| Coil | 2.5.0 |
| Kotlinx Coroutines | 1.8.1 |
| JUnit / MockK / coroutines-test | 4.13.2 / 1.13.10 / 1.8.1 (`mockwebserver` лише для тестів OAuth) |
| ktlint‑gradle | 12.0.2 |

Чому не новіші версії: зв'язка Kotlin 1.9.24 + Compose Compiler 1.5.14 сумісна «з коробки»; перехід на Kotlin 2.x вимагає окремого Compose‑плагіна (`org.jetbrains.kotlin.plugin.compose`) і винесений у беклог.

API дискового кешу (`SimpleCache`, `LeastRecentlyUsedCacheEvictor`, `CacheDataSource.Factory`) у Media3 **1.4.1** походять із `media3-datasource:1.4.1` (разом із явними `media3-common` / `media3-database`): артефакту `media3-datasource-cache` на 1.4.1 не існує. Усі артефакти Media3 зафіксовані на 1.4.1, залишків ExoPlayer 2.x немає.

### 4.3. Конфігурація збірки

* `gradle/wrapper/` + `gradlew` / `gradlew.bat` — локальний Gradle не потрібен (перевірено на macOS/Linux CI та Windows).
* `gradle.properties` — `android.useAndroidX=true`, JVM args, кеш/паралельність.
* `local.properties` (у `.gitignore`) — `sdk.dir`, або змінна `ANDROID_HOME`.
* `.editorconfig` — правила ktlint; свідомо вимкнено `no-wildcard-imports` (конвенція Compose), `function-naming` (PascalCase для composable) і `class-signature` (некрасиве форматування `@Inject constructor`).
* Базова адреса Invidious — `BuildConfig.INVIDIOUS_BASE_URL` у модулі `data`, за замовчуванням `https://yt.chocolatemoo53.com/`, перекривається збіркою:
  `./gradlew assembleDebug -PinvidiousBaseUrl=https://yewtu.be/` (слеш в кінці обов'язковий).
* Публічні інстанси часто падають/вимикають API — актуальний список: <https://api.invidious.io/>.

## 5. План розробки (milestones)

1. **0‑1 місяць** – Створення проекту, налаштування CI, базова UI (MainActivity, NavHost). ✅
2. **1‑2 місяць** – Реалізація Invidious API (Retrofit, Moshi), Repository, RepositoryImpl. ✅
3. **2‑3 місяць** – ViewModels і UI‑компоненти для Home/Shorts/Search. ✅
4. **3‑4 місяць** – Плейєр (ExoPlayer) з вибором якості, аудіо, fullscreen. ✅ (PIP і resume — у межах наступного пункту)
5. **4‑5 місяць** – Тестування, кешування, Dark‑Mode, баг‑фікси. ✅ тести, кешування записів, темна/світла тема з перемикачем
6. **5‑6 місяць** – Підготовка релізу 1.0, документація, підписка на канали. ✅ read-only Google-акаунт (TV device-code OAuth + InnerTube): підписки, історія YouTube і сповіщення

Станом на поточну ітерацію закрито: п'ять вкладок із YouTube-стилем Головної (полиця Shorts, картки з аватарами, скелетони), deep link на конкретний Shorts, навігацію на канал за `UC…id`, пагінацію пошуку/каналу/коментарів, контроли плеєра (якість, fullscreen, PIP, resume), стани завантаження/помилки з retry, екран перегляду відео, вертикальний плеєр шортсів, локальну історію переглядів, повний екран Налаштувань (тема, історія, максимальна якість, Про застосунок) із застосуванням обраної якості до вибору стріму, а також read-only вхід у Google із зашифрованими токенами, стрічкою підписок, історією YouTube і best-effort сповіщеннями.

## 6. Відомі обмеження

1. **Trending не пагінується**: `/api/v1/trending` повертає фіксовану стрічку, тому на головній немає нескінченного скролу — лише кнопка refresh; категорії перемикаються чипсами, а вже завантажена категорія береться з пам'яті. Pull‑to‑refresh свідомо не додано: API `material3` 1.2.1 для нього незручний, а оновлення версій поза скоупом.
2. **Кеш**: для Shorts тепер cache-first — Room v4 (`shorts_feed`) + DataStore (`known_channels`), stale‑while‑revalidate з індикатором оновлення; зберігаються лише метадані, підписані URL стрімів ніколи не потрапляють у кеш. Для решти вкладок Room лишається write‑through (читання тільки для одного відео/каналу), офлайн‑режиму немає. Локальна історія — окрема таблиця `watch_history`, не кеш метаданих.
3. **Тема**: темна типово за системою, світла — перемикачем у Налаштуваннях; вибір зберігається в DataStore. Системні події зміни теми не перечитують налаштування на льоту (потрібен холодний старт).
4. **Коментарі**: показуються лише top‑level коментарі, без відповідей; лайки та відповіді не інтерактивні.
5. **Обмеження інстансу**: на тестовому інстансі `description` і `formatStreams` у `/api/v1/videos/{id}` порожні. HLS-сегменти VOD там віддають **403** (варіантні плейлисти ведуть на YouTube-слайси `/govp/slices=…`), тоді як adaptive-доріжки (`videoplayback?…&range=`) — **206**; тому VOD грає через merged adaptive, а HLS використовується для live. На інших інстансах поведінка може відрізнятися.
6. **Мертвий код**: кореневий `src/` та порожній модуль `ui`.
7. **Тестування**: 399 JVM unit-тестів у debug / 408 разом із release (0 failures) + **5 інструментальних Room-тестів** (міграції/DAO, включно з `3→4`) на TCL 6102H (Android 12); `:data:connectedAndroidTest` — 5/5 PASS; ktlint чистий, debug APK збирається. Phase 1 device QA (P7WKDESW5PKZONVK): повторний вхід у Shorts показав стрічку одразу з нулем discovery-запитів; ~4 свайпи дали по одному `Preparing` на кожен кліп, без помилок відтворення чи ANR. **Phase 2 device QA (Media3 preload + 15-свайповий стрес)**: 15 послідовних свайпів, **0 помилок відтворення / 0 крешів, ~4 MB приріст PSS**; успіх Media3 preload заявлено лише за виміряними результатами. **Чутність Merged audio (окремо прослухана) НЕ перевірялася вухом** — підтверджено лише, що обидва URL (відео й аудіо) реєструються для preload. Compose/UI-тестів немає — перевірка екранів ручна.
8. **Акаунт**: вхід — read-only TV OAuth, тож застосунок не підписується, не ставить лайки й не видаляє нічого на YouTube. Google може будь-коли відкликати сесію — тоді екран показує банер «Сесія завершилася» й повторний вхід, а локальна історія лишається. Сповіщення — best-effort: TV-ендпоінт `FEnotifications` відповідає HTTP 400, тож екран показує «Немає нових сповіщень» замість помилки мережі, а Підписки — стрічка останніх відео, бо TV-ендпоінт не повертає список каналів.
9. **OAuth-дані**: `clientId`/`clientSecret` витягуються з публічної TV-сторінки YouTube і кешуються в зашифрованому сховищі; це неофіційний шлях, і YouTube може його зламати без попередження. Файл токенів виключений із cloud backup.
10. **Медіа-кеш і передзавантаження Shorts**: дисковий кеш Media3 — 256 MiB LRU; ліміт пам'яті preload — 96 MiB; буферизація `min/max/playback/afterRebuffer = 1500/5000/1000/1500` мс (номінальний `min=1000` фактично обрізається до 1500, бо ExoPlayer забороняє `min < afterRebuffer`). Один плеєр на стрічку + `DefaultPreloadManager` із ковзним вікном радіуса 2; для Merged-джерел у preload реєструються обидва URL (відео й аудіо); чутність Merged audio окремо не перевірялася вухом.
11. **Paging 3 — спроможність, не виграш для UI**: `RemoteMediator` і paging source реалізовані й протестовані, але залишаються **дормантними**: екран досі споживає `UiState<List<Video>>` і викликає `loadMore()`. Це база для пізнішого переходу UI на `LazyPagingItems`, а не користувацька перевага в цій версії.
12. **Полиця Shorts на Головній**: її окремий пошук `#shorts` лишається **поза скоупом** — на тестовому інстансі він повертає горизонтальні ролики, які забруднили б вертикальний кеш Shorts; стрічка вкладки будується лише з `/channels/{id}/shorts`.

## 7. Ключові вузли і посилання

- **Invidious API** – <https://github.com/iv-org/invidious> / <https://docs.invidious.io/api/>
- **Список інстансів** – <https://api.invidious.io/>
- **Media3 (ExoPlayer)** – <https://developer.android.com/media/media3>
- **Hilt** – <https://dagger.dev/hilt/>
- **Room** – <https://developer.android.com/topic/libraries/architecture/room>

## 8. Додаткові ресурси

- CI Workflow (GitHub Actions) – `.github/workflows/android.yml`
- Тема та UI – `app/src/main/java/com/example/newtube/ui/theme`
- Основний модуль – `app` (`MainActivity.kt`, `ui/nav/NavGraph.kt`)
- Екран перегляду та плеєр – `app/src/main/java/com/example/newtube/ui/video` (`WatchScreen.kt`, `VideoPlayer.kt`, `WatchViewModel.kt`)
- Шортси – `app/src/main/java/com/example/newtube/ui/shorts`
- Коментарі – `app/src/main/java/com/example/newtube/ui/comments`
- Спільні стани/форматування – `app/src/main/java/com/example/newtube/ui/common`
- Мережевий шар – `data/src/main/java/com/example/newtube/remote`
- Акаунт – `data/src/main/java/com/example/newtube/account` (OAuth, credentials, encrypted store, repository) і `data/src/main/java/com/example/newtube/innertube` (парсер + клієнт)
- Екрани входу й акаунта – `app/src/main/java/com/example/newtube/ui/login`, `ui/you`, `ui/subscriptions`, `ui/notifications`
- Мапінг і кеш – `data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt`
- Позиція відтворення – `data/src/main/java/com/example/newtube/preferences/DataStorePlaybackPositionRepository.kt`

---

Цей файл `SPEC.md` містить специфікацію, фактичний стан реалізації та план розробки.
