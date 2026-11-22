# Google Account (Phases 4–5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sign in with a Google account through YouTube's TV device-code OAuth and show the account's YouTube history, subscriptions and notifications inside NewTube, read-only.

**Architecture:** Domain gains account models and an `AccountRepository` interface. `:data` implements it with three pieces: a credential scraper (`youtube.com/tv` → client id/secret), an OAuth device-code client, and a tolerant InnerTube JSON parser/client. Tokens and the profile live in `EncryptedSharedPreferences`. `:app` adds a Login screen, a signed-in You tab, the Subscriptions grid, remote history inside the existing History screen, and a best-effort Notifications screen. Invidious keeps serving browsing, channels and playback; InnerTube is used only for account data.

**Tech Stack:** Kotlin, Hilt, OkHttp, Retrofit (Invidious only), kotlinx.serialization `JsonElement`, EncryptedSharedPreferences (`androidx.security:security-crypto`), Compose Material 3, JUnit4 + MockK + kotlinx-coroutines-test + MockWebServer (test only).

**Spec:** `docs/superpowers/specs/2026-11-12-youtube-home-account-design.md` (§13–§21, §22 Phases 4–5). The Phase 0 spike already passed; its corrections are folded into §17.2, §17.4, §17.5 and §18.2 and its fixtures live in `data/src/test/resources/spike/`.

## Global Constraints

- `minSdk = 24`, `compileSdk/targetSdk = 34`; do not change them.
- **Only new runtime dependency:** `androidx.security:security-crypto:1.1.0-alpha06` in `:data`. Test-only additions allowed: `com.squareup.okhttp3:mockwebserver:4.12.0`.
- `:domain` stays pure Kotlin/JVM: no Android, `androidx` or framework imports; coroutines flows only.
- The Fire TV Cobalt user agent is a single constant used for every Google request: `Mozilla/5.0 (Linux armeabi-v7a; Android 7.1.2; Fire OS 6.0) Cobalt/22.lts.3.306369-gold (unlike Gecko) v8/8.8.278.8-jit gles Starboard/13, Amazon_ATV_mediatek8695_2019/NS6294 (Amazon, AFTMM, Wireless) com.amazon.firetv.youtube/22.3.r2.v66.0`.
- OAuth endpoints: `POST https://www.youtube.com/o/oauth2/device/code` and `POST https://www.youtube.com/o/oauth2/token`; InnerTube: `POST https://www.youtube.com/youtubei/v1/browse` and `POST https://www.youtube.com/youtubei/v1/account/accounts_list`; TV page: `https://www.youtube.com/tv`.
- Device-code JSON: `model_name: "ytlr::"`, `scope: "http://gdata.youtube.com https://www.googleapis.com/auth/youtube-paid-content"`, grant type `http://oauth.net/grant_type/device/1.0`.
- `accounts_list` must send `"returnPersonaAccounts": true` (false returns an empty account list — proven in Phase 0). `pageId` is optional and absent for owner accounts.
- Access-token lifetime is ~18–24 h; refresh at `expires_at − 5 min`; refresh does not rotate the refresh token.
- Never hardcode client id/secret; never log token bodies, refresh tokens, emails or full InnerTube responses. Token/InnerTube loggers: none on the token client, `BASIC` + `redactHeader("Authorization")` on the InnerTube client in debug only.
- `NetworkModule` already provides an **unqualified** `OkHttpClient` for Invidious. The two new clients must be qualified (`@Named("oauth")`, `@Named("innertube")`) so Hilt keeps all three apart; every consumer takes the matching `@Named` parameter.
- UI copy is Ukrainian; "Shorts" is never translated. Never show a dead button: every CTA leads to Login or retry.
- Remote history is **never** written to Room; sign-out clears tokens + remote caches and keeps local history.
- Tests must not touch the network; all Google JSON comes from fixtures or MockWebServer.
- JVM ViewModel tests: `Dispatchers.setMain(UnconfinedTestDispatcher())` in `@Before` and `resetMain()` in `@After` (the existing `SettingsViewModelTest`/`HistoryViewModelTest` show the pattern; `UnconfinedTestDispatcher` needs `@OptIn(ExperimentalCoroutinesApi::class)`). With Main unconfined, `viewModelScope.launch` and `stateIn` run eagerly, so reading `uiState.value` after an action is deterministic.
- `AccountRepository.state` is a `StateFlow`; test fakes must return `MutableStateFlow(...)`, never `flowOf(...)`.
- ktlint must pass; run `ktlintFormat` then (separately) `ktlintCheck`.
- Conventional commit per task.

## File Structure

**domain** (new files; one responsibility each)
- `model/UserAccount.kt`, `model/AccountState.kt`, `model/LoginState.kt`, `model/RemoteVideo.kt`, `model/AccountResult.kt`
- `repository/AccountRepository.kt`

**data**
- `account/ClientCredentials.kt` — value type + pure scraping regexes (`YouTubeTvPage` object)
- `account/ClientCredentialsProvider.kt` — interface
- `account/YouTubeTvCredentialsProvider.kt` — scrape + 7-day encrypted cache
- `account/SecretStore.kt` — `get/put/clear` string store interface
- `account/EncryptedPrefsSecretStore.kt` — `EncryptedSharedPreferences` implementation
- `account/OAuthApi.kt` — wire models + interface (`requestDeviceCode`, `pollToken`, `refresh`, `fetchProfile`)
- `account/YouTubeOAuthApi.kt` — OkHttp implementation
- `account/AccountRepositoryImpl.kt` — login orchestration, single-flight refresh, state, data calls
- `innertube/InnerTubeParser.kt` — pure walker/parser object
- `innertube/InnerTubeService.kt` — interface + `parse` contract
- `innertube/YouTubeInnerTubeService.kt` — OkHttp implementation
- `di/AccountModule.kt` — bindings and providers
- `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` + manifest attributes (data module has no manifest yet: create `data/src/main/AndroidManifest.xml` only if Hilt requires it; backup attributes live in the **app** manifest for the app process)

**app**
- `ui/login/LoginScreen.kt`, `ui/login/LoginViewModel.kt`
- `ui/you/YouViewModel.kt`, modify `ui/you/YouScreen.kt`
- `ui/subscriptions/SubscriptionsViewModel.kt`, rewrite `ui/subscriptions/SubscriptionsScreen.kt`, `ui/common/RemoteVideoCard.kt`
- `ui/history/HistoryViewModel.kt` (extend), `ui/history/HistoryScreen.kt` (extend)
- `ui/notifications/NotificationsViewModel.kt`, rewrite `ui/notifications/NotificationsScreen.kt`
- `ui/settings/SettingsViewModel.kt`/`SettingsScreen.kt` (account section)
- `ui/nav/NavGraph.kt` (login route)

**tests**
- `data/src/test/java/com/example/newtube/account/*Test.kt`, `data/src/test/java/com/example/newtube/innertube/InnerTubeParserTest.kt`
- fixtures already at `data/src/test/resources/spike/`; add `oauth/` fixtures under the same root
- `app/src/test/java/com/example/newtube/ui/{login,you,subscriptions,history,notifications}/*Test.kt`

## Review Focus

