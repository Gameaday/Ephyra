package eu.kanade.tachiyomi.network.interceptor

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
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
}
