package ephyra.domain.reader.media

/**
 * The byte-budgeted working store for page bytes.
 *
 * Exists because the reader caches page bytes on the page object
 * (`ReaderPage.resource.cachedBytes`), which is unbounded. A long webtoon chapter scrolls through
 * hundreds of pages and each keeps its full encoded bytes alive until the chapter is released —
 * growth proportional to how far the reader scrolled, which is the mechanism behind the reported
 * jank rather than a rendering problem.
 *
 * The contract is deliberately narrow:
 *  - accounts for **bytes**, not entry count — page sizes vary by orders of magnitude, so a count
 *    limit bounds nothing meaningful;
 *  - [put] reports whether the value was retained, so a caller never assumes a hit it did not get;
 *  - [pin] protects a page the UI is actively reading, so eviction cannot pull bytes out from under
 *    an in-flight composition.
 *
 * Durable bytes stay in the chapter/download store. This holds only the working set.
 */
interface PageByteStore {
    /** Returns the retained bytes for [id], or null on a miss. Marks the entry most recently used. */
    fun get(id: PageSourceId): ByteArray?

    /**
     * Whether bytes for [id] are retained, without affecting recency.
     *
     * Separate from [get] on purpose: a presence check that reordered the LRU would let a
     * diagnostic change what gets evicted next.
     */
    fun contains(id: PageSourceId): Boolean

    /**
     * Offers bytes for [id].
     *
     * Returns false when the value was not retained — it exceeded the whole budget, or was declined
     * to protect already-pinned pages. Not an error; the caller must not treat the value as cached.
     */
    fun put(id: PageSourceId, bytes: ByteArray): Boolean

    /** Removes [id]. Returns the bytes if they were present. */
    fun remove(id: PageSourceId): ByteArray?

    /**
     * Marks [id] as in active use so it is not evicted.
     *
     * Pinning is reference counting: unbalanced pins would leak entries permanently, which is the
     * same unbounded growth this store exists to prevent.
     */
    fun pin(id: PageSourceId)

    /** Releases one pin. */
    fun unpin(id: PageSourceId)

    /** Drops everything, including pinned entries. Used on chapter change. */
    fun clear()

    /** Bytes currently retained, including pinned entries. */
    val retainedBytes: Int

    /** The configured ceiling. */
    val budgetBytes: Int
}
