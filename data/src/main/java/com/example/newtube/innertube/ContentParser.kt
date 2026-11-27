package com.example.newtube.innertube

import com.example.newtube.model.Channel
import com.example.newtube.model.Comment
import com.example.newtube.model.CommentPage
import com.example.newtube.model.PlaybackSource
import com.example.newtube.model.Video
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class ParsedVideoDetails(
    val video: Video,
    val description: String,
    val related: List<Video>,
    val playback: PlaybackSource,
)

data class ParsedChannel(
    val channel: Channel,
    val continuation: String?,
)

/** Maps youtubei browse/search/player/next JSON into the app's domain models. */
object ContentParser {
    private val countPattern =
        Regex("""([0-9]+(?:[.,][0-9]+)?)\s*([KMB]|тис\.?|млн|млрд)?""", RegexOption.IGNORE_CASE)

    fun parseFeed(json: String): ParserResult<Video> {
        val root = InnerTubeParser.parse(json) ?: return ParserResult(emptyList(), null)
        val found = mutableListOf<Video>()
        InnerTubeParser.walk(root) { obj ->
            val renderer = obj.videoRenderer() ?: return@walk
            renderer.toVideo()?.let(found::add)
        }
        return ParserResult(found.distinctBy { it.id }, firstContinuation(root))
    }

    fun parseVideoDetails(json: String): ParsedVideoDetails? {
        val root = InnerTubeParser.parse(json) as? JsonObject ?: return null
        val details = root.at("videoDetails") as? JsonObject ?: return null
        val id = details.value("videoId")?.takeIf { it.isNotBlank() } ?: return null
        val live =
            details.value("isLiveContent") == "true" ||
                root.value("microformat", "playerMicroformatRenderer", "liveBroadcastDetails", "isLiveNow") == "true"
        val playback = playbackSource(root, live) ?: return null
        val video =
            Video(
                id = id,
                title = details.value("title").orEmpty(),
                channelName = details.value("author").orEmpty(),
                channelId = details.value("channelId").orEmpty(),
                thumbnailUrl = (details.at("thumbnail", "thumbnails") as? JsonArray)?.largest().orEmpty(),
                duration = details.value("lengthSeconds")?.toLongOrNull() ?: 0L,
                views = parseCount(details.value("viewCount")),
                uploadedAt =
                    details.value("publishDate")
                        ?: root.value("microformat", "playerMicroformatRenderer", "publishDate").orEmpty(),
                isLive = live,
                authorAvatarUrl =
                    (root.at("microformat", "playerMicroformatRenderer", "thumbnail", "thumbnails") as? JsonArray)
                        ?.largest().orEmpty(),
            )
        return ParsedVideoDetails(
            video = video,
            description = details.value("shortDescription").orEmpty().trim(),
            related = parseRelated(root),
            playback = playback,
        )
    }

    fun parseComments(json: String): CommentPage {
        val root = InnerTubeParser.parse(json) ?: return CommentPage(emptyList(), null, 0L)
        val found = mutableListOf<Comment>()
        InnerTubeParser.walk(root) { obj ->
            val renderer = obj["commentRenderer"] as? JsonObject ?: return@walk
            val id = renderer.value("commentId")?.takeIf { it.isNotBlank() } ?: return@walk
            val content = renderer.value("contentText").orEmpty()
            if (content.isBlank()) return@walk
            found.add(
                Comment(
                    id = id,
                    author = renderer.value("authorText").orEmpty(),
                    authorChannelId = renderer.firstBrowseId(),
                    authorAvatarUrl =
                        (renderer.at("authorThumbnail", "thumbnails") as? JsonArray)
                            ?.largest().orEmpty(),
                    content = content,
                    likeCount = parseCount(renderer.value("likeCountText")),
                    publishedText = renderer.value("publishedTimeText").orEmpty(),
                    isPinned = renderer.value("isPinned") == "true",
                ),
            )
        }
        return CommentPage(found.distinctBy { it.id }, firstContinuation(root), totalComments(root))
    }

