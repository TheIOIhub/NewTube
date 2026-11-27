# NewTube – Специфікація та План Розробки

## 1. Загальний опис

**Назва:** *NewTube* (версія 1.0.0)

**Мета проекту:** Створити Android‑додаток, що дозволяє переглядати відео, шортси і прямі трансляції із YouTube без реклами, звертаючись безпосередньо до InnerTube YouTube без сторонніх посередників.

## 2. Функціональні вимоги

| Вкладка | Функція | Опис | Стан |
|---------|---------|------|------|
| **Оболонка** | П'ять вкладок | Головна, Shorts, Пошук, Підписки, Ви в одному `NavigationBar` (підписи 10 sp, без «пігулки» вибраного); на Watch/Settings/History/About панель ховається | ✅ реалізовано |
| **Головна** | Стрічка за категоріями | брендовий top bar (лого, оновлення, дзвіночок, пошук), чипси All/Music/Gaming/Movies/Sports/News/Technology, горизонтальна **полиця Shorts** із вертикальними плитками, скелетони під час завантаження; кожна категорія — один нативний `browse`-фід, перемішаний на кожен запит | ✅ реалізовано |
| **Картки** | YouTube-стиль | 16:9 мініатюра, бейдж тривалості/LIVE, аватар каналу 36 dp, назва, українські перегляди й дата; та сама картка у пошуку, схожих відео та завантаженнях каналу | ✅ реалізовано |
| **Шортси** | Вертикальні відео | справжні Shorts із нативної стрічки `FEshorts` (`browse` + `continuation`); фільтр `1..180` с без live, вертикальний свайп‑плеєр на весь екран + перемішування; тайл із полиці відкриває вкладку на конкретному кліпі (`shorts?videoId=…`) | ✅ реалізовано |
| **Пошук** | Пошук відео | `POST /youtubei/v1/search` (`query` + `continuation`), живий пошук з debounce 500 мс і догрузкою сторінок; підказки через `/youtubei/v1/search/suggestions` | ✅ реалізовано (без історії пошуку) |
| **Канали** | Профіль каналу | аватар, назва, підписники, опис + стрічка відео з `continuation` | ✅ реалізовано (навігація за `UC…id`) |
| **Відео** | Екран перегляду | плеєр 16:9 зверху, назва/перегляди/дата, канал, опис, коментарі, схожі відео | ✅ реалізовано |
| **Коментарі** | Список коментарів | `POST /youtubei/v1/next` (`continuation`), перша сторінка + `continuation`, лічильник, закріплені | ✅ реалізовано (без відповідей/лайків) |
| **Плеєр** | Media3 1.4.1 (ExoPlayer) | live → HLS, VOD → progressive → merged adaptive (avc1 ≤ вибраної якості) → HLS як запасний; меню якості, fullscreen, PIP, resume; Shorts — один плеєр із `DefaultPreloadManager` (радіус 2), 256 MiB дисковий кеш і передзавантаження мініатюр | ✅ реалізовано (Phase 2 device QA — 15 свайпів, див. §6) |
| **Історія** | Локальна історія | Room-таблиця `watch_history`: один рядок на відео (upsert, ліміт 500), найновіші зверху, відносний час українською; Shorts пишеться лише після 3 с на сторінці; тап відкриває відео | ✅ реалізовано |
| **Історія YouTube** | Віддалена історія | після входу і ввімкненого перемикача екран показує справжню історію акаунта (відео й Shorts, підзаголовок «Історія YouTube») з пагінацією; віддалені рядки не пишуться в Room; збій або прострочена сесія повертає локальний список із банером | ✅ реалізовано |
| **Налаштування** | Повний екран | Акаунт (вхід/вихід), Вигляд (системна/темна/світла тема), Історія (запис переглядів, перемикач історії YouTube, підтверджене очищення разом із позиціями), Відтворення (480/720/1080p, типово 720p), Про застосунок | ✅ реалізовано |
| **Вхід** | Google TV OAuth | device-code вхід на `google.com/device`: код, зворотний відлік, «Відкрити сайт», копіювання коду, скасування й повтор; токени у `EncryptedSharedPreferences`, оновлення access-токена без участі користувача | ✅ реалізовано |
| **Підписки** | Стрічка підписок | останні відео акаунта з TV-ендпоінта `FEsubscriptions` як сітка 2×N із `continuation`; CTA для входу, банер простроченої сесії; це стрічка відео, а не список каналів, бо TV-ендпоінт не повертає `gridChannelRenderer` | ✅ реалізовано |
| **Сповіщення** | Best-effort | один запит `FEnotifications`, рядки відкривають Watch; порожня або недоступна відповідь показує «Немає нових сповіщень» (без пагінації й бейджів) | ✅ реалізовано |
| **Ви / Акаунт** | Стан акаунта | гість бачить CTA входу; після входу — аватар 48 dp, ім'я, email і підтверджений вихід; вихід не чіпає локальну історію | ✅ реалізовано |
| **Кешування** | Метадані відео/каналів і стрічки | Room v5: write‑through для `getVideoById`/`getChannelDetails`; `feed_cache` — cache-first для Головної, Пошуку, Каналу й Shorts (stale‑while‑revalidate з індикатором оновлення, лише метадані — підписані URL стрімів не зберігаються); Shorts додатково тримає `shorts_feed` + DataStore `known_channels` | ⚠️ частково (офлайн-відтворення й історії YouTube немає) |
| **Тема** | Темна/світла | Material3, темна типово за системою, перемикач у Налаштуваннях зберігається в DataStore | ✅ реалізовано |

