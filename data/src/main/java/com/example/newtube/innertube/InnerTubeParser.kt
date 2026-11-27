package com.example.newtube.innertube

import com.example.newtube.model.RemoteVideo
import com.example.newtube.model.UserAccount
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

data class ParserResult<T>(
    val items: List<T>,
    val continuation: String?,
)

/**
 * Tolerant reader for TV InnerTube browse responses.
 *
 * The TV client answers with `tileRenderer` nodes; web clients use
 * `videoRenderer`/`lockupViewModel`. Both shapes are understood, and unknown
 * shapes simply produce fewer items instead of throwing.
 */
object InnerTubeParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseVideos(json: String): ParserResult<RemoteVideo> {
        val root = parse(json) ?: return ParserResult(emptyList(), null)
        val found = mutableListOf<RemoteVideo>()
        walk(root) { obj ->
            val tile = obj["tileRenderer"] as? JsonObject
            if (tile != null) {
                tile.toTileVideo()?.let(found::add)
            } else {
                obj.toGenericVideo()?.let(found::add)
            }
        }
        val unique = LinkedHashMap<String, RemoteVideo>()
        found.forEach { unique.putIfAbsent(it.id, it) }
        return ParserResult(unique.values.toList(), firstContinuation(root))
    }

    fun parseAccount(json: String): UserAccount? {
        val root = parse(json) ?: return null
        val accounts = mutableListOf<Pair<Boolean, UserAccount>>()
        walk(root) { obj ->
            if (obj.boolean("hasChannel") == true && obj.containsKey("accountName")) {
                val account =
                    UserAccount(
                        name = obj["accountName"]?.text().orEmpty(),
                        email = obj["accountByline"]?.text().orEmpty(),
                        avatarUrl = (obj.path("accountPhoto", "thumbnails") as? JsonArray)?.largestThumbnail().orEmpty(),
                        pageId = firstPageId(obj),
                    )
                accounts.add((obj.boolean("isSelected") == true) to account)
            }
        }
        return accounts.firstOrNull { it.first }?.second ?: accounts.firstOrNull()?.second
    }

    fun durationMs(text: String): Long {
        val parts = text.trim().split(":")
        if (parts.size !in 2..3) return 0L
        val numbers = parts.map { it.toLongOrNull() ?: return 0L }
        if (numbers.any { it < 0 }) return 0L
        return when (numbers.size) {
            2 -> numbers[0] * 60 + numbers[1]
            else -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
        } * 1000
    }

    internal fun parse(text: String): JsonElement? =
        runCatching { json.parseToJsonElement(text.removePrefix("\uFEFF")) }.getOrNull()

    internal fun walk(
        element: JsonElement,
        visit: (JsonObject) -> Unit,
    ) {
        when (element) {
            is JsonObject -> {
                visit(element)
                element.values.forEach { walk(it, visit) }
            }
            is JsonArray -> element.forEach { walk(it, visit) }
            else -> Unit
        }
    }

    private fun JsonObject.toTileVideo(): RemoteVideo? {
        val select = obj("onSelectCommand")
        val watch = select?.obj("watchEndpoint")
        val reel = select?.obj("reelWatchEndpoint")
        val id = watch?.string("videoId") ?: reel?.string("videoId") ?: return null

        val metadata = obj("metadata")?.obj("tileMetadataRenderer")
        val title = metadata?.get("title")?.text()?.takeIf { it.isNotBlank() } ?: return null
        val header = obj("header")?.obj("tileHeaderRenderer")
        val thumbnail =
            (header?.path("thumbnail", "thumbnails") as? JsonArray)?.largestThumbnail()
                ?: (reel?.path("thumbnail", "thumbnails") as? JsonArray)?.largestThumbnail()
                ?: ""

        val timeStatus =
            header?.array("thumbnailOverlays")
                .orEmpty()
                .mapNotNull { (it as? JsonObject)?.obj("thumbnailOverlayTimeStatusRenderer") }
                .firstOrNull()
        val durationText = timeStatus?.get("text")?.text().orEmpty()
        val live =
            timeStatus?.string("style") == "LIVE" ||
                durationText.equals("LIVE", ignoreCase = true) ||
                hasLiveBadge(this)

        val texts = metadata?.lineTexts().orEmpty()
        val channelName =
            texts.getOrNull(0)?.takeIf { it.isNotBlank() }
                ?: select?.obj("onLongPressCommand")
                    ?.obj("showMenuCommand")
                    ?.path("subtitle")
                    ?.text()
                    ?.substringBefore(" @")
                    .orEmpty()

        return RemoteVideo(
            id = id,
            title = title,
            channelName = channelName,
            channelId = firstChannelId(this),
            thumbnailUrl = thumbnail,
            durationMs = durationMs(durationText),
            viewsText = texts.getOrNull(1).orEmpty(),
            publishedText = texts.getOrNull(2).orEmpty(),
            isLive = live,
        )
    }

    private fun JsonObject.toGenericVideo(): RemoteVideo? {
        val id = string("videoId") ?: return null
        val titleElement = get("title") ?: get("headline") ?: return null
        val title = titleElement.text()?.takeIf { it.isNotBlank() } ?: return null
        val durationText = path("lengthText")?.text().orEmpty()
        return RemoteVideo(
            id = id,
            title = title,
            channelName = byline()?.text().orEmpty(),
            channelId = firstChannelId(this),
            thumbnailUrl = (path("thumbnail", "thumbnails") as? JsonArray)?.largestThumbnail().orEmpty(),
            durationMs = durationMs(durationText),
            viewsText = path("viewCountText")?.text().orEmpty(),
            publishedText = path("publishedTimeText")?.text().orEmpty(),
            isLive = boolean("isLive") == true || hasLiveBadge(this),
        )
    }

    private fun JsonObject.byline(): JsonElement? =
        get("shortBylineText") ?: get("longBylineText") ?: get("ownerText")

    private fun JsonObject.lineTexts(): List<String> =
        array("lines").orEmpty().flatMap { line ->
            (line as? JsonObject)
                ?.obj("lineRenderer")
                ?.array("items")
                .orEmpty()
                .mapNotNull { item ->
                    (item as? JsonObject)
                        ?.obj("lineItemRenderer")
                        ?.get("text")
                        ?.text()
                        ?.takeIf { it.isNotBlank() }
                }
        }

    private fun firstContinuation(root: JsonElement): String? {
        var next: String? = null
        var command: String? = null
        walk(root) { obj ->
            if (next == null) {
                next = obj.obj("nextContinuationData")?.string("continuation")
            }
            if (command == null) {
                command = obj.obj("continuationCommand")?.string("token")
            }
        }
        return next ?: command
    }

    private fun firstChannelId(root: JsonElement): String {
        var channelId: String? = null
        walk(root) { obj ->
            if (channelId == null) {
                channelId =
                    obj.obj("browseEndpoint")
                        ?.string("browseId")
                        ?.takeIf { it.startsWith("UC") }
            }
        }
        return channelId.orEmpty()
    }

    private fun firstPageId(root: JsonElement): String? {
        var pageId: String? = null
        walk(root) { obj ->
            if (pageId == null) {
                pageId = obj.obj("pageIdToken")?.string("pageId")
            }
        }
        return pageId
    }

    private fun hasLiveBadge(root: JsonElement): Boolean {
        var live = false
        walk(root) { obj ->
            if (!live) {
                live =
                    obj.obj("metadataBadgeRenderer")
                        ?.string("style")
                        ?.contains("LIVE") == true
            }
        }
        return live
    }

    internal fun JsonElement.text(): String? =
        when (this) {
            is JsonPrimitive -> contentOrNull
            is JsonObject ->
                string("simpleText")
                    ?: array("runs")
                        ?.joinToString("") { (it as? JsonObject)?.string("text").orEmpty() }
                        ?.takeIf { it.isNotBlank() }
            else -> null
        }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    internal fun JsonObject.path(vararg keys: String): JsonElement? {
        var current: JsonElement = this
        for (key in keys) {
            current = (current as? JsonObject)?.get(key) ?: return null
        }
        return current
    }

    internal fun JsonArray.largestThumbnail(): String? =
        filterIsInstance<JsonObject>()
            .maxByOrNull { (it["width"] as? JsonPrimitive)?.intOrNull ?: 0 }
            ?.string("url")
}
