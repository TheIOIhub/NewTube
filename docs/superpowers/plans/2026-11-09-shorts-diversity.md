# Shorts diversity — wide automatic mix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One sentence: make the Shorts tab a wide automatic mix from 6–8 themed channels per page instead of 3 repetitive ones.

**Architecture:** Confine all logic changes to `VideoRepositoryImpl` in `:data` — widen `SHORTS_CHANNEL_QUERIES`, raise `SHORTS_CHANNELS_PER_PAGE` to 7, cap each channel's contribution with `PER_CHANNEL_TAKE`, cap per-query-bucket picks, and expand `FALLBACK_SHORTS_CHANNELS`. No UI, domain, or `VideoRepository` signature change; existing `Mutex`-guarded rotation and failure isolation stay.

**Tech Stack:** Kotlin, Android Gradle (unit tests via `:app:testDebugUnitTest`), kotlinx-coroutines `async`/`awaitAll`, MockK + JUnit4.

**Spec:** `docs/superpowers/specs/2026-11-09-shorts-diversity-design.md`

## Global Constraints

- Invidious `/api/v1/channels/{id}/shorts` is the only Shorts source; no feed endpoint, no new remote endpoints, no server change.
- `VideoRepository.getShorts(page: Int): List<Video>` signature unchanged; contract unchanged: throw only when *every* selected channel failed, else return list (possibly empty).
- Shorts filter stays `lengthSeconds in 1..180 && !liveNow`; dedup by `Video.id`.
- No category chips/topic picker UI on Shorts (explicitly declined).
- Rotation state stays behind `shortsChannelMutex`; no new DI, no offline feed cache.

## Review Focus

- **One loud channel eats the page:** a 48-clip channel must not dominate a 28-item page — pinned by the per-channel cap test in Task 1.
- **All channels 500:** repository must rethrow the first failure so `ShortsViewModel` shows Error+Retry (existing test `getShorts throws when every channel request fails` must stay green).
- **Dead/empty discovery + exhausted rotation:** feed must fall back to the curated list and never stall or return blank — pinned in Task 2.
- **Slow first paint from ~10 parallel calls:** still parallel `async` with `runCatching`; page must never call channels sequentially.
- **Duplicates across channels/pages:** final list is deduped by id after merge; ViewModel-level dedup untouched.

---

### Task 1: Wide mix — queries, channel count, per-channel cap, bucket cap

**Files:**
- Modify: `data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt` (`SHORTS_CHANNEL_QUERIES`, `SHORTS_CHANNELS_PER_PAGE`, `SHORTS_PER_PAGE`, new `PER_CHANNEL_TAKE` / `SHORTS_DISCOVERY_QUERIES_PER_PAGE` / `MAX_CHANNELS_PER_QUERY_BUCKET`, `nextShortsChannels()`, `getShorts()`)
- Test: `app/src/test/java/com/example/newtube/repository/VideoRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `InvidiousService.searchChannels(query, type="channel", page=1): List<ChannelSearchDto>` and `getChannelShorts(channelId, continuation): ChannelVideosDto` (unchanged); existing test helpers `videoDto(id, length)`, `ChannelSearchDto(authorId)`.
- Produces: `getShorts(page)` still `suspend fun getShorts(page: Int): List<Video>`, now built from `SHORTS_CHANNELS_PER_PAGE = 7` channels, capped at `PER_CHANNEL_TAKE = 7` clips per channel, final `SHORTS_PER_PAGE = 28`.

- [ ] **Step 1: Write the failing tests**

Add to `VideoRepositoryImplTest`:

```kotlin
@Test
fun `getShorts caps per channel contribution and mixes many channels`() = runBlocking {
    coEvery { service.searchChannels(any(), any(), any()) } returns
        (1..8).map { ChannelSearchDto(authorId = "UC$it") }
    coEvery { service.getChannelShorts(any(), null) } answers {
        val channel = firstArg<String>()
        ChannelVideosDto(
            videos = (1..20).map { videoDto("${channel}_$it", length = 30) }
        )
    }
    coJustRun { dao.insertAll(any()) }

    val shorts = repository.getShorts(1)

    assertEquals(28, shorts.size)
    // no single channel contributes more than PER_CHANNEL_TAKE
    val perChannel = shorts.groupBy { it.channelId }
    assertTrue(perChannel.values.all { it.size <= 7 })
    assertTrue(perChannel.size >= 4)
}

