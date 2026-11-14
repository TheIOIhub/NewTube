# Home Categories + Real Shorts Sources — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fake `#shorts` search feed with real vertical Shorts from discovered shorts channels, and give Home a category chip row backed by a large multi-source shuffled pool.

**Architecture:** Domain gains a `HomeCategory` enum and `VideoRepository.getHomeFeed(category)`. The data layer composes feeds from declarative sources (trending / popular / search) fetched in parallel, then deduplicates and shuffles. Shorts are built from `/channels/{id}/shorts` of randomly discovered channels with a curated fallback. UI adds Material 3 chips on Home and a refresh button on Shorts.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Retrofit + Moshi, Room, ExoPlayer 2.19, MockK + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-11-08-home-categories-shorts-sources-design.md`

## Global Constraints

* All user-visible strings are English.
* No new dependencies; no server-side changes; base URL stays in `BuildConfig`.
* Repository network work runs on `Dispatchers.IO`; UI never blocks.
* Tests are JVM unit tests with MockK; `testOptions { unitTests.isReturnDefaultValues = true }` is already set.
* `ktlintFormat` and `ktlintCheck` must run in **separate** Gradle invocations.
* Feed order is random by design: tests assert **sets** of ids, never list order.
* Shorts duration window: `1..180` seconds (inclusive), `liveNow == false`.
* Home feed cap: 60 items; Shorts page cap: 24 items; 3 channels per Shorts page.
* Reference today's existing conventions: `VideoRepositoryImpl` mapping helpers, `UiState`, `toUserMessage()`.

## Review Focus

* All feed sources fail → screen shows Error + Retry, never a blank/endless spinner.
* Same video id returned by trending, popular and search → appears exactly once.
* Shorts page containing only already-shown ids → pager keeps the list and retries; it must not mark the feed as ended.
* Shorts of 61–180 s are included; 181 s and live items are excluded.
* Switching Home category while an older request is in flight → the stale response must not replace the newly selected category's feed.
* A single channel request returning 500 → the other channels still deliver a page.

---

### Task 1: Domain + remote + Home feed repository

**Files:**
- Create: `domain/src/main/java/com/example/newtube/model/HomeCategory.kt`
- Modify: `domain/src/main/java/com/example/newtube/repository/VideoRepository.kt`
- Create: `data/src/main/java/com/example/newtube/remote/ChannelSearchDto.kt`
- Modify: `data/src/main/java/com/example/newtube/remote/InvidiousService.kt`
- Modify: `data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt`
- Test: `app/src/test/java/com/example/newtube/repository/VideoRepositoryImplTest.kt`

**Interfaces:**
- Produces: `enum class HomeCategory(val label: String)` with `ALL, MUSIC, GAMING, MOVIES, SPORTS, NEWS, TECHNOLOGY`.
- Produces: `suspend fun VideoRepository.getHomeFeed(category: HomeCategory): List<Video>`.
- Produces: `InvidiousService.getPopular(): List<VideoDto>` and `searchChannels(query: String, type: String = "channel", page: Int = 1): List<ChannelSearchDto>`.
- Consumes: existing `VideoDto.toEntityOrNull()`, `VideoEntity.toDomain()`, `dao.insertAll`.

- [ ] **Step 1: Create the domain enum**

```kotlin
package com.example.newtube.model

/** Top-level feed tabs shown on the Home screen. */
enum class HomeCategory(val label: String) {
    ALL("All"),
    MUSIC("Music"),
    GAMING("Gaming"),
    MOVIES("Movies"),
    SPORTS("Sports"),
    NEWS("News"),
    TECHNOLOGY("Technology"),
}
```

- [ ] **Step 2: Add `getHomeFeed` to the repository interface**

```kotlin
/** Mixed feed for a Home category; sources are shuffled on every call. */
suspend fun getHomeFeed(category: HomeCategory): List<Video>
```

- [ ] **Step 3: Add the channel-search DTO and the two Retrofit endpoints**

`ChannelSearchDto`:

```kotlin
@JsonClass(generateAdapter = true)
data class ChannelSearchDto(
    val authorId: String,
    val author: String = "",
    val subCount: Long = 0,
    val description: String = "",
)
```

`InvidiousService` additions:

```kotlin
@GET("api/v1/popular")
suspend fun getPopular(): List<VideoDto>

