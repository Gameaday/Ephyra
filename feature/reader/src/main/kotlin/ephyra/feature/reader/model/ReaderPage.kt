package ephyra.feature.reader.model

import eu.kanade.tachiyomi.source.model.Page

/**
 * One reader page: immutable identity plus a [PageResource] payload handle (R-C, Stage 4 of the
 * reader modular redesign, 2026-11-09).
 *
 * **What the page is after this sweep.** Identity (`index`, `url`, `imageUrl`, the owning
 * [ReaderChapter]) and cheap session metadata ([isAbsorbed], [isBlockedByFilter],
 * [needsFreshAddress]) — everything heavy a page can hold, the stream lambda, the cached bytes,
 * the merged bitmap and the recorded dimensions, lives behind [resource], whose lifecycle the
 * chapter owns. Audit finding F5 was this class carrying that payload directly: every adapter,
 * viewer and dialog that held a page could reach its bytes, and payload lifetime was page
 * lifetime. Access now goes through the handle, which is enforced by the build rather than by
 * review — the payload members no longer exist here, and `ReaderPageResourceOwnershipTest` fails
 * the build if they come back.
 *
 * **What still mutates on the page, on purpose.** The inherited [Page.status] is the
 * extension-facing load state (source-api; changing it breaks the extension ABI), and the three
 * session flags are boolean navigation metadata — absorbed/blocked pages report [isHidden] to the
 * completion policy and the adapters, and the recovery ladder reads [needsFreshAddress]. None of
 * them holds payload, and their contracts are pinned by their own tests. The gate names them so
 * adding a fourth mutable member is a deliberate edit.
 */
open class ReaderPage(
    index: Int,
    url: String = "",
    imageUrl: String? = null,
    resource: PageResource = PageResource(),
) : Page(index, url, imageUrl, null) {

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