- **Concurrent refresh:** two screens load with an expired access token at the same moment — exactly one refresh fires, both requests retry, no token loop (Task 4).
- **Slow user at `google.com/device`:** the code expires before approval — clean `Error(EXPIRED)`, polling stops, retry issues a fresh code (Task 4).
- **Revoked session mid-use:** refresh returns `invalid_grant` — `AccountState.Expired`, inline banner + local-history fallback, no crash and no repeated refresh storm (Tasks 4, 8).
- **Unexpected InnerTube shapes:** missing/renamed renderers, items without ids, odd `lengthText` — parser yields zero/short lists without throwing, `ParseEmpty` empty state (Task 3).
- **Account boundaries:** sign-out keeps local history and clears remote caches; remote history never reaches Room; tokens never appear in logs (Tasks 4, 6, 8).

---

### Task 1: Client-credential scraping

**Files:**
- Create: `data/src/main/java/com/example/newtube/account/ClientCredentials.kt`
- Create: `data/src/main/java/com/example/newtube/account/ClientCredentialsProvider.kt`
- Create: `data/src/main/java/com/example/newtube/account/SecretStore.kt`
- Create: `data/src/main/java/com/example/newtube/account/YouTubeTvCredentialsProvider.kt`
- Test: `data/src/test/java/com/example/newtube/account/YouTubeTvCredentialsTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `data class ClientCredentials(val clientId: String, val clientSecret: String)`
  - `object YouTubeTvPage { fun findBaseJsUrl(html: String): String?; fun findClientCredentials(js: String): ClientCredentials?; fun resolve(url: String): String }`
  - `interface ClientCredentialsProvider { suspend fun get(): ClientCredentials }`
  - `interface SecretStore { fun get(key: String): String?; fun put(key: String, value: String); fun clear() }`
  - `class YouTubeTvCredentialsProvider(private val fetcher: suspend (String) -> String, private val store: SecretStore, private val clock: () -> Long) : ClientCredentialsProvider`

- [ ] **Step 1: Write the failing test**

```kotlin
class YouTubeTvCredentialsTest {
    private val html = resource("/spike/youtube_tv_base_js.html")
    private val js = resource("/spike/base_js_client_credentials.js")

    @Test fun `finds and resolves the cobalt base-js url`() {
        val url = YouTubeTvPage.findBaseJsUrl(html)
        assertEquals(
            "https://www.youtube.com/s/_/kabuki/_/js/k=kabuki.base_cr.en_US.nHfsRbOf2SY.es6.O/am=AAAAAABoCAAqAYAIwAxSEDQU/d=1/excm=main/rs=ANjRhVmdi6bcqB2lO7nivVczi6dytaDe0Q/m=base",
            url,
        )
        assertEquals("https://www.youtube.com/x.js", YouTubeTvPage.resolve("/x.js"))
    }

    @Test fun `finds the client credentials in the base js fixture`() {
        val c = YouTubeTvPage.findClientCredentials(js)!!
        assertEquals("861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com", c.clientId)
        assertEquals("REDACTED_SECRET", c.clientSecret)
    }

    @Test fun `parses the id-then-src form and the m-base form`() {
        assertTrue(YouTubeTvPage.findBaseJsUrl("""<script id="base-js" src="/a.js"></script>""")!!.endsWith("/a.js"))
        assertTrue(YouTubeTvPage.findBaseJsUrl("""s.src = '/b.js?m=base';""")!!.endsWith("m=base"))
    }

    @Test fun `returns null for unrelated markup`() {
        assertNull(YouTubeTvPage.findBaseJsUrl("<html></html>"))
        assertNull(YouTubeTvPage.findClientCredentials("var x = 1;"))
    }

    @Test fun `caches credentials for seven days then refetches`() = runTest {
        val store = InMemorySecretStore()
        var fetches = 0
        val provider = YouTubeTvCredentialsProvider(
            fetcher = { url -> fetches++; if (url.endsWith("/tv")) html else js },
            store = store,
            clock = { now },
        )
        provider.get()
        provider.get()
        assertEquals(2, fetches)          // tv page + base js
        now += 7 * 24 * 60 * 60 * 1000L + 1
        provider.get()
        assertEquals(4, fetches)
    }
}
```

Add to the test file a tiny `InMemorySecretStore : SecretStore` (map-backed) and a resource helper `resource(path) = checkNotNull(javaClass.getResource(path)).readText()`.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :data:testDebugUnitTest --tests "com.example.newtube.account.YouTubeTvCredentialsTest" --console=plain`
Expected: compilation error — `YouTubeTvPage` / `ClientCredentials` unresolved.

- [ ] **Step 3: Implement the scraper and provider**

`YouTubeTvPage` tries the three base-JS patterns from the spec in order (`id="base-js" … src="…"`, `.src = '…m=base'`, `.src = '…'; .id = 'base-js'`), resolves relative URLs against `https://www.youtube.com`, and matches `clientId:"([-\w]+\.apps\.googleusercontent\.com)",\s*\w+:"(\w+)"`. `YouTubeTvCredentialsProvider` stores `client_id`, `client_secret`, `client_fetched_at` in `SecretStore`, reuses them while `clock() - fetchedAt < 7 days`, and always fetches the page and JS on a cache miss.

- [ ] **Step 4: Run test to verify it passes**

Run: same command as Step 2. Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add data/src/main/java/com/example/newtube/account data/src/test/java/com/example/newtube/account
git commit -m "feat: scrape YouTube TV client credentials with a 7-day encrypted cache"
```

---

### Task 2: OAuth device-code client

**Files:**
- Create: `data/src/main/java/com/example/newtube/account/OAuthApi.kt`
- Create: `data/src/main/java/com/example/newtube/account/YouTubeOAuthApi.kt`
- Test: `data/src/test/java/com/example/newtube/account/YouTubeOAuthApiTest.kt`
- Fixtures: `data/src/test/resources/oauth/device_code.json`, `token_success.json`, `token_pending.json`, `token_slow_down.json`, `token_denied.json`

**Interfaces:**
- Consumes: `ClientCredentials` (Task 1).
- Produces:
  - `data class DeviceCode(val deviceCode: String, val userCode: String, val verificationUrl: String, val expiresInSeconds: Int, val intervalSeconds: Int)`
  - `data class Tokens(val accessToken: String, val refreshToken: String?, val expiresInSeconds: Long)`
  - `sealed interface TokenPoll { data class Granted(val tokens: Tokens) : TokenPoll; data object Pending; data object SlowDown; data object Denied; data object Expired }`
  - `interface OAuthApi { suspend fun requestDeviceCode(credentials: ClientCredentials, deviceId: String): DeviceCode; suspend fun pollToken(credentials: ClientCredentials, deviceCode: String): TokenPoll; suspend fun refresh(credentials: ClientCredentials, refreshToken: String): Tokens }`
  - `class InvalidGrantException(message: String) : IOException(message)` — `refresh` throws it when the token endpoint answers `invalid_grant`; Task 4 catches it to sign out.
  - `class YouTubeOAuthApi(private val client: OkHttpClient, private val baseUrl: String = "https://www.youtube.com") : OAuthApi` — `baseUrl` exists so the test can point at MockWebServer.

- [ ] **Step 1: Write the failing test**

```kotlin
class YouTubeOAuthApiTest {
    private val server = MockWebServer()
    private val creds = ClientCredentials("id.apps.googleusercontent.com", "secret")
    private lateinit var api: YouTubeOAuthApi

