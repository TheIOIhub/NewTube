package com.example.newtube.usecase

import com.example.newtube.model.FeedSignals
import com.example.newtube.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankFeedTest {
    @Test
    fun `watched subscribed Ukrainian channel outranks unwatched US channel`() {
        val ua = video("ua", "Новини України", "UCua")
        val us = video("us", "Breaking news", "UCus")
        val signals =
            FeedSignals(
                channelAffinity = mapOf("UCua" to 1f),
                subscribedChannelIds = setOf("UCua"),
                preferredLanguage = "uk",
            )

        assertEquals(listOf("ua", "us"), rankFeed(listOf(us, ua), signals).map { it.id })
    }

    @Test
    fun `equal scores preserve the input order`() {
        val first = video("1", "Video one", "UC1")
        val second = video("2", "Video two", "UC2")
        val signals = FeedSignals(emptyMap(), emptySet(), "uk")

        assertEquals(listOf("1", "2"), rankFeed(listOf(first, second), signals).map { it.id })
    }

    @Test
    fun `at most three videos per channel survive`() {
        val videos = (1..6).map { video("$it", "Video $it", "UCsame") }
        val signals = FeedSignals(emptyMap(), emptySet(), "uk")

        assertEquals(3, rankFeed(videos, signals).size)
    }

    @Test
    fun `empty signals still order Ukrainian titles first and never return empty`() {
        val ua = video("ua", "Новини України", "UCua")
        val us = video("us", "Breaking news", "UCus")
        val signals = FeedSignals(emptyMap(), emptySet(), "uk")

        val result = rankFeed(listOf(us, ua), signals)

        assertEquals(listOf("ua", "us"), result.map { it.id })
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `closer affinity beats weaker affinity when language matches`() {
        val recent = video("recent", "Новини", "UCrecent")
        val old = video("old", "Новини тижня", "UCold")
        val signals =
            FeedSignals(
                channelAffinity = mapOf("UCrecent" to 0.9f, "UCold" to 0.1f),
                subscribedChannelIds = emptySet(),
                preferredLanguage = "uk",
            )

        assertEquals(listOf("recent", "old"), rankFeed(listOf(old, recent), signals).map { it.id })
    }

    private fun video(
        id: String,
        title: String,
        channelId: String,
    ) = Video(
        id = id,
        title = title,
        channelName = channelId,
        channelId = channelId,
        thumbnailUrl = "https://example.com/$id.jpg",
        duration = 120L,
        views = 100L,
        uploadedAt = "1 day ago",
        isLive = false,
    )
}
