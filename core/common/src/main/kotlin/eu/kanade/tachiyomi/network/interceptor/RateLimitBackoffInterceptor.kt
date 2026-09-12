package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * An OkHttp interceptor that records HTTP 429 Too Many Requests responses and enforces
 * a backoff window per host before allowing subsequent requests to proceed.
 *
 * If a request to a rate-limited host arrives while a brief backoff (<= 5s) remains,
 * the thread pauses briefly until the window expires. For longer backoffs, an [IOException]
 * is thrown immediately so thread pools are not blocked.
 */
class RateLimitBackoffInterceptor(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) : Interceptor {

    private val hostBackoffs = ConcurrentHashMap<String, Long>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        // Check if this host is currently in a backoff window
        val backoffUntil = hostBackoffs[host]
        if (backoffUntil != null) {
            val now = clock()
            val remainingMs = backoffUntil - now
            if (remainingMs > 0) {
                if (remainingMs <= MAX_SYNC_WAIT_MS) {
                    try {
                        Thread.sleep(remainingMs)
                    } catch (e: InterruptedException) {
                        throw IOException("Rate-limit backoff interrupted for $host", e)
                    }
                    hostBackoffs.remove(host)
                } else {
                    val waitSec = (remainingMs + 999) / 1000
                    throw IOException("HTTP 429 rate-limit active for $host. Retry after ${waitSec}s")
                }
            } else {
                hostBackoffs.remove(host)
            }
        }

        val response = chain.proceed(request)

        if (response.code == 429) {
            val backoffDurationMs = parseRetryAfter(response) ?: DEFAULT_BACKOFF_MS
            val clampedMs = min(backoffDurationMs, MAX_BACKOFF_MS)
            hostBackoffs[host] = clock() + clampedMs
        }

        return response
    }

    private fun parseRetryAfter(response: Response): Long? {
        val header = response.header("Retry-After")?.trim() ?: return null
        header.toLongOrNull()?.let { seconds ->
            if (seconds > 0) return TimeUnit.SECONDS.toMillis(seconds)
        }
        val dateHeader = response.headers.getDate("Retry-After")
        if (dateHeader != null) {
            val diffMs = dateHeader.time - System.currentTimeMillis()
            if (diffMs > 0) return diffMs
        }
        return null
    }

    companion object {
        private const val DEFAULT_BACKOFF_MS = 5_000L
        private const val MAX_SYNC_WAIT_MS = 500L
        private const val MAX_BACKOFF_MS = 300_000L // 5 minutes
    }
}