    @Before fun setUp() { server.start(); api = YouTubeOAuthApi(OkHttpClient(), server.url("/").toString()) }
    @After fun tearDown() = server.shutdown()

    @Test fun `device code request carries the tv payload`() = runTest {
        server.enqueue(json("/oauth/device_code.json", 200))
        val code = api.requestDeviceCode(ClientCredentials("id.apps.googleusercontent.com", "secret"), "0011223344556677")
        assertEquals("SPPX-SFWV", code.userCode)
        assertEquals(1800, code.expiresInSeconds)
        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"model_name\":\"ytlr::\""))
        assertTrue(body.contains("http://gdata.youtube.com https://www.googleapis.com/auth/youtube-paid-content"))
        assertEquals(server.url("/o/oauth2/device/code").toString(), request.requestUrl.toString())
    }

    @Test fun `poll maps granted pending slow_down denied and expired`() = runTest {
        server.enqueue(json("/oauth/token_success.json", 200))
        assertTrue(api.pollToken(creds, "dc") is TokenPoll.Granted)
        server.enqueue(json("/oauth/token_pending.json", 400))
        assertEquals(TokenPoll.Pending, api.pollToken(creds, "dc"))
        server.enqueue(json("/oauth/token_slow_down.json", 400))
        assertEquals(TokenPoll.SlowDown, api.pollToken(creds, "dc"))
        server.enqueue(json("/oauth/token_denied.json", 400))
        assertEquals(TokenPoll.Denied, api.pollToken(creds, "dc"))
        server.enqueue(rawJson("""{"error":"expired_token"}""", 400))
        assertEquals(TokenPoll.Expired, api.pollToken(creds, "dc"))
    }

    @Test fun `refresh sends the refresh grant and keeps a null refresh token`() = runTest {
        server.enqueue(rawJson("""{"access_token":"a2","expires_in":66928,"token_type":"Bearer"}""", 200))
        val t = api.refresh(creds, "r1")
        assertEquals("a2", t.accessToken)
        assertNull(t.refreshToken)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"grant_type\":\"refresh_token\""))
    }

    @Test fun `refresh maps invalid_grant to a typed exception`() = runTest {
        server.enqueue(rawJson("""{"error":"invalid_grant"}""", 400))
        try {
            api.refresh(creds, "stale")
            throw AssertionError("expected InvalidGrantException")
        } catch (expected: InvalidGrantException) {
            Unit
        }
    }
}
```

`json(path, code)` = `MockResponse().setResponseCode(code).setBody(resource(path)).addHeader("Content-Type", "application/json")`; `rawJson(body, code)` is the same with an inline body. Start the server in `@Before` and build the API there from `server.url("/").toString()` (the port is only known after `start()`). Declare `private val creds = ClientCredentials("id.apps.googleusercontent.com", "secret")`.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :data:testDebugUnitTest --tests "com.example.newtube.account.YouTubeOAuthApiTest" --console=plain`
Expected: FAIL — unresolved `YouTubeOAuthApi`.

- [ ] **Step 3: Implement the OkHttp client**

Add `testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")` to `data/build.gradle.kts`. Build JSON with `kotlinx.serialization` `buildJsonObject`, send to `baseUrl + path`, set `Content-Type: application/json`, the Cobalt UA, `Accept-Language: en-US,en;q=0.9`. Map 400 bodies by their `error` field; any other non-2xx throws `IOException`. `invalid_grant` on refresh throws `InvalidGrantException`. Never attach a logging interceptor.

- [ ] **Step 4: Run test to verify it passes**

Run: same command as Step 2. Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add data/build.gradle.kts data/src/main/java/com/example/newtube/account data/src/test
git commit -m "feat: add the OAuth device-code and token client"
```

---

### Task 3: InnerTube parser and browse client

**Files:**
- Create: `data/src/main/java/com/example/newtube/innertube/InnerTubeParser.kt`
- Create: `data/src/main/java/com/example/newtube/innertube/InnerTubeService.kt`
- Create: `data/src/main/java/com/example/newtube/innertube/YouTubeInnerTubeService.kt`
- Create domain models: `domain/src/main/java/com/example/newtube/model/RemoteVideo.kt`, `UserAccount.kt`
- Test: `data/src/test/java/com/example/newtube/innertube/InnerTubeParserTest.kt`

**Interfaces:**
- Consumes: the spy fixtures under `data/src/test/resources/spike/`.
- Produces:
  - `data class RemoteVideo(val id: String, val title: String, val channelName: String, val channelId: String, val thumbnailUrl: String, val durationMs: Long, val viewsText: String, val publishedText: String, val isLive: Boolean)`
  - `data class UserAccount(val name: String, val email: String, val avatarUrl: String, val pageId: String? = null)`
  - `object InnerTubeParser { fun parseVideos(json: String): ParserResult<RemoteVideo>; fun parseAccount(json: String): UserAccount?; fun durationMs(text: String): Long }`
  - `data class ParserResult<T>(val items: List<T>, val continuation: String?)`
  - `interface InnerTubeService { suspend fun browseVideos(browseId: String, continuation: String? = null, accessToken: String): String; suspend fun browseSubscriptions(continuation: String? = null, accessToken: String): String; suspend fun fetchProfile(accessToken: String): String }`
  - `class YouTubeInnerTubeService(private val client: OkHttpClient, private val pageIdProvider: () -> String?)` sets `X-Goog-Pageid` only when `pageIdProvider()` is non-null (owner accounts have none, brand accounts may). The client is injected with `@Named("innertube")`; `pageIdProvider` reads `account_json` through the store.

- [ ] **Step 1: Write the failing test**

```kotlin
class InnerTubeParserTest {
    private val history = resource("/spike/browse_FEhistory.json")
    private val subs = resource("/spike/browse_FEsubscriptions.json")
    private val account = resource("/spike/accounts_list.json")

    @Test fun `parses the history fixture`() {
        val r = InnerTubeParser.parseVideos(history)
        assertEquals(15, r.items.size) // 10 videos + 5 shorts
        assertNotNull(r.continuation)
        val first = r.items.first()
        assertTrue(first.id.isNotBlank())
        assertTrue(first.title.isNotBlank())
        assertEquals(15, r.items.map { it.id }.distinct().size)
    }

    @Test fun `parses the subscriptions feed and dedupes videos`() {
        val r = InnerTubeParser.parseVideos(subs)
        assertEquals(27, r.items.size) // 29 tiles, 2 repeated videos
        assertTrue(r.items.all { it.id.isNotBlank() })
        assertTrue(r.items.all { it.title.isNotBlank() })
        assertEquals(27, r.items.map { it.id }.distinct().size)
        assertNotNull(r.continuation)
    }

    @Test fun `parses a shorts tile`() {
        val r = InnerTubeParser.parseVideos(history)
        val short = r.items.first { it.id == "FmsoQUD2Sj4" }
        assertTrue(short.title.isNotBlank())
        assertTrue(short.thumbnailUrl.contains("/vi/FmsoQUD2Sj4/"))
        assertEquals(0L, short.durationMs) // shorts show an icon, not a duration
    }