### 2.1. Мережевий шар (фактичний контракт)

Усі запити стрічок, пошуку, підказок, каналів і плеєра йдуть напряму до InnerTube YouTube (`youtubei/v1`); стороннього сервера API немає. Клієнти й шляхи ендпоінтів беруться з конфігурації збірки (див. §4.3), а функції акаунта (історія, підписки, сповіщення) використовують TVHTML5-клієнт з Bearer‑токеном.

| Призначення | Запит | Модель |
|---|---|---|
| Головна (тренди/категорії) | `POST /youtubei/v1/browse` (`FEtrending`, `FEmusic_home`, `FEgaming_home`, `FEmovies_home`, `FEsports_home`, `FEnws_home`) | `JsonObject` → `List<Video>` (до 60) |
| Shorts (полиця й стрічка) | `POST /youtubei/v1/browse` (`FEshorts`, далі `continuation`) | `JsonObject` → `List<Video>` (1..180 с, не live) |
| Пошук | `POST /youtubei/v1/search` (`query`, `params`, `continuation`) | `JsonObject` → `List<Video>` |
| Підказки пошуку | `POST /youtubei/v1/search/suggestions` (`query`) | `List<String>` |
| Відео каналу | `POST /youtubei/v1/browse` (`UC…`, `continuation`) | `ChannelVideosPage` |
| Деталі + стрім | `POST /youtubei/v1/player` (`videoId`, `contentCheckOk`, `racyCheckOk`) | `VideoDetails` + `PlaybackSource` |
| Коментарі | `POST /youtubei/v1/next` (`continuation`) | `CommentPage` |

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

* `ContentParser` читає `JsonObject` толерантно: невідомі вузли (реклама, `lockupViewModel`, інші види renderer) пропускаються, а не ламають розбір; фільтр контенту діє до мапінгу в доменну модель;
* кожен запит пробує ланцюжок клієнтів (`ANDROID` → `ANDROID_VR` → `IOS`, плюс варіанти з конфігурації); для `player` відповідь приймається тільки зі статусом `playabilityStatus.status = OK`, інакше пробується наступний клієнт;
* `hl`/`gl` у контексті запиту беруться з `AppSettings.contentLanguage`/`contentCountry` (типово `uk`/`UA`) і змінюються разом із налаштуваннями;
* пагінація InnerTube береться з `continuations[].nextContinuationData.continuation` (не з `continuationCommand.token`), тому сторінки не перетинаються; постійний курсор тримається для Shorts і пошуку;
* Shorts читаються з нативної стрічки `FEshorts`: `reelItemRenderer` не має `lengthText`, тому 0 означає «тривалість невідома», а не помилку; лишаються ролики `1..180` с без live;
* Головна — один `browse`-запит на категорію (до 60 елементів), догрузки немає; «Для вас» додатково ранжується локально за історією, підписками й мовою;
* URL відтворення береться лише зі свіжої відповіді `player`: для live — HLS, для VOD — progressive, далі merged-пара (відео + аудіо) у межах обраної якості (480/720/1080); підписані URL ніколи не пишуться в Room/DataStore;
* опис і рекомендації приходять у `player`; якщо опис порожній, блок опису не показується.

