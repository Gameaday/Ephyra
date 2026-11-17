package ephyra.app.image

import android.content.ComponentCallbacks2
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader

/**
 * The image-cache memory trim policy, split out of `App` by B5 (`OPS-001`).
 *
 * The policy's core is the baseline: the loader's configured memory-cache size, captured —
 * and clamped to the hard cap — when the loader is built. Trimming used to halve whatever
 * the cache currently held, so each trim halved the previous trim: a long session that hit
 * memory pressure a few times ratcheted the cache down towards nothing and never put it
 * back, even after the pressure cleared. Remembering the configured size makes the trim
 * idempotent and lets [restore] put it back when the app is foregrounded again.
 *
 * Why trim at [ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW] rather than waiting for CRITICAL:
 * long webtoon sessions pin chapter bytes + decoded strips in RAM, and waiting for CRITICAL
 * meant the reader appeared "full" (stalled loads) until the app was restarted. Trimming early
 * drops only decoded bitmaps; chapter bytes stay in the Coil/chapter disk caches, so
 * back-scroll re-decodes instead of re-downloading.
 */
class ImageCacheMemoryPolicy {

    @Volatile
    private var baselineMaxSize: Long? = null

    /**
     * Captures the loader's configured memory-cache size — once, before any trim has had a
     * chance to shrink it, so a trim always computes from the configured size rather than from
     * the previous trim's result — and enforces the hard cap: Coil's `maxSize` setter trims to
     * the assigned size, and this runs at loader build, before any trim could halve from it.
     */
    fun captureBaseline(loader: ImageLoader) {
        val memoryCache = loader.memoryCache ?: return
        val configured = memoryCache.maxSize
        val capped = cappedBaseline(configured)
        if (capped != configured) memoryCache.maxSize = capped
        baselineMaxSize = capped
    }

    /**
     * Restores the memory cache to its configured size after a trim. Coming back to the
     * foreground is the point at which the reason for trimming is gone; without this the
     * shrink was permanent for the life of the process.
     */
    fun restore(context: Context) {
        val baseline = baselineMaxSize ?: return
        val memoryCache = SingletonImageLoader.get(context).memoryCache ?: return
        if (memoryCache.maxSize < baseline) memoryCache.maxSize = baseline
    }

    /**
     * Applies a system trim. At CRITICAL pressure the cache is cleared; at any lower pressure
     * the loader is left alone and [restore] puts it back on the next foreground.
     *
     * The old behaviour halved the cache at `TRIM_MEMORY_RUNNING_LOW`, which the system raises
     * for foreground memory churn and ordinary backgrounding — not just real pressure. With one
     * or two covers on screen that trim still evicted them, and Coil then re-decoded from disk and
     * replayed its crossfade on every back-navigation: the "covers take longer than they should,
     * and reload on every nav back" report. `restore` only resets the cache *size*; it cannot
     * bring back evicted bitmaps, so the only way to keep covers warm is to not evict them at
     * benign levels. Memory pressure is still honoured — CRITICAL clears (and the background
     * fraction + hard cap bound the normal case) — but routine level changes no longer flush the
     * cover working set. Long webtoon sessions are still protected by the background-retention
     * fraction and the `MEMORY_CACHE_HARD_CAP_BYTES` ceiling, which trim even without a callback.
     */
    @Suppress("DEPRECATION")
    fun trim(context: Context, level: Int) {
        val memoryCache = SingletonImageLoader.get(context).memoryCache
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            memoryCache?.clear()
        }
    }

    companion object {
        /**
         * Hard ceiling on the decoded-image memory cache: 96 MiB (audit P1/P3, 2026-11-13).
         *
         * The loader sizes its cache as a fraction of the heap, and the manifest enables
         * `largeHeap`, so on big-heap devices that fraction reaches ~150 MiB of decoded
         * bitmaps before anything has gone wrong. Percent sizing stays the day-to-day
         * authority; this ceiling only binds where the fraction would balloon, and it is what
         * keeps the background-retention fraction bounded in absolute terms.
         */
        const val MEMORY_CACHE_HARD_CAP_BYTES = 96L * 1024 * 1024

        /**
         * Pressure-trim floor: 16 MiB (reviewed for audit P1, 2026-11-13 — kept).
         *
         * Pins roughly a screenful of decoded covers so a foreground pressure trim cannot
         * evict the covers the user is about to see. Raising it would neuter the trim on
         * LOW-tier devices, whose baseline (20% of a small heap) already sits near the floor:
         * on a 128 MiB heap the baseline is ~26 MiB and half of it is below this floor. The
         * background-retention raise, not this floor, is P1's lever.
         */
        const val TRIM_FLOOR_BYTES = 16L * 1024 * 1024

        /** The configured size clamped to the hard cap — the baseline trims restore to. */
        fun cappedBaseline(configuredMaxSize: Long): Long =
            configuredMaxSize.coerceAtMost(MEMORY_CACHE_HARD_CAP_BYTES)

        /**
         * The size a foreground pressure trim shrinks to: half the baseline, never below the
         * floor — and never above the baseline, which the old formula violated on small-heap
         * devices: below a 32 MiB baseline, half of it is under the floor and a "trim" grew
         * the cache under memory pressure.
         */
        fun trimTarget(baseline: Long): Long =
            (baseline / 2).coerceAtLeast(TRIM_FLOOR_BYTES).coerceAtMost(baseline)
    }
}