    @Test fun `parses the account fixture`() {
        val a = InnerTubeParser.parseAccount(account)!!
        assertEquals("Test Account", a.name)
        assertEquals("user@example.com", a.email)
        assertTrue(a.avatarUrl.startsWith("https://example.invalid/"))
        assertNull(a.pageId)
    }

    @Test fun `tolerates unknown shapes and odd durations`() {
        assertEquals(0, InnerTubeParser.parseVideos("""{"contents":{"x":1}}""").items.size)
        assertEquals(250_000L, InnerTubeParser.durationMs("4:10"))
        assertEquals(3_723_000L, InnerTubeParser.durationMs("1:02:03"))
        assertEquals(0L, InnerTubeParser.durationMs("LIVE"))
    }

    @Test fun `skips entries without a video id and joins run titles`() {
        val one = """{"a":{"title":{"runs":[{"text":"Hello "},{"text":"world"}]},
                     "videoId":"abcdefghijk","viewCountText":{"simpleText":"1.2M views"}}}"""
        val r = InnerTubeParser.parseVideos("""{"contents":[$one]}""")
        assertEquals(1, r.items.size)
        assertEquals("Hello world", r.items[0].title)
        assertEquals("1.2M views", r.items[0].viewsText)
        val none = """{"a":{"title":{"simpleText":"no id"}}}"""
        assertEquals(0, InnerTubeParser.parseVideos("""{"contents":[$none]}""").items.size)
    }
}
```
Add the same `resource(path)` helper as Task 1; these are pure JUnit tests (no `runTest`).

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :data:testDebugUnitTest --tests "com.example.newtube.innertube.InnerTubeParserTest" --console=plain`
Expected: FAIL — unresolved `InnerTubeParser`.

- [ ] **Step 3: Implement the tolerant walker**

Parse with `Json.parseToJsonElement` and walk the tree recursively. The TV responses use `tileRenderer` nodes (not `videoRenderer`/`lockupViewModel`), so handle them explicitly and keep a generic fallback:

1. **`tileRenderer` with a video endpoint** — read `onSelectCommand.watchEndpoint.videoId`, else `onSelectCommand.reelWatchEndpoint.videoId` (Shorts). Skip a tile with no id.
   - title: `metadata.tileMetadataRenderer.title` (`simpleText`, else `runs[].text` joined);
   - thumbnail: `header.tileHeaderRenderer.thumbnail.thumbnails.last().url`, else `reelWatchEndpoint.thumbnail.thumbnails.last().url`;
   - duration: `header.tileHeaderRenderer.thumbnailOverlays[].thumbnailOverlayTimeStatusRenderer.text.simpleText` (Shorts carry an icon, no text → `0`);
   - channel name: first non-blank text in `metadata.tileMetadataRenderer.lines[0]` (`runs[].text` joined); if the lines are empty, use the `contentId`-less subtitle in `onLongPressCommand.showMenuCommand.subtitle.simpleText` before the first ` @`;
   - `viewsText`: first non-blank item of `lines[1..]` (when it looks like a count/label), `publishedText`: the next non-blank item;
   - `channelId`: first `browseEndpoint.browseId` starting with `UC` anywhere in the tile subtree (TV puts it in the long-press "go to channel" menu), else `""`;
   - `isLive`: `true` when a time-status style is `LIVE` or a badge says live.
2. **Generic fallback** — also collect any object that has `videoId` plus `title`/`headline` (web `videoRenderer`, `lockupViewModel`), reading `viewCountText.simpleText`/`publishedTimeText.simpleText` and the largest thumbnail. This keeps the parser tolerant if YouTube changes the shape.
3. **Dedupe by id**, keeping the first occurrence (the subscriptions fixture repeats two videos).

`durationMs` parses `h:mm:ss`/`m:ss`, otherwise 0. Continuation is the first `continuations[].nextContinuationData.continuation` (TV shape), falling back to `continuationCommand.token`. `YouTubeInnerTubeService` posts the §18.1 body (TVHTML5 client, WATCH screen, uk/UA, UA string) with `Authorization: Bearer …`, `Referer: https://www.youtube.com/tv`, and returns the raw body string so parsing stays testable.

- [ ] **Step 4: Run test to verify it passes**

Run: same command as Step 2. Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/example/newtube/model data/src/main/java/com/example/newtube/innertube data/src/test
git commit -m "feat: add the tolerant InnerTube parser and browse client"
```

---

### Task 4: Account repository, token storage and login flow

**Files:**
- Create domain: `model/AccountState.kt`, `model/LoginState.kt`, `model/AccountResult.kt`, `model/AccountPage.kt`, `repository/AccountRepository.kt`
- Create: `data/src/main/java/com/example/newtube/account/EncryptedPrefsSecretStore.kt`
- Create: `data/src/main/java/com/example/newtube/account/AccountRepositoryImpl.kt`
- Create: `data/src/main/java/com/example/newtube/di/AccountModule.kt`
- Modify: `data/build.gradle.kts` (security-crypto), `app/src/main/AndroidManifest.xml` + `app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml`
- Test: `data/src/test/java/com/example/newtube/account/AccountRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `ClientCredentialsProvider`, `OAuthApi`, `InnerTubeService`, `InnerTubeParser`, `SecretStore`.
- Produces:
  - `sealed interface AccountState { data object SignedOut; data class SignedIn(val account: UserAccount); data class Expired(val account: UserAccount?) }`
  - `sealed interface LoginState { data object Requesting; data class Waiting(val userCode: String, val verificationUrl: String, val expiresAtMs: Long); data object Success; data class Error(val reason: LoginError) }`
  - `enum class LoginError { ACCESS_DENIED, EXPIRED, NETWORK }`
  - `data class AccountPage<T>(val items: List<T>, val continuation: String?)`
  - `sealed interface AccountResult<out T> { data class Success<T>(val data: T); data object NeedsSignIn; data object Network; data object Server; data object ParseEmpty }`
  - `interface AccountRepository { val state: StateFlow<AccountState>; fun loginFlow(): Flow<LoginState>; suspend fun signOut(); suspend fun history(continuation: String? = null): AccountResult<AccountPage<RemoteVideo>>; suspend fun subscriptions(continuation: String? = null): AccountResult<AccountPage<RemoteVideo>>; suspend fun notifications(): AccountResult<List<RemoteVideo>> }`
  - `class AccountRepositoryImpl(private val credentials: ClientCredentialsProvider, private val oauth: OAuthApi, private val innerTube: InnerTubeService, private val store: SecretStore, private val clock: () -> Long, private val deviceId: String)` uses the `InnerTubeParser` object directly (no injected parser).
  - `SecretStore` from Task 1 is bound to `EncryptedPrefsSecretStore`.

- [ ] **Step 1: Write the failing test**