@Test
fun `getShorts spreads picks across query buckets`() = runBlocking {
    // first two discovery queries return overlapping channels, ensure a later
    // bucket still contributes channels on the same page
    coEvery { service.searchChannels(any(), any(), any()) } answers {
        listOf(ChannelSearchDto(authorId = "UC-a"), ChannelSearchDto(authorId = "UC-b"))
    }
    coEvery { service.getChannelShorts(any(), null) } returns
        ChannelVideosDto(videos = listOf(videoDto("s-$firstArgHash", length = 30)))
    // NOTE: replace placeholder with distinct ids per channel; seed at least
    // 4 different channels across the page
    coJustRun { dao.insertAll(any()) }

    val shorts = repository.getShorts(1)

    assertTrue(shorts.size >= 4)
}
```

Concrete values: channels `UC1..UC8`, 20 clips each (`UC1_1..UC1_20`, …), all `length = 30`. Expected page size `28` (`7 channels × 7 = 49` candidates capped to `28`). Replace the bucket test's placeholder with a per-channel answer so each channel yields its own ids; assert ≥4 distinct channels appear (if discovery answers the same 2 channels for every query, the implementation must still consult the fallback/pool to reach 7 channels — otherwise the test fails, which is the point).

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :app:testDebugUnitTest --tests "*VideoRepositoryImplTest*" --console=plain`
Expected: FAIL — `SHORTS_PER_PAGE` is still 24, per-channel cap does not exist, only 3 channels are requested so the page is smaller than 28 and one channel's 20 clips dominate (or the channel-count assertion fails).

- [ ] **Step 3: Implement the wide-mix constants and merge logic in `VideoRepositoryImpl.kt`**

Change the companion:

```kotlin
private const val SHORTS_CHANNELS_PER_PAGE = 7
private const val SHORTS_PER_PAGE = 28
private const val PER_CHANNEL_TAKE = 7
private const val SHORTS_DISCOVERY_QUERIES_PER_PAGE = 4
private const val MAX_CHANNELS_PER_QUERY_BUCKET = 2
```

Expand `SHORTS_CHANNEL_QUERIES` to the ~22 themed queries:

```kotlin
val SHORTS_CHANNEL_QUERIES = listOf(
    "shorts", "funny shorts", "animal shorts", "pet shorts",
    "cooking shorts", "food shorts", "dance shorts", "music shorts",
    "gaming shorts", "esports shorts", "sports shorts", "soccer shorts",
    "movie shorts", "science shorts", "tech shorts", "fitness shorts",
    "travel shorts", "fashion shorts", "comedy shorts", "news shorts",
    "education shorts", "life hacks shorts",
)
```

In `nextShortsChannels()`: take `SHORTS_DISCOVERY_QUERIES_PER_PAGE` (4) shuffled queries for `searchChannels` (was 2), and when building `chosen`, enforce the bucket cap — track which query each `authorId` came from and accept at most `MAX_CHANNELS_PER_QUERY_BUCKET` (2) channels per query before filling from the remaining pool / fallback.

In `getShorts()`: keep the parallel `async { runCatching { service.getChannelShorts(it) } }` shape and the all-failed-throw contract. After the 1..180 s / `!liveNow` filter and pre-`toDomainVideos()` dedup, apply the per-channel cap: group DTOs by `authorId`, shuffle within each group, take `PER_CHANNEL_TAKE` per channel, flatten, then final shuffle + `take(SHORTS_PER_PAGE)`. Keep `toDomainVideos()` (DB write-through) and the final `distinctBy { it.id }` + `shuffled()`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `gradlew.bat :app:testDebugUnitTest --tests "*VideoRepositoryImplTest*" --console=plain`
Expected: PASS (all `VideoRepositoryImplTest` tests, including the two new ones and the pre-existing failure/rotation tests).

- [ ] **Step 5: Commit**

```bash
git add data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt app/src/test/java/com/example/newtube/repository/VideoRepositoryImplTest.kt
git commit -m "feat: widen shorts mix to seven themed channels per page"
```

### Task 2: Expanded curated fallback list + fallback/rotation tests

**Files:**
- Modify: `data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt` (`FALLBACK_SHORTS_CHANNELS`)
- Test: `app/src/test/java/com/example/newtube/repository/VideoRepositoryImplTest.kt`

**Interfaces:**
- Consumes: Task 1's `getShorts` / `nextShortsChannels()`; test stub `coEvery { service.searchChannels(any(), any(), any()) } returns emptyList()` + `coEvery { service.getChannelShorts(any(), null) }`.
- Produces: `FALLBACK_SHORTS_CHANNELS` with ~15–20 verified IDs; unchanged `getShorts` signature.

- [ ] **Step 1: Verify fallback channel IDs against the instance**

For each candidate ID, call `GET /api/v1/channels/{id}/shorts` on the configured instance and keep IDs that return 200 with ≥1 item where `lengthSeconds in 1..180`. Keep the existing 4 (`UCgJ5_1F6yJhYLnyMszUdmUg`, `UCMHpfyAqrjrhn-mRS2bUL0g`, `UCoi2zyVut5_hIKw4eEUji6g`, `UCX6OQ3DkcsbYNE6H8uQQuVA`) and add 12–16 more shorts-first channels until the list reaches 15–20 IDs. Dead IDs are harmless (skipped on failure) but prefer probing so only live IDs ship.