## 3. Нефункціональні вимоги

- Підтримка Android API 24+ (compileSdk/targetSdk 34).
- UI на Jetpack Compose (Material 3).
- Тестованість: JUnit4 + MockK + kotlinx‑coroutines‑test (287 JVM unit‑тестів, 0 failures: репозиторії, InnerTube-клієнт/парсер, OAuth, мапінги налаштувань/історії, ViewModel-класи, форматери, feed/Shorts-кеш/медіа/preload/Paging) + **5 інструментальних тестів Room** (міграції `1→5`, включно з `4→5`, та DAO `watch_history`) на реальному пристрої; `./gradlew.bat :data:connectedAndroidTest --no-daemon` — **5/5 PASS**.
- Локальне збереження: Room v5 (міграції `1→2` додає `videos.channelId`, `2→3` — `videos.authorAvatarUrl` і `watch_history`, `3→4` — `shorts_feed`, `4→5` — `feed_cache`), DataStore Preferences для позиції відтворення, налаштувань, `known_channels` і content config, `EncryptedSharedPreferences` (`androidx.security:security-crypto`) для OAuth-токенів, клієнтських даних і профілю; файл `account_tokens` виключений із backup.
- Лінтинг: ktlint (плагін `org.jlleitschuh.gradle.ktlint`), `ktlintCheck` проходить.
- CI/CD: GitHub Actions `.github/workflows/android.yml` (`assembleDebug`, `ktlintCheck`, `test`).
- Ліцензія MIT (файл `LICENSE` ще не доданий).

## 4. Архітектура та збірка

### 4.1. Модулі

```
app      Android application: Compose UI (п'ять вкладок, Home/Shorts/Search/Subscriptions/You/Login/History/Settings/Notifications), ViewModels, навігація, тема, Hilt entry points
data     Android library: OkHttp InnerTube-клієнт із ланцюжком клієнтів і толерантний JSON-парсер, Room (entities/DAO/db/migrations, включно з feed cache v5 і shorts_feed), DataStore (позиції + налаштування + known_channels + content config), Media3 disk cache (256 MiB LRU), encrypted token store, YouTube TV OAuth, Hilt‑модулі, VideoRepositoryImpl / HistoryRepositoryImpl / AccountRepositoryImpl
domain   Kotlin/JVM: доменні моделі (Video, VideoDetails, PlaybackSource, Channel, Comment, CommentPage, ChannelVideosPage, AppSettings, WatchHistoryEntry, RemoteVideo, UserAccount, AccountState, LoginState, AccountResult) та інтерфейси VideoRepository / PlaybackPositionRepository / HistoryRepository / SettingsRepository / AccountRepository
ui       Android library‑заглушка (порожня, зарезервована під винесення Compose‑компонентів)
```

Потік даних: `domain` → `data` (`VideoRepositoryImpl` + кеш + мапінг InnerTube `JsonObject`→Entity→Domain) → `app` (`ViewModel` + `Compose`). DI — Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, `SingletonComponent`).

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
* Клієнти й шляхи ендпоінтів InnerTube читаються з `BuildConfig.CONTENT_CONFIG_URL` у модулі `data`. Порожнє значення за замовчуванням (`-PcontentConfigUrl=`) означає вбудований конфіг (`ANDROID`, `ANDROID_VR`, `IOS`); перекривається збіркою:
  `./gradlew assembleDebug -PcontentConfigUrl=https://example.com/youtube-config.json`.
* JSON-конфіг може містити `contentClients`, `playerClients`, `endpoints`, `ttlMillis`, `fetchedAt`; валідна відповідь кешується в DataStore (TTL 6 год за замовчуванням), а за збій або порожнє значення працює вбудований конфіг.
* Збірка `release` вмикає R8 (`isMinifyEnabled = true`) з `proguard-android-optimize.txt` і `app/proguard-rules.pro`; APK поки що без підпису.

## 5. План розробки (milestones)