@GET("api/v1/search")
suspend fun searchChannels(
    @Query("q") query: String,
    @Query("type") type: String = "channel",
    @Query("page") page: Int = 1,
): List<ChannelSearchDto>
```

- [ ] **Step 4: Write the failing Home feed tests**

Add to `VideoRepositoryImplTest` (imports: `HomeCategory`, `ChannelSearchDto`):

```kotlin
@Test
fun `getHomeFeed ALL merges every source and removes duplicates`() = runBlocking {
    coEvery { service.getTrending("US", "Default") } returns listOf(videoDto("1"), videoDto("2"))
    coEvery { service.getTrending("US", "Gaming") } returns listOf(videoDto("2"), videoDto("3"))
    coEvery { service.getPopular() } returns listOf(videoDto("3"), videoDto("4"))
    coEvery { service.searchVideos(any(), any(), any()) } returns listOf(videoDto("4"), videoDto("5"))
    coJustRun { dao.insertAll(any()) }

    val feed = repository.getHomeFeed(HomeCategory.ALL)

    assertEquals(setOf("1", "2", "3", "4", "5"), feed.map { it.id }.toSet())
    assertEquals(feed.size, feed.map { it.id }.distinct().size)
}

@Test
fun `getHomeFeed keeps the feed when one source fails`() = runBlocking {
    coEvery { service.getTrending("US", "Default") } throws IOException("boom")
    coEvery { service.getTrending("US", "Gaming") } returns listOf(videoDto("2"))
    coEvery { service.getPopular() } returns listOf(videoDto("3"))
    coEvery { service.searchVideos(any(), any(), any()) } throws IOException("boom")
    coJustRun { dao.insertAll(any()) }

    val feed = repository.getHomeFeed(HomeCategory.ALL)

    assertEquals(setOf("2", "3"), feed.map { it.id }.toSet())
}

@Test
fun `getHomeFeed fails only when every source fails`() = runBlocking {
    coEvery { service.getTrending(any(), any()) } throws IOException("down")
    coEvery { service.getPopular() } throws IOException("down")
    coEvery { service.searchVideos(any(), any(), any()) } throws IOException("down")

    val error = runCatching { repository.getHomeFeed(HomeCategory.MUSIC) }.exceptionOrNull()

    assertTrue(error is IOException)
}

@Test
fun `getHomeFeed topic category prefixes its sources`() = runBlocking {
    coEvery { service.getTrending("US", "Gaming") } returns listOf(videoDto("g1"))
    coEvery { service.searchVideos(any(), any(), any()) } returns listOf(videoDto("m1"))
    coJustRun { dao.insertAll(any()) }

    val feed = repository.getHomeFeed(HomeCategory.GAMING)

    assertEquals(setOf("g1", "m1"), feed.map { it.id }.toSet())
    coVerify { service.getTrending("US", "Gaming") }
}
```

Add `import java.io.IOException` and `import io.mockk.coVerify` to the test file.

- [ ] **Step 5: Run the new tests and confirm they fail**

Run: `cmd /c "gradlew.bat :app:testDebugUnitTest --tests *VideoRepositoryImplTest* --console=plain > tdd-log.txt 2>&1"`
Expected: FAIL — `getHomeFeed` unresolved / tests failing.

- [ ] **Step 6: Implement the feed composer in `VideoRepositoryImpl`**

Add imports: `HomeCategory`, `kotlinx.coroutines.async`, `kotlinx.coroutines.awaitAll`, `kotlinx.coroutines.coroutineScope`.

```kotlin
private sealed interface FeedSource {
    data class Trending(val type: String) : FeedSource
    data object Popular : FeedSource
    data class Search(val query: String) : FeedSource
}