- [ ] **Step 2: Write the failing test (fallback coverage + rotation reset)**

```kotlin
@Test
fun `getShorts rotates through fallback pool when discovery is empty`() = runBlocking {
    coEvery { service.searchChannels(any(), any(), any()) } returns emptyList()
    coEvery { service.getChannelShorts(any(), null) } answers {
        ChannelVideosDto(videos = listOf(videoDto("fb-${firstArg<String>()}", length = 30)))
    }
    coJustRun { dao.insertAll(any()) }

    val page1 = repository.getShorts(1)
    val page2 = repository.getShorts(2)

    assertTrue(page1.size >= SHORTS_CHANNELS_PER_PAGE)
    assertTrue(page1.map { it.id }.toSet() != page2.map { it.id }.toSet())
}
```

Adjust assertions to the actual `SHORTS_CHANNELS_PER_PAGE`/`SHORTS_PER_PAGE` values from Task 1 (7/28): page1 is a non-empty mixed set from the fallback pool; page2 differs (rotation, not the identical list). Replace `SHORTS_CHANNELS_PER_PAGE` token with the literal `7` if the constant is private — read it from the companion.

- [ ] **Step 3: Run the test to verify it fails (before the list expansion)**

Run: `gradlew.bat :app:testDebugUnitTest --tests "*VideoRepositoryImplTest.getShorts rotates through fallback pool when discovery is empty*" --console=plain`
Expected: FAIL with the current 4-ID fallback if the asserted diversity (≥7 channels per page) is not reachable.

- [ ] **Step 4: Expand `FALLBACK_SHORTS_CHANNELS` to the verified ~15–20 IDs**

Replace the companion list with the verified IDs from Step 1, each with a `// name` comment like the existing four entries. No logic change in this step.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `gradlew.bat :app:testDebugUnitTest --tests "*VideoRepositoryImplTest*" --console=plain`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add data/src/main/java/com/example/newtube/implementation/VideoRepositoryImpl.kt app/src/test/java/com/example/newtube/repository/VideoRepositoryImplTest.kt
git commit -m "feat: expand curated shorts fallback to 15-20 channels"
```

### Task 3: Full test run + device verification

**Files:**
- Create: (no new files; uses root logs `build-log.txt` / `tdd-log.txt` as scratch output)
- Test: whole suite `gradlew.bat test`

**Interfaces:**
- Consumes: Tasks 1–2 finished code on this branch.
- Produces: verified APK installable on the connected device; no API changes.

- [ ] **Step 1: Run the full JVM test suite**

Run: `gradlew.bat test --console=plain > tdd-log.txt 2>&1`
Expected: BUILD SUCCESSFUL, no failures in `ShortsViewModelTest` (its `getShorts` stubbing is unaffected by the constants change) or any other test.

- [ ] **Step 2: Lint + build**

Run: `gradlew.bat ktlintCheck :app:assembleDebug --console=plain > build-log.txt 2>&1`
Expected: BUILD SUCCESSFUL. If ktlint complains, run `gradlew.bat ktlintFormat` once and re-run; commit formatting separately if it touches files.

- [ ] **Step 3: Install on the connected device and eyeball the Shorts tab**

Run: `adb devices` (expect the user's phone with USB debugging), then `gradlew.bat :app:installDebug`.
Open the Shorts tab; swipe through 6–8 shorts and confirm: (a) most shorts come from *different* channels, (b) topics vary (not all one compilation channel), (c) no blank/black stall pages, (d) the refresh icon reshuffles. Note: if the instance 500s on a channel, it is skipped silently — that is expected, not a failure.

- [ ] **Step 4: Commit (only if formatting changed anything)**

```bash
git add -A
git commit -m "chore: format after shorts diversity change"
```

If nothing changed, skip the commit and report the green build + device check.

## Self-Review

- Spec coverage: design §Goals → Tasks 1–2 (7 channels, ~22 queries, per-channel cap, 28/page, 15–20 fallback). Non-goals (no chips/UI) → Task 3 verifies UI untouched. Contracts (throw-only-when-all-failed, 1..180 s, dedup, Mutex rotation) → preserved + covered by kept tests.
- Step scan: each step is one test, one run, one edit, one commit; no step asks for unspecified behaviour.
- Type consistency: `getShorts(page: Int): List<Video>` unchanged across tasks; new constants are private companion `const val`s; test helpers `videoDto`/`ChannelSearchDto`/`firstArg` consistent with current file.
- Proportion: plan is longer than the spec because the spec's "wide mix" hides five numeric decisions (query count, channels/page, take/page, bucket cap, fallback size) and the TDD steps pin them down; no full function bodies are transcripted beyond constant values.