    fun parseChannel(json: String): ParsedChannel {
        val root = InnerTubeParser.parse(json) ?: return ParsedChannel(emptyChannel(), null)
        val header = (root as? JsonObject)?.at("header") as? JsonObject
        val renderer =
            header?.at("channelHeaderRenderer") as? JsonObject
                ?: header?.at("browseHeaderRenderer") as? JsonObject
                ?: return ParsedChannel(emptyChannel(), firstContinuation(root))
        val channel =
            Channel(
                id = renderer.firstBrowseId(),
                name = renderer.value("title").orEmpty(),
                avatarUrl = (renderer.at("avatar", "thumbnails") as? JsonArray)?.largest().orEmpty(),
                subscriberCount = parseCount(renderer.value("subscriberCountText")),
                totalVideos = parseCount(renderer.value("videoCountText")).toInt(),
                description = renderer.value("description").orEmpty(),
            )
        return ParsedChannel(channel, firstContinuation(root))
    }

    /**
     * InnerTube wraps the same video payload in several renderer shapes, and
     * rich items wrap it one level deeper. The walk visits every shape, so
     * deduplication by video id keeps mixed feeds stable.
     */
    private fun JsonObject.videoRenderer(): JsonObject? =
        this["videoRenderer"] as? JsonObject
            ?: this["compactVideoRenderer"] as? JsonObject
            ?: (this["richItemRenderer"] as? JsonObject)?.at("content", "videoRenderer") as? JsonObject
            ?: this["reelItemRenderer"] as? JsonObject

    private fun JsonObject.toVideo(): Video? {
        val id =
            value("videoId")
                ?: value("navigationEndpoint", "reelWatchEndpoint", "videoId")?.takeIf { it.isNotBlank() }
                ?: return null
        if (id.isBlank()) return null
        val title = value("title") ?: value("headline") ?: return null
        if (title.isBlank()) return null
        val thumbnail = (at("thumbnail", "thumbnails") as? JsonArray)?.largest()
        if (thumbnail.isNullOrBlank()) return null
        return Video(
            id = id,
            title = title,
            channelName = value("ownerText") ?: value("longBylineText") ?: value("shortBylineText").orEmpty(),
            channelId = firstBrowseId(),
            thumbnailUrl = thumbnail,
            duration = value("lengthText")?.let { InnerTubeParser.durationMs(it) / 1_000L } ?: 0L,
            views = parseCount(value("viewCountText")),
            uploadedAt = value("publishedTimeText").orEmpty(),
            isLive = isLive(),
            authorAvatarUrl =
                (
                    at(
                        "channelThumbnailSupportedRenderers",
                        "channelThumbnailWithLinkRenderer",
                        "thumbnail",
                        "thumbnails",
                    ) as? JsonArray
                )?.largest().orEmpty(),
        )
    }

    private fun JsonObject.isLive(): Boolean {
        if (value("lengthText").equals("LIVE", ignoreCase = true)) return true
        if (value("viewCountText")?.contains("watching", ignoreCase = true) == true) return true
        var live = false
        InnerTubeParser.walk(this) { obj ->
            if (!live && obj.value("metadataBadgeRenderer", "style")?.contains("LIVE", ignoreCase = true) == true) {
                live = true
            }
        }
        return live
    }

    /**
     * Ad formats and ad breaks are deliberately outside this method: only
     * `streamingData.formats` and `streamingData.adaptiveFormats` are mapped.
     */
    private fun playbackSource(
        root: JsonObject,
        live: Boolean,
    ): PlaybackSource? {
        val streaming = root.at("streamingData") as? JsonObject ?: return null
        val hls = streaming.value("hlsManifestUrl")?.takeIf { it.isNotBlank() }
        if (live && hls != null) return PlaybackSource.Hls(hls)

        val progressive =
            streaming
                .formats("formats")
                .filter { !it.value("url").isNullOrBlank() }
                .maxByOrNull { it.videoScore() }
        if (progressive != null) return PlaybackSource.Progressive(checkNotNull(progressive.value("url")))

        val adaptive = streaming.formats("adaptiveFormats")
        val video =
            adaptive
                .filter { it.value("mimeType")?.startsWith("video/", ignoreCase = true) == true }
                .filter { !it.value("url").isNullOrBlank() }
                .maxByOrNull { it.videoScore() }
        val audio =
            adaptive.filter {
                it.value("mimeType")?.startsWith("audio/", ignoreCase = true) == true &&
                    !it.value("url").isNullOrBlank()
            }
        if (video != null && audio.isNotEmpty()) {
            val audioUrls = audio.map { checkNotNull(it.value("url")) }.distinct()
            return PlaybackSource.Merged(
                videoUrl = checkNotNull(video.value("url")),
                audioUrl = audioUrls.first(),
                audioAlternatives = audioUrls.drop(1),
            )
        }
        return hls?.let { PlaybackSource.Hls(it) }
    }

