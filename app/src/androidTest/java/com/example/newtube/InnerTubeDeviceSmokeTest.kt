package com.example.newtube

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.newtube.innertube.ContentConfig
import com.example.newtube.innertube.ContentConfigRepository
import com.example.newtube.innertube.YouTubeContentClient
import com.example.newtube.innertube.bundledContentConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Live check from the phone: the client chain must work on a mobile IP too. */
@RunWith(AndroidJUnit4::class)
class InnerTubeDeviceSmokeTest {
    private val bundled = bundledContentConfig()

    private val repository =
        object : ContentConfigRepository {
            override val config: Flow<ContentConfig> = MutableStateFlow(this@InnerTubeDeviceSmokeTest.bundled)

            override suspend fun refresh(): Result<ContentConfig> = Result.success(bundled)

            override fun bundled(): ContentConfig = bundled

            override fun decodeConfig(text: String): ContentConfig? = null
        }

    private val client =
        YouTubeContentClient(
            client =
                OkHttpClient
                    .Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build(),
            config = repository,
        )

    @Test
    fun playerSearchAndBrowseUseTheClientChain() =
        runBlocking {
            val player = client.player("dQw4w9WgXcQ")
            assertTrue(player.contains(""""status":"OK""""))
            assertTrue(player.isNotBlank())
            println("SMOKE player client=${client.lastSuccessfulClient}")

            // browse/search carry no playabilityStatus; an OK response means the
            // chain returned a non-empty body instead of exhausting its clients.
            val search = client.search("test")
            assertTrue(search.isNotBlank())
            println("SMOKE search client=${client.lastSuccessfulClient}")

            val browse = client.browse("FEtrending")
            assertTrue(browse.isNotBlank())
            println("SMOKE browse client=${client.lastSuccessfulClient}")

            assertNotNull(client.lastSuccessfulClient)
        }
}
