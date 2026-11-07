package ephyra.app.image

import android.content.ComponentCallbacks2
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader

/**
 * The image-cache memory trim policy, split out of `App` by B5 (`OPS-001`).
 *
 * The policy's core is the baseline: the loader's configured memory-cache size, captured when
 * the loader is built. Trimming used to halve whatever the cache currently held, so each trim
 * halved the previous trim: a long session that hit memory pressure a few times ratcheted the
 * cache down towards nothing and never put it back, even after the pressure cleared. Remembering
 * the configured size makes the trim idempotent and lets [restore] put it back when the app is
 * foregrounded again.
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
     * the previous trim's result.
     */
    fun captureBaseline(loader: ImageLoader) {
        baselineMaxSize = loader.memoryCache?.maxSize
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
     * Applies a system trim: at foreground pressure the cache is halved *of its configured size*
     * (never below 16 MiB); at critical pressure it is cleared. Background eviction is handled
     * by Coil's own lifecycle-aware policy configured on the loader.
     */
    fun trim(context: Context, level: Int) {
        val memoryCache = SingletonImageLoader.get(context).memoryCache
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && memoryCache != null) {
            val baseline = baselineMaxSize ?: memoryCache.maxSize
            memoryCache.maxSize = (baseline / 2).coerceAtLeast(16L * 1024 * 1024)
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            memoryCache?.clear()
        }
    }
}
