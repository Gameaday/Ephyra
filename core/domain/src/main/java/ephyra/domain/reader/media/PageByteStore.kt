package ephyra.domain.reader.media

/**
 * The byte-budgeted working store for page bytes.
 *
 * This exists because the current reader caches page bytes directly on the page object
 * (`ReaderPage.cachedBytes`), which is never bounded. A long webtoon chapter scrolls through
 * hundreds of pages, and every one of them keeps its full encoded bytes alive until the chapter
 * object is released. That is unbounded growth proportional to how far the reader scrolled, which
 * is the mechanism behind the reported jank and instability rather than a rendering problem.
 *
 * The contract is deliberately narrow:
 *  - the store accounts for **bytes**, not entry count, because page sizes vary by orders of
 *    magnitude and a count-based limit bounds nothing meaningful;
 *  - [put] reports whether the value was actually retained, so a caller never assumes a hit it did
 *    not get and silently renders from a value the store has already dropped;
 *  - [pin] protects a page the UI is actively reading, so an eviction cannot pull bytes out from
 *    under an in-flight composition.
 *
 * Durable bytes stay in the chapter/download store. This holds only the working set.
 */
interface PageByteStore {
    /** Returns the retained bytes for [id], or null on a miss. Marks the entry most recently used. */
    fun get(id: PageSourceId): ByteArray?

    /**
     * Whether bytes for [id] are retained, without affecting recency.
     *
     * Separate from [get] on purpose. A presence check that reorders the LRU would let a diagnostic
     * or an eviction-skipping check change what gets evicted next, which is a bug that only shows up
     * as unexplained cache behaviour.
     */
    fun contains(id: PageSourceId): Boolean

    /**
     * Offers bytes for [id].
     *
     * Returns false when the value was not retained: either it exceeded the whole budget, or it
     * was declined to protect already-pinned pages. A false result is not an error; the caller
     * simply must not treat the value as cached.
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
