package com.example.newtube.implementation

import com.example.newtube.database.ChannelDao
import com.example.newtube.database.ChannelEntity
import com.example.newtube.database.VideoDao
import com.example.newtube.database.VideoEntity
import com.example.newtube.implementation.VideoContentFilter.applyContentSettings
import com.example.newtube.innertube.ContentParser
import com.example.newtube.innertube.YouTubeContentClient
import com.example.newtube.model.AccountResult
import com.example.newtube.model.AccountState
import com.example.newtube.model.AppSettings
import com.example.newtube.model.Channel
import com.example.newtube.model.ChannelVideosPage
import com.example.newtube.model.CommentPage
import com.example.newtube.model.FeedSignals
import com.example.newtube.model.HomeCategory
import com.example.newtube.model.PlaybackSource
import com.example.newtube.model.Video
import com.example.newtube.model.VideoDetails
import com.example.newtube.repository.AccountRepository
import com.example.newtube.repository.FeedKey
import com.example.newtube.repository.HistoryRepository
import com.example.newtube.repository.SettingsRepository
import com.example.newtube.repository.ShortsCacheRepository
import com.example.newtube.repository.VideoRepository
import com.example.newtube.usecase.rankFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val contentClient: YouTubeContentClient,
    private val dao: VideoDao,
    private val channelDao: ChannelDao,
    private val shortsCache: ShortsCacheRepository,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val accountRepository: AccountRepository,
    private val feedCache: FeedCacheRepository,
) : VideoRepository {
    /** YouTube's own browse token for the next Shorts page. */
    @Volatile
    private var shortsContinuation: String? = null

    /** Search continuations are per query: another query must not steal the token. */
    private val searchContinuations = ConcurrentHashMap<String, String>()

    /** Called when the user changes content preferences so cursors do not stay stale. */
    override suspend fun clearFeedCaches() {
        shortsContinuation = null
        searchContinuations.clear()
        shortsCache.clearKnownChannelIds()
        shortsCache.clearFeedCache()
        feedCache.clearAll()
    }

    /**
     * Cached JSON is parsed and normalised on every emission so the UI sees
     * stale data in exactly the same shape as a refreshed feed.
     */
    override fun observeFeed(key: FeedKey): Flow<List<Video>> =
        feedCache
            .observe(key.cacheKey())
            .map { json ->
                val items = json?.let(::decodeCachedFeed).orEmpty()
                items.forFeedKey(key)
            }
            .flowOn(Dispatchers.Default)

    /** A failed or unusable refresh never overwrites the working cached feed. */
    override suspend fun refreshFeed(key: FeedKey): Result<List<Video>> =
        runCatching {
            val json = withContext(Dispatchers.IO) { key.fetchJson() }
            val items = ContentParser.parseFeed(json).items.forFeedKey(key)
            // Only a usable parsed feed may replace the cached page: a nonblank
            // bot-check page or an exhausted search token must keep the old feed.
            if (items.isNotEmpty()) feedCache.put(key.cacheKey(), items.toCacheJson())
            items
        }

    /**
     * One normalisation path for cached and freshly fetched feeds: content
     * filtering + metadata persistence (and the same limit/ranking the old
     * suspend feed methods applied) must not depend on which path produced it.
     */
    private suspend fun List<Video>.forFeedKey(key: FeedKey): List<Video> =
        when (key) {
            is FeedKey.Home -> {
                val limited = withoutShorts().toFilteredVideos().take(HOME_FEED_LIMIT)
                if (key.category == HomeCategory.FOR_YOU) rankForYou(limited) else limited
            }
            is FeedKey.Search -> toFilteredVideos().take(HOME_FEED_LIMIT)
            is FeedKey.Channel -> toFilteredVideos()
            FeedKey.Shorts ->
                filter { it.duration in 0L..SHORTS_MAX_SECONDS && !it.isLive }
                    .toFilteredVideos()
                    .take(SHORTS_PER_PAGE)
            FeedKey.Notifications -> toFilteredVideos()
        }

    private fun FeedKey.cacheKey(): String =
        when (this) {
            is FeedKey.Home -> "home:${category.name}"
            FeedKey.Shorts -> "shorts"
            is FeedKey.Search -> "search:$query:$page"
            is FeedKey.Channel -> "channel:$channelId"
            FeedKey.Notifications -> "notifications"
        }

    /** Every branch is one InnerTube response, kept verbatim for the cache. */
    private suspend fun FeedKey.fetchJson(): String =
        when (this) {
            is FeedKey.Home -> contentClient.search(category.searchQuery(), HOME_SEARCH_PARAMS, null)
            FeedKey.Shorts -> contentClient.search(SHORTS_QUERY, null, null)
            is FeedKey.Search -> searchJson(query, page)
            is FeedKey.Channel -> contentClient.browse(channelId)
            FeedKey.Notifications -> contentClient.browse(BROWSE_NOTIFICATIONS)
        }

    override suspend fun getTrending(): List<Video> =
        withContext(Dispatchers.IO) {
            ContentParser.parseFeed(contentClient.search(HOME_SEARCH_QUERY, HOME_SEARCH_PARAMS, null))
                .items
                .withoutShorts()
                .toFilteredVideos()
        }

    /** One native YouTube browse feed per tab; no topic-mix searches. */
    override suspend fun getHomeFeed(category: HomeCategory): List<Video> =
        withContext(Dispatchers.IO) {
            ContentParser.parseFeed(contentClient.search(category.searchQuery(), HOME_SEARCH_PARAMS, null))
                .items
                .withoutShorts()
                .toFilteredVideos()
                .take(HOME_FEED_LIMIT)
        }

    override suspend fun getForYouFeed(): List<Video> = rankForYou(getHomeFeed(HomeCategory.ALL))

    /** Shared by the direct feed and the cache-backed Home(FOR_YOU) path. */
    private suspend fun rankForYou(base: List<Video>): List<Video> =
        withContext(Dispatchers.IO) {
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

    private fun HomeCategory.searchQuery(): String =
        when (this) {
            HomeCategory.FOR_YOU, HomeCategory.ALL -> HOME_SEARCH_QUERY
            HomeCategory.MUSIC -> "музика"
            HomeCategory.GAMING -> "ігри"
            HomeCategory.MOVIES -> "фільми"
            HomeCategory.SPORTS -> "спорт"
            HomeCategory.NEWS -> "новини"
            HomeCategory.TECHNOLOGY -> "технології"
        }

    /**
     * Paged loads for the Shorts mediator. YouTube returns the reel shelf with
     * a continuation token, so every page is exactly one browse call.
     */
    override suspend fun getShorts(page: Int): List<Video> =
        withContext(Dispatchers.IO) {
            val feed =
                ContentParser.parseFeed(
                    contentClient.search(SHORTS_QUERY, null, if (page > 1) shortsContinuation else null),
                )
            shortsContinuation = feed.continuation
            // reelItemRenderer carries no lengthText, so 0 means "unknown", not "invalid".
            feed.items
                .filter { it.duration in 0L..SHORTS_MAX_SECONDS && !it.isLive }
                .toFilteredVideos()
                .take(SHORTS_PER_PAGE)
        }

    /** One lightweight browse for the Home reel shelf; does not move the Shorts cursor. */
    override suspend fun getShortsShelf(limit: Int): List<Video> =
        withContext(Dispatchers.IO) {
            ContentParser
                .parseFeed(contentClient.search(SHORTS_QUERY, null, null))
                .items
                .filter { it.duration in 0L..SHORTS_SHELF_MAX_SECONDS && !it.isLive }
                .toFilteredVideos()
                .take(limit.coerceAtLeast(0))
        }

    /**
     * Page 1 starts a fresh query; later pages reuse the token returned with
     * the previous page. Without a token the caller sees an empty page, which
     * tells the mediator to stop instead of re-appending the same videos.
     */
    override suspend fun search(
        query: String,
        page: Int,
    ): List<Video> =
        withContext(Dispatchers.IO) {
            searchJson(query, page).toFilteredVideos().take(HOME_FEED_LIMIT)
        }

    /** Shared by search() and refreshFeed() so both use the same page cursor. */
    private suspend fun searchJson(
        query: String,
        page: Int,
    ): String {
        val continuation =
            if (page <= 1) {
                // A fresh page 1 invalidates only this query's cursor.
                searchContinuations.remove(query)
                null
            } else {
                searchContinuations[query] ?: return ""
            }
        val response = contentClient.search(query, null, continuation)
        val next = ContentParser.parseFeed(response).continuation?.takeIf { it.isNotBlank() }
        if (next != null) {
            searchContinuations[query] = next
        } else {
            searchContinuations.remove(query)
        }
        return response
    }

    override suspend fun searchSuggestions(query: String): List<String> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) {
                emptyList()
            } else {
                runCatching { contentClient.suggestions(query) }.getOrDefault(emptyList())
            }
        }

    override suspend fun getVideoById(videoId: String): Video =
        withContext(Dispatchers.IO) {
            dao.getVideoById(videoId)?.toDomain() ?: loadVideo(videoId)
        }

    private suspend fun loadVideo(videoId: String): Video {
        val parsed =
            ContentParser.parseVideoDetails(contentClient.player(videoId))
                ?: throw IllegalStateException("Unexpected payload for video $videoId")
        return listOf(parsed.video).persistVideos().first()
    }

    /**
     * Playback always comes from a fresh player response: signed stream URLs
     * expire, so cached Room metadata is never used to resolve a source.
     */
    override suspend fun getVideoDetails(
        videoId: String,
        maxHeight: Int,
    ): VideoDetails =
        withContext(Dispatchers.IO) {
            val response = contentClient.player(videoId)
            val parsed =
                ContentParser.parseVideoDetails(response)
                    ?: throw IllegalStateException("Unexpected payload for video $videoId")
            val playback = resolvePlayback(response, maxHeight, parsed.video.isLive) ?: parsed.playback
            val related = parsed.related.toFilteredVideos()
            val video = listOf(parsed.video).persistVideos().first()
            VideoDetails(
                video = video,
                description = parsed.description,
                related = related,
                playback = playback,
            )
        }

    override suspend fun getChannelVideos(
        channelId: String,
        continuation: String?,
    ): ChannelVideosPage =
        withContext(Dispatchers.IO) {
            val feed = ContentParser.parseFeed(contentClient.browse(channelId, continuation))
            ChannelVideosPage(
                videos = feed.items.toFilteredVideos(),
                continuation = feed.continuation?.takeIf { it.isNotBlank() },
            )
        }

    override suspend fun getChannelDetails(channelId: String): Channel =
        withContext(Dispatchers.IO) {
            channelDao.getChannelById(channelId)?.toDomain()
                ?: ContentParser
                    .parseChannel(contentClient.browse(channelId))
                    .channel
                    .toEntity()
                    .let { entity ->
                        channelDao.insert(entity)
                        entity.toDomain()
                    }
        }

    override suspend fun getComments(
        videoId: String,
        continuation: String?,
    ): CommentPage =
        withContext(Dispatchers.IO) {
            val token =
                continuation?.takeIf { it.isNotBlank() }
                    ?: ContentParser.parseComments(contentClient.player(videoId)).continuation
                    ?: throw IllegalStateException("No comments continuation for video $videoId")
            val page = ContentParser.parseComments(contentClient.next(token))
            CommentPage(
                comments = page.comments,
                continuation = page.continuation?.takeIf { it.isNotBlank() },
                totalCount = page.totalCount,
            )
        }

    /**
     * YouTube mixes reels into ordinary search results, and the Home feed is
     * search-backed now. Shorts have their own shelf and tab, so keep them out
     * of Home; live clips are allowed even though their duration is unknown.
     */
    private fun List<Video>.withoutShorts(): List<Video> =
        filter { it.isLive || it.duration > SHORTS_MAX_SECONDS }

    /** Applies the local content filters and writes only metadata to Room. */
    private suspend fun String.toFilteredVideos(): List<Video> =
        ContentParser.parseFeed(this).items.toFilteredVideos()

    private suspend fun List<Video>.toFilteredVideos(): List<Video> =
        applyContentSettings(settingsRepository.settings.first()).persistVideos()

    private suspend fun List<Video>.persistVideos(): List<Video> {
        val unique = distinctBy { it.id }
        if (unique.isNotEmpty()) {
            dao.insertAll(unique.map { it.toEntity() })
        }
        return unique
    }

    /**
     * Task 2's parser prefers the highest muxed progressive track. The quality
     * cap is applied here instead, on the raw player response: an adaptive
     * H.264/AV1 video at or below the cap plus an audio track is preferred, with
     * progressive and HLS as fallbacks. Ad breaks are never read.
     */
    private fun resolvePlayback(
        json: String,
        maxHeight: Int,
        live: Boolean,
    ): PlaybackSource? {
        val root = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonObject ?: return null
        val streaming = root["streamingData"] as? JsonObject ?: return null
        val hls = streaming.string("hlsManifestUrl")?.takeIf { it.isNotBlank() }
        if (live && hls != null) return PlaybackSource.Hls(hls)

        val cap = maxHeight.takeIf { it > 0 } ?: AppSettings.DEFAULT_QUALITY
        val adaptive = streaming.formats("adaptiveFormats")
        val cappedVideo =
            adaptive
                .filter { it.isPlayableVideo() && it.height() <= cap }
                .sortedWith(compareByDescending<JsonObject> { it.codecRank() }.thenByDescending { it.height() })
                .firstOrNull()
        val audio = adaptive.filter { it.isPlayableAudio() }
        if (cappedVideo != null && audio.isNotEmpty()) {
            val urls = audio.map { checkNotNull(it.string("url")) }.distinct()
            return PlaybackSource.Merged(checkNotNull(cappedVideo.string("url")), urls.first(), urls.drop(1))
        }

        val progressive =
            streaming
                .formats("formats")
                .filter { it.isPlayableVideo() && it.height() <= cap }
                .maxByOrNull { it.height() }
        if (progressive != null) {
            return PlaybackSource.Progressive(checkNotNull(progressive.string("url")))
        }
        return hls?.let(PlaybackSource::Hls)
    }

    private fun JsonObject.formats(key: String): List<JsonObject> =
        (this[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    private fun JsonObject.isPlayableVideo(): Boolean =
        string("mimeType")?.startsWith("video/", ignoreCase = true) == true && !string("url").isNullOrBlank()

    private fun JsonObject.isPlayableAudio(): Boolean =
        string("mimeType")?.startsWith("audio/", ignoreCase = true) == true && !string("url").isNullOrBlank()

    /** H.264 first, then AV1, then other codecs; unknown resolutions lose to capped ones. */
    private fun JsonObject.codecRank(): Int =
        when {
            string("mimeType")?.contains("avc1", ignoreCase = true) == true -> 3
            string("mimeType")?.contains("av01", ignoreCase = true) == true -> 2
            else -> 1
        }

    private fun JsonObject.height(): Int =
        int("height") ?: string("qualityLabel")?.takeWhile { it.isDigit() }?.toIntOrNull() ?: Int.MAX_VALUE

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun Video.toEntity(): VideoEntity =
        VideoEntity(
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

    private fun Channel.toEntity(): ChannelEntity =
        ChannelEntity(
            id = id,
            name = name,
            avatarUrl = avatarUrl,
            subscriberCount = subscriberCount,
            totalVideos = totalVideos,
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

    /**
     * Room's CursorWindow rejects rows above ~2 MB, and a raw InnerTube feed
     * response can exceed that. Cache only the normalised video metadata.
     */
    private fun List<Video>.toCacheJson(): String =
        buildJsonArray {
            this@toCacheJson.forEach { video ->
                add(
                    buildJsonObject {
                        put("id", video.id)
                        put("title", video.title)
                        put("channelName", video.channelName)
                        put("channelId", video.channelId)
                        put("thumbnailUrl", video.thumbnailUrl)
                        put("duration", video.duration)
                        put("views", video.views)
                        put("uploadedAt", video.uploadedAt)
                        put("isLive", video.isLive)
                        put("authorAvatarUrl", video.authorAvatarUrl)
                    },
                )
            }
        }.toString()

    private fun decodeCachedFeed(json: String): List<Video> =
        runCatching {
            Json.parseToJsonElement(json).jsonArray.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                Video(
                    id = id,
                    title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    channelName = obj["channelName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    channelId = obj["channelId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    thumbnailUrl = obj["thumbnailUrl"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    duration = obj["duration"]?.jsonPrimitive?.longOrNull ?: 0L,
                    views = obj["views"]?.jsonPrimitive?.longOrNull ?: 0L,
                    uploadedAt = obj["uploadedAt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    isLive = obj["isLive"]?.jsonPrimitive?.booleanOrNull ?: false,
                    authorAvatarUrl = obj["authorAvatarUrl"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            }
        }.getOrElse { emptyList() }

    private companion object {
        const val HOME_SEARCH_QUERY = "популярні відео"

        /** InnerTube search filter: 4–20 minutes, keeps Shorts out server-side. */
        const val HOME_SEARCH_PARAMS = "EgIYAw=="
        const val SHORTS_QUERY = "#shorts"
        const val BROWSE_NOTIFICATIONS = "FEnotifications"
        const val HOME_FEED_LIMIT = 60
        const val HISTORY_SIGNAL_LIMIT = 500
        const val DAY_MS = 86_400_000L
        const val SHORTS_MAX_SECONDS = 180L
        const val SHORTS_SHELF_MAX_SECONDS = 60L
        const val SHORTS_PER_PAGE = 28
    }
}