```kotlin
class AccountRepositoryImplTest {
    private val store = InMemorySecretStore()
    private val oauth = FakeOAuthApi()
    private val innerTube = FakeInnerTubeService()
    private val credentials = object : ClientCredentialsProvider {
        override suspend fun get() = ClientCredentials("id", "secret")
    }
    private val clock = FakeClock()
    private val repo = AccountRepositoryImpl(
        credentials = credentials,
        oauth = oauth,
        innerTube = innerTube,
        store = store,
        clock = { clock.now },
        deviceId = "0011223344556677",
    )

    @Test fun `restores a stored profile as signed in`() {
        store.put("account_json", """{"name":"N","email":"e","avatarUrl":"a"}""")
        assertEquals("N", (repo.state.value as AccountState.SignedIn).account.name)
    }

    @Test fun `login emits requesting waiting then success and stores tokens`() = runTest {
        oauth.pollResults.add(TokenPoll.Granted(Tokens("a1", "r1", 64001)))
        val states = repo.loginFlow().toList()
        assertEquals(LoginState.Requesting, states[0])
        assertTrue(states[1] is LoginState.Waiting)
        assertEquals(LoginState.Success, states.last())
        assertEquals("r1", store.get("refresh_token"))
        assertTrue(repo.state.value is AccountState.SignedIn)
    }

    @Test fun `denied code reports ACCESS_DENIED and writes no tokens`() = runTest {
        oauth.pollResults.add(TokenPoll.Denied)
        val states = repo.loginFlow().toList()
        assertEquals(LoginState.Error(LoginError.ACCESS_DENIED), states.last())
        assertNull(store.get("refresh_token"))
    }

    @Test fun `expired code reports EXPIRED`() = runTest {
        oauth.pollResults.add(TokenPoll.Expired)
        assertEquals(LoginState.Error(LoginError.EXPIRED), repo.loginFlow().toList().last())
        assertNull(store.get("refresh_token"))
    }

    @Test fun `history refreshes an expired token once and retries`() = runTest {
        seedSignedIn(expiresAt = clock.now - 1)
        val page = repo.history()
        assertTrue(page is AccountResult.Success)
        assertEquals(1, oauth.refreshCalls)
        assertEquals("Bearer fresh", innerTube.lastAuthorization)
    }

    @Test fun `two concurrent requests share one refresh`() = runTest {
        seedSignedIn(expiresAt = clock.now - 1)
        val a = async { repo.history() }
        val b = async { repo.history() }
        a.await(); b.await()
        assertEquals(1, oauth.refreshCalls)
    }

    @Test fun `a second 401 marks the session expired`() = runTest {
        seedSignedIn(expiresAt = clock.now + 60_000)
        innerTube.unauthorized = true
        repo.history()
        assertTrue(repo.state.value is AccountState.Expired)
    }

    @Test fun `refresh invalid_grant signs out and clears tokens`() = runTest {
        seedSignedIn(expiresAt = clock.now - 1)
        oauth.refreshFailsWithInvalidGrant = true
        val r = repo.history()
        assertEquals(AccountResult.NeedsSignIn, r)
        assertNull(store.get("refresh_token"))
        assertEquals(AccountState.SignedOut, repo.state.value)
    }

    @Test fun `sign out keeps local history untouched`() = runTest {
        seedSignedIn(expiresAt = clock.now + 60_000)
        repo.signOut()
        assertEquals(AccountState.SignedOut, repo.state.value)
        assertNull(store.get("account_json"))
    }

    @Test fun `parse empty maps to ParseEmpty`() = runTest {
        seedSignedIn(expiresAt = clock.now + 60_000)
        innerTube.videosJson = """{"contents":{}}"""
        assertEquals(AccountResult.ParseEmpty, repo.history())
    }
}
```

Fakes live in the same test file: `FakeOAuthApi` (`pollResults: ArrayDeque<TokenPoll>`, `refreshCalls`, `refreshFailsWithInvalidGrant` — when set, `refresh` throws `InvalidGrantException`), `FakeInnerTubeService` (`videosJson`, `subscriptionsJson`, `profileJson` loaded from the spy fixtures; `unauthorized`, `lastAuthorization`), `FakeClock(var now: Long)`, `InMemorySecretStore`, and `seedSignedIn(expiresAt)` writing `refresh_token`, `expires_at`, `account_json`.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :data:testDebugUnitTest --tests "com.example.newtube.account.AccountRepositoryImplTest" --console=plain`
Expected: FAIL — unresolved types.

- [ ] **Step 3: Implement the repository and bindings**

Add `implementation("androidx.security:security-crypto:1.1.0-alpha06")`. `EncryptedPrefsSecretStore` uses `MasterKey` AES256_GCM + file `account_tokens`; provide it from `@ApplicationContext Context`. Repository keys: `access_token`, `refresh_token`, `expires_at`, `device_id`, `client_id`, `client_secret`, `client_fetched_at`, `account_json`. `account_json` is serialized with `buildJsonObject`/`JsonObject` by hand — `:domain` has no serialization compiler plugin, so do **not** add `@Serializable` to `UserAccount` or add the plugin. `device_id` is a random 16-digit string generated once and kept in the store. `loginFlow()` is cold: Requesting → credentials → device code → Waiting → poll at `interval`, `slow_down` adds 5 s, network/5xx backs off up to 30 s until `expiresAtMs`, terminal errors emit `Error`; success writes tokens, fetches `accounts_list` via `InnerTubeService.fetchProfile`, parses, publishes `SignedIn`, emits `Success`. A `Mutex` guards refresh; after acquiring it, re-read `expires_at` and skip if another coroutine already refreshed (this is what makes the concurrent test pass), set `expires_at` from `now + expires_in`, keep the old refresh token when the response has none, and `invalid_grant` clears the store and sets `SignedOut`. `history`/`subscriptions`/`notifications` call `ensureToken()` then the InnerTube service, map 401 to one refresh + retry, 403/401-after-retry to `NeedsSignIn` + `Expired`, IO/timeout to `Network`, 5xx to `Server`, zero items to `ParseEmpty`. Restore state in `init` from `account_json`. Bind everything in `AccountModule` (`@Binds` for `SecretStore`, `AccountRepository`; `@Provides` for the token client as `@Named("oauth") OkHttpClient` with no logger, the InnerTube client as `@Named("innertube") OkHttpClient` with BASIC + `redactHeader("Authorization")` in debug only, `OAuthApi`, `InnerTubeService`, `ClientCredentialsProvider`). Add app backup rules excluding `account_tokens`.

- [ ] **Step 4: Run test to verify it passes**

Run: same command as Step 2. Expected: PASS (10 tests).

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/example/newtube data/src/main app/src/main
git commit -m "feat: add encrypted account storage and the login/refresh state machine"
```

---

### Task 5: Login screen

**Files:**
- Create: `app/src/main/java/com/example/newtube/ui/login/LoginViewModel.kt`
- Create: `app/src/main/java/com/example/newtube/ui/login/LoginScreen.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/nav/NavGraph.kt`, `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/example/newtube/ui/login/LoginViewModelTest.kt`

**Interfaces:**
- Consumes: `AccountRepository.loginFlow(): Flow<LoginState>`.
- Produces: route `"login"`; `LoginViewModel(repository: AccountRepository)` with `val state: StateFlow<LoginState>`, `fun start()`, `fun cancel()`.

- [ ] **Step 1: Write the failing test**

