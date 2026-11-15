package com.example.newtube.ui.common

import com.example.newtube.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoMetaLineTest {
    @Test
    fun `broken trending metadata hides views and relative time`() {
        val meta = videoMetaLine(video(duration = 0, views = 0, uploadedAt = "0 seconds ago"))

        assertFalse(meta.showViews)
        assertNull(meta.uploadedAt)
    }

    @Test
    fun `a real new video keeps its zero views when it has a duration`() {
        val meta = videoMetaLine(video(duration = 120, views = 0, uploadedAt = "1 hour ago"))

        assertTrue(meta.showViews)
        assertEquals("1 hour ago", meta.uploadedAt)
    }

    @Test
    fun `blank upload time is omitted`() {
        val meta = videoMetaLine(video(duration = 60, views = 5, uploadedAt = ""))

        assertTrue(meta.showViews)
        assertNull(meta.uploadedAt)
    }

    @Test
    fun `a normal video keeps every segment`() {
        val meta = videoMetaLine(video(duration = 300, views = 1_200_000, uploadedAt = "2 days ago"))

        assertTrue(meta.showViews)
        assertEquals("2 days ago", meta.uploadedAt)
    }

    private fun video(duration: Long, views: Long, uploadedAt: String) =
        Video(
            id = "v1",
            title = "Video",
            channelName = "Channel",
            channelId = "UC1",
            thumbnailUrl = "https://example.com/thumb.jpg",
            duration = duration,
            views = views,
            uploadedAt = uploadedAt,
            isLive = false,
        )
}
