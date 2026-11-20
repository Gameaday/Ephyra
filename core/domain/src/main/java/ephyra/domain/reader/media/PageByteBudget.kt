package ephyra.domain.reader.media

/**
 * Derives the page-byte working-set budget from the device's heap ceiling.
 *
 * A fixed budget is wrong both ways: on low‑RAM 64 MiB can be a third of the heap, so the store
 * competes with the renderer for memory it does not have; on high‑RAM the same 64 MiB is a rounding
 * error, so the store evicts pages the user will revisit because the number predated the device.
 *
 * A **fraction of [memoryClassMb]** rather than an absolute, clamped at both ends: a very small heap
 * still needs room for a few pages, and a very large one must not let the cache grow into the
 * out‑of‑memory it was sized to avoid.
 *
 * Pure and Android‑free so the bounds are testable on the JVM.
 */
object PageByteBudget {

    /** Lower clamp. Below roughly this, caching a few pages at a time is the best we can do. */
    const val MIN_BYTES: Int = 48 * 1024 * 1024

    /** Upper clamp. A cache larger than this is itself a memory-pressure risk. */
    const val MAX_BYTES: Int = 192 * 1024 * 1024

    /**
     * Share of the heap the encoded working set may occupy.
     *
     * Deliberately well under a quarter: decoded slices in flight, the Compose image cache and the
     * frame buffers draw from the same heap, and this store holds *retained* bytes reusable at any
     * moment.
     */
    const val HEAP_FRACTION: Double = 0.125

    /**
     * The store may never claim more than this share of the heap, whatever the floor would like.
     *
     * This is the ceiling that binds on small devices: a 48 MiB floor is a sensible *cache* size,
     * but on a 64 MB heap it is three quarters of the whole app budget — the store would be evicting
     * the renderer, not competing with it. The floor yields to this, not the reverse.
     */
    const val MAX_HEAP_SHARE: Double = 0.25

    /**
     * Bytes the working set may retain for a device with [memoryClassMb] megabytes of heap.
     *
     * [memoryClassMb] is `ActivityManager.getMemoryClass()` — the per‑app ceiling, not device RAM.
     * Zero or negative input is treated as the smallest realistic class rather than producing a zero
     * budget, which would make every [PageByteStore.put] fail and silently disable caching.
     *
     * The floor is applied *after* the heap‑share ceiling and is itself capped by it, so
     * [MIN_BYTES] can never push the store past [MAX_HEAP_SHARE].
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