```kotlin
class LoginViewModelTest {
    private val repo = mockk<AccountRepository>(relaxed = true)

    @Test fun `start exposes the waiting code from the repository`() = runTest {
        every { repo.loginFlow() } returns flowOf(
            LoginState.Waiting("SPPX-SFWV", "https://www.google.com/device", 1_800_000L),
        )
        val vm = LoginViewModel(repo)
        vm.start()
        advanceUntilIdle()
        assertEquals("SPPX-SFWV", (vm.state.value as LoginState.Waiting).userCode)
    }

    @Test fun `cancel cancels the collecting job`() = runTest {
        var collectorFinished = false
        every { repo.loginFlow() } returns
            flow {
                try {
                    emit(LoginState.Requesting)
                    awaitCancellation()
                } finally {
                    collectorFinished = true
                }
            }
        val vm = LoginViewModel(repo)
        vm.start()
        vm.cancel()
        assertTrue(collectorFinished)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.ui.login.LoginViewModelTest" --console=plain`
Expected: FAIL — unresolved `LoginViewModel`.

- [ ] **Step 3: Implement ViewModel and screen**

ViewModel collects `loginFlow()` into a `MutableStateFlow<LoginState>` on `start()`, cancels in `onCleared`. Screen states per §17.1: spinner + "Отримуємо код…"; Waiting → monospace `userCode`, the instruction, countdown from `expiresAtMs`, buttons "Відкрити сайт" (`ACTION_VIEW`, `FLAG_ACTIVITY_NEW_TASK`), "Копіювати код" (`ClipboardManager` + snackbar "Код скопійовано"), "Скасувати", subtle "Перевіряємо…"; Success → "Готово" and `popBackStack()`; Error → Ukrainian message + "Спробувати ще" → `start()`. Add the strings listed in Task 10's copy block (create them here). Add `composable("login")` to `NavGraph`.

- [ ] **Step 4: Run test to verify it passes**

Run: same command as Step 2. Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/newtube/ui/login app/src/main/java/com/example/newtube/ui/nav app/src/main/res/values/strings.xml
git commit -m "feat: add the Google device-code login screen"
```

---

### Task 6: You tab account state, sign-out and Settings account row

**Files:**
- Create: `app/src/main/java/com/example/newtube/ui/you/YouViewModel.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/you/YouScreen.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/settings/SettingsViewModel.kt`, `SettingsScreen.kt`, `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/com/example/newtube/ui/settings/SettingsViewModelTest.kt` (constructor gains `AccountRepository`; add an account-state test)
- Test: `app/src/test/java/com/example/newtube/ui/you/YouViewModelTest.kt`

**Interfaces:**
- Consumes: `AccountRepository.state`, `signOut()`.
- Produces: `YouViewModel(accountRepository: AccountRepository)` with `val account: StateFlow<AccountState> = accountRepository.state`, `fun signOut()`; Settings `account: StateFlow<AccountState>` (collected from the same repository).

- [ ] **Step 1: Write the failing test**

```kotlin
class YouViewModelTest {
    private val account = mockk<AccountRepository>(relaxed = true)

    @Test fun `signed out exposes SignedOut`() = runTest {
        every { account.state } returns MutableStateFlow(AccountState.SignedOut)
        val vm = YouViewModel(account)
        assertEquals(AccountState.SignedOut, vm.account.value)
    }

    @Test fun `signed in exposes the profile`() = runTest {
        val user = UserAccount("N", "e", "a")
        every { account.state } returns MutableStateFlow(AccountState.SignedIn(user))
        val vm = YouViewModel(account)
        assertEquals(user, (vm.account.value as AccountState.SignedIn).account)
    }

    @Test fun `signOut delegates to the repository`() = runTest {
        every { account.state } returns MutableStateFlow(AccountState.SignedOut)
        val vm = YouViewModel(account)
        vm.signOut()
        coVerify(exactly = 1) { account.signOut() }
    }
}
```

Also update `SettingsViewModelTest`: add `private val account: AccountRepository = mockk(relaxed = true)`, `every { account.state } returns MutableStateFlow(AccountState.SignedOut)` in `setUp`, pass it as the fourth `viewModel()` argument, and add `settings exposes the account state` asserting `viewModel().account.value == AccountState.SignedOut`.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.ui.you.YouViewModelTest" --console=plain`
Expected: FAIL — unresolved `YouViewModel`.

- [ ] **Step 3: Implement**

You tab per §14: signed out → generic avatar row "Увійти в Google" / "Історія та підписки" opening `login`; signed in → 48 dp avatar, name, email, and a "Вийти" row with a confirmation dialog ("Вийти з акаунта?" / "Історія переглядів у застосунку залишиться."). Keep Історія, Підписки, Налаштування, Про застосунок. Settings gains an **Акаунт** section: signed out → "Увійти в Google" → `login`; signed in → name/email + destructive "Вийти" with the same dialog. `use_remote_history` stays disabled with its subtitle when signed out.

- [ ] **Step 4: Run test to verify it passes**