override suspend fun getHomeFeed(category: HomeCategory): List<Video> = withContext(Dispatchers.IO) {
    val sources = category.feedSources()
    val results = coroutineScope {
        sources.map { source -> async { source.load() } }.awaitAll()
    }
    if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
    val videos = results.mapNotNull { it.getOrNull() }.flatten()
    videos.toDomainVideos().distinctBy { it.id }.shuffled().take(HOME_FEED_LIMIT)
}

private suspend fun FeedSource.load(): Result<List<VideoDto>> = runCatching {
    when (this) {
        is FeedSource.Trending -> service.getTrending(type = type)
        FeedSource.Popular -> service.getPopular()
        is FeedSource.Search -> service.searchVideos(query)
    }
}

private fun HomeCategory.feedSources(): List<FeedSource> = when (this) {
    HomeCategory.ALL -> {
        val topics = ALL_TOPIC_QUERIES.shuffled().take(2).map(FeedSource::Search)
        listOf(FeedSource.Trending("Default"), FeedSource.Trending("Gaming"), FeedSource.Popular) + topics
    }
    HomeCategory.MUSIC -> pickQueries(MUSIC_QUERIES)
    HomeCategory.GAMING -> listOf(FeedSource.Trending("Gaming")) + pickQueries(GAMING_QUERIES)
    HomeCategory.MOVIES -> pickQueries(MOVIE_QUERIES)
    HomeCategory.SPORTS -> pickQueries(SPORTS_QUERIES)
    HomeCategory.NEWS -> pickQueries(NEWS_QUERIES)
    HomeCategory.TECHNOLOGY -> pickQueries(TECH_QUERIES)
}

private fun pickQueries(pool: List<String>): List<FeedSource> =
    pool.shuffled().take(TOPIC_QUERY_COUNT).map(FeedSource::Search)
```

Constants (companion object or private top-level file constants):

```kotlin
private const val HOME_FEED_LIMIT = 60
private const val TOPIC_QUERY_COUNT = 3
private val MUSIC_QUERIES = listOf("music video", "new song", "official video", "live music")
private val GAMING_QUERIES = listOf("gaming highlights", "gameplay", "esports highlights", "new game")
private val MOVIE_QUERIES = listOf("movie trailer", "movie review", "new movie", "film trailer")
private val SPORTS_QUERIES = listOf("sports highlights", "football highlights", "nba highlights", "tennis highlights")
private val NEWS_QUERIES = listOf("news today", "world news", "breaking news", "daily news")
private val TECH_QUERIES = listOf("technology review", "tech news", "gadgets", "science technology")
private val ALL_TOPIC_QUERIES = MUSIC_QUERIES + GAMING_QUERIES + MOVIE_QUERIES + SPORTS_QUERIES + NEWS_QUERIES + TECH_QUERIES
```

- [ ] **Step 7: Run the repository tests until green**

Run: `cmd /c "gradlew.bat :app:testDebugUnitTest --tests *VideoRepositoryImplTest* --console=plain > tdd-log.txt 2>&1"`
Expected: BUILD SUCCESSFUL; every `VideoRepositoryImplTest` passes.

- [ ] **Step 8: Format and commit**

```bash
gradlew.bat ktlintFormat
git add domain/src/main/java/com/example/newtube/model/HomeCategory.kt domain/src/main/java/com/example/newtube/repository/VideoRepository.kt data/src/main/java/com/example/newtube/remote/ChannelSearchDto.kt data/src/main/java/com/example/newtube/remote/InvidiousService.kt data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt app/src/test/java/com/example/newtube/repository/VideoRepositoryImplTest.kt
git commit -m "feat: aggregate Home feed from multiple Invidious sources"
```

---

### Task 2: Real Shorts repository

**Files:**
- Modify: `data/src/main/java/com/example/newtube/remote/InvidiousService.kt`
- Modify: `data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt`
- Test: `app/src/test/java/com/example/newtube/repository/VideoRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `searchChannels(query, type = "channel", page = 1)` from Task 1.
- Produces: `InvidiousService.getChannelShorts(channelId: String, continuation: String? = null): ChannelVideosDto`.
- Produces: `getShorts(page: Int)` returns only `1..180` s, non-live, deduplicated clips.

