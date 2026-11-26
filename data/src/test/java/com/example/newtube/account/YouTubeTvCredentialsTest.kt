package com.example.newtube.account

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeTvCredentialsTest {
    private val html = resource("/spike/youtube_tv_base_js.html")
    private val js = resource("/spike/base_js_client_credentials.js")
    private var now = 1_000_000L

    @Test
    fun `finds and resolves the cobalt base-js url`() {
        val url = YouTubeTvPage.findBaseJsUrl(html)
        assertEquals(
            "https://www.youtube.com/s/_/kabuki/_/js/k=kabuki.base_cr.en_US.nHfsRbOf2SY.es6.O/am=AAAAAABoCAAqAYAIwAxSEDQU/d=1/excm=main/rs=ANjRhVmdi6bcqB2lO7nivVczi6dytaDe0Q/m=base",
            url,
        )
        assertEquals("https://www.youtube.com/x.js", YouTubeTvPage.resolve("/x.js"))
    }

    @Test
    fun `finds the client credentials in the base js fixture`() {
        val c = YouTubeTvPage.findClientCredentials(js)!!
        assertEquals("861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com", c.clientId)
        assertEquals("REDACTED_SECRET", c.clientSecret)
    }

    @Test
    fun `parses the id-then-src form and the m-base form`() {
        assertTrue(YouTubeTvPage.findBaseJsUrl("""<script id="base-js" src="/a.js"></script>""")!!.endsWith("/a.js"))
        assertTrue(YouTubeTvPage.findBaseJsUrl("""s.src = '/b.js?m=base';""")!!.endsWith("m=base"))
    }

    @Test
    fun `returns null for unrelated markup`() {
        assertNull(YouTubeTvPage.findBaseJsUrl("<html></html>"))
        assertNull(YouTubeTvPage.findClientCredentials("var x = 1;"))
    }

    @Test
    fun `caches credentials for seven days then refetches`() =
        runTest {
            val store = InMemorySecretStore()
            var fetches = 0
            val provider =
                YouTubeTvCredentialsProvider(
                    fetcher = { url ->
                        fetches++
                        if (url.endsWith("/tv")) html else js
                    },
                    store = store,
                    clock = { now },
                )
            provider.get()
            provider.get()
            assertEquals(2, fetches) // tv page + base js
            now += 7 * 24 * 60 * 60 * 1000L + 1
            provider.get()
            assertEquals(4, fetches)
        }

    @Test
    fun `fetches off the calling thread because the fetcher blocks`() =
        runTest {
            val callerThread = Thread.currentThread().name
            val fetchThreads = mutableListOf<String>()
            val provider =
                YouTubeTvCredentialsProvider(
                    fetcher = { url ->
                        fetchThreads += Thread.currentThread().name
                        if (url.endsWith("/tv")) html else js
                    },
                    store = InMemorySecretStore(),
                    clock = { now },
                )
            provider.get()
            assertEquals(2, fetchThreads.size)
            assertNotEquals(callerThread, fetchThreads.first())
        }

    @Test
    fun `invalidate forces the next get to scrape again`() =
        runTest {
            var fetches = 0
            val provider =
                YouTubeTvCredentialsProvider(
                    fetcher = { url ->
                        fetches++
                        if (url.endsWith("/tv")) html else js
                    },
                    store = InMemorySecretStore(),
                    clock = { now },
                )
            provider.get()
            provider.get()
            assertEquals(2, fetches)
            provider.invalidate()
            provider.get()
            assertEquals(4, fetches)
        }

    @Test(expected = CredentialsUnavailableException::class)
    fun `an unrecognisable page fails as unavailable`() =
        runTest {
            YouTubeTvCredentialsProvider(
                fetcher = { "<html></html>" },
                store = InMemorySecretStore(),
                clock = { now },
            ).get()
        }

    @Test
    fun `credentials and tokens hide their secrets when printed`() {
        val credentials = ClientCredentials("client-id", "client-secret-value")
        val tokens = Tokens("access-value", "refresh-value", 10)
        assertTrue(!credentials.toString().contains("client-secret-value"))
        assertTrue(!tokens.toString().contains("refresh-value"))
        assertTrue(!tokens.toString().contains("access-value"))
    }

    private fun resource(path: String): String = checkNotNull(javaClass.getResource(path)).readText()
}

private class InMemorySecretStore : SecretStore {
    private val values = mutableMapOf<String, String>()

    override fun get(key: String): String? = values[key]

    override fun put(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}
