package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

/**
 * An OkHttp interceptor that enforces a per-host backoff window whenever a remote source
 * signals that it is unwilling or unable to serve requests right now.
 *
 * Both HTTP 429 Too Many Requests and HTTP 503 Service Unavailable are treated as backoff
 * signals. A `Retry-After` header is honoured when present (delta-seconds or an HTTP date);
 * otherwise the delay escalates exponentially per consecutive failure for that host, with
 * jitter applied. The counter resets the moment the host serves a normal response, so a
 * recovered source is not penalised for a transient hiccup.
 *
 * While a window is active, a short remainder (<= [MAX_SYNC_WAIT_MS]) is slept off and a
 * longer one fails fast with an [IOException], so OkHttp's thread pool is never parked for
 * minutes on end.
 *
 * @param clock monotonic milliseconds; injectable so backoff escalation is testable.
 * @param random draws in `[0, 1)`; injectable so jitter does not make tests flaky.
 */
class RateLimitBackoffInterceptor(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
    private val random: () -> Double = { Random.nextDouble() },
) : Interceptor {

    private data class Backoff(val untilMs: Long, val code: Int)

    private val hostBackoffs = ConcurrentHashMap<String, Backoff>()
    private val hostFailures = ConcurrentHashMap<String, Int>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        // Check if this host is currently in a backoff window
        val active = hostBackoffs[host]
        if (active != null) {
            val remainingMs = active.untilMs - clock()
            if (remainingMs > 0) {
                if (remainingMs <= MAX_SYNC_WAIT_MS) {
                    try {
                        Thread.sleep(remainingMs)
                    } catch (e: InterruptedException) {
                        throw IOException("Backoff interrupted for $host", e)
                    }
                    hostBackoffs.remove(host)
                } else {
                    val waitSec = (remainingMs + 999) / 1000
                    throw IOException("HTTP ${active.code} backoff active for $host. Retry after ${waitSec}s")
                }
            } else {
                hostBackoffs.remove(host)
            }
        }

        val response = chain.proceed(request)

        if (response.code == HTTP_TOO_MANY_REQUESTS || response.code == HTTP_SERVICE_UNAVAILABLE) {
            val attempt = hostFailures.merge(host, 1, Int::plus) ?: 1
            // An explicit Retry-After from the source always wins; it knows its own recovery
            // window better than any client-side guess.
            val delayMs = parseRetryAfter(response) ?: exponentialBackoffMs(attempt)
            hostBackoffs[host] = Backoff(clock() + min(delayMs, MAX_BACKOFF_MS), response.code)
        } else {
            hostFailures.remove(host)
        }

        return response
    }

    /**
     * Exponential backoff with jitter: the ceiling doubles on every consecutive failure and
     * the delay is drawn uniformly between the previous ceiling and the new one.
     *
     * The randomness is the point. Chapter pages are fetched in parallel, so without jitter
     * every in-flight request would retry at the same instant and trip the limit again.
     */
    private fun exponentialBackoffMs(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, MAX_BACKOFF_EXPONENT)
        val previousCeiling = BASE_BACKOFF_MS shl exponent
        val ceiling = BASE_BACKOFF_MS shl (exponent + 1)
        return previousCeiling + ((ceiling - previousCeiling) * random()).toLong()
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
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVICE_UNAVAILABLE = 503

        private const val BASE_BACKOFF_MS = 5_000L
        private const val MAX_SYNC_WAIT_MS = 500L
        private const val MAX_BACKOFF_MS = 300_000L // 5 minutes
        private const val MAX_BACKOFF_EXPONENT = 6
    }
}