- [ ] **Step 1: Add the channel-shorts endpoint**

```kotlin
@GET("api/v1/channels/{channelId}/shorts")
suspend fun getChannelShorts(
    @Path("channelId") channelId: String,
    @Query("continuation") continuation: String? = null,
): ChannelVideosDto
```

- [ ] **Step 2: Write the failing Shorts tests**

```kotlin
@Test
fun `getShorts uses discovered channels and keeps 1 to 180 seconds`() = runBlocking {
    coEvery { service.searchChannels(any(), any(), any()) } returns
        listOf(ChannelSearchDto(authorId = "UCa"), ChannelSearchDto(authorId = "UCb"))
    coEvery { service.getChannelShorts("UCa", null) } returns
        ChannelVideosDto(videos = listOf(videoDto("short1", length = 59), videoDto("long", length = 181), videoDto("live").copy(liveNow = true)))
    coEvery { service.getChannelShorts("UCb", null) } returns
        ChannelVideosDto(videos = listOf(videoDto("short2", length = 90), videoDto("short1", length = 59)))
    coJustRun { dao.insertAll(any()) }

    val shorts = repository.getShorts(1)

    assertEquals(setOf("short1", "short2"), shorts.map { it.id }.toSet())
}

@Test
fun `getShorts skips a channel that fails`() = runBlocking {
    coEvery { service.searchChannels(any(), any(), any()) } returns
        listOf(ChannelSearchDto(authorId = "UCbad"), ChannelSearchDto(authorId = "UCgood"))
    coEvery { service.getChannelShorts("UCbad", null) } throws IOException("500")
    coEvery { service.getChannelShorts("UCgood", null) } returns
        ChannelVideosDto(videos = listOf(videoDto("short1", length = 30)))
    coJustRun { dao.insertAll(any()) }

    assertEquals(listOf("short1"), repository.getShorts(1).map { it.id })
}

@Test
fun `getShorts falls back to curated channels when discovery is empty`() = runBlocking {
    coEvery { service.searchChannels(any(), any(), any()) } returns emptyList()
    coEvery { service.getChannelShorts(any(), null) } returns
        ChannelVideosDto(videos = listOf(videoDto("fallback", length = 30)))
    coJustRun { dao.insertAll(any()) }

    assertEquals(listOf("fallback"), repository.getShorts(1).map { it.id })
    coVerify { service.getChannelShorts(any(), null) }
}

@Test
fun `getShorts throws when every channel request fails`() = runBlocking {
    coEvery { service.searchChannels(any(), any(), any()) } returns
        listOf(ChannelSearchDto(authorId = "UCa"), ChannelSearchDto(authorId = "UCb"), ChannelSearchDto(authorId = "UCc"))
    coEvery { service.getChannelShorts(any(), null) } throws IOException("500")

    val error = runCatching { repository.getShorts(1) }.exceptionOrNull()

    assertTrue(error is IOException)
}
```

`videoDto` currently accepts `(id, length)`, so `videoDto("short1", length = 59)` works.

- [ ] **Step 3: Run the tests and confirm they fail**

Run: `cmd /c "gradlew.bat :app:testDebugUnitTest --tests *VideoRepositoryImplTest* --console=plain > tdd-log.txt 2>&1"`
Expected: FAIL — new behaviour not implemented.

- [ ] **Step 4: Implement the channel-based `getShorts`**

Replace the current `getShorts` body. Add `import kotlinx.coroutines.sync.Mutex`, `withLock`, `awaitAll`, `async`, `coroutineScope`.