1. **0‑1 місяць** – Створення проекту, налаштування CI, базова UI (MainActivity, NavHost). ✅
2. **1‑2 місяць** – Реалізація мережевого шару (Retrofit/Moshi, згодом замінено на OkHttp + InnerTube), Repository, RepositoryImpl. ✅
3. **2‑3 місяць** – ViewModels і UI‑компоненти для Home/Shorts/Search. ✅
4. **3‑4 місяць** – Плейєр (ExoPlayer) з вибором якості, аудіо, fullscreen. ✅ (PIP і resume — у межах наступного пункту)
5. **4‑5 місяць** – Тестування, кешування, Dark‑Mode, баг‑фікси. ✅ тести, кешування записів, темна/світла тема з перемикачем
6. **5‑6 місяць** – Підготовка релізу 1.0, документація, підписка на канали. ✅ read-only Google-акаунт (TV device-code OAuth + InnerTube): підписки, історія YouTube і сповіщення

Станом на поточну ітерацію закрито: п'ять вкладок із YouTube-стилем Головної (полиця Shorts, картки з аватарами, скелетони), deep link на конкретний Shorts, навігацію на канал за `UC…id`, пагінацію пошуку/каналу/коментарів, контроли плеєра (якість, fullscreen, PIP, resume), стани завантаження/помилки з retry, екран перегляду відео, вертикальний плеєр шортсів, локальну історію переглядів, повний екран Налаштувань (тема, історія, максимальна якість, Про застосунок) із застосуванням обраної якості до вибору стріму, а також read-only вхід у Google із зашифрованими токенами, стрічкою підписок, історією YouTube і best-effort сповіщеннями.

## 6. Відомі обмеження

1. **Головна не пагінується**: кожна категорія — один `browse`-запит без continuation, тому нескінченного скролу немає; категорії перемикаються чипсами, а стрічка береться з кешу й оновлюється кнопкою refresh. Pull‑to‑refresh свідомо не додано: API `material3` 1.2.1 для нього незручний, а оновлення версій поза скоупом.
2. **Кеш**: Room v5 (`feed_cache`) дає cache-first для Головної, Пошуку, Каналу й Shorts зі stale‑while‑revalidate та індикатором оновлення; Shorts додатково мають `shorts_feed` + DataStore (`known_channels`). Зберігаються лише метадані, підписані URL стрімів ніколи не потрапляють у кеш; офлайн-відтворення немає. Локальна історія — окрема таблиця `watch_history`, не кеш метаданих.
3. **Тема**: темна типово за системою, світла — перемикачем у Налаштуваннях; вибір зберігається в DataStore. Системні події зміни теми не перечитують налаштування на льоту (потрібен холодний старт).
4. **Коментарі**: показуються лише top‑level коментарі, без відповідей; лайки та відповіді не інтерактивні.
5. **Залежність від InnerTube**: доступність і статус клієнтів (`playabilityStatus`) залежать від YouTube; якщо всі клієнти в ланцюжку відхилили запит, показується помилка з retry, а кешована стрічка лишається на екрані. Для live використовується HLS, для VOD — progressive/merged adaptive зі свіжої відповіді `player`.
6. **Мертвий код**: лише порожній модуль `ui` (кореневий `src/` видалено).
7. **Тестування**: 287 JVM unit-тестів (0 failures) + **5 інструментальних Room-тестів** (міграції/DAO, включно з `4→5`) на TCL 6102H (Android 12); `:data:connectedAndroidTest` — 5/5 PASS; ktlint чистий, debug APK і minified release APK збираються. Phase 1 device QA (P7WKDESW5PKZONVK): повторний вхід у Shorts показав стрічку одразу з нулем discovery-запитів; ~4 свайпи дали по одному `Preparing` на кожен кліп, без помилок відтворення чи ANR. **Phase 2 device QA (Media3 preload + 15-свайповий стрес)**: 15 послідовних свайпів, **0 помилок відтворення / 0 крешів, ~4 MB приріст PSS**; успіх Media3 preload заявлено лише за виміряними результатами. **Чутність Merged audio (окремо прослухана) НЕ перевірялася вухом** — підтверджено лише, що обидва URL (відео й аудіо) реєструються для preload. Compose/UI-тестів немає — перевірка екранів ручна.
8. **Акаунт**: вхід — read-only TV OAuth, тож застосунок не підписується, не ставить лайки й не видаляє нічого на YouTube. Google може будь-коли відкликати сесію — тоді екран показує банер «Сесія завершилася» й повторний вхід, а локальна історія лишається. Сповіщення — best-effort: TV-ендпоінт `FEnotifications` відповідає HTTP 400, тож екран показує «Немає нових сповіщень» замість помилки мережі, а Підписки — стрічка останніх відео, бо TV-ендпоінт не повертає список каналів.
9. **OAuth-дані**: `clientId`/`clientSecret` витягуються з публічної TV-сторінки YouTube і кешуються в зашифрованому сховищі; це неофіційний шлях, і YouTube може його зламати без попередження. Файл токенів виключений із cloud backup.
10. **Медіа-кеш і передзавантаження Shorts**: дисковий кеш Media3 — 256 MiB LRU; ліміт пам'яті preload — 96 MiB; буферизація `min/max/playback/afterRebuffer = 1500/5000/1000/1500` мс (номінальний `min=1000` фактично обрізається до 1500, бо ExoPlayer забороняє `min < afterRebuffer`). Один плеєр на стрічку + `DefaultPreloadManager` із ковзним вікном радіуса 2; для Merged-джерел у preload реєструються обидва URL (відео й аудіо); чутність Merged audio окремо не перевірялася вухом.
11. **Paging 3 — спроможність, не виграш для UI**: `RemoteMediator` і paging source реалізовані й протестовані, але залишаються **дормантними**: екран досі споживає `UiState<List<Video>>` і викликає `loadMore()`. Це база для пізнішого переходу UI на `LazyPagingItems`, а не користувацька перевага в цій версії.
12. **Полиця Shorts на Головній**: її окремий пошук `#shorts` лишається **поза скоупом** — на тестовому інстансі він повертає горизонтальні ролики, які забруднили б вертикальний кеш Shorts; стрічка вкладки будується лише з `/channels/{id}/shorts`.

