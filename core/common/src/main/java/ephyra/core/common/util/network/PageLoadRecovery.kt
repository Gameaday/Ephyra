package ephyra.core.common.util.network

import ephyra.core.common.util.network.PageLoadRecoveryAction.GIVE_UP
import ephyra.core.common.util.network.PageLoadRecoveryAction.RETRY_SAME_URL
import ephyra.core.common.util.network.PageLoadRecoveryAction.RE_RESOLVE_URL
import eu.kanade.tachiyomi.network.HttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.random.Random

/** What a caller should do about a page image that failed to load. */
enum class PageLoadRecoveryAction {
    /** The connection failed and this URL is still the best one available. Try it again. */
    RETRY_SAME_URL,

    /**
     * The URL is what failed. Drop it and ask the source for a different one.
     *
     * This is the arm a source with rotating image hosts depends on: the first resolution returns a
     * CDN that is refusing or unreachable, the second returns a different one, and the page loads.
     */
    RE_RESOLVE_URL,

    /** Nothing left to try. Surface [PageLoadRecoveryDecision.error] to the user. */
    GIVE_UP,
}

/**
 * What to do about one failed load, and why.
 *
 * @property action the caller's next move.
 * @property dropUrl whether the failed URL must be cleared from the page before the next attempt,
 *   *including* when [action] is [PageLoadRecoveryAction.GIVE_UP]. A URL the classifier has
 *   indicted is dead whether or not we try again, and leaving it on the page would let it reach the
 *   persisted page list, so the next open of the chapter would start from an address already known
 *   to be bad.
 * @property delayMs how long to wait before acting. Zero when [action] is
 *   [PageLoadRecoveryAction.GIVE_UP], because nothing follows it.
 * @property attempt the 1-based attempt number this decision was made for.
 * @property reason a short human-readable cause, for logs and tests.
 * @property error the throwable to surface if this is [PageLoadRecoveryAction.GIVE_UP].
 */
data class PageLoadRecoveryDecision(
    val action: PageLoadRecoveryAction,
    val dropUrl: Boolean,
    val delayMs: Long,
    val attempt: Int,
    val reason: String,
    val error: Throwable,
)

/**
 * One attempt at loading a page image, kept so a final failure can say what was tried.
 *
 * @property url the URL requested, or `null` when the failure happened before one existed.
 * @property error what went wrong.
 * @property errorLabel a short description of [error], safe to put in a log line.
 */
data class PageLoadAttempt(val url: String?, val error: Throwable, val errorLabel: String)

/**
 * The single owner of "a page image failed; what now?" — the retry decision, the budget, the
 * backoff, the memory of a URL already proved unusable, and the record of what was tried.
 *
 * **Why this is one object rather than a helper called at four sites.** The reader and the
 * downloader each carried their own copy of this decision, and they had already drifted in three
 * ways that matter in the real world:
 *
 * - **Different backoff for the same rule.** The reader waited 1s/2s/4s; the downloader waited
 *   2s/4s/8s, from `(2L shl attempt) * 1000`. Two schedules, one meaning, no compiler objecting.
 * - **No jitter in either.** Chapter pages load in parallel, so pages that failed together retried
 *   together, in lockstep, at the instant the backoff expired. A source that was already
 *   unhappy received a synchronised burst. The app already knew this — `RateLimitBackoffInterceptor`
 *   jitters, and its own comment says *"without jitter every in-flight request would retry at the
 *   same instant and trip the limit again"* — but that interceptor only covers 429/503, and the
 *   URL-stale family this class is mostly about is 403 and dead hosts.
 * - **Only the reader could change hosts.** The reader dropped a URL the classifier indicted and
 *   asked the source again; the downloader retried the identical string. `TransientErrors` was
 *   shared between them — which is `DEF-021`'s fix — but only the *classification* was shared.
 *   The *action* was still two copies, so for any source with short-lived signed image URLs the
 *   same chapter read successfully and failed to download.
 *
 * **What is deliberately not here.** This decides; it does not act. Dropping the URL and calling
 * `getImageUrl` belong to the caller, which owns the page and the source. Putting the action here
 * too would need a `Source` in `core:common` and would make the rule untestable without one.
 *
 * **The budget is one counter, not two.** It is tempting to give "this URL is dead, ask again" a
 * larger allowance than "the connection wobbled", on the grounds that only the URL changes. That
 * would raise the worst-case request count for a page. The request budget is held to what it was,
 * and only the *delay* differs by kind: a re-resolve is answered sooner because the next attempt is
 * a different request to a possibly different host, while a same-URL retry keeps the longer ramp
 * because it is the same request again.
 *
 * Not thread-safe, and deliberately so: one instance belongs to one page's load, and the pages of
 * a chapter load concurrently on separate instances. See [ReResolvePacer] for the one piece of
 * state that genuinely has to be shared.
 */