    private fun JsonObject.formats(key: String): List<JsonObject> =
        (at(key) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    private fun JsonObject.videoScore(): Int {
        val height = value("height")?.toIntOrNull() ?: 0
        val avc1Bonus = if (value("mimeType")?.contains("avc1", ignoreCase = true) == true) 1 else 0
        return height * 10 + avc1Bonus
    }

    private fun parseRelated(root: JsonObject): List<Video> {
        val contents = root.at("contents") ?: return emptyList()
        val found = mutableListOf<Video>()
        InnerTubeParser.walk(contents) { obj ->
            val renderer = obj.videoRenderer() ?: return@walk
            renderer.toVideo()?.let(found::add)
        }
        return found.distinctBy { it.id }
    }

    private fun firstContinuation(root: JsonElement): String? {
        var token: String? = null
        // Page continuations are preferred so a comment's reply command cannot win.
        InnerTubeParser.walk(root) { obj ->
            if (token == null) {
                token = obj.explicitContinuation() ?: obj.value("nextContinuationData", "continuation")
            }
        }
        if (token == null) {
            InnerTubeParser.walk(root) { obj ->
                if (token == null) {
                    token =
                        obj.value("continuationCommand", "token")
                            ?: obj.value("continuationEndpoint", "continuationCommand", "token")
                }
            }
        }
        return token?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.explicitContinuation(): String? {
        if (!containsKey("continuationItemRenderer")) return null
        return value("continuationItemRenderer", "continuationCommand", "token")
            ?: value("continuationItemRenderer", "continuationEndpoint", "continuationCommand", "token")
            ?: value("continuationItemRenderer", "nextContinuationData", "continuation")
    }

    private fun totalComments(root: JsonElement): Long {
        var total = 0L
        InnerTubeParser.walk(root) { obj ->
            if (total == 0L) {
                total = parseCount(obj.value("commentCount"))
                if (total == 0L) total = parseCount(obj.value("countText"))
            }
        }
        return total
    }

    private fun JsonObject.firstBrowseId(): String {
        var id = ""
        InnerTubeParser.walk(this) { obj ->
            if (id.isEmpty()) {
                val candidate = (obj.at("browseEndpoint") as? JsonObject)?.value("browseId").orEmpty()
                if (candidate.startsWith("UC")) id = candidate
            }
        }
        return id
    }

    /** Counts text such as "1.2K views" or "1,234 Comments". */
    private fun parseCount(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val match = countPattern.find(text) ?: return 0L
        val raw = match.groupValues[1]
        val normalized =
            if (raw.contains(',')) {
                val fraction = raw.substringAfter(',')
                if (fraction.length == 3) raw.replace(",", "") else raw.replace(',', '.')
            } else {
                raw
            }
        val base = normalized.toDoubleOrNull() ?: return 0L
        val multiplier =
            when (match.groupValues[2].lowercase()) {
                "k", "тис", "тис." -> 1_000.0
                "m", "млн" -> 1_000_000.0
                "b", "млрд" -> 1_000_000_000.0
                else -> 1.0
            }
        return (base * multiplier).toLong()
    }

    private fun emptyChannel(): Channel = Channel("", "", "", 0, 0, "")

    private fun JsonObject.at(vararg keys: String): JsonElement? = with(InnerTubeParser) { path(*keys) }

    private fun JsonElement.asText(): String? = with(InnerTubeParser) { text() }

    private fun JsonObject.value(vararg keys: String): String? = at(*keys)?.asText()

    private fun JsonArray.largest(): String? = with(InnerTubeParser) { largestThumbnail() }
}
