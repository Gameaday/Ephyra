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
 * **Thread safety.** Every mutator is `@Synchronized`, and that is load-bearing rather than
 * decorative. The store is a plain [LinkedHashMap] plus an `Int` counter, and it is *not* confined
 * to one thread by its callers: `PageByteStoreOwner.retain` is reached from the decode path, which
 * runs `withIOContext` per visible page and therefore on several `Dispatchers.IO` threads at once,
 * while the viewport pin (`PageViewportPinPolicy`) and the chapter's disposal run on Main and on the
 * view-model scope. Two concurrent `put`s lose a counter update, so `retainedBytes` under-reports
 * and the store exceeds its own budget — which is the unbounded growth this class exists to
 * prevent. Concurrent structural modification of the map can additionally corrupt it, and `clear`
 * can throw [ConcurrentModificationException] out of chapter disposal, which would leave the old
 * chapter's loader unrecycled. These are O(1) map operations; the lock is not on any decode's hot
 * path in a way that matters, and correctness of the budget matters more than the uncontended
 * speed of a counter.
 */
class BoundedPageByteStore(
    override val budgetBytes: Int,
    /**
     * Invoked for every entry this store drops — by eviction, by [remove], or by [clear].
     *
     * This exists because bounding the store is not sufficient on its own. A cache whose only
     * reference to the bytes is the store bounds the store; a cache that *also* holds the same
     * array somewhere else — the reader keeps the payload on the page object — bounds nothing at all,
     * because the second reference keeps the array alive after eviction. The owner of those bytes
     * must therefore be told when the store lets go, or the budget is a number that describes the
     * store and not the memory.
     *
     * Not called for a value that was never admitted, and not called for a pin that keeps an entry.
     */
    private val onEvict: (PageSourceId) -> Unit = {},
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

    override val retainedBytes: Int
        get() = synchronized(this) { retained }

    /** How many values were declined to avoid evicting pinned pages. */
    val refusedForPinnedEntries: Int
        get() = synchronized(this) { blockedByPin }

    /** Number of retained entries. */
    val entryCount: Int
        get() = synchronized(this) { entries.size }

    /**
     * Sum of the byte lengths of the entries actually held.
     *
     * Exposed so tests can assert that [retainedBytes] agrees with the real contents. The two are
     * maintained by different code paths on purpose: the counter is what production relies on, and
     * recomputing is the only way to catch it drifting.
     */
    val retainedEntryBytes: Int
        get() = synchronized(this) { entries.values.sumOf { it.bytes.size } }

    @Synchronized
    override fun get(id: PageSourceId): ByteArray? {
        val entry = entries[id] ?: return null
        // Re-insert to mark as most recently used.
        entries.remove(id)
        entries[id] = entry
        return entry.bytes
    }

    @Synchronized
    override fun contains(id: PageSourceId): Boolean = entries.containsKey(id)

    @Synchronized
    override fun put(id: PageSourceId, bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        if (bytes.size > budgetBytes) return false

        val existing = entries[id]
        if (existing != null) {
            retained -= existing.bytes.size
            entries.remove(id)
            // The old array is being dropped in favour of a new load of the same id. Notifying is
            // what lets the owner clear the reference it holds to the *previous* bytes; staying
            // silent here would leave that reference alive with no store entry to explain it.
            onEvict(id)
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

    @Synchronized
    override fun remove(id: PageSourceId): ByteArray? {
        val entry = entries.remove(id) ?: return null
        retained -= entry.bytes.size
        onEvict(id)
        return entry.bytes
    }

    @Synchronized
    override fun pin(id: PageSourceId) {
        val entry = entries[id] ?: return
        entries[id] = entry.copy(pinned = entry.pinned + 1)
    }

    @Synchronized
    override fun unpin(id: PageSourceId) {
        val entry = entries[id] ?: return
        if (entry.pinned > 0) {
            entries[id] = entry.copy(pinned = entry.pinned - 1)
        }
    }

    @Synchronized
    override fun clear() {
        // Snapshot the keys first: onEvict may re-enter the store (a listener that writes through
        // would otherwise mutate the map this is iterating).
        val dropped = entries.keys.toList()
        entries.clear()
        retained = 0
        blockedByPin = 0
        dropped.forEach(onEvict)
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
            onEvict(candidate.key)
            remaining -= candidate.value.bytes.size
        }
    }
}
