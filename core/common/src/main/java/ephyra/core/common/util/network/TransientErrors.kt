
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
 * - A **name that does not resolve** (`UnknownHostException`) is the same shape of problem as a
 *   `403`, and it was missing here. It arrives as an `IOException`, so it was always *retried* —
 *   but [shouldReResolveUrl] reported the URL as fine, so every attempt re-requested the identical
 *   host that had just failed to resolve, and the user's own Retry did exactly the same. A page
 *   whose image CDN host is dead was unrecoverable by construction, which is the `DEF-023` report.
 * - A URL that **cannot address a host at all** — `MalformedImageUrlException`, raised by
 *   [ImageUrlPolicy] before any request is sent — is the third member of this family. Like the
 *   other two it indicts the URL rather than the connection, so it also requires a fresh one; the
 *   difference is that the identical string provably can never work, which is why the reader stops
 *   re-resolving rather than re-requesting.
 * - `4xx` otherwise is permanent: a malformed request will fail identically forever.
 *
 * [shouldReResolveUrl] exists so a caller can tell the two transient cases apart, because they need
 * different handling: a `429` should be retried against the same URL after a backoff, while a
 * `403` — or a name that does not resolve — needs a fresh URL first.
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
