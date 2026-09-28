package ephyra.core.common.util.network

import eu.kanade.tachiyomi.network.HttpException
import java.io.IOException

/**
 * One definition of "this failure is worth retrying".
 *
 * **Why this exists.** The reader (`HttpPageLoader`) and the downloader (`Downloader`) each carried
 * their own copy of the same predicate, and they had already drifted in a way that cost users
 * images: the reader's copy treated only `IOException` and `429`/`5xx` as transient, so a `403` on an
 * expired signed URL was permanent — and since the reader also never re-resolved the URL on retry,
 * the page failed after a full backoff ladder for a URL the source would have replaced. Two copies
 * of a rule that must agree, in two modules, with no compiler enforcing it, is the same shape as the
 * triplicated download path rule fixed earlier in this programme.
 *
 * **The rule.**
 * - `IOException` is transient: the request may not have completed, so retrying is free of
 *   consequence beyond the wait.
 * - `429` and `5xx` are transient: the server is asking us to come back.
 * - `403` and `410` are transient **only in the sense that the URL may be stale**, so the caller
 *   must re-resolve before retrying. Treating them as permanent is what produced the missed-image
 *   report; treating them as transient *without* re-resolving would just repeat the same failure.
 *   `HttpPageLoader` now re-resolves on every retry, which is what makes this classification correct
 *   rather than merely optimistic.
 * - `4xx` otherwise is permanent: a malformed request will fail identically forever.
 *
 * [shouldReResolveUrl] exists so a caller can tell the two transient cases apart, because they need
 * different handling: a `429` should be retried against the same URL after a backoff, while a `403`
 * needs a fresh URL first.
 */
object TransientErrors {

    /** True when [error] is worth retrying, with or without a fresh URL. */
    fun isTransient(error: Throwable): Boolean = when (error) {
        is IOException -> true
        is HttpException -> error.code == 429 || error.code >= 500 || isStale(error)
        else -> false
    }

    /**
     * True when [error] indicates the URL itself is stale, so the caller must ask the source for a
     * new one before retrying.
     */
    fun shouldReResolveUrl(error: Throwable): Boolean = when (error) {
        is HttpException -> isStale(error)
        else -> false
    }

    private fun isStale(error: HttpException): Boolean = error.code == 403 || error.code == 410
}
