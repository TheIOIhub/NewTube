package com.example.newtube.usecase

import com.example.newtube.model.FeedSignals
import com.example.newtube.model.Video

/**
 * Deterministic, explainable ranking of an already regionalised feed.
 * No Android imports: this is pure Kotlin so it can be unit-tested on the JVM.
 */
fun rankFeed(
    videos: List<Video>,
    signals: FeedSignals,
): List<Video> {
    if (videos.isEmpty()) return emptyList()
    val maxViews = videos.maxOf { it.views.coerceAtLeast(0) }.toFloat().coerceAtLeast(1f)
    val ranked =
        videos
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<Video>> { score(it.value, signals, maxViews) }
                    .thenBy { it.index },
            ).map { it.value }

    val perChannel = mutableMapOf<String, Int>()
    return ranked
        .filter { video ->
            val count = perChannel[video.channelId] ?: 0
            val allowed = count < MAX_PER_CHANNEL
            perChannel[video.channelId] = count + 1
            allowed
        }.take(MAX_FEED_ITEMS)
}

private fun score(
    video: Video,
    signals: FeedSignals,
    maxViews: Float,
): Float {
    val affinity = signals.channelAffinity[video.channelId] ?: 0f
    val subscribed = if (video.channelId in signals.subscribedChannelIds) 1f else 0f
    val language = if (languageMatches(video, signals.preferredLanguage)) 1f else 0f
    val views = video.views.coerceAtLeast(0) / maxViews
    return 2f * affinity + subscribed + 1.5f * language + 0.5f * views
}

private fun languageMatches(
    video: Video,
    preferredLanguage: String,
): Boolean {
    if (preferredLanguage !in setOf("uk", "ru")) return false
    val text = "${video.title} ${video.channelName}"
    return text.any { it in '\u0400'..'\u04FF' }
}

private const val MAX_PER_CHANNEL = 3
private const val MAX_FEED_ITEMS = 60
