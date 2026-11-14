# Shorts diversity — wide automatic mix — design

Date: 2026-11-09
Status: awaiting approval

## Intent

The Shorts tab currently pulls from only 2–4 channels per session. On a real
device it feels repetitive: the same few compilation channels come back again
and again. The user wants an **automatic wide mix** — many topics/categories
randomly interleaved, with no chips, no user choice, no UI change.

## Goals

* Each Shorts page is built from **6–8 distinct channels** (was 3), chosen so
  topics are not all from one bucket.
* Take only the **5–8 freshest shorts per channel**, then shuffle the merged
  pool and return **24–32 items** per page. One channel can no longer dominate
  a page even if it returns 48 clips.
* Broaden the discovery pool from 8 to **~22 themed channel queries** and the
  curated fallback from 4 to **~15–20 channels**, so variety survives a flaky
  discovery or an instance 500.
* Keep existing rotation (`usedShortsChannels` + `Mutex'), failure isolation
  (one channel failing does not fail the page), dedup by id, 1..180 s filter.

## Non-goals

* No category chips or topic picker on the Shorts screen (explicitly declined).
* No personalization, subscriptions, or watch-history ranking.
* No per-channel continuation paging; first page per channel stays enough.
* No server/Invidious change; no offline caching of feeds.

## Constraints and facts

* Invidious has no Shorts feed; `/api/v1/channels/{id}/shorts` is the only
  reliable vertical source (verified in the 2026-11-08 design).
* `getShorts` runs inside a `@Singleton` repository, so rotation state stays
  behind `shortsChannelMutex`.
* `ShortsViewModel` deduplicates by id and retries on duplicates, so the
  repository contract must stay: throw only when *every* selected channel
  failed; empty list when at least one succeeded but yielded nothing usable.

## Approaches considered

* **A — Wide mix (chosen).** More queries + more channels per page + a
  per-channel take-cap + bigger fallback list. Real diversity with no UI work.
  Trade-off: ~10 parallel requests per page (2–4 search + 6–8 channel shorts),
  slightly slower first paint, individual 500s are skipped.
* **B — Topic pools with balancing.** Group queries/fallbacks per topic and
  guarantee 1 channel per topic per page. More code and a hand-balanced pool to
  maintain; rejected as overkill for "random mix".
* **C — Minimal bump (3 → 6 channels).** Fastest, but diversity still depends
  on whatever discovery returns; rejected because it would not fix the complaint.

## Design

All changes are confined to `VideoRepositoryImpl` (`SHORTS_*` constants and
`nextShortsChannels()` / `getShorts()`) plus its tests. No UI change, no
`VideoRepository` signature change, no new remote endpoints.

```kotlin
private const val SHORTS_CHANNELS_PER_PAGE = 7
private const val SHORTS_PER_PAGE = 28
private const val PER_CHANNEL_TAKE = 7
```

`SHORTS_CHANNEL_QUERIES` grows from 8 to ~22, roughly one per topic:
`shorts`, `funny shorts`, `animal shorts`, `pet shorts`, `cooking shorts`,
`food shorts`, `dance shorts`, `music shorts`, `gaming shorts`,
`esports shorts`, `sports shorts`, `soccer shorts`, `movie shorts`,
`science shorts`, `tech shorts`, `fitness shorts`, `travel shorts`,
`fashion shorts`, `comedy shorts`, `news shorts`, `education shorts`,
`life hacks shorts`.

Discovery keeps its shape (2–4 random queries per page, parallel
`searchChannels`), but the larger pool makes repeats far less likely. Channel
selection additionally keeps topic spread: at most 2 channels picked from any
single query bucket per page, then the rest filled at random from the
discovered pool. If discovery is empty or fully used, reset and draw from
discovered + the expanded `FALLBACK_SHORTS_CHANNELS` (~15–20 well-known
shorts-first channels, incl. the current 4).

`getShorts(page)` steps:

1. Select 6–8 channels via `nextShortsChannels()` under the mutex.
2. Fetch each channel's `/shorts` in parallel with `runCatching`; if *all*
   failed, rethrow the first failure.
3. Merge, keep `lengthSeconds in 1..180 && !liveNow`, dedup by id.
4. Interleave-cap: shuffle first, then take at most `PER_CHANNEL_TAKE` per
   channel before the final shuffle (so 7×7 = 49 candidates → 28 shuffled).
   Simplest equivalent: shuffle the merged list and take `SHORTS_PER_PAGE`,
   but the pre-cap is what prevents one loud channel from eating the page.
5. `toDomainVideos()`, dedup, shuffle, take `SHORTS_PER_PAGE`.

## Tests

Update `VideoRepositoryImplTest`:

* Page merges clips from 6+ channels (was 3) and caps per-channel contribution.
* Only 1..180 s items kept; duplicates by id collapsed.
* Discovery queries cover multiple topics (mock several queries, assert at
  least 3 different buckets appear on one page).
* One failing channel is skipped; all failing → exception propagates.
* Empty discovery → expanded fallback list is used; used channels rotate and
  reset.

Existing `ShortsViewModelTest` covers dedup/retry behaviour and needs no
change.

## Risks / trade-offs

* More requests per page → slower first load and more exposure to instance
  500s. Mitigated by parallel `async` + failure isolation; a failed channel
  just contributes nothing.
* Broader pool may include low-quality or niche channels. Acceptable: the user
  asked for breadth explicitly; compile-channel skew is reduced by the
  per-channel cap.
* Fallback channel IDs must be verified against the instance at build time;
  dead IDs are harmless (their request fails and is skipped).

## Left to the builder

* Verify each fallback channel ID returns shorts on the default instance;
  keep the current 4 and add ~12–16 confirmed ones.
* Final exact query wording and `SHORTS_CHANNELS_PER_PAGE` between 6–8
  (default 7) depending on observed load time on device.
* Verify on the connected device via USB debugging: first paint, variety
  across 3–4 swipes, and behaviour when a channel 500s.
