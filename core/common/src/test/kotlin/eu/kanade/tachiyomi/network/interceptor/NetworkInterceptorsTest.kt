package eu.kanade.tachiyomi.network.interceptor

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class NetworkInterceptorsTest {

    private val defaultUserAgent = "Mozilla/5.0 (Ephyra Test Client)"
    private val defaultUserAgentProvider: () -> String = { defaultUserAgent }

    // ── UserAgentInterceptor Tests ───────────────────────────────────────────

    @Test
    fun `UserAgentInterceptor preserves existing User-Agent header`() {
        val interceptor = UserAgentInterceptor(defaultUserAgentProvider)
        val mockChain = mockk<Interceptor.Chain>()
        val mockResponse = mockk<Response>(relaxed = true)

        val originalRequest = Request.Builder()
            .url("https://example.com")
            .header("User-Agent", "CustomBrowser/1.0")
            .build()

        every { mockChain.request() } returns originalRequest
        val requestSlot = slot<Request>()
        every { mockChain.proceed(capture(requestSlot)) } returns mockResponse

        val result = interceptor.intercept(mockChain)

        assertEquals(mockResponse, result)
        verify(exactly = 1) { mockChain.proceed(any()) }
        assertEquals("CustomBrowser/1.0", requestSlot.captured.header("User-Agent")) {
            "Existing User-Agent header must remain unaltered"
        }
    }

    @Test
    fun `UserAgentInterceptor injects default User-Agent if none is present`() {
        val interceptor = UserAgentInterceptor(defaultUserAgentProvider)
        val mockChain = mockk<Interceptor.Chain>()
        val mockResponse = mockk<Response>(relaxed = true)

        val originalRequest = Request.Builder()
            .url("https://example.com")
            .build() // No UA header

        every { mockChain.request() } returns originalRequest
        val requestSlot = slot<Request>()
        every { mockChain.proceed(capture(requestSlot)) } returns mockResponse

        val result = interceptor.intercept(mockChain)

        assertEquals(mockResponse, result)
        verify(exactly = 1) { mockChain.proceed(any()) }
        assertEquals(defaultUserAgent, requestSlot.captured.header("User-Agent")) {
            "Default User-Agent header must be injected when missing"
        }
    }

    @Test
    fun `UserAgentInterceptor overwrites empty User-Agent with default value`() {
        val interceptor = UserAgentInterceptor(defaultUserAgentProvider)
        val mockChain = mockk<Interceptor.Chain>()
        val mockResponse = mockk<Response>(relaxed = true)

        val originalRequest = Request.Builder()
            .url("https://example.com")
            .header("User-Agent", "") // Empty UA header
            .build()

        every { mockChain.request() } returns originalRequest
        val requestSlot = slot<Request>()
        every { mockChain.proceed(capture(requestSlot)) } returns mockResponse

        val result = interceptor.intercept(mockChain)

        assertEquals(mockResponse, result)
        verify(exactly = 1) { mockChain.proceed(any()) }
        assertEquals(defaultUserAgent, requestSlot.captured.header("User-Agent")) {
            "Empty User-Agent header must be overwritten with default provider value"
        }
    }

    // ── UncaughtExceptionInterceptor Tests ───────────────────────────────────

    @Test
    fun `UncaughtExceptionInterceptor proceeds normally on success`() {
        val interceptor = UncaughtExceptionInterceptor()
        val mockChain = mockk<Interceptor.Chain>()
        val mockResponse = mockk<Response>(relaxed = true)
        val mockRequest = Request.Builder().url("https://example.com").build()

        every { mockChain.request() } returns mockRequest
        every { mockChain.proceed(mockRequest) } returns mockResponse

        val result = interceptor.intercept(mockChain)

        assertEquals(mockResponse, result)
        verify(exactly = 1) { mockChain.proceed(mockRequest) }
    }

    @Test
    fun `UncaughtExceptionInterceptor propagates IOException without modifications`() {
        val interceptor = UncaughtExceptionInterceptor()
        val mockChain = mockk<Interceptor.Chain>()
        val mockRequest = Request.Builder().url("https://example.com").build()
        val originalException = IOException("Network connection lost")

        every { mockChain.request() } returns mockRequest
        every { mockChain.proceed(mockRequest) } throws originalException

        val thrown = assertThrows(IOException::class.java) {
            interceptor.intercept(mockChain)
        }

        assertEquals(originalException, thrown) {
            "Standard IOExceptions must pass through unchanged"
        }
    }

    @Test
    fun `UncaughtExceptionInterceptor catches RuntimeException and wraps in IOException`() {
        val interceptor = UncaughtExceptionInterceptor()
        val mockChain = mockk<Interceptor.Chain>()
        val mockRequest = Request.Builder().url("https://example.com").build()
        val runtimeException = NullPointerException("Unexpected null reference")

        every { mockChain.request() } returns mockRequest
        every { mockChain.proceed(mockRequest) } throws runtimeException

        val thrown = assertThrows(IOException::class.java) {
            interceptor.intercept(mockChain)
        }

        assertEquals(runtimeException, thrown.cause) {
            "The wrapped exception cause must match the originally thrown RuntimeException"
        }
        assertTrue(thrown.message?.contains("NullPointerException") == true || thrown.cause == runtimeException) {
            "RuntimeExceptions must be wrapped inside a non-fatal IOException boundary"
        }
    }

    // ── CloudflareInterceptor Tests ──────────────────────────────────────────

    @Test
    fun `CloudflareInterceptor intercepts 403 with cf-ray and turnstile challenge`() {
        val mockContext = mockk<android.content.Context>(relaxed = true)
        val mockCookieJar = mockk<eu.kanade.tachiyomi.network.AndroidCookieJar>(relaxed = true)
        val interceptor = CloudflareInterceptor(mockContext, mockCookieJar, defaultUserAgentProvider)

        val htmlBody = """
            <!DOCTYPE html>
            <html>
            <body>
                <div id="turnstile-wrapper">
                    <div id="cf-turnstile"></div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val request = Request.Builder().url("https://example.com/source").build()
        val response = Response.Builder()
            .request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(403)
            .message("Forbidden")
            .header("cf-ray", "8b123456789-DFW")
            .body(htmlBody.toResponseBody("text/html".toMediaType()))
            .build()

        assertTrue(interceptor.shouldIntercept(response)) {
            "CloudflareInterceptor must intercept 403 responses with Cloudflare cf-ray and Turnstile challenge"
        }
    }

    @Test
    fun `CloudflareInterceptor ignores normal 200 OK responses`() {
        val mockContext = mockk<android.content.Context>(relaxed = true)
        val mockCookieJar = mockk<eu.kanade.tachiyomi.network.AndroidCookieJar>(relaxed = true)
        val interceptor = CloudflareInterceptor(mockContext, mockCookieJar, defaultUserAgentProvider)

        val request = Request.Builder().url("https://example.com/source").build()
        val response = Response.Builder()
            .request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("Server", "cloudflare")
            .body("<html><body>OK</body></html>".toResponseBody("text/html".toMediaType()))
            .build()

        org.junit.jupiter.api.Assertions.assertFalse(interceptor.shouldIntercept(response)) {
            "CloudflareInterceptor must not intercept normal 200 responses"
        }
    }

    @Test
    fun `CloudflareInterceptor ignores non-Cloudflare 403 responses`() {
        val mockContext = mockk<android.content.Context>(relaxed = true)
        val mockCookieJar = mockk<eu.kanade.tachiyomi.network.AndroidCookieJar>(relaxed = true)
        val interceptor = CloudflareInterceptor(mockContext, mockCookieJar, defaultUserAgentProvider)

        val request = Request.Builder().url("https://example.com/source").build()
        val response = Response.Builder()
            .request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(403)
            .message("Forbidden")
            .header("Server", "Apache")
            .body("<html><body>Forbidden</body></html>".toResponseBody("text/html".toMediaType()))
            .build()

        org.junit.jupiter.api.Assertions.assertFalse(interceptor.shouldIntercept(response)) {
            "CloudflareInterceptor must not intercept non-Cloudflare 403 responses"
        }
    }

    // ── RateLimitBackoffInterceptor Tests ────────────────────────────────────

    @Test
    fun `RateLimitBackoffInterceptor proceeds normally on 200 OK`() {
        val interceptor = RateLimitBackoffInterceptor()
        val mockChain = mockk<Interceptor.Chain>()
        val request = Request.Builder().url("https://api.source.com/manga").build()
        val response = Response.Builder()
            .request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()

        every { mockChain.request() } returns request
        every { mockChain.proceed(request) } returns response

        val result = interceptor.intercept(mockChain)
        assertEquals(200, result.code)
    }

    @Test
    fun `RateLimitBackoffInterceptor records 429 and blocks immediate subsequent requests`() {
        val interceptor = RateLimitBackoffInterceptor()
        val mockChain = mockk<Interceptor.Chain>()
        val request = Request.Builder().url("https://rate-limited.com/chapter").build()
        val response429 = Response.Builder()
            .request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .header("Retry-After", "30")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()

        every { mockChain.request() } returns request
        every { mockChain.proceed(request) } returns response429

        // First request receives 429
        val result = interceptor.intercept(mockChain)
        assertEquals(429, result.code)

        // Second request to same host within 30s backoff should fail fast
        val ex = assertThrows(IOException::class.java) {
            interceptor.intercept(mockChain)
        }
        assertTrue(ex.message!!.contains("rate-limit active for rate-limited.com")) {
            "Should throw IOException indicating active rate-limit backoff"
        }
    }

    @Test
    fun `RateLimitBackoffInterceptor backoff is scoped per host`() {
        val interceptor = RateLimitBackoffInterceptor()
        val chainHostA = mockk<Interceptor.Chain>()
        val chainHostB = mockk<Interceptor.Chain>()

        val requestA = Request.Builder().url("https://host-a.com/page").build()
        val requestB = Request.Builder().url("https://host-b.com/page").build()

        val response429 = Response.Builder()
            .request(requestA)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .header("Retry-After", "60")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()

        val response200 = Response.Builder()
            .request(requestB)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()

        every { chainHostA.request() } returns requestA
        every { chainHostA.proceed(requestA) } returns response429

        every { chainHostB.request() } returns requestB
        every { chainHostB.proceed(requestB) } returns response200

        // Host A gets 429
        interceptor.intercept(chainHostA)

        // Host B should NOT be blocked
        val resultB = interceptor.intercept(chainHostB)
        assertEquals(200, resultB.code)
    }
}
