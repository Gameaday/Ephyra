package ephyra.feature.reader.model

import ephyra.core.common.util.system.ImageUtil
import ephyra.domain.reader.media.BoundedPageByteStore
import ephyra.domain.reader.media.PageByteBudget
import ephyra.domain.reader.media.PageSourceId
import ephyra.domain.reader.media.PageViewportPinPolicy
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the reader's encoded page bytes, bounded by [PageByteBudget].
 *
 * **Why this exists rather than a bare [BoundedPageByteStore].** The store bounds its own entries,
 * but the reader also holds the same array on [ReaderPage.cachedBytes]. A store that evicts an
 * entry while that second reference is live bounds the store and not the heap — the bytes stay
 * reachable and the budget describes a cache rather than the process. This class closes the loop:
 * the store's eviction callback clears the page's own reference, so the two can never disagree
 * about who is holding the bytes.
 *
 * **Why a chapter-scoped owner.** One store per chapter, discarded with the chapter, so there is no
 * cross-chapter retention and no way for a stale chapter's bytes to survive into the next one.
 * That also makes [clear] the whole teardown.
 *
 * **Threading.** The owner's methods are `@Synchronized`, and the two index maps are concurrent.
 * That is not defensive decoration: this owner is a reader-session singleton reached from the
 * decode path, which runs `withIOContext` per visible page and so arrives on several
 * `Dispatchers.IO` threads at once, while the viewport pin is driven from the scroll handlers on
 * Main and the whole thing is torn down from the view-model scope. Lock order is always
 * *owner then store* and never the reverse — [onEvict] is the one callback that runs while the
 * store holds its own lock, so it touches only the concurrent maps and must never re-enter a
 * synchronized member of this class, or a disposal racing a decode could deadlock.
 */
class PageByteStoreOwner(
    memoryClassMb: Int,
) {
    private val byId = ConcurrentHashMap<PageSourceId, ReaderPage>()

    /**
     * Page index to identity, so a viewport can be pinned without a reverse scan of [byId].
     *
     * The store is keyed by identity and the pin policy speaks indices, so without this the
     * viewport path would have to search the map for every page in the window on every scroll.
     */
    private val indexToId = ConcurrentHashMap<Int, PageSourceId>()

    private fun unboundId(index: Int) = PageSourceId(
        sourceKey = UNBOUND_SOURCE,
        pageKey = index.toString(),
        revision = PageSourceId.UNKNOWN_REVISION,
    )

    /**
     * Set after construction because the eviction callback needs [byId], and a lambda cannot
     * capture the instance under construction. One store per owner, so this is assigned exactly
     * once before any page can be offered.
     */
    private val store = BoundedPageByteStore(
        budgetBytes = PageByteBudget.forMemoryClassMb(memoryClassMb),
        onEvict = ::onEvict,
    )

    private val pinPolicy = PageViewportPinPolicy()

    private fun onEvict(id: PageSourceId) {
        // Clearing the page's field is the whole point: without it the store's accounting and the
        // page's own reference describe two different sets of live bytes.
        byId.remove(id)?.cachedBytes = null
    }

    private fun idFor(page: ReaderPage): PageSourceId = PageSourceId(
        sourceKey = page.chapter.chapter.id.toString(),
        pageKey = page.index.toString(),
        // The page number is the revision proxy: a chapter re-fetch can renumber pages, and a
        // re-numbered page is different content behind the same index.
        revision = page.number.toString(),
    )

    /**
     * Offers [bytes] for [page] and returns the array the caller should keep using, or null.
     *
     * The returned value is the same instance either way. What changes is whether the store
     * retained it: when it did not, [page]'s own reference is left null so the array becomes
     * unreachable as soon as the caller lets go of it.
     */
    @Synchronized
    fun retain(page: ReaderPage, bytes: ByteArray): ByteArray {
        val id = idFor(page)
        val retained = store.put(id, bytes)
        byId[id] = page
        indexToId[page.index] = id
        page.cachedBytes = if (retained) bytes else null
        return bytes
    }

    /**
     * Pins the window around the viewport using this owner's own index mapping.
     *
     * The convenience form for callers that hold pages rather than identities; the index-based
     * overload exists because the pin policy is defined in indices.
     */
    @Synchronized
    fun pinViewportAt(index: Int, pageCount: Int) = pinViewportAt(index, pageCount) { i ->
        indexToId[i] ?: unboundId(i)
    }

    /** Bytes held for [page], without affecting eviction order. */
    @Synchronized
    fun peek(page: ReaderPage): ByteArray? {
        val bytes = page.cachedBytes ?: return null
        return if (store.contains(idFor(page))) bytes else null
    }

    /**
     * Pins the pages around the viewport at [index] of [pageCount].
     *
     * Declared rather than counted so a page cannot be pinned without also being unpin-able: see
     * [PageViewportPinPolicy] on why a per-composable pin/unpin pair leaks.
     *
     * The policy works in page indices (that is what a viewport knows) while the store works in
     * identities, so [idFor] bridges them. Resolving to a page that was never offered is harmless:
     * pinning an absent entry is a no-op.
     */
    @Synchronized
    fun pinViewportAt(index: Int, pageCount: Int, idFor: (Int) -> PageSourceId) {
        pinPolicy.update(store, index, pageCount, idFor)
    }

    /** Releases every pin and drops every retained array. Called when the chapter is disposed. */
    @Synchronized
    fun clear(idFor: (Int) -> PageSourceId) {
        pinPolicy.releaseAll(store, idFor)
        store.clear()
        byId.clear()
        indexToId.clear()
    }

    /** Releases pins and retained bytes using this owner's own page-index mapping. */
    @Synchronized
    fun clear() = clear { index -> indexToId[index] ?: unboundId(index) }

    /** Bytes currently retained. Exposed for tests and for the on-device measurement. */
    val retainedBytes: Int get() = store.retainedBytes

    /** The configured ceiling, for the same reason. */
    val budgetBytes: Int get() = store.budgetBytes

    /** Number of pages the store currently holds bytes for. */
    val pageCount: Int get() = byId.size

    private companion object {
        /**
         * Source key for an index with no retained entry.
         *
         * Distinct from any real chapter id so a pin computed for a page that was never offered
         * cannot collide with a real one, and distinct from any real source so [PageSourceId]'s
         * non-blank requirement stays satisfied.
         */
        const val UNBOUND_SOURCE = "unbound"
    }
}
