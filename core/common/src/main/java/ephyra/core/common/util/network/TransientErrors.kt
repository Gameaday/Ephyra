
package ephyra.core.common.util.network

import eu.kanade.tachiyomi.network.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * One definition of "this failure is worth retrying".
 *
 * The reader and downloader each carried a copy and had drifted: the reader treated only
 * `IOException` and `429`/`5xx` as transient, so a `403` on an expired signed URL was permanent.
 *
 * - `IOException` — transient; the request may not have completed.
 * - `429`, `5xx` — transient; the server is asking us to come back.
 * - `403`, `410`, `UnknownHostException`, [MalformedImageUrlException] — transient **only in that
 *   the URL may be stale**, so the caller must re-resolve. `HttpPageLoader` does, which is what
 *   makes this correct rather than optimistic.
 * - other `4xx` — permanent; a malformed request fails identically forever.
 *
 * [shouldReResolveUrl] separates the two transient cases: a `429` retries the same URL, a `403`
 * needs a fresh one first.
 */
object TransientErrors {

    /** True when [error] is worth retrying, with or without a fresh URL. */
    fun isTransient(error: Throwable): Boolean = when (error) {
        // Named ahead of the `IOException` arm for the reader of the rule rather than for the
        // behaviour: an `UnknownHostException` *is* an `IOException`, so either arm returns true.
        // See [shouldReResolveUrl] for why this one failure additionally needs a new URL.
        is UnknownHostException -> true
        is IOException -> true
        is HttpException -> error.code == 429 || error.code >= 500 || isStale(error)
        else -> false
    }

    /**
     * True when [error] indicates the URL itself is stale or unusable, so the caller must ask the
     * source for a new one before retrying.
     *
     * Walks the [Throwable.cause] chain, because the signal is a *type* and layers legitimately
     * re-wrap: the shared `Call.await()` re-wraps every failure, and the downloader's
     * `retryWhen` sees whatever its callee threw. A classifier that inspects only the outermost
     * exception degrades to "no opinion" the moment anything wraps — which is precisely how
     * `DEF-020` and then `DEF-021` happened. The walk is depth-bounded so a pathological or
     * cyclic chain terminates rather than spins.
     *
     * Only the chain is walked, not [isTransient]'s rule: a `RuntimeException` that merely
     * *contains* an `IOException` is a bug in a source extension and stays permanent, so widening
     * the walk to that rule would make real defects look like flaky network.
     */
    fun shouldReResolveUrl(error: Throwable): Boolean {
        var candidate: Throwable? = error
        var depth = 0
        while (candidate != null && depth < MAX_CAUSE_DEPTH) {
            val current = candidate
            when (current) {
                // The name in this URL did not resolve, so the URL is the suspect. The source may
                // hand back a different host on the next resolution, and a fresh URL is the only
                // thing that can recover from a dead one.
                is UnknownHostException -> return true
                // The host is reachable but is not serving this URL: a certificate that does not
                // verify, a refused connection. These are the same shape of verdict as a name that
                // does not resolve -- the *host* is at fault, not the connection -- and the sources
                // that produce them are the ones that hand out a different CDN on the next
                // resolution, which is the only thing that can recover.
                //
                // Left out on purpose: `SocketTimeoutException`. It is an `IOException`, so it is
                // still retried, but a timeout is not evidence about the host -- it is what a slow
                // read looks like too, and there is no type that separates the two phases portably.
                // Re-resolving on it would spend a source round-trip on the many timeouts that a
                // re-resolve cannot fix.
                is SSLPeerUnverifiedException, is SSLHandshakeException, is ConnectException -> return true
                // The URL cannot address a host at all — a splicing artifact such as
                // `cmdx98sb0x3yprd.mangadex.network,https`. Re-requesting it cannot succeed, but
                // *re-resolving* still can, because the source builds the string that is malformed
                // and may build a different one next time. That is why this belongs here and not
                // in the permanent arm, even though the URL itself will never work as it stands.
                is MalformedImageUrlException -> return true
                is HttpException -> if (isStale(current)) return true
            }
            candidate = current?.cause
            depth++
        }
        return false
    }

    private fun isStale(error: HttpException): Boolean = error.code == 403 || error.code == 410

    /**
     * How far [shouldReResolveUrl] walks a cause chain. A cause chain deeper than this is already
     * pathological; bounding it keeps the walk provably terminating instead of relying on every
     * wrapper in the app behaving.
     */
    private const val MAX_CAUSE_DEPTH = 8
}
