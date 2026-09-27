package ephyra.feature.reader.model

import ephyra.core.common.util.system.logcat
import ephyra.domain.chapter.model.Chapter
import ephyra.feature.reader.loader.PageLoader
import kotlinx.coroutines.flow.MutableStateFlow

class ReaderChapter(var chapter: Chapter) {

    val stateFlow = MutableStateFlow<State>(State.Wait)
    var state: State
        get() = stateFlow.value
        set(value) {
            stateFlow.value = value
        }

    val pages: List<ReaderPage>?
        get() = (state as? State.Loaded)?.pages

    var pageLoader: PageLoader? = null

    /**
     * The chapter's bounded page-byte working set, or null before the reader supplies one.
     *
     * Chapter-scoped so [clear] is the whole teardown: no stale chapter's bytes survive into the
     * next one, and disposal cannot leave a store alive. Null until the ViewModel constructs it
     * (it needs the `Application` for the heap class) and null in tests and previews, where the
     * write sites fall back to the page's own field exactly as before.
     *
     * The store bounds the *array*; it is the owner's eviction callback that clears
     * [ReaderPage.cachedBytes], so a bounded store and a retained page can never disagree.
     */
    var byteStore: PageByteStoreOwner? = null

    /**
     * Caches [bytes] for [page] through this chapter's store, if one exists.
     *
     * Returns the same array either way, so a call site reads exactly as before. What changes is
     * whether the bytes are *retained*: when the store declines them the page's own reference is
     * left null, and the array becomes unreachable as soon as the caller lets go of it.
     */
    fun cacheBytes(page: ReaderPage, bytes: ByteArray): ByteArray =
        byteStore?.retain(page, bytes) ?: bytes.also { page.cachedBytes = it }

    /** Pins the window around [index] of [pageCount]. No-op without a store. */
    fun pinViewport(index: Int, pageCount: Int) {
        byteStore?.pinViewportAt(index, pageCount)
    }

    /** Drops the chapter's retained bytes and every pin. Called on disposal. */
    fun releaseByteStore() {
        byteStore?.clear()
    }

    var requestedPage: Int = 0
    var startingAtBeginning: Boolean = false
    var startFromEnd: Boolean = false

    private var references = 0

    fun ref() {
        references++
    }

    fun unref() {
        references--
        if (references == 0) {
            if (pageLoader != null) {
                logcat { "Recycling chapter ${chapter.name}" }
            }
            pageLoader?.recycle()
            pageLoader = null
            startingAtBeginning = false
            startFromEnd = false
            // Release the bounded working set before the pages go, so eviction clears each page's
            // own reference while the page list is still reachable. Dropping the store afterwards
            // would leave the arrays alive on the pages with nothing left to explain them.
            releaseByteStore()
            // Drop every heavy page payload (bytes + merges + measured dims) before
            // dropping the page list so all allocations become unreachable for the next
            // GC; ART reclaims the pixel buffers, so no explicit recycling is required
            // (recycling while Compose holds a snapshot crashes). This is the boundary
            // disposal that keeps long sessions from pinning every visited chapter:
            // the active chapter is untouched, so in-chapter scrolling never re-downloads.
            (state as? State.Loaded)?.pages?.forEach { page ->
                page.releasePageResources()
            }
            state = State.Wait
        }
    }

    sealed interface State {
        data object Wait : State
        data object Loading : State
        data class Error(val error: Throwable) : State
        data class Loaded(val pages: List<ReaderPage>) : State
    }
}
