package ephyra.domain.reader.media

/**
 * A bounded LRU [PageByteStore].
 *
 * Eviction is strictly least-recently-used, and pinned entries are never evicted. Two rules matter
 * more than the LRU ordering itself:
 *
 *  1. **Oversized values are refused, not admitted.** A value larger than the entire budget cannot
 *     ever be retained alongside anything else. Admitting it would evict the whole working set and
 *     then immediately evict itself, leaving the cache emptier than before the call.
 *  2. **Accounting is exact.** [retainedBytes] is maintained by the same operations that mutate
 *     the store, so it cannot drift. A drifting counter eventually reports a store as full while it
 *     is nearly empty, which looks like a leak that cannot be fixed by adding memory.
 *
 * Not thread-safe by design: a single reader session owns one store, and a lock here would add
 * contention on the scroll path without adding correctness the callers do not already provide.
 */
class BoundedPageByteStore(
    override val budgetBytes: Int,
) : PageByteStore {

    init {
        require(budgetBytes > 0) { "budgetBytes must be positive" }
    }

    private data class Entry(
        val bytes: ByteArray,
        val pinned: Int,
    )

    /**
     * Insertion-ordered map used as an LRU. Re-inserting a key on access moves it to the end,
     * which is exactly LRU ordering without a separate recency list to keep in sync.
     */
    private val entries = LinkedHashMap<PageSourceId, Entry>()

    private var retained = 0

    /** Entries refused because they were pinned, for diagnostics and tests. */
    private var blockedByPin = 0

    override val retainedBytes: Int get() = retained

    /** How many values were declined to avoid evicting pinned pages. */
    val refusedForPinnedEntries: Int get() = blockedByPin

    /** Number of retained entries. */
    val entryCount: Int get() = entries.size

    /**
     * Sum of the byte lengths of the entries actually held.
     *
     * Exposed so tests can assert that [retainedBytes] agrees with the real contents. The two are
     * maintained by different code paths on purpose: the counter is what production relies on, and
     * recomputing is the only way to catch it drifting.
     */
    val retainedEntryBytes: Int get() = entries.values.sumOf { it.bytes.size }

    override fun get(id: PageSourceId): ByteArray? {
        val entry = entries[id] ?: return null
        // Re-insert to mark as most recently used.
        entries.remove(id)
        entries[id] = entry
        return entry.bytes
    }

    override fun contains(id: PageSourceId): Boolean = entries.containsKey(id)

    override fun put(id: PageSourceId, bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        if (bytes.size > budgetBytes) return false

        val existing = entries[id]
        if (existing != null) {
            retained -= existing.bytes.size
            entries.remove(id)
        }

        // Preserve an outstanding pin across a byte replacement: the UI may still be reading the
        // previous value, and dropping the pin here would let it be evicted mid-composition.
        val pinCount = existing?.pinned ?: 0

        var needed = bytes.size + retained
        if (needed > budgetBytes) {
            evictUntil(needed - budgetBytes, protecting = id)
        }
        if (retained + bytes.size > budgetBytes) {
            // Everything evictable is gone and it still does not fit, so the remainder is pinned.
            blockedByPin++
            return false
        }

        entries[id] = Entry(bytes = bytes, pinned = pinCount)
        retained += bytes.size
        return true
    }

    override fun remove(id: PageSourceId): ByteArray? {
        val entry = entries.remove(id) ?: return null
        retained -= entry.bytes.size
        return entry.bytes
    }

    override fun pin(id: PageSourceId) {
        val entry = entries[id] ?: return
        entries[id] = entry.copy(pinned = entry.pinned + 1)
    }

    override fun unpin(id: PageSourceId) {
        val entry = entries[id] ?: return
        if (entry.pinned > 0) {
            entries[id] = entry.copy(pinned = entry.pinned - 1)
        }
    }

    override fun clear() {
        entries.clear()
        retained = 0
        blockedByPin = 0
    }

    /**
     * Drops least-recently-used unpinned entries until at least [target] bytes are freed.
     *
     * Iteration order is insertion order, so the first unpinned entry found is the least recently
     * used. The newly inserted [protecting] key is never in the map yet, so it needs no special
     * handling here, but it is named for clarity at the call site.
     */
    private fun evictUntil(target: Int, protecting: PageSourceId) {
        var remaining = target
        if (remaining <= 0) return
        val iterator = entries.entries.iterator()
        while (iterator.hasNext() && remaining > 0) {
            val candidate = iterator.next()
            if (candidate.key == protecting) continue
            if (candidate.value.pinned > 0) continue
            retained -= candidate.value.bytes.size
            iterator.remove()
            remaining -= candidate.value.bytes.size
        }
    }
}