```kotlin
private val shortsChannelMutex = Mutex()
private val usedShortsChannels = mutableSetOf<String>()

override suspend fun getShorts(page: Int): List<Video> = withContext(Dispatchers.IO) {
    val selected = shortsChannelMutex.withLock { nextShortsChannels() }
    val results = coroutineScope {
        selected.map { channelId -> async { runCatching { service.getChannelShorts(channelId) } } }.awaitAll()
    }
    if (results.isNotEmpty() && results.all { it.isFailure }) throw results.first().exceptionOrNull()!!

    val videos = results.mapNotNull { it.getOrNull() }.flatMap { it.videos }
    videos.toDomainVideos()
        .filter { it.duration in 1..SHORTS_MAX_SECONDS && !it.isLive }
        .distinctBy { it.id }
        .shuffled()
        .take(SHORTS_PER_PAGE)
}

/** Must be called with [shortsChannelMutex] held. */
private suspend fun nextShortsChannels(): List<String> {
    val discovered = runCatching {
        coroutineScope {
            SHORTS_CHANNEL_QUERIES.shuffled().take(2).map { query ->
                async { runCatching { service.searchChannels(query).map { it.authorId } } }
            }.awaitAll().mapNotNull { it.getOrNull() }.flatten()
        }
    }.getOrDefault(emptyList())

    val unseen = (discovered + usedShortsChannels).filterNot { it in usedShortsChannels }.distinct().shuffled()
    val chosen = unseen.take(SHORTS_CHANNELS_PER_PAGE).ifEmpty {
        usedShortsChannels.clear()
        (discovered + FALLBACK_SHORTS_CHANNELS).distinct().shuffled().take(SHORTS_CHANNELS_PER_PAGE)
    }
    usedShortsChannels += chosen
    return chosen
}
```

Note: `(discovered + usedShortsChannels).filterNot { it in usedShortsChannels }` equals `discovered` minus used — keep it simpler as `discovered.distinct().filterNot { it in usedShortsChannels }.shuffled()`. Use the simpler form; also `nextShortsChannels` must fall back to `FALLBACK_SHORTS_CHANNELS` when discovery is empty even if some channels were used. Implement:

```kotlin
val fresh = discovered.distinct().filterNot { it in usedShortsChannels }.shuffled()
val chosen = fresh.take(SHORTS_CHANNELS_PER_PAGE).ifEmpty {
    usedShortsChannels.clear()
    (discovered.distinct() + FALLBACK_SHORTS_CHANNELS).distinct().shuffled().take(SHORTS_CHANNELS_PER_PAGE)
}
```

Constants:

```kotlin
private const val SHORTS_MAX_SECONDS = 180L
private const val SHORTS_CHANNELS_PER_PAGE = 3
private const val SHORTS_PER_PAGE = 24
private val SHORTS_CHANNEL_QUERIES = listOf(
    "shorts", "funny shorts", "dance shorts", "gaming shorts",
    "sports shorts", "movie shorts", "music shorts", "news shorts",
)
private val FALLBACK_SHORTS_CHANNELS = listOf(
    "UCgJ5_1F6yJhYLnyMszUdmUg", // Shorts Break
    "UCMHpfyAqrjrhn-mRS2bUL0g", // A for Adley Shorts
    "UCoi2zyVut5_hIKw4eEUji6g", // Zakiiino short
    "UCX6OQ3DkcsbYNE6H8uQQuVA", // MrBeast
)
```

- [ ] **Step 5: Run the repository tests until green**

Run: `cmd /c "gradlew.bat :app:testDebugUnitTest --tests *VideoRepositoryImplTest* --console=plain > tdd-log.txt 2>&1"`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Format and commit**

```bash
gradlew.bat ktlintFormat
git add data/src/main/java/com/example/newtube/remote/InvidiousService.kt data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt app/src/test/java/com/example/newtube/repository/VideoRepositoryImplTest.kt
git commit -m "feat: build Shorts from real vertical clips of discovered channels"
```

---

### Task 3: Home ViewModel and category chips

**Files:**
- Modify: `app/src/main/java/com/example/newtube/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/home/HomeScreen.kt`
- Test: `app/src/test/java/com/example/newtube/ui/home/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `VideoRepository.getHomeFeed(HomeCategory)` and `HomeCategory.label` from Task 1.
- Produces: `HomeViewModel.selectedCategory: StateFlow<HomeCategory>` and `selectCategory(category: HomeCategory)`.

- [ ] **Step 1: Update the ViewModel tests first**

Replace `getTrending()` mocks with `getHomeFeed(any())`. Add:

```kotlin
@Test
fun `starts on All and loads the aggregated feed`() = runTest {
    val videos = listOf(video("1"))
    coEvery { repository.getHomeFeed(HomeCategory.ALL) } returns videos

    val viewModel = HomeViewModel(repository)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(HomeCategory.ALL, viewModel.selectedCategory.value)
    assertEquals(UiState.Success(videos), viewModel.uiState.value)
}

