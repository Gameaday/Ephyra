package ephyra.core.common.util.network

import eu.kanade.tachiyomi.network.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Which layer of the pipeline a failure belongs to.
 *
 * **Why this exists.** The MangaDex report was `Unable to resolve host
 * "cmdxd98sb0x3yprd.mangadex.network,https"`. That message is a *transport* fact — a name did not
 * resolve — and it is the last thing that happened, not the first. The string was already malformed
 * when the layer that built it produced it; every layer after that faithfully carried a broken value
 * to the resolver and reported on the wrong subject. Establishing that took a number of exchanges,
 * and none of them could have been avoided from the error text alone.
 *
 * Naming the layer is what makes a failure actionable. [SOURCE] means the extension or server is
 * wrong, [ADAPTER] means our mapping is wrong, [TRANSPORT] means the network is wrong, [RENDER] means
 * the reader is wrong. Those have different owners and different fixes, and a report that says which
 * one is worth far more than a report that says what happened.
 *
 * **Transport is where the MangaDex defect was seen, not where it was caused.** That is the trap this
 * enum exists to make visible: the first layer to notice a problem is routinely not the layer that
 * produced it. A transport failure whose input failed validation is an [ADAPTER] failure reported
 * late, and the question worth asking on seeing any [TRANSPORT] error is "did an earlier layer hand us
 * something impossible?"
 */
enum class FailureLayer {
    /** The source itself: the server returned an error, or an extension could not reach its site. */
    SOURCE,

    /**
     * Our mapping from a source's shape into the canonical shape.
     *
     * A defect here is ours, not the source's: the source answered correctly and we read it wrongly.
     * This is the layer a conformance suite pins, because it is the only one fully testable without a
     * device.
     */
    ADAPTER,

    /**
     * Moving bytes between places: DNS, TLS, connection, HTTP status, timeouts.
     *
     * This is where a failure is *observed*, not necessarily where it originated — see [ADAPTER].
     */
    TRANSPORT,

    /** Turning content we already hold into what the user sees: decode, layout, paging, gestures. */
    RENDER,
}

/**
 * A failure with the layer that owns it, and enough context to act on.
 *
 * @property layer which part of the pipeline owns this failure.
 * @property operation what was being attempted, in source terms — `getPages`, `image request`.
 * @property subject the specific thing that failed: a URL, an item key, a page index. Present because
 *   "MangaDex failed" is not actionable but "chapter 12's third page image" is.
 * @property cause the underlying throwable, retained rather than flattened so [TransientErrors] can
 *   still classify it by type.
 */
data class LayeredFailure(
    val layer: FailureLayer,
    val operation: String,
    val subject: String?,
    val cause: Throwable?,
) {
    /**
     * One line for a log or a user-facing message.
     *
     * Built to be greppable: the layer leads so a report can be triaged by pattern match, and the
     * subject comes before the cause's own message so the thing that failed is visible even when the
     * cause's message is a generic resolver complaint.
     */
    fun describe(): String = buildString {
        append(layer.name).append(' ').append(operation)
        if (subject != null) append(" [").append(subject).append(']')
        if (cause != null) append(": ").append(cause.message ?: cause::class.simpleName)
    }

    companion object {
        /**
         * Best-effort classification of an arbitrary throwable into the layer that owns it.
         *
         * A mapping from types this codebase already raises, not an exhaustive one. The default is
         * [SOURCE] rather than [TRANSPORT] because an unrecognised failure is more often a source
         * behaving unexpectedly than a network fault — and because defaulting to "transport" is what
         * sent the MangaDex investigation toward the resolver when the string was the problem.
         */
        fun classify(operation: String, subject: String?, error: Throwable): LayeredFailure {
            val layer = when (error) {
                // Raised by ImageUrlPolicy before any request. The string was already impossible, so
                // whatever produced it owns the fault, and that is our mapping in every case seen.
                is MalformedImageUrlException -> FailureLayer.ADAPTER
                // Named ahead of the IOException arm for the reader of the rule, not for behaviour:
                // every one of these is an IOException, so either arm returns TRANSPORT.
                is UnknownHostException,
                is SocketTimeoutException,
                is ConnectException,
                is SSLHandshakeException,
                is SSLPeerUnverifiedException,
                is IOException,
                -> FailureLayer.TRANSPORT
                // A non-2xx is the server's verdict on our request, which is a fact about the source.
                is HttpException -> FailureLayer.SOURCE
                else -> FailureLayer.SOURCE
            }
            return LayeredFailure(layer, operation, subject, error)
        }
    }
}