class PageLoadRecovery(
    /** Retries allowed after the first attempt. The default reproduces the reader's previous cap. */
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    /**
     * How long the automatic ladder may keep a page off the Retry button before it surfaces the
     * error instead. Zero or less disables the bound.
     */
    private val maxRetryElapsedMs: Long = DEFAULT_MAX_RETRY_ELAPSED_MS,
    private val sameUrlBaseDelayMs: Long = DEFAULT_SAME_URL_BASE_DELAY_MS,
    private val reResolveBaseDelayMs: Long = DEFAULT_RE_RESOLVE_BASE_DELAY_MS,
    private val maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
    private val random: () -> Double = { Random.nextDouble() },
    /** Monotonic milliseconds; injectable so the elapsed bound is testable without sleeping. */
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {

    private val recorded = mutableListOf<PageLoadAttempt>()
    private var retries = 0

    /**
     * A URL this load has already found structurally unusable.
     *
     * Held here rather than on the page because it is a fact about *this attempt sequence*, not
     * about the page: the page's own answer to "is my URL any good" is that it no longer has one.
     * Its only use is to stop asking the source for a string already proved incapable of addressing
     * a host — every such call is a round-trip spent learning nothing.
     */
    private var rejectedUrl: String? = null

    /**
     * The error a caller should surface if this load gives up: the most recent failure that
     * happened while *holding* a URL. See [onFailure] for why a resolution failure is excluded.
     */
    private var headline: Throwable? = null

    /**
     * When the first failure happened, or `null` before it. Elapsed time is measured *from* here
     * rather than against a precomputed deadline, so a generous or effectively-infinite budget
     * cannot overflow the comparison -- adding `Long.MAX_VALUE` to a monotonic clock wraps negative
     * and would make the bound fire on the very first failure, turning "no bound" into "no retries".
     */
    private var retryStartedAtMs: Long? = null

    /** Every attempt made so far, oldest first. */
    val attempts: List<PageLoadAttempt> get() = recorded.toList()

    /** The last failure, or `null` before the first one. */
    val lastError: Throwable? get() = recorded.lastOrNull()?.error

    /**
     * True once this load has failed and is being retried, so a resolution now is a *re*-resolution.
     *
     * Callers must consult this before pacing anything. A page's first resolution is on the hot path
     * — the user is waiting for the image — and spacing first resolutions out is a latency tax on
     * every chapter open to solve a problem that only exists after a failure. The distinction is
     * here, in the object that knows the attempt sequence, rather than inferred at each call site.
     */
    val isRetrySequence: Boolean get() = recorded.isNotEmpty()

    /**
     * True when [resolved] is a string this load has already rejected as unable to address a host.
     *
     * The caller uses this to fail immediately instead of spending a request on an address that
     * provably cannot work. It reports the *rejection*, not a policy verdict — whether the string is
     * acceptable in the first place is [ImageUrlPolicy]'s question, and the caller asks that too.
     */
    fun isKnownUnusable(resolved: String?): Boolean = resolved != null && resolved == rejectedUrl

    /**
     * Records that [url] passed the pre-flight check, clearing the unusable-URL memory.
     *
     * Called on every attempt that gets past [ImageUrlPolicy.requireUsable], which is what the
     * loader's `rejectedUrl = null` did — so a URL the source repeats after having produced a good
     * one is asked for again rather than refused on the strength of an older failure.
     */
    fun onResolved(url: String?) {
        rejectedUrl = null
    }

    /** Records a structurally unusable [url], so it is not requested a second time. */
    fun onRejectedUrl(url: String?) {
        if (url != null) rejectedUrl = url
    }

    /**
     * Decides what to do about [error], which was raised while requesting [url].
     *
     * Records the attempt either way: a page that gives up having tried three hosts is the case
     * worth being able to explain afterwards, and it is exactly the case that currently cannot be.
     *
     * **A `null` [url] means this failed while *resolving*, not while loading**, and that is the
     * whole signal this method needs. The caller clears the URL before asking the source for a
     * replacement, so a resolution that then throws arrives here with nothing in hand.
     *
     * That is a real fault and it is recorded, but it is not the one the user needs to hear. The
     * common shape is a source that supplies `Page.imageUrl` in `pageListParse` and never implements
     * `imageUrlParse`, so the base `UnsupportedOperationException` replaces the `403` that actually
     * stopped the page — the reader reports the machinery instead of the cause. So only a failure
     * that happened with a URL in hand becomes the error surfaced on giving up; a resolution failure
     * is detail, and [summary] carries it.
     */
    fun onFailure(url: String?, error: Throwable): PageLoadRecoveryDecision {
        recorded += PageLoadAttempt(url, error, describe(error))
        val attempt = recorded.size
        if (url != null) {
            headline = error
        }

        // Whether the URL is indicted is independent of whether the failure is worth retrying, and
        // the caller needs both: a permanent 404 is not retried, but a 403 is, and both must leave
        // the page without a URL that is known to be bad.
        val dropUrl = TransientErrors.shouldReResolveUrl(error)
        if (error is MalformedImageUrlException) {
            // Keyed off the exception rather than a re-run of the policy, so the remembered string
            // is exactly the one that was rejected.
            onRejectedUrl(error.url)
        }

        if (!TransientErrors.isTransient(error)) {
            return giveUp(attempt, dropUrl, error, "not a transient failure")
        }
        if (retries >= maxRetries) {
            return giveUp(attempt, dropUrl, error, "retry budget exhausted after $maxRetries retries")
        }
        // After the two reasons above, so a page that has been retrying for a while and then meets
        // something permanent still reports *that*, rather than the clock.
        if (maxRetryElapsedMs > 0) {
            val now = clock()
            val started = retryStartedAtMs
            if (started != null && now - started >= maxRetryElapsedMs) {
                return giveUp(
                    attempt,
                    dropUrl,
                    error,
                    "still failing after ${now - started}ms of automatic retrying",
                )
            }
            if (started == null) retryStartedAtMs = now
        }

        retries++
        val action = if (dropUrl) RE_RESOLVE_URL else RETRY_SAME_URL
        return PageLoadRecoveryDecision(
            action = action,
            dropUrl = dropUrl,
            delayMs = backoffMs(action, retries),
            attempt = attempt,
            reason = if (dropUrl) "the URL is at fault" else "the connection failed",
            error = error,
        )
    }

    /**
     * The delay before attempt number [retry] (1-based) of the given [action].
     *
     * Jittered between the previous attempt's delay and half again it, for the reason
     * `RateLimitBackoffInterceptor` gives: pages load in parallel, so a fixed delay has every failed
     * page retrying at the same instant. The floor is the un-jittered schedule, so jitter can only
     * make a caller wait longer, never less than it would have before. Exposed so a caller pacing
     * its own re-resolutions can ask the same question without reaching for a second schedule.
     */
    fun backoffMs(action: PageLoadRecoveryAction, retry: Int): Long {
        val base = when (action) {
            RE_RESOLVE_URL -> reResolveBaseDelayMs
            RETRY_SAME_URL -> sameUrlBaseDelayMs
            GIVE_UP -> return 0
        }
        val step = (retry - 1).coerceIn(0, MAX_BACKOFF_EXPONENT)
        val previousCeiling = base shl step
        // Half again rather than double. Any spread stops the lockstep, so the wider window bought
        // nothing and cost up to a second of extra waiting on the first retry; the ladder still
        // escalates, because the floor doubles every attempt.
        val ceiling = (previousCeiling + previousCeiling / 2).coerceAtMost(maxDelayMs)
        val boundedPrevious = previousCeiling.coerceAtMost(maxDelayMs)
        if (ceiling <= boundedPrevious) return boundedPrevious
        return boundedPrevious + ((ceiling - boundedPrevious) * random()).toLong()
    }

    private fun giveUp(attempt: Int, dropUrl: Boolean, error: Throwable, reason: String) =
        PageLoadRecoveryDecision(
            action = GIVE_UP,
            dropUrl = dropUrl,
            delayMs = 0,
            attempt = attempt,
            reason = reason,
            // The failure that actually stopped the page, not necessarily the last thing that went
            // wrong. They differ exactly when a replacement URL could not be obtained, and in that
            // case the last thing that went wrong is the machinery rather than the cause.
            error = headline ?: error,
        )

    /**
     * A one-line account of every attempt, for the log at the point a page finally fails.
     *
     * `tried 3 URLs: cdn-a.example (HTTP error 403), cdn-b.example (timeout), cdn-c.example
     * (HTTP error 403)`. This is the sentence whose absence made the original report a puzzle: the
     * page failed, the user could see one error, and nothing anywhere recorded which hosts had been
     * tried or what each had said.
     */
    fun summary(): String {
        if (recorded.isEmpty()) return "no attempts recorded"
        return "tried ${recorded.size} attempt(s): " +
            recorded.joinToString(", ") { attempt ->
                val host = attempt.url?.let(::hostOf) ?: "<no url>"
                "$host (${attempt.errorLabel})"
            }
    }

    private fun hostOf(url: String): String = url
        .substringAfter("://", url)
        .substringBefore('/')
        .ifBlank { "<unparseable>" }

    /**
     * A short label for a throwable, for a log line: the status code where there is one, otherwise
     * the exception type. A stack trace is not wanted in a one-line summary, but `HttpException`
     * and `ConnectException` are both "an exception happened" to anyone reading a log, and the code
     * is the whole point of the line.
     */
    private fun describe(error: Throwable): String = when (error) {
        is HttpException -> "HTTP ${error.code}"
        is SocketTimeoutException -> "timeout"
        is UnknownHostException -> "host did not resolve"
        else -> error::class.simpleName ?: "Throwable"
    }

    /**
     * Policy defaults, public so a caller can reason about — or a test can assert against — the
     * same numbers the defaults use, rather than a second copy of them in a test that can drift.
     */
    companion object {
        /** Retries allowed after the first attempt. */
        const val DEFAULT_MAX_RETRIES = 3

        /** Delay before the first same-URL retry, doubled per attempt up to [DEFAULT_MAX_DELAY_MS]. */
        const val DEFAULT_SAME_URL_BASE_DELAY_MS = 1_000L

        /**
         * Delay before the first re-resolve, lower than [DEFAULT_SAME_URL_BASE_DELAY_MS] because
         * the next attempt is a different request to a possibly different host.
         */
        const val DEFAULT_RE_RESOLVE_BASE_DELAY_MS = 250L

        /** Ceiling for both ladders. Beyond this a retry stops being worth the reader's patience. */
        const val DEFAULT_MAX_DELAY_MS = 8_000L

        /**
         * How long the automatic ladder may run before the page surfaces its error and a Retry
         * button instead.
         *
         * **Why the user needs this bound and not just an attempt count.** The page is behind a
         * spinner for the whole ladder, and the Retry button only exists in the error state -- so an
         * attempt count is also a bound on how long the user is left with no way to intervene.
         *
         * **What it does and does not bound, measured rather than assumed.** It bounds *our* retrying,
         * not the source's response time, and truncating an in-flight request would discard a
         * transfer that may be about to succeed. So it helps in proportion to how much of the wait
         * was ours. At 1.5s per request:
         *
         * | failure shape                        | unbounded | bounded |
         * |--------------------------------------|-----------|---------|
         * | connection reset (same URL, 1s base) | 14.7s     | 8.2s    |
         * | slow CDN, 4s/request, dead host      | 18.2s     | 12.9s   |
         * | 403, re-resolving (250ms base)       |  8.2s     | 8.2s    |
         *
         * The last row is the one worth being straight about: a re-resolving ladder retries so
         * quickly that the *attempt* budget binds first, and this bound changes nothing. That is the
         * deliberate cost of protecting the CDN swap -- four cheap attempts at a possibly different
         * host beat three -- and it is why the number here is a ceiling on our own behaviour rather
         * than a promise about how long anyone waits.
         *
         * Either way the button the user is given is worth exactly what the ladder was: `retryPage`
         * starts a *fresh* recovery with a full budget.
         */
        const val DEFAULT_MAX_RETRY_ELAPSED_MS = 5_000L

        private const val MAX_BACKOFF_EXPONENT = 6
    }
}
