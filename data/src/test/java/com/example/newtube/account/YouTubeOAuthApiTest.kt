package com.example.newtube.account

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class YouTubeOAuthApiTest {
    private val server = MockWebServer()
    private val creds = ClientCredentials("id.apps.googleusercontent.com", "secret")
    private lateinit var api: YouTubeOAuthApi

    @Before
    fun setUp() {
        server.start()
        api = YouTubeOAuthApi(OkHttpClient(), server.url("/").toString())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `device code request carries the tv payload`() =
        runTest {
            server.enqueue(json("/oauth/device_code.json", 200))
            val code = api.requestDeviceCode(creds, "0011223344556677")
            assertEquals("SPPX-SFWV", code.userCode)
            assertEquals(1800, code.expiresInSeconds)
            assertEquals(5, code.intervalSeconds)
            assertEquals("https://www.google.com/device", code.verificationUrl)
            val request = server.takeRequest()
            val body = request.body.readUtf8()
            assertTrue(body.contains("\"model_name\":\"ytlr::\""))
            assertTrue(body.contains("http://gdata.youtube.com https://www.googleapis.com/auth/youtube-paid-content"))
            assertEquals(server.url("/o/oauth2/device/code").toString(), request.requestUrl.toString())
            assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
            assertEquals("en-US,en;q=0.9", request.getHeader("Accept-Language"))
        }

    @Test
    fun `poll maps granted pending slow_down denied and expired`() =
        runTest {
            server.enqueue(json("/oauth/token_success.json", 200))
            assertTrue(api.pollToken(creds, "dc") is TokenPoll.Granted)
            server.enqueue(json("/oauth/token_pending.json", 400))
            assertEquals(TokenPoll.Pending, api.pollToken(creds, "dc"))
            server.enqueue(json("/oauth/token_slow_down.json", 400))
            assertEquals(TokenPoll.SlowDown, api.pollToken(creds, "dc"))
            server.enqueue(json("/oauth/token_denied.json", 400))
            assertEquals(TokenPoll.Denied, api.pollToken(creds, "dc"))
            server.enqueue(rawJson("""{"error":"expired_token"}""", 400))
            assertEquals(TokenPoll.Expired, api.pollToken(creds, "dc"))
        }

    @Test
    fun `refresh sends the refresh grant and keeps a null refresh token`() =
        runTest {
            server.enqueue(rawJson("""{"access_token":"a2","expires_in":66928,"token_type":"Bearer"}""", 200))
            val t = api.refresh(creds, "r1")
            assertEquals("a2", t.accessToken)
            assertNull(t.refreshToken)
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("\"grant_type\":\"refresh_token\""))
            assertTrue(body.contains("\"refresh_token\":\"r1\""))
        }

    @Test
    fun `refresh maps invalid_grant to a typed exception`() =
        runTest {
            server.enqueue(rawJson("""{"error":"invalid_grant"}""", 400))
            try {
                api.refresh(creds, "stale")
                throw AssertionError("expected InvalidGrantException")
            } catch (expected: InvalidGrantException) {
                Unit
            }
        }

    private fun json(
        path: String,
        code: Int,
    ): MockResponse =
        rawJson(resource(path), code)

    private fun rawJson(
        body: String,
        code: Int,
    ): MockResponse =
        MockResponse()
            .setResponseCode(code)
            .setBody(body)
            .addHeader("Content-Type", "application/json")

    private fun resource(path: String): String = checkNotNull(javaClass.getResource(path)).readText()
}