@Test
fun `selecting a category loads it and caches the result`() = runTest {
    coEvery { repository.getHomeFeed(HomeCategory.ALL) } returns listOf(video("all"))
    coEvery { repository.getHomeFeed(HomeCategory.GAMING) } returns listOf(video("game"))

    val viewModel = HomeViewModel(repository)
    testDispatcher.scheduler.advanceUntilIdle()
    viewModel.selectCategory(HomeCategory.GAMING)
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(UiState.Success(listOf(video("game"))), viewModel.uiState.value)

    viewModel.selectCategory(HomeCategory.ALL)
    viewModel.selectCategory(HomeCategory.GAMING)
    testDispatcher.scheduler.advanceUntilIdle()

    coVerify(exactly = 1) { repository.getHomeFeed(HomeCategory.GAMING) }
}

@Test
fun `refresh re-fetches the selected category`() = runTest {
    coEvery { repository.getHomeFeed(HomeCategory.NEWS) } returns listOf(video("news"))

    val viewModel = HomeViewModel(repository)
    testDispatcher.scheduler.advanceUntilIdle()
    viewModel.selectCategory(HomeCategory.NEWS)
    testDispatcher.scheduler.advanceUntilIdle()
    viewModel.refresh()
    testDispatcher.scheduler.advanceUntilIdle()

    coVerify(exactly = 2) { repository.getHomeFeed(HomeCategory.NEWS) }
}

@Test
fun `a late response from a previous category is ignored`() = runTest {
    val never = CompletableDeferred<List<Video>>()
    coEvery { repository.getHomeFeed(HomeCategory.ALL) } coAnswers { never.await() }
    coEvery { repository.getHomeFeed(HomeCategory.SPORTS) } returns listOf(video("sport"))

    val viewModel = HomeViewModel(repository)
    viewModel.selectCategory(HomeCategory.SPORTS)
    testDispatcher.scheduler.advanceUntilIdle()
    never.complete(listOf(video("stale")))
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(UiState.Success(listOf(video("sport"))), viewModel.uiState.value)
}
```

Imports needed: `HomeCategory`, `io.mockk.coVerify`, `kotlinx.coroutines.CompletableDeferred`.

- [ ] **Step 2: Run the ViewModel tests and confirm they fail**

Run: `cmd /c "gradlew.bat :app:testDebugUnitTest --tests *HomeViewModelTest* --console=plain > tdd-log.txt 2>&1"`
Expected: FAIL — `selectedCategory`/`selectCategory` unresolved.

- [ ] **Step 3: Implement the ViewModel**

State and behaviour:

```kotlin
private val _selectedCategory = MutableStateFlow(HomeCategory.ALL)
val selectedCategory: StateFlow<HomeCategory> = _selectedCategory
private val cache = mutableMapOf<HomeCategory, List<Video>>()
private var requestToken = 0

fun selectCategory(category: HomeCategory) {
    if (category == _selectedCategory.value) return
    _selectedCategory.value = category
    val cached = cache[category]
    if (cached != null) {
        _uiState.value = UiState.Success(cached)
        return
    }
    load()
}

fun load() = fetch(showLoading = true)
fun refresh() = fetch(showLoading = false)

