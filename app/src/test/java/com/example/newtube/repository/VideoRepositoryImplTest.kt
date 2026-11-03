package com.example.newtube.repository

import com.example.newtube.remote.VideoDto
import com.example.newtube.remote.InvidiousService
import com.example.newtube.database.VideoDao
import io.mockk.coEvery
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.any
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import com.example.newtube.implementation.VideoRepositoryImpl

class VideoRepositoryImplTest {
    private lateinit var repository: VideoRepositoryImpl
    private val service: InvidiousService = mockk()
    private val dao: VideoDao = mockk()

    @Before
    fun setUp() {
        repository = VideoRepositoryImpl(service, dao)
    }

    @Test
    fun `getTrending should return mapped videos`() = runBlocking {
        val dto = VideoDto(
            id = "1",
            title = "Test Video",
            author = "Test Author",
            thumbnail = "http://example.com/thumb.jpg",
            duration = 120L,
            viewCount = 100L,
            uploadedAt = "2024-01-01",
            live = false
        )
        coEvery { service.getTrending(page = 1) } returns listOf(dto)
        justRun { dao.insertAll(any()) }
        val result = repository.getTrending()
        assertEquals(1, result.size)
        val video = result[0]
        assertEquals("1", video.id)
        assertEquals("Test Video", video.title)
        assertEquals("Test Author", video.channelName)
    }
}
