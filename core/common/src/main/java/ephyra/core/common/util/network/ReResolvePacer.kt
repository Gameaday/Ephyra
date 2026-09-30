package ephyra.core.common.util.network

import java.util.concurrent.atomic.AtomicLong

/**
 * Spaces out re-resolutions so a chapter whose pages all fail at once does not ask the source the
 * same question simultaneously, several times over.
 *
 * **The failure this prevents.** Sources that mint short-lived signed image URLs commonly issue them
 * in batches with a common expiry. A chapter opened near that boundary has *every* page fail at
 * almost the same moment, and each page's own ladder then re-resolves on its own schedule. With a
 * fixed backoff and a bounded worker pool, those re-resolutions land together — a synchronised burst
 * at a source that is already struggling, which is the condition that turns a recoverable CDN
 * problem into a rate-limited one. Per-page jitter (see [PageLoadRecovery.backoffMs]) spreads the
 * *retries*; this spreads the *questions asked of the source*, which is the scarcer resource.
 *
 * **Why it is not a lock.** The obvious implementation serialises callers behind a mutex. That puts
 * a lock on the page-load hot path to solve a problem whose worst outcome is two requests going out
 * together — the state it is protecting against today anyway. So the read and the write are both
 * lock-free and a race is benign in the safe direction: two callers may both decide to wait the
 * same interval, and both proceed. The guarantee is "roughly one per interval", not "exactly one",
 * and the alternative costs more than the guarantee is worth.
 *
 * **Scope is deliberately one chapter.** This is per-loader, not per-source and not per-process.
 * Pages of one chapter failing together is the observed shape; correlating failures across chapters
 * would mean process-wide state with a lifetime nobody owns, to fix a case with no evidence behind
 * it.
 *
 * @param minIntervalMs the shortest gap permitted between two re-resolutions.
 * @param clock monotonic milliseconds; injectable so the pacing is testable without sleeping.
 */
class ReResolvePacer(
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {

    private val lastRequestAtMs = AtomicLong(Long.MIN_VALUE / 2)

    /**
     * How long the caller should wait before issuing a **re**-resolution.
     *
     * Returns `0` when the previous one is older than [minIntervalMs], so the common case — a
     * single page retrying alone — costs one clock read and no delay at all.
     *
     * **Only call this for a re-resolution.** A page's first resolution is on the hot path: the
     * user is waiting for that image, and the active page competes with the preload window for the
     * same few workers, so pacing first resolutions taxes every chapter open to solve a problem
     * that only exists after something has already failed. Measured on a six-page preload window
     * that was 1.2s of added latency and up to 400ms on the page being waited for. Ask
     * [PageLoadRecovery.isRetrySequence] before calling.
     */
    fun paceReResolution(): Long {
        val now = clock()
        val previous = lastRequestAtMs.get()
        // A lost race costs nothing: both callers see the same stale value, both wait, both go.
        lastRequestAtMs.set(now)
        if (previous == Long.MIN_VALUE / 2) return 0
        val elapsed = now - previous
        return if (elapsed >= minIntervalMs) 0 else minIntervalMs - elapsed
    }

    companion object {
        /**
         * Long enough to blunt a burst, short enough that a genuine rotation is not throttled into
         * the user's waiting. A chapter's worth of pages resolving a second apart is well inside
         * what any source considers ordinary browsing.
         */
        const val DEFAULT_MIN_INTERVAL_MS = 400L
    }
}