## 7. Ключові вузли і посилання

- **InnerTube (youtubei/v1)** – клієнт і парсер у `data/src/main/java/com/example/newtube/innertube`; дизайн переходу — `docs/superpowers/specs/2026-11-27-direct-innertube-green-tuber-design.md`
- **YouTube TV OAuth** – <https://www.youtube.com/tv>
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
- Мережевий шар – `data/src/main/java/com/example/newtube/innertube` (клієнт, конфіг, парсер)
- Акаунт – `data/src/main/java/com/example/newtube/account` (OAuth, credentials, encrypted store, repository) і `data/src/main/java/com/example/newtube/innertube` (парсер + клієнт)
- Екрани входу й акаунта – `app/src/main/java/com/example/newtube/ui/login`, `ui/you`, `ui/subscriptions`, `ui/notifications`
- Мапінг і кеш – `data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt`
- Позиція відтворення – `data/src/main/java/com/example/newtube/preferences/DataStorePlaybackPositionRepository.kt`

---

Цей файл `SPEC.md` містить специфікацію, фактичний стан реалізації та план розробки.

## Додаток (2026-11-27): регіональний контент і розширений плеєр

- `AppSettings` доповнено полями `contentCountry` (`UA`), `contentLanguage` (`uk`), `preferredAudioLanguage`, `appLanguage`, жестами, `resizeMode` (константи Media3 FIT=0/FILL=3/ZOOM=4), `doubleTapSeekSeconds`, `videoBrightness` та ін.; збереження — DataStore `settings`, єдиний шлях запису `SettingsRepository.update`.
- `YouTubeContentClient` передає `hl`/`gl` з налаштувань у контекст клієнта для `browse`, `search`, `search/suggestions` та `player`. Зміна країни/мови інвалідує кеші Home і Shorts; Shorts тепер беруться з нативної стрічки `FEshorts`, а не з пошуку каналів.
- `HomeCategory.FOR_YOU` ("Для вас") ранжує регіональну стрічку чистою функцією `rankFeed` за локальною історією, підписками та мовою; бекенд рекомендацій не додавався.
- Фільтр контенту (`VideoContentFilter`) діє до мапінгу InnerTube `JsonObject` у доменну модель: вікові обмеження та best-effort безпечний режим; підказки пошуку та приховування метаданих керуються налаштуваннями.
- Плеєр: меню аудіодоріжок із `TrackSelectionOverride` і відкатом на перествочення джерела для `Merged`, цикл FIT→FILL→ZOOM, жести подвійного тапу (третини екрана) та вертикальні свайпи у повному екрані (зліва яскравість, справа гучність).
- Дизайн і дослідження: `docs/superpowers/specs/2026-11-27-regional-personalized-player-design.md`, `docs/research/2026-11-27-newpipe-player-features.md`.
