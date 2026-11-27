package com.example.newtube.implementation

import com.example.newtube.implementation.VideoContentFilter.applyContentSettings
import com.example.newtube.model.AppSettings
import com.example.newtube.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoContentFilterTest {
    @Test
    fun `age restricted content is dropped by default`() {
        val videos = listOf(video("safe"), video("adult", title = "Adult video"))

        assertEquals(listOf("safe"), videos.applyContentSettings(AppSettings()).map { it.id })
    }

    @Test
    fun `age restricted content is kept when enabled`() {
        val videos = listOf(video("safe"), video("adult", title = "Adult video"))

        assertEquals(
            listOf("safe", "adult"),
            videos
                .applyContentSettings(AppSettings(ageRestrictedContent = true))
                .map { it.id },
        )
    }

    @Test
    fun `safe mode drops denylisted titles even when age restricted content is enabled`() {
        val videos = listOf(video("ok"), video("bad", title = "Adult video 18+"))

        val result =
            videos.applyContentSettings(
                AppSettings(ageRestrictedContent = true, safeMode = true),
            )

        assertEquals(listOf("ok"), result.map { it.id })
    }

    @Test
    fun `safe mode keeps neutral Ukrainian titles`() {
        val videos = listOf(video("ua", title = "Новини України"))

        val result = videos.applyContentSettings(AppSettings(safeMode = true))

        assertTrue(result.single().title == "Новини України")
    }

    private fun video(
        id: String,
        title: String = "Video $id",
    ) = Video(
        id = id,
        title = title,
        channelName = "Author",
        channelId = "UC$id",
        thumbnailUrl = "https://example.invalid/$id.jpg",
        duration = 60L,
        views = 0L,
        uploadedAt = "",
        isLive = false,
    )
}