private fun fetch(showLoading: Boolean) {
    val token = ++requestToken          // supersedes any in-flight request
    val category = _selectedCategory.value
    if (showLoading) _uiState.value = UiState.Loading else _isRefreshing.value = true
    viewModelScope.launch {
        try {
            val feed = repository.getHomeFeed(category)
            if (token != requestToken) return@launch   // a newer category won
            cache[category] = feed
            _uiState.value = UiState.Success(feed)
        } catch (e: Exception) {
            if (token != requestToken) return@launch
            if (showLoading || _uiState.value !is UiState.Success) {
                _uiState.value = UiState.Error(e.toUserMessage())
            }
        } finally {
            if (token == requestToken) _isRefreshing.value = false
        }
    }
}
```

The cache is consulted only by `selectCategory`, so `refresh()` naturally
bypasses it and replaces the entry. There is deliberately no `loading` boolean:
a category switch must be able to supersede an in-flight request, and the token
makes the stale response a no-op.

`init { load() }` stays.

- [ ] **Step 4: Run the ViewModel tests until green**

Run: `cmd /c "gradlew.bat :app:testDebugUnitTest --tests *HomeViewModelTest* --console=plain > tdd-log.txt 2>&1"`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Add the chip row to `HomeScreen`**

* Replace the title `"Trending"` with `"Home"`.
* Between the title row and the feed, add:

```kotlin
LazyRow(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    items(HomeCategory.entries) { category ->
        FilterChip(
            selected = category == selectedCategory,
            onClick = { viewModel.selectCategory(category) },
            label = { Text(category.label) },
        )
    }
}
```

Collect `selectedCategory` with `collectAsState()`. Imports: `LazyRow`, `FilterChip`, `HomeCategory`, `Arrangement`.

- [ ] **Step 6: Compile the app and commit**

Run: `cmd /c "gradlew.bat :app:assembleDebug --console=plain > build-log.txt 2>&1"`
Expected: BUILD SUCCESSFUL.

```bash
gradlew.bat ktlintFormat
git add app/src/main/java/com/example/newtube/ui/home app/src/test/java/com/example/newtube/ui/home/HomeViewModelTest.kt
git commit -m "feat: Home category chips with cached multi-source feeds"
```

---

### Task 4: Shorts ViewModel resilience + UI refresh

**Files:**
- Modify: `app/src/main/java/com/example/newtube/ui/shorts/ShortsViewModel.kt`
- Modify: `app/src/main/java/com/example/newtube/ui/shorts/ShortsScreen.kt`
- Test: `app/src/test/java/com/example/newtube/ui/shorts/ShortsViewModelTest.kt`

**Interfaces:**
- Consumes: `getShorts(page)` from Task 2.
- Produces: unchanged public ViewModel API plus the new empty-result error behaviour.

- [ ] **Step 1: Add failing ViewModel tests**

```kotlin
@Test
fun `a reload that yields no clips is an error with retry`() = runTest {
    coEvery { repository.getShorts(any()) } returns emptyList()

    val viewModel = ShortsViewModel(repository)
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is UiState.Error)
}

