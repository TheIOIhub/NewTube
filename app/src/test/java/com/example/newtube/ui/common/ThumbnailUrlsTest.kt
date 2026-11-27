package com.example.newtube.ui.common

import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Size
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ThumbnailUrlsTest {
    private val context: Context = mockk()
    private lateinit var imageLoader: ImageLoader

    @Before
    fun setUp() {
        imageLoader = mockk(relaxed = true)
        Coil.setImageLoader(imageLoader)
    }

    @After
    fun tearDown() {
        Coil.reset()
    }

    @Test
    fun `hq and mq build YouTube thumbnail URLs`() {
        assertEquals("https://i.ytimg.com/vi/abc/hqdefault.jpg", ThumbnailUrls.hq("abc"))
        assertEquals("https://i.ytimg.com/vi/abc/mqdefault.jpg", ThumbnailUrls.mq("abc"))
    }

    @Test
    fun `prefetch enqueues one sized request per URL`() {
        runBlocking {
            val requests = mutableListOf<ImageRequest>()
            every { imageLoader.enqueue(any()) } answers {
                requests.add(firstArg<ImageRequest>())
                mockk(relaxed = true)
            }

            val urls = listOf(ThumbnailUrls.hq("abc"), ThumbnailUrls.mq("xyz"))
            ThumbnailUrls.prefetch(context, urls, sizePx = 320)

            assertEquals(urls.size, requests.size)
            assertEquals(urls, requests.map { it.data })
            requests.forEach { request ->
                assertEquals(Size(320, 320), request.sizeResolver.size())
            }
        }
    }
}
