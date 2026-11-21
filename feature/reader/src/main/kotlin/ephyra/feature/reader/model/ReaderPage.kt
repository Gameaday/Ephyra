package ephyra.feature.reader.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One reader page: immutable identity plus a [PageResource] payload handle.
 *
 * Identity (`index`, `url`, [imageUrl], the owning [ReaderChapter]) and cheap session metadata
 * ([isAbsorbed], [isBlockedByFilter], [needsFreshAddress]). Everything heavy — the stream
 * lambda, cached bytes, merged bitmap, recorded dimensions — lives behind [resource], whose
 * lifecycle the chapter owns. `ReaderPageResourceOwnershipTest` fails the build if those
 * payload members reappear here.
 *
 * Does not extend the extension ABI's `Page`. The loader receives `Page` from a source and
 * maps it here, so the reader describes a page in its own terms.
 */
open class ReaderPage(
    val index: Int,
    val url: String = "",
    imageUrl: String? = null,
    resource: PageResource = PageResource(),
) {

    /** Resolved image address, or null when the source has not provided one yet. */
    var imageUrl: String? = imageUrl

    /** One-based page number, for display. */
    val number: Int get() = index + 1

    private val _statusFlow = MutableStateFlow<PageState>(PageState.Queue)

    /** Observed by the viewers, which recompose a page when its state changes. */
    val statusFlow: StateFlow<PageState> = _statusFlow.asStateFlow()

    var status: PageState
        get() = _statusFlow.value
        set(value) {
            _statusFlow.value = value
        }

    private val _progressFlow = MutableStateFlow(0)

    val progressFlow: StateFlow<Int> = _progressFlow.asStateFlow()

    var progress: Int
        get() = _progressFlow.value
        set(value) {
            _progressFlow.value = value
        }

    open lateinit var chapter: ReaderChapter

    /**
     * The payload handle for this page — bytes, stream, merges and recorded dimensions.
     *
     * Created with the page and never replaced; [InsertPage] shares its parent's handle. The
     * chapter owns the handle's lifecycle: see [PageResource] for who writes what.
     */
    val resource: PageResource = resource

    /**
     * True once this page has been absorbed by the previous page as a stub during smart combine.
     * Absorbed pages are removed from the adapter's item list but remain in [chapter.pages].
     * The flag is used by the ViewModel to determine the effective last page of a chapter so
     * that read-marking still fires when the last pages are a merged pair.
     */
    @Volatile
    var isAbsorbed: Boolean = false

    /**
     * True when this page matched a blocked perceptual-hash entry during pre-processing.
     * Set by [ephyra.app.ui.reader.loader.ReaderPagePreProcessor] for both
     * downloaded and online pages.
     */
    @Volatile
    var isBlockedByFilter: Boolean = false

    /**
     * Convenience flag: `true` when the page should be hidden from the viewer, either
     * because it was absorbed by smart-combine or blocked by the page filter.
     * Adapters, page counts, and navigation all use this to determine visible pages.
     */
    val isHidden: Boolean get() = isAbsorbed || isBlockedByFilter

    /** The intrinsic aspect of the rendered surface, from the dims recorded on the handle. */
    val aspectRatio: Float?
        get() = resource.aspectRatio

    /**
     * Set when the recovery ladder has indicted this page's address and a replacement must come from
     * a fresh page list.
     *
     * **Why a flag rather than clearing the address.** For a source that populates `Page.imageUrl` in
     * `getPageList` — every 1.6 extension, since upstream removed the per-page chain — an address of
     * `null` is a page that can never be loaded again: the loader asks `getImageUrl`, which such a
     * source does not implement, and the inherited default throws. That was the reported MangaDex
     * failure, and it happened on every path that dropped an address.
     *
     * The address is left in place until its replacement arrives, so the page, the disk-cache key and
     * the URL that will be requested stay the same string in the meantime — which is the invariant the
     * reader is careful about elsewhere.
     */
    @Volatile
    var needsFreshAddress: Boolean = false
}