@Test
fun `loadMore keeps the feed alive when a page only repeats known ids`() = runTest {
    val initial = (1..11).map { video("$it") }
    coEvery { repository.getShorts(1) } returns initial.take(4)
    coEvery { repository.getShorts(2) } returns initial.drop(4).take(4)
    coEvery { repository.getShorts(3) } returns initial.drop(8)
    // reload() stops after 3 pages / >= 10 clips, so loadMore starts at page 4
    coEvery { repository.getShorts(4) } returns listOf(initial[0])   // duplicate only
    coEvery { repository.getShorts(5) } returns listOf(video("12"))

    val viewModel = ShortsViewModel(repository)
    testDispatcher.scheduler.advanceUntilIdle()
    viewModel.loadMore()
    testDispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as UiState.Success
    assertEquals(12, state.data.size)
    assertEquals("12", state.data.last().id)
}
```

- [ ] **Step 2: Run and confirm failure**

Run: `cmd /c "gradlew.bat :app:testDebugUnitTest --tests *ShortsViewModelTest* --console=plain > tdd-log.txt 2>&1"`
Expected: FAIL.

- [ ] **Step 3: Implement the ViewModel changes**

* `reload()`: after the collection loop, `if (collected.isEmpty()) { _uiState.value = UiState.Error("No shorts available right now") } else Success(...)`.
* `loadMore()`: collect only ids that are not already published, keep looping up to `MAX_PAGE_ATTEMPTS`, and **never** set `reachedEnd` on duplicates — only on an empty repository response:

```kotlin
val currentIds = current.data.map { it.id }.toSet()
val collected = mutableListOf<Video>()
var attempts = 0
while (attempts < MAX_PAGE_ATTEMPTS && !reachedEnd) {
    val batch = repository.getShorts(nextPage)
    nextPage++
    attempts++
    if (batch.isEmpty()) {
        reachedEnd = true
        break
    }
    collected += batch.filter { it.id !in currentIds }
    if (collected.isNotEmpty()) break
}
if (collected.isNotEmpty()) {
    _uiState.value = UiState.Success((current.data + collected).distinctBy { it.id })
}
```

- [ ] **Step 4: Run the Shorts ViewModel tests until green**

Run: `cmd /c "gradlew.bat :app:testDebugUnitTest --tests *ShortsViewModelTest* --console=plain > tdd-log.txt 2>&1"`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Add the refresh overlay to `ShortsScreen`**

Wrap the pager in a `Box(Modifier.fillMaxSize())` and overlay:

```kotlin
IconButton(
    onClick = viewModel::reload,
    modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(8.dp)
        .background(Color.Black.copy(alpha = 0.35f), CircleShape),
) {
    Icon(Icons.Default.Refresh, contentDescription = "Shuffle shorts", tint = Color.White)
}
```

Imports: `Icons`, `filled.Refresh`, `Icon`, `IconButton`, `CircleShape`, `clip` if needed.

- [ ] **Step 6: Compile and commit**

Run: `cmd /c "gradlew.bat :app:assembleDebug --console=plain > build-log.txt 2>&1"`
Expected: BUILD SUCCESSFUL.

```bash
gradlew.bat ktlintFormat
git add app/src/main/java/com/example/newtube/ui/shorts app/src/test/java/com/example/newtube/ui/shorts/ShortsViewModelTest.kt
git commit -m "feat: reshuffleable Shorts feed with resilient paging"
```

---

### Task 5: Verification, device pass, documentation

**Files:**
- Modify: `SPEC.md`, `TASKS.md`, `README.md`
- Delete: `logcat-device.txt`, `tdd-log.txt`, `fmt-log.txt`, `verify-log.txt`, `build-log.txt` (temporary files in the repo root)

- [ ] **Step 1: Run the full quality gate**

```bash
gradlew.bat ktlintFormat
```

Then, in a separate invocation:

```bash
gradlew.bat ktlintCheck test assembleDebug
```

Expected: BUILD SUCCESSFUL. Count tests from the XML reports; expect the previous 28 plus the new repository/ViewModel tests (~42). Note the exact number for the docs.

- [ ] **Step 2: Install and smoke-test on the device**

```bash
adb logcat -c
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.example.newtube
adb shell am start -n com.example.newtube/.MainActivity
```

Manual checks:
1. Home shows the chip row; tapping `Gaming`, `Movies`, `Sports`, `News`, `Technology` changes the feed; ↻ produces a different mix.
2. Shorts contain only vertical clips (no letterboxed landscape), playback starts, swipe continues, refresh icon reshuffles.
3. Open a regular video from Home — playback still works.
4. `adb logcat -d -v time | Select-String "VideoPlayer|ERROR_CODE|AndroidRuntime"` shows no new `ERROR_CODE_IO_BAD_HTTP_STATUS`.

- [ ] **Step 3: Update the documentation**

* `SPEC.md`: replace the Shorts row description (real `/shorts` channels, 1–180 s); player row unchanged; add Home categories to the feature table; update the instance-limitations bullet and the unit-test count.
* `TASKS.md`: mark this feature as tasks 39–42 (Home feed composer, Shorts channel sources, category chips, Shorts refresh) and extend the fixed-bugs list with "`#shorts` search returns landscape clips".
* `README.md`: mention category chips and the new Shorts source; update the feature list.

- [ ] **Step 4: Remove temporary files and show the final diff**

```bash
git status --short
```

Expected: only intended source/docs changes; temp `*-log.txt` files removed.

- [ ] **Step 5: Propose the commit**

Show the user the staged summary and ask before committing the feature work.
