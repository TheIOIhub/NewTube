package com.example.newtube.di

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCacheModuleTest {
    @Test
    fun maxBytesIs256MiB() {
        assertEquals(268_435_456L, PLAYBACK_CACHE_MAX_BYTES)
    }
}
