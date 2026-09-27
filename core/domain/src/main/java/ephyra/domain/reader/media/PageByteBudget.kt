package ephyra.domain.reader.media

/**
 * Derives the page-byte working-set budget from the device's heap ceiling.
 *
 * A fixed budget is wrong in both directions at once. On a low-RAM device 64 MiB can be a third of
 * the entire heap, and the store would then compete with the renderer for memory it does not have.
 * On a high-RAM device the same 64 MiB is a rounding error, and the store would evict pages the
 * user is likely to revisit purely because the number was chosen before the device was known.
 *
 * The budget is a **fraction of [memoryClassMb]** rather than an absolute value, so it tracks the
 * device. It is clamped at both ends because the fraction alone is not sufficient: a very small heap
 * still needs enough room for a handful of pages to be cached at all, and a very large one must
 * not let the cache grow into something that itself causes the out-of-memory it was sized to avoid.
 *
 * Pure and Android-free so the bounds are testable on the JVM — a policy that can only be exercised
 * on hardware is a policy that goes unexercised.
 */
object PageByteBudget {

    /** Lower clamp. Below roughly this, caching a few pages at a time is the best we can do. */
    const val MIN_BYTES: Int = 48 * 1024 * 1024

    /** Upper clamp. A cache larger than this is itself a memory-pressure risk. */
    const val MAX_BYTES: Int = 192 * 1024 * 1024

    /**
     * Share of the heap the encoded working set may occupy.
     *
     * Deliberately well under a quarter: the decoded slices in flight, the Compose image cache and
     * the frame buffers all draw from the same heap, and this store holds *retained* bytes that may
     * be reused at any moment.
     */
    const val HEAP_FRACTION: Double = 0.125

    /**
     * The store may never claim more than this share of the heap, whatever the floor would like.
     *
     * This is the ceiling that actually binds on small devices. A 48 MiB floor is a sensible
     * *cache* size, but on a 64 MB heap it is three quarters of the entire app budget — the store
     * would not be competing for memory alongside the renderer, it would be evicting it. So the
     * floor yields to this rather than the other way round: on a small heap the correct budget is
     * a small one.
     */
    const val MAX_HEAP_SHARE: Double = 0.25

    /**
     * Bytes the working set may retain for a device with [memoryClassMb] megabytes of heap.
     *
     * [memoryClassMb] is `ActivityManager.getMemoryClass()`: the per-app heap ceiling, not the
     * device's total RAM. Zero or negative input is treated as the smallest realistic class rather
     * than producing a zero budget, which would make every [PageByteStore.put] fail and silently
     * disable caching entirely.
     *
     * The floor is applied *after* the heap-share ceiling, and is itself clamped by it, so
     * [MIN_BYTES] can never push the store past [MAX_HEAP_SHARE] on a small device.
     */
    fun forMemoryClassMb(memoryClassMb: Int): Int {
        val heapBytes = (memoryClassMb.coerceAtLeast(1).toLong()) * 1024L * 1024L
        val shareCeiling = minOf(MAX_BYTES.toLong(), (heapBytes * MAX_HEAP_SHARE).toLong())
        // A floor above the ceiling would be unachievable, so the floor is capped by it.
        val floor = minOf(MIN_BYTES.toLong(), shareCeiling)
        val fractionBytes = (heapBytes * HEAP_FRACTION).toLong()
        return fractionBytes.coerceIn(floor, shareCeiling).toInt()
    }
}
