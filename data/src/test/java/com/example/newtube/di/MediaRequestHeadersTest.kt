package com.example.newtube.di

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaRequestHeadersTest {
    @Test
    fun `media requests carry the Android client identity`() {
        val h = MediaRequestHeaders.headers()
        assertEquals("com.google.android.youtube/20.10.38 (Linux; U; Android 12; en_US) gzip", h["User-Agent"])
        assertEquals("https://www.youtube.com/", h["Referer"])
        assertEquals("https://www.youtube.com", h["Origin"])
    }
}