Run: same command as Step 2. Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/newtube/ui/you app/src/main/java/com/example/newtube/ui/settings app/src/main/res/values/strings.xml app/src/test
git commit -m "feat: show the signed-in account in You and Settings with sign-out"
```

---

### Task 7: Subscriptions tab

**Files:**
- Create: `app/src/main/java/com/example/newtube/ui/subscriptions/SubscriptionsViewModel.kt`
- Create: `app/src/main/java/com/example/newtube/ui/common/RemoteVideoCard.kt`
- Rewrite: `app/src/main/java/com/example/newtube/ui/subscriptions/SubscriptionsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/example/newtube/ui/subscriptions/SubscriptionsViewModelTest.kt`

**Interfaces:**
- Consumes: `AccountRepository.state`, `subscriptions(continuation)`.
- Produces: `SubscriptionsUiState(signedOut: Boolean, items: List<RemoteVideo>, loading: Boolean, loadingMore: Boolean, error: Boolean, expired: Boolean, hasMore: Boolean)`; `SubscriptionsViewModel(accountRepository)` with `uiState: StateFlow<SubscriptionsUiState>`, `fun load()`, `fun loadMore()`, `fun retry()`; `RemoteVideoCard(video: RemoteVideo, onClick: (String) -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Write the failing test**

```kotlin
class SubscriptionsViewModelTest {
    private val account = mockk<AccountRepository>(relaxed = true)
    private val vm by lazy { SubscriptionsViewModel(account) }
    private val signedIn = AccountState.SignedIn(UserAccount("N", "e", "a"))

    private fun video(id: String) =
        RemoteVideo(id, "Video $id", "Channel", "UC1", "thumb", 60_000, "1.2M views", "1 day ago", false)

    @Test fun `signed out produces the CTA state`() = runTest {
        every { account.state } returns MutableStateFlow(AccountState.SignedOut)
        vm.load(); advanceUntilIdle()
        assertTrue(vm.uiState.value.signedOut)
    }

    @Test fun `first page fills the grid`() = runTest {
        every { account.state } returns MutableStateFlow(signedIn)
        coEvery { account.subscriptions(null) } returns
            AccountResult.Success(AccountPage(listOf(video("a")), "c2"))
        vm.load(); advanceUntilIdle()
        assertEquals(1, vm.uiState.value.items.size)
        assertTrue(vm.uiState.value.hasMore)
    }

    @Test fun `loadMore appends without duplicates`() = runTest {
        every { account.state } returns MutableStateFlow(signedIn)
        coEvery { account.subscriptions(null) } returns AccountResult.Success(
            AccountPage(listOf(video("a"), video("b")), "c2"),
        )
        coEvery { account.subscriptions("c2") } returns AccountResult.Success(
            AccountPage(listOf(video("b"), video("c")), null),
        )
        vm.load(); advanceUntilIdle()
        vm.loadMore(); advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), vm.uiState.value.items.map { it.id })
    }

    @Test fun `needs sign in shows the expired banner`() = runTest {
        every { account.state } returns MutableStateFlow(AccountState.Expired(null))
        coEvery { account.subscriptions(null) } returns AccountResult.NeedsSignIn
        vm.load(); advanceUntilIdle()
        assertTrue(vm.uiState.value.expired)
    }

    @Test fun `network error exposes retry`() = runTest {
        every { account.state } returns MutableStateFlow(signedIn)
        coEvery { account.subscriptions(null) } returns AccountResult.Network
        vm.load(); advanceUntilIdle()
        assertTrue(vm.uiState.value.error)
        assertTrue(vm.uiState.value.items.isEmpty())
    }

    @Test fun `retry loads again after an error`() = runTest {
        every { account.state } returns MutableStateFlow(signedIn)
        coEvery { account.subscriptions(null) } returnsMany listOf(
            AccountResult.Network,
            AccountResult.Success(AccountPage(listOf(video("a")), null)),
        )
        vm.load(); advanceUntilIdle()
        assertTrue(vm.uiState.value.error)
        vm.retry(); advanceUntilIdle()
        assertEquals(1, vm.uiState.value.items.size)
        assertFalse(vm.uiState.value.error)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.ui.subscriptions.SubscriptionsViewModelTest" --console=plain`
Expected: FAIL — unresolved `SubscriptionsViewModel`.

- [ ] **Step 3: Implement**

`SubscriptionsViewModel(accountRepository: AccountRepository)` holds a `MutableStateFlow(SubscriptionsUiState())`; `load()` reads `accountRepository.state.value` and launches the request in `viewModelScope`. `loadMore()` no-ops unless a continuation exists and nothing else is in flight; dedupe by `id`. Signed out: full-screen CTA (icon, "Увійдіть у Google, щоб бачити нові відео з підписок", "Увійти" → `login`, secondary text). Signed in: `LazyVerticalGrid(GridCells.Fixed(2))` of `RemoteVideoCard` — 16:9 thumbnail, duration badge (hidden when `durationMs == 0`), title 2 lines, channel, `viewsText` + `publishedText` (raw strings, never parsed); tap → `watch/{id}`; append via continuation when the last item becomes visible. Loading → 6 skeleton cells. Empty → "Немає нових відео з підписок". Error → message + "Повторити". Expired → inline banner "Сесія завершилася" + "Увійти знову" → `login`. Do not cache locally.

> **Spike correction (important):** the TV `FEsubscriptions` response is a **feed of recent videos**, not a channel grid. The 2026-11-20 capture contains 29 video tiles from 11 distinct channels (`gridChannelRenderer` never appears). So this tab shows videos from subscriptions, matching the YouTube TV app; the earlier channel-grid wording in spec §13 is superseded.

- [ ] **Step 4: Run test to verify it passes**

Run: same command as Step 2. Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/newtube/ui/subscriptions app/src/main/res/values/strings.xml app/src/test
git commit -m "feat: load the account's subscriptions into a paginated grid"
```

---

### Task 8: Remote history in the History screen

**Files:**
- Modify: `app/src/main/java/com/example/newtube/ui/history/HistoryViewModel.kt`, `HistoryScreen.kt`, `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/example/newtube/ui/history/HistoryViewModelTest.kt` (extend)

**Interfaces:**
- Consumes: `AccountRepository.state`, `history(continuation)`, `HistoryRepository.observeLocal`, `SettingsRepository.settings`.
- Produces: `HistoryUiState(local: List<WatchHistoryEntry>, remote: List<RemoteVideo>, remoteSource: Boolean, loadingMore: Boolean, expired: Boolean, error: Boolean, hasMore: Boolean)`; `HistoryViewModel` with `uiState`, `loadMore()`, `retry()`.

- [ ] **Step 1: Write the failing test**

```kotlin
class HistoryViewModelTest {
    private val local: HistoryRepository = mockk(relaxed = true)
    private val account: AccountRepository = mockk(relaxed = true)
    private val settings: SettingsRepository = mockk(relaxed = true)
    private val entry = WatchHistoryEntry("v1", "T", "C", "UC1", "", "", 60_000, 1)
    private val remote = RemoteVideo("v2", "R", "Ch", "UC2", "thumb", 60_000, "1.2M views", "1 day ago", false)
    private val signedIn = AccountState.SignedIn(UserAccount("N", "e", "a"))

    @Before fun setUp() {
        every { local.observeLocal() } returns flowOf(listOf(entry))
        every { settings.settings } returns flowOf(AppSettings())
    }

    @Test fun `signed out uses local history`() = runTest {
        every { account.state } returns MutableStateFlow(AccountState.SignedOut)
        val vm = HistoryViewModel(local, account, settings)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.remoteSource)
        assertEquals(1, vm.uiState.value.local.size)
    }

    @Test fun `signed in with remote history off uses local history`() = runTest {
        every { account.state } returns MutableStateFlow(signedIn)
        every { settings.settings } returns flowOf(AppSettings(useRemoteHistory = false))
        val vm = HistoryViewModel(local, account, settings)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.remoteSource)
    }

    @Test fun `signed in with remote history on loads the first page`() = runTest {
        every { account.state } returns MutableStateFlow(signedIn)
        coEvery { account.history(null) } returns AccountResult.Success(AccountPage(listOf(remote), null))
        val vm = HistoryViewModel(local, account, settings)
        vm.retry(); advanceUntilIdle()
        assertTrue(vm.uiState.value.remoteSource)
        assertEquals(listOf("v2"), vm.uiState.value.remote.map { it.id })
    }

    @Test fun `remote history failure falls back to local with the banner`() = runTest {
        every { account.state } returns MutableStateFlow(AccountState.Expired(UserAccount("N", "e", "a")))
        coEvery { account.history(null) } returns AccountResult.NeedsSignIn
        val vm = HistoryViewModel(local, account, settings)
        vm.retry(); advanceUntilIdle()
        assertFalse(vm.uiState.value.remoteSource)
        assertEquals(1, vm.uiState.value.local.size)
        assertTrue(vm.uiState.value.expired)
    }

    @Test fun `remote history is not written to Room`() = runTest {
        every { account.state } returns MutableStateFlow(signedIn)
        coEvery { account.history(null) } returns AccountResult.Success(AccountPage(listOf(remote), null))
        val vm = HistoryViewModel(local, account, settings)
        vm.retry(); advanceUntilIdle()
        coVerify(exactly = 0) { local.record(any()) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.ui.history.HistoryViewModelTest" --console=plain`
Expected: FAIL — new constructor params / `HistoryUiState` unresolved.

- [ ] **Step 3: Implement**

Choose the source per §15.1. Keep a `MutableStateFlow<HistoryUiState>` in the ViewModel and collect `combine(historyRepository.observeLocal(), accountRepository.state, settingsRepository.settings)` in `init`, so tests can read `uiState.value` deterministically. Remote rows: 160×90 thumbnail, title 2 lines, channel, `viewsText` + `publishedText` (raw, never parsed into `Long`), tap → `watch/{id}`. Subtitle "Історія YouTube" when remote. Paginate with continuation, appending with id-dedupe (the init load and a manual retry can overlap). On `NeedsSignIn`/`Expired` show the local list plus an inline banner with "Увійти знову". Local rows keep the existing renderer. Remote rows never call `HistoryRepository.record`. The existing `HistoryViewModelTest` is replaced by the new class body above (the old local-only assertions are folded into the signed-out case).

- [ ] **Step 4: Run test to verify it passes**

Run: same command as Step 2. Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/newtube/ui/history app/src/main/res/values/strings.xml app/src/test
git commit -m "feat: show YouTube history in the History screen when signed in"
```

---

### Task 9: Notifications screen

**Files:**
- Create: `app/src/main/java/com/example/newtube/ui/notifications/NotificationsViewModel.kt`
- Rewrite: `app/src/main/java/com/example/newtube/ui/notifications/NotificationsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/example/newtube/ui/notifications/NotificationsViewModelTest.kt`

**Interfaces:**
- Consumes: `AccountRepository.state`, `notifications()`.
- Produces: `NotificationsUiState(signedOut: Boolean, items: List<RemoteVideo>, loading: Boolean, error: Boolean)`; `NotificationsViewModel` with `uiState`, `load()`, `retry()`.

- [ ] **Step 1: Write the failing test**

```kotlin
class NotificationsViewModelTest {
    private val account = mockk<AccountRepository>(relaxed = true)
    private val vm by lazy { NotificationsViewModel(account) }
    private val item = RemoteVideo("v1", "T", "C", "UC1", "", 0, "", "1 day ago", false)
    private val signedIn = AccountState.SignedIn(UserAccount("N", "e", "a"))

    @Test fun `signed out shows the CTA`() = runTest {
        every { account.state } returns MutableStateFlow(AccountState.SignedOut)
        vm.load(); advanceUntilIdle()
        assertTrue(vm.uiState.value.signedOut)
    }

    @Test fun `items render from one request`() = runTest {
        every { account.state } returns MutableStateFlow(signedIn)
        coEvery { account.notifications() } returns AccountResult.Success(listOf(item))
        vm.load(); advanceUntilIdle()
        assertEquals(1, vm.uiState.value.items.size)
        coVerify(exactly = 1) { account.notifications() }
    }

    @Test fun `parse empty becomes the empty state`() = runTest {
        every { account.state } returns MutableStateFlow(signedIn)
        coEvery { account.notifications() } returns AccountResult.ParseEmpty
        vm.load(); advanceUntilIdle()
        assertTrue(vm.uiState.value.items.isEmpty())
        assertFalse(vm.uiState.value.error)
    }

    @Test fun `network failure exposes retry`() = runTest {
        every { account.state } returns MutableStateFlow(signedIn)
        coEvery { account.notifications() } returns AccountResult.Network
        vm.load(); advanceUntilIdle()
        assertTrue(vm.uiState.value.error)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.newtube.ui.notifications.NotificationsViewModelTest" --console=plain`
Expected: FAIL — unresolved `NotificationsViewModel`.

- [ ] **Step 3: Implement**

`NotificationsViewModel(accountRepository: AccountRepository)` holds a `MutableStateFlow(NotificationsUiState())`; `load()` reads `accountRepository.state.value` and makes one `notifications()` request in `viewModelScope`. Signed out → the Subscriptions CTA. Signed in → one `FEnotifications` request, rows with optional thumbnail, title, channel, published text; a row with a video id opens Watch, others are non-clickable. Empty/unparsable → "Немає нових сповіщень". No pagination, no badges. If the endpoint proves unusable on device, keep the empty state and record the limitation in the ledger — it must not block the release.

- [ ] **Step 4: Run test to verify it passes**

Run: same command as Step 2. Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/newtube/ui/notifications app/src/main/res/values/strings.xml app/src/test
git commit -m "feat: add best-effort YouTube notifications"
```

---

### Task 10: Strings, docs and full verification

**Files:**
- Modify: `app/src/main/res/values/strings.xml`, `README.md`, `SPEC.md`, `TASKS.md`, `app/src/main/AndroidManifest.xml`
- Modify: `.superpowers/sdd/2026-11-12-youtube-home-ui-and-local-history/progress.md` (new Plan 2 workspace ledger entry)

**Interfaces:**
- Consumes: all previous tasks.
- Produces: shippable build.

Copy block (add only missing keys, keep the existing style):

```
login_title=Вхід у Google
login_requesting=Отримуємо код…
login_waiting_hint=Відкрийте google.com/device на телефоні або комп'ютері та введіть код
login_open_site=Відкрити сайт
login_copy_code=Копіювати код
login_copied=Код скопійовано
login_checking=Перевіряємо…
login_expires_in=Код дійсний ще %1$s
login_error_denied=Доступ відхилено
login_error_expired=Код прострочено. Спробуйте ще раз
login_error_network=Немає з’єднання. Перевірте мережу
login_retry=Спробувати ще
account_sign_in=Увійти в Google
account_sign_out=Вийти
account_sign_out_title=Вийти з акаунта?
account_sign_out_body=Історія переглядів у застосунку залишиться.
settings_account=Акаунт
subscriptions_sign_in_title=Увійдіть у Google, щоб бачити нові відео з підписок
subscriptions_sign_in_body=Вхід використовується лише для даних акаунта.
subscriptions_empty=Немає нових відео з підписок
session_expired=Сесія завершилася
session_expired_action=Увійти знову
history_remote_subtitle=Історія YouTube
notifications_empty=Немає нових сповіщень
```

- [ ] **Step 1: Update the docs**

README: replace the "not implemented" account line with the signed-in feature list and note read-only behaviour. SPEC: screen table rows for Вхід/Підписки/Історія YouTube/Сповіщення/Акаунт, testing counts, limitations (TV OAuth can be revoked, notifications best effort, subscriptions is a recent-video feed because the TV endpoint returns no channel list). TASKS: mark the Phase 4–5 rows, add the Phase 6 checklist.

- [ ] **Step 2: Full verification**

Run, each separately:
1. `.\gradlew.bat ktlintFormat`
2. `.\gradlew.bat ktlintCheck`
3. `.\gradlew.bat test`
4. `.\gradlew.bat :app:assembleDebug`
Expected: all green; test count grows by the tasks above.

- [ ] **Step 3: Device checklist on the TCL 6102H**

Signed out: You/Settings/Subscriptions CTAs open Login and there are no dead buttons. Login shows a real code, the countdown and "Відкрити сайт". After approval the You tab shows name/email/avatar; Subscriptions shows the real recent-video feed; History shows "Історія YouTube" (including Shorts); Notifications shows rows or "Немає нових сповіщень". Sign out: You returns to guest, local history is still there, no remote rows leak into it. Airplane mode during login → network error + retry. Force-stop after sign-in → still signed in. Rotate and PiP do not crash.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "docs: document Google sign-in and verify the account release"
```

---

## Assumptions to confirm during execution

- `security-crypto:1.1.0-alpha06` works on the Android 12 TCL; if `EncryptedSharedPreferences` throws, fall back to a Keystore AES-GCM blob in DataStore and record the deviation.
- `FEnotifications` may be empty/unparsable; Task 9 ships the empty state in that case.
- The device-code fixtures are hand-written from the documented payloads; MockWebServer only needs their shape.
