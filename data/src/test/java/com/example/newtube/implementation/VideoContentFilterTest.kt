package com.example.newtube.implementation

import com.example.newtube.implementation.VideoContentFilter.applyContentSettings
import com.example.newtube.model.AppSettings
import com.example.newtube.remote.VideoDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoContentFilterTest {
    @Test
    fun `age restricted content is dropped by default`() {
        val videos = listOf(dto("safe"), dto("adult", familyFriendly = false))

        assertEquals(listOf("safe"), videos.applyContentSettings(AppSettings()).map { it.videoId })
    }

    @Test
    fun `age restricted content is kept when enabled`() {
        val videos = listOf(dto("safe"), dto("adult", familyFriendly = false))

        assertEquals(
            listOf("safe", "adult"),
            videos
                .applyContentSettings(AppSettings(ageRestrictedContent = true))
                .map { it.videoId },
        )
    }

    @Test
    fun `safe mode drops denylisted titles even when age restricted content is enabled`() {
        val videos = listOf(dto("ok"), dto("bad", title = "Adult video 18+"))

        val result =
            videos.applyContentSettings(
                AppSettings(ageRestrictedContent = true, safeMode = true),
            )

        assertEquals(listOf("ok"), result.map { it.videoId })
    }

    @Test
    fun `safe mode keeps neutral Ukrainian titles`() {
        val videos = listOf(dto("ua", title = "Новини України"))

        val result = videos.applyContentSettings(AppSettings(safeMode = true))

        assertTrue(result.single().title == "Новини України")
    }

    private fun dto(
        id: String,
        title: String = "Video $id",
        familyFriendly: Boolean = true,
    ) = VideoDto(videoId = id, title = title, isFamilyFriendly = familyFriendly)
}
