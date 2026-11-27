package com.example.newtube.implementation

import com.example.newtube.database.ChannelDao
import com.example.newtube.database.ChannelEntity
import com.example.newtube.database.VideoDao
import com.example.newtube.database.VideoEntity
import com.example.newtube.model.AccountResult
import com.example.newtube.model.AccountState
import com.example.newtube.model.AppSettings
import com.example.newtube.model.Channel
import com.example.newtube.model.ChannelVideosPage
import com.example.newtube.model.Comment
import com.example.newtube.model.CommentPage
import com.example.newtube.model.HomeCategory
import com.example.newtube.model.PlaybackSource
import com.example.newtube.model.FeedSignals
import com.example.newtube.model.Video
import com.example.newtube.model.VideoDetails
import com.example.newtube.usecase.rankFeed
import com.example.newtube.implementation.VideoContentFilter.applyContentSettings
import com.example.newtube.remote.ChannelDto
import com.example.newtube.remote.CommentDto
import com.example.newtube.remote.FormatStreamDto
import com.example.newtube.remote.InvidiousService
import com.example.newtube.remote.ThumbnailDto
import com.example.newtube.remote.VideoDto
import com.example.newtube.repository.AccountRepository
import com.example.newtube.repository.HistoryRepository
import com.example.newtube.repository.SettingsRepository
import com.example.newtube.repository.ShortsCacheRepository
import com.example.newtube.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val service: InvidiousService,
    private val dao: VideoDao,
    private val channelDao: ChannelDao,
    private val shortsCache: ShortsCacheRepository,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val accountRepository: AccountRepository,
) : VideoRepository {
    /** Serializes channel rotation and the shared "already used" set. */
    private val shortsChannelMutex = Mutex()
    private val usedShortsChannels = mutableSetOf<String>()

    private data class ContentPrefs(val region: String, val language: String)

    private suspend fun contentPrefs(): ContentPrefs =
        settingsRepository.settings.first().let { ContentPrefs(it.contentCountry, it.contentLanguage) }

    /** Called when the user changes content country/language so caches do not stay stale. */
    suspend fun clearFeedCaches() {
        shortsCache.clearKnownChannelIds()
    }

    override suspend fun getTrending(): List<Video> =
        withContext(Dispatchers.IO) {
            val prefs = contentPrefs()
            service.getTrending(region = prefs.region, hl = prefs.language).toDomainVideos()
        }

    /**
     * Builds a Home feed from several Invidious sources, fetched in parallel.
     * A failing source is skipped so one flaky endpoint cannot blank the whole
     * screen; only when every source failed does the error propagate.
     */
    override suspend fun getHomeFeed(category: HomeCategory): List<Video> =
        withContext(Dispatchers.IO) {
            val prefs = contentPrefs()
            val results =
                coroutineScope {
                    category.feedSources().map { source -> async { source.load(prefs) } }.awaitAll()
                }
            if (results.all { it.isFailure }) {
                throw results.first().exceptionOrNull() ?: IllegalStateException("No feed sources")
            }
            results
                .mapNotNull { it.getOrNull() }
                .flatten()
                .toDomainVideos()
                .distinctBy { it.id }
                .shuffled()
                .take(HOME_FEED_LIMIT)
        }

    override suspend fun getForYouFeed(): List<Video> =
        withContext(Dispatchers.IO) {
            val base = getHomeFeed(HomeCategory.ALL)
            val settings = settingsRepository.settings.first()
            val history = historyRepository.observeLocal(HISTORY_SIGNAL_LIMIT).first()
            val now = System.currentTimeMillis()
            val affinity =
                history
                    .groupBy { it.channelId }
                    .mapValues { (_, entries) ->
                        entries.maxOf { entry ->
                            val ageDays = (now - entry.watchedAt).coerceAtLeast(0L) / DAY_MS
                            1f / (1f + ageDays.toFloat())
                        }
                    }
            val subscriptions =
                if (accountRepository.state.value is AccountState.SignedIn) {
                    runCatching {
                        (accountRepository.subscriptions() as? AccountResult.Success)?.data?.items
                            ?.map { it.channelId }
                            ?.toSet()
                    }.getOrDefault(null) ?: emptySet()
                } else {
                    emptySet()
                }
            rankFeed(base, FeedSignals(affinity, subscriptions, settings.contentLanguage))
        }

    private sealed interface FeedSource {
        data class Trending(val type: String) : FeedSource

        data object Popular : FeedSource

        data class Search(val query: String) : FeedSource
    }

    private suspend fun FeedSource.load(prefs: ContentPrefs): Result<List<VideoDto>> =
        runCatching {
            when (this) {
                is FeedSource.Trending ->
                    service.getTrending(type = type, region = prefs.region, hl = prefs.language)
                FeedSource.Popular -> service.getPopular()
                is FeedSource.Search ->
                    service.searchVideos(query, region = prefs.region, hl = prefs.language)
            }
        }

    private fun HomeCategory.feedSources(): List<FeedSource> =
        when (this) {
            HomeCategory.FOR_YOU -> listOf(FeedSource.Trending("Default"))
            HomeCategory.ALL ->
                listOf(
                    FeedSource.Trending("Default"),
                    FeedSource.Trending("Gaming"),
                    FeedSource.Popular,
                ) + ALL_TOPIC_QUERIES.shuffled().take(ALL_TOPIC_QUERY_COUNT).map(FeedSource::Search)
            HomeCategory.MUSIC -> pickQueries(MUSIC_QUERIES)
            HomeCategory.GAMING -> listOf(FeedSource.Trending("Gaming")) + pickQueries(GAMING_QUERIES)
            HomeCategory.MOVIES -> pickQueries(MOVIE_QUERIES)
            HomeCategory.SPORTS -> pickQueries(SPORTS_QUERIES)
            HomeCategory.NEWS -> pickQueries(NEWS_QUERIES)
            HomeCategory.TECHNOLOGY -> pickQueries(TECH_QUERIES)
        }

    private fun pickQueries(pool: List<String>): List<FeedSource> =
        pool.shuffled().take(TOPIC_QUERY_COUNT).map(FeedSource::Search)

    /**
     * Paged loads for the Shorts mediator: every call advances the channel
     * rotation, so consecutive `page` values yield a fresh batch without a
     * separate continuation API.
     */
    override suspend fun getShorts(page: Int): List<Video> =
        withContext(Dispatchers.IO) {
            // Invidious has no Shorts feed and its search ignores YouTube's
            // Shorts shelf, so the only reliable source is the per-channel
            // `/shorts` endpoint. Known channel ids are reused first; channels
            // are discovered only when no usable cached ids are left.
            val first = shortsChannelMutex.withLock { nextShortsChannels() }
            var results = fetchShorts(first.ids)
            if (first.fromCache && results.isNotEmpty() && results.all { it.second.isFailure }) {
                // Every cached channel failed: discover fresh ones and mix in
                // the curated fallback instead of repeating the failing set.
                val retry = shortsChannelMutex.withLock { nextShortsChannels(forceDiscovery = true) }
                results = fetchShorts(retry.ids)
            }
            if (results.isNotEmpty() && results.all { it.second.isFailure }) {
                throw results.first().second.exceptionOrNull()
                    ?: IllegalStateException("No shorts channels")
            }

            val capped =
                results
                    .mapNotNull { (_, result) -> result.getOrNull()?.videos }
                    .flatMap { videos ->
                        videos
                            .filter { it.lengthSeconds in 1..SHORTS_MAX_SECONDS && !it.liveNow }
                            .distinctBy { it.videoId }
                            .shuffled()
                            .take(PER_CHANNEL_TAKE)
                    }
                    .shuffled()

            capped
                .toDomainVideos()
                .distinctBy { it.id }
                .shuffled()
                .take(SHORTS_PER_PAGE)
        }

    override suspend fun getShortsShelf(limit: Int): List<Video> =
        withContext(Dispatchers.IO) {
            val prefs = contentPrefs()
            service
                .getShorts(page = 1, region = prefs.region, hl = prefs.language)
                .asSequence()
                .filter { it.lengthSeconds in 1..SHORTS_SHELF_MAX_SECONDS && !it.liveNow }
                .distinctBy { it.videoId }
                .shuffled()
                .take(limit.coerceAtLeast(0))
                .toList()
                .toDomainVideos()
        }

    /**
     * Keeps the selected channel id next to its result so the per-channel cap
     * never depends on the payload's authorId.
     */
    private suspend fun fetchShorts(selected: List<String>) =
        coroutineScope {
            selected.map { channelId ->
                async { channelId to runCatching { service.getChannelShorts(channelId) } }
            }.awaitAll()
        }

    private data class ShortsChannelSelection(
        val ids: List<String>,
        val fromCache: Boolean,
    )

    /** Must be called with [shortsChannelMutex] held. */
    private suspend fun nextShortsChannels(forceDiscovery: Boolean = false): ShortsChannelSelection {
        val known = shortsCache.knownChannelIds()
        if (!forceDiscovery && known.isNotEmpty()) {
            val fresh = known.filterNot { it in usedShortsChannels }
            // All known ids have been used: clear and start a new cycle so the
            // feed keeps rotating instead of stalling.
            val pool =
                if (fresh.isEmpty()) {
                    usedShortsChannels.clear()
                    known.toList()
                } else {
                    fresh
                }
            val chosen = pool.shuffled().take(SHORTS_CHANNELS_PER_PAGE)
            usedShortsChannels += chosen
            return ShortsChannelSelection(chosen, fromCache = true)
        }

        val discoveredWithBucket =
            runCatching {
                coroutineScope {
                    (UKRAINIAN_SHORTS_CHANNEL_QUERIES.shuffled() +
                            SHORTS_CHANNEL_QUERIES.shuffled())
                        .distinct()
                        .take(SHORTS_DISCOVERY_QUERIES_PER_PAGE)
                        .map { query ->
                            async {
                                runCatching {
                                    query to service.searchChannels(query).map { it.authorId }
                                }
                            }
                        }.awaitAll()
                        .mapNotNull { it.getOrNull() }
                        .flatMap { (query, ids) -> ids.map { query to it } }
                }
            }.getOrDefault(emptyList())

        val discovered = discoveredWithBucket.map { it.second }
        if (discovered.isNotEmpty()) {
            // Persist discovered ids only; the curated fallback is never cached.
            shortsCache.rememberChannelIds(
                (known + discovered).distinct().take(MAX_KNOWN_SHORTS_CHANNELS).toSet(),
            )
        }
        // Group by the query bucket that surfaced each channel; cap how many
        // channels any single bucket can contribute.
        val bucketById = discoveredWithBucket.groupBy({ it.first }, { it.second })
        val freshByBucket =
            bucketById.mapValues { (_, ids) ->
                ids.distinct().filterNot { it in usedShortsChannels }.shuffled()
            }

        val picked = mutableListOf<String>()
        for (bucket in bucketById.keys.shuffled()) {
            if (picked.size >= SHORTS_CHANNELS_PER_PAGE) break
            val candidates = freshByBucket.getValue(bucket).filterNot { it in picked }
            picked += candidates.take(MAX_CHANNELS_PER_QUERY_BUCKET)
        }
        if (picked.size < SHORTS_CHANNELS_PER_PAGE) {
            val remaining =
                discovered
                    .distinct()
                    .filterNot { it in usedShortsChannels || it in picked }
                    .shuffled()
            picked += remaining.take(SHORTS_CHANNELS_PER_PAGE - picked.size)
        }

        val chosen =
            if (picked.isEmpty()) {
                // Every discovered channel has been used; reset and mix the
                // discovery pool with the curated list so the feed never stalls.
                usedShortsChannels.clear()
                (discovered.distinct() + FALLBACK_SHORTS_CHANNELS)
                    .distinct()
                    .shuffled()
                    .take(SHORTS_CHANNELS_PER_PAGE)
            } else if (picked.size < SHORTS_CHANNELS_PER_PAGE) {
                val fillPool =
                    (discovered.distinct() + FALLBACK_SHORTS_CHANNELS)
                        .distinct()
                        .filterNot { it in picked || it in usedShortsChannels }
                        .shuffled()
                picked + fillPool.take(SHORTS_CHANNELS_PER_PAGE - picked.size)
            } else {
                picked
            }
        usedShortsChannels += chosen
        return ShortsChannelSelection(chosen, fromCache = false)
    }

    override suspend fun search(query: String, page: Int): List<Video> =
        withContext(Dispatchers.IO) {
            val prefs = contentPrefs()
            service.searchVideos(query, page = page, region = prefs.region, hl = prefs.language).toDomainVideos()
        }

    override suspend fun searchSuggestions(query: String): List<String> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) {
                emptyList()
            } else {
                runCatching {
                    val prefs = contentPrefs()
                    service.getSearchSuggestions(query, region = prefs.region, hl = prefs.language).suggestions
                }.getOrDefault(emptyList())
            }
        }

    override suspend fun getVideoById(videoId: String): Video =
        withContext(Dispatchers.IO) {
            dao.getVideoById(videoId)?.toDomain()
                ?: service.getVideoDetails(videoId, region = contentPrefs().region, hl = contentPrefs().language).toDomainVideo()
        }

    override suspend fun getVideoDetails(videoId: String, maxHeight: Int): VideoDetails =
        withContext(Dispatchers.IO) {
            val prefs = contentPrefs()
            val dto = service.getVideoDetails(videoId, region = prefs.region, hl = prefs.language)
            val video = dto.toDomainVideo()
            val playback =
                dto.toPlaybackSource(maxHeight)
                    ?: throw IllegalStateException("No playable stream returned for video $videoId")
            VideoDetails(
                video = video,
                description = dto.description.trim(),
                related =
                    dto.recommendedVideos
                        .applyContentSettings(settingsRepository.settings.first())
                        .mapNotNull { it.toDomainOrNull() },
                playback = playback,
            )
        }

    override suspend fun getChannelVideos(channelId: String, continuation: String?): ChannelVideosPage =
        withContext(Dispatchers.IO) {
            val response = service.getChannelVideos(channelId, continuation)
            ChannelVideosPage(
                videos = response.videos.toDomainVideos(),
                continuation = response.continuation,
            )
        }

    override suspend fun getChannelDetails(channelId: String): Channel =
        withContext(Dispatchers.IO) {
            channelDao.getChannelById(channelId)?.toDomain()
                ?: service
                    .getChannelDetails(channelId)
                    .toEntity()
                    .apply { channelDao.insert(this) }
                    .toDomain()
        }

    override suspend fun getComments(videoId: String, continuation: String?): CommentPage =
        withContext(Dispatchers.IO) {
            val response = service.getComments(videoId, continuation)
            CommentPage(
                comments = response.comments.mapNotNull { it.toDomainOrNull() },
                continuation = response.continuation?.takeIf { it.isNotBlank() },
                totalCount = response.commentCount,
            )
        }

    private suspend fun List<VideoDto>.toDomainVideos(): List<Video> {
        val entities =
            applyContentSettings(settingsRepository.settings.first()).mapNotNull { it.toEntityOrNull() }
        dao.insertAll(entities)
        return entities.map { it.toDomain() }
    }

    private suspend fun VideoDto.toDomainVideo(): Video {
        val entity =
            toEntityOrNull() ?: throw IllegalStateException("Unexpected payload for video $videoId")
        dao.insertAll(listOf(entity))
        return entity.toDomain()
    }

    private fun VideoDto.toDomainOrNull(): Video? = toEntityOrNull()?.toDomain()

    /**
     * Largest avatar at or below 176 px; anything bigger is a last resort.
     */
    private fun List<ThumbnailDto>.avatarUrl(): String {
        val usable = filter { it.url.isNotBlank() }
        val preferred = usable.filter { it.width in 1..176 }
        return (preferred.maxByOrNull { it.width } ?: usable.maxByOrNull { it.width })?.url.orEmpty()
    }

    /**
     * Feed and search responses are mixed: channels and playlists carry no
     * `videoId` and are skipped. The `type` field is deliberately not used as a
     * filter: Invidious labels live broadcasts `livestream` (not `video`), and
     * recommendations come without any `type` at all, so a whitelist would drop
     * perfectly playable entries.
     */
    private fun VideoDto.toEntityOrNull(): VideoEntity? {
        val id = videoId
        if (id.isNullOrBlank()) return null
        return VideoEntity(
            id = id,
            title = title,
            channelName = author,
            channelId = authorId,
            authorAvatarUrl = authorThumbnails.avatarUrl(),
            thumbnailUrl = videoThumbnails.maxByOrNull { it.width }?.url.orEmpty(),
            duration = lengthSeconds,
            views = viewCount,
            uploadedAt = publishedText,
            isLive = liveNow,
        )
    }

    private fun VideoEntity.toDomain(): Video =
        Video(
            id = id,
            title = title,
            channelName = channelName,
            channelId = channelId,
            authorAvatarUrl = authorAvatarUrl,
            thumbnailUrl = thumbnailUrl,
            duration = duration,
            views = views,
            uploadedAt = uploadedAt,
            isLive = isLive,
        )

    /**
     * Picks the stream to hand to ExoPlayer.
     *
     * Live broadcasts must go through HLS: that is the only source Invidious
     * exposes for them, and live HLS segments are served fine.
     *
     * VOD is the opposite: on the default instance the HLS variant playlists
     * point at YouTube slice URLs (`/govp/slices=…`) that answer 403, while the
     * adaptive DASH tracks (`videoplayback?…&range=`) answer 206. So for VOD the
     * order is: a progressive `formatStreams` entry within the quality cap
     * (already contains audio), then a merged adaptive pair (one video + one
     * audio track), then a progressive track above the cap as a last resort,
     * and only then the HLS manifest.
     */
    private fun VideoDto.toPlaybackSource(maxHeight: Int): PlaybackSource? {
        val cap = maxHeight.takeIf { it > 0 } ?: AppSettings.DEFAULT_QUALITY
        val isLive = liveNow || type == "livestream"
        if (isLive) {
            hlsUrl?.takeIf { it.isNotBlank() }?.let { return PlaybackSource.Hls(it) }
        }

        formatStreams.firstPlayableUrl(cap)?.let { return PlaybackSource.Progressive(it) }

        val videoUrl = adaptiveFormats.bestVideoUrl(cap)
        val audioUrl = adaptiveFormats.bestAudioUrl()
        if (videoUrl != null && audioUrl != null) {
            val alternatives =
                adaptiveFormats
                    .filter { format ->
                        format.type?.startsWith("audio/") == true &&
                            !format.language.isNullOrBlank() &&
                            !format.url.isNullOrBlank()
                    }.mapNotNull { it.url }
                    .distinct()
            return PlaybackSource.Merged(videoUrl, audioUrl, alternatives)
        }

        // Last resort: accept a progressive track above the cap, then HLS.
        formatStreams.firstPlayableUrl()?.let { return PlaybackSource.Progressive(it) }
        return hlsUrl?.takeIf { it.isNotBlank() }?.let { PlaybackSource.Hls(it) }
    }

    /**
     * Prefers a hardware-friendly `avc1` track at or below [maxHeight] and falls
     * back to the smallest available resolution when everything is above the
     * cap. `av01` entries are only used when no `avc1` track exists, since
     * hardware decoding depends on the device.
     */
    private fun List<FormatStreamDto>.bestVideoUrl(maxHeight: Int): String? {
        val candidates =
            filter {
                !it.url.isNullOrBlank() && it.type?.startsWith("video/mp4") == true
            }
        if (candidates.isEmpty()) return null

        val capped = candidates.filter { (it.heightOrNull() ?: Int.MAX_VALUE) <= maxHeight }
        val pool = capped.ifEmpty { candidates }
        val byQuality =
            if (capped.isNotEmpty()) {
                compareByDescending<FormatStreamDto> { it.isAvc() }
                    .thenByDescending { it.heightOrNull() ?: 0 }
            } else {
                compareByDescending<FormatStreamDto> { it.isAvc() }
                    .thenBy { it.heightOrNull() ?: Int.MAX_VALUE }
            }
        return pool.sortedWith(byQuality).firstOrNull()?.url
    }

    private fun List<FormatStreamDto>.bestAudioUrl(): String? =
        filter { !it.url.isNullOrBlank() && it.type?.startsWith("audio/mp4") == true }
            .sortedWith(
                compareByDescending<FormatStreamDto> { it.type?.contains("mp4a.40.2") == true }
                    .thenByDescending { it.bitrate?.toIntOrNull() ?: 0 },
            ).firstOrNull()
            ?.url

    private fun FormatStreamDto.heightOrNull(): Int? = qualityLabel?.takeWhile { it.isDigit() }?.toIntOrNull()

    private fun FormatStreamDto.isAvc(): Boolean = type?.contains("avc1") == true

    private fun List<FormatStreamDto>.firstPlayableUrl(maxHeight: Int? = null): String? =
        firstOrNull { format ->
            val height = format.heightOrNull()
            !format.url.isNullOrBlank() &&
                (maxHeight == null || height == null || height <= maxHeight)
        }?.url

    private fun CommentDto.toDomainOrNull(): Comment? {
        val id = commentId ?: return null
        return Comment(
            id = id,
            author = author,
            authorChannelId = authorId.orEmpty(),
            authorAvatarUrl = authorThumbnail,
            content = content,
            likeCount = likeCount,
            publishedText = publishedText,
            isPinned = isPinned,
        )
    }

    private fun ChannelDto.toEntity(): ChannelEntity =
        ChannelEntity(
            id = authorId,
            name = author,
            avatarUrl = authorThumbnails.lastOrNull()?.url.orEmpty(),
            subscriberCount = subCount,
            totalVideos = latestVideos.size,
            description = description,
        )

    private fun ChannelEntity.toDomain(): Channel =
        Channel(
            id = id,
            name = name,
            avatarUrl = avatarUrl,
            subscriberCount = subscriberCount,
            totalVideos = totalVideos,
            description = description,
        )

    private companion object {
        const val HOME_FEED_LIMIT = 60
        const val HISTORY_SIGNAL_LIMIT = 500
        const val DAY_MS = 86_400_000L
        const val TOPIC_QUERY_COUNT = 3
        const val ALL_TOPIC_QUERY_COUNT = 2
        const val SHORTS_MAX_SECONDS = 180L
        const val SHORTS_CHANNELS_PER_PAGE = 7
        const val SHORTS_PER_PAGE = 28
        const val PER_CHANNEL_TAKE = 7
        const val SHORTS_DISCOVERY_QUERIES_PER_PAGE = 4
        const val MAX_CHANNELS_PER_QUERY_BUCKET = 2
        const val MAX_KNOWN_SHORTS_CHANNELS = 42
        const val SHORTS_SHELF_MAX_SECONDS = 60L

        val MUSIC_QUERIES = listOf("music video", "new song", "official video", "live music")
        val GAMING_QUERIES = listOf("gaming highlights", "gameplay", "esports highlights", "new game")
        val MOVIE_QUERIES = listOf("movie trailer", "movie review", "new movie", "film trailer")
        val SPORTS_QUERIES =
            listOf("sports highlights", "football highlights", "nba highlights", "tennis highlights")
        val NEWS_QUERIES = listOf("news today", "world news", "breaking news", "daily news")
        val TECH_QUERIES = listOf("technology review", "tech news", "gadgets", "science technology")
        val ALL_TOPIC_QUERIES =
            MUSIC_QUERIES + GAMING_QUERIES + MOVIE_QUERIES + SPORTS_QUERIES + NEWS_QUERIES + TECH_QUERIES

        val UKRAINIAN_SHORTS_CHANNEL_QUERIES =
            listOf(
                "шорти",
                "українські шорти",
                "короткі відео",
                "гумористичні шорти",
                "кулінарні шорти",
                "ігрові шорти",
                "музичні шорти",
                "спортивні шорти",
                "цікаві факти шорти",
                "новини шорти",
            )
        val UKRAINIAN_FALLBACK_SHORTS_CHANNELS = emptyList<String>()

        val SHORTS_CHANNEL_QUERIES =
            UKRAINIAN_SHORTS_CHANNEL_QUERIES + listOf(
                "shorts", "funny shorts", "animal shorts", "pet shorts",
                "cooking shorts", "food shorts", "dance shorts", "music shorts",
                "gaming shorts", "esports shorts", "sports shorts", "soccer shorts",
                "movie shorts", "science shorts", "tech shorts", "fitness shorts",
                "travel shorts", "fashion shorts", "comedy shorts", "news shorts",
                "education shorts", "life hacks shorts",
            )
        val FALLBACK_SHORTS_CHANNELS =
            UKRAINIAN_FALLBACK_SHORTS_CHANNELS + listOf(
                // Shorts Break
                "UCgJ5_1F6yJhYLnyMszUdmUg",
                // A for Adley Shorts
                "UCMHpfyAqrjrhn-mRS2bUL0g",
                // Zakiiino short
                "UCoi2zyVut5_hIKw4eEUji6g",
                // MrBeast
                "UCX6OQ3DkcsbYNE6H8uQQuVA",
                // Funniest YT Clips
                "UCdTQfOZ4xB-A0lm2QzM1NVQ",
                // Panda Shorts
                "UCEYlNI8wno2Xp7YUNdMN22A",
                // Natural Habitat Shorts
                "UCSb_Sui6FBxVS4_ROsrU_Iw",
                // 1 Minute Animals
                "UCejVe2sNPjjvfCXg35q_EQQ",
                // Vida Animal Shorts
                "UCSx6wiYRQfX4SwG4eWBpHAQ",
                // cookininshort
                "UC6vQqdqWjC2OO74WqERQ-kA",
                // Cooking with Kian
                "UCZnEb05GaAdBbMv4XjBZbaw",
                // Royal Cooking
                "UCjAKzWYZLayOQfSud0lU1vQ",
                // Miniature Cooking Ideas
                "UCGW2OA5IB1l1YbIeiVLxKcA",
                // Thay Huy Kid Dance
                "UCotnhpvYSd-pUAdrb0ceKZw",
                // Dance with Alisha
                "UCXORBdm4-ahxpPp94LiN8sA",
                // ABCD Dance Factory
                "UC6jaZrPy_AlsMdSPGM7jSyg",
            )
    }
}
