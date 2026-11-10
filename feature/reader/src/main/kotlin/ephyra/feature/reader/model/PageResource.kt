package ephyra.feature.reader.model

import android.graphics.Bitmap
import java.io.InputStream

/**
 * The mutable payload of one reader page, held behind [ReaderPage.resource] (R-C, Stage 4 of the
 * reader modular redesign, 2026-11-09).
 *
 * **Why a handle exists.** Before Stage 4 the payload fields lived on [ReaderPage] itself (audit
 * finding F5): a stream lambda, a merged bitmap, mutable dimensions and a byte array, all
 * `@Volatile`, all reachable from every adapter, viewer and dialog that held the page. The page
 * object is a long-lived *identity* — compose keys, adapters and the session record hold it for as
 * long as the chapter is open — so payload fields on it made payload lifetime equal page lifetime
 * and invited writes from anywhere. This class is the boundary: [ReaderPage] is metadata plus this
 * handle, and every payload read or write goes through `page.resource`.
 *
 * **Who owns the lifecycle.** The chapter, in three named places: the bounded byte store
 * ([PageByteStoreOwner]) is the only thing that retains and evicts [cachedBytes] while the chapter
 * is open, and its eviction callback clears the field through this handle so the store's accounting
 * and the heap can never disagree; the loaders and the smart-merge scan are the writers of
 * [stream] and [mergedBitmap]; and chapter disposal ([ReaderChapter.unref]) calls
 * [clearLoadedImage] on every page's handle, which is the boundary disposal that keeps long
 * sessions from pinning every visited chapter. The handle itself is created with the page and never
 * replaced: a page cannot lose its resource or acquire another page's — and [InsertPage] shares its
 * parent's handle, which is the sharing the old code approximated by copying the stream lambda.
 *
 * **What deliberately does not live here.** The session flags (`isAbsorbed`, `isBlockedByFilter`,
 * `needsFreshAddress`) and the inherited extension-facing `Page.status` stay on the page: they are
 * cheap navigation metadata whose consumers (completion policy, adapters, the recovery ladder) are
 * pinned by their own tests, not payload, and moving them would change those contracts without
 * touching the defect this class exists for.
 *
 * **Threading.** The members stay `@Volatile` exactly as they were on the page: the decode path
 * writes from several `Dispatchers.IO` threads at once, the smart-merge scan writes from its own IO
 * job, and the render path reads from Main.
 */
class PageResource {

    /**
     * The byte source of the page image: a fresh-open lambda over the disk cache, an archive entry,
     * a content URI or an EPUB path. Written by the loaders; read by the render path, the
     * smart-merge scan, the pre-processor and the image-action dialog. Nulled by the reload path
     * and by loader recycle so the captured addresses can be collected.
     */
    @Volatile
    var stream: (() -> InputStream)? = null

    /**
     * In-memory cache of the encoded image payload to prevent redundant disk I/O on
     * recomposition. Retained **only** through the chapter's bounded byte store; a direct
     * assignment here bypasses the budget and is what `PageByteWriteSiteTest` fails the build for.
     */
    @Volatile
    var cachedBytes: ByteArray? = null

    /**
     * Cached [Bitmap] of a smart-combine merge. Non-null once the merge has succeeded; renders
     * hand it to Compose via `asImageBitmap()` without any encoding or decoding.
     *
     * **Lifecycle:** cleared via [clearMergedBitmap] when the page is retried or when the owning
     * [ReaderChapter] is disposed. The previous bitmap is deliberately never recycled — ART reclaims
     * the pixel buffer, and explicit recycling is unsafe because a Compose snapshot may still
     * reference the bitmap.
     */
    @Volatile
    var mergedBitmap: Bitmap? = null

    /**
     * Intrinsic image dimensions recorded once decoded, enabling LazyColumn to preserve exact
     * item height during reverse scrolling. `0` is the model's "unknown" state.
     */
    @Volatile
    var width: Int = 0

    @Volatile
    var height: Int = 0

    /** The intrinsic aspect from the recorded dims, or null while unknown. */
    val aspectRatio: Float?
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else null

    /**
     * True once a viewer recorded decoded dimensions for this page — the proof that a surface was
     * actually produced rather than bytes merely sitting in the working set.
     *
     * This is the rendered-surface signal the automatic background samples through (audit F7):
     * both viewers write the dimensions only after a real decode or merge, so `hasRecordedSurface`
     * distinguishes "the page the user is looking at has rendered" from "some bytes happen to be
     * cached".
     */
    val hasRecordedSurface: Boolean
        get() = width > 0 && height > 0

    /**
     * Bytes of the rendered surface, or null until one was recorded (see [hasRecordedSurface]).
     *
     * The automatic background tint reads this rather than [cachedBytes] directly: sampling the
     * working bytes keyed on whatever was cached sampled the wrong page and un-rendered pages
     * alike. A merged page whose bytes were never cached reports null here, exactly as it would
     * have before — the merge bitmap is not sampleable without an encode, which was never worth
     * doing per page change.
     */
    val renderedSurfaceBytes: ByteArray?
        get() = if (hasRecordedSurface) cachedBytes else null

    /** Drops the cached merge so the next render triggers a fresh merge. */
    fun clearMergedBitmap() {
        mergedBitmap = null
    }

    /**
     * Clears the loaded image payload and derived dimensions without changing page identity.
     * Used before an online page is fetched again after a failed or invalidated load, and at
     * chapter disposal.
     */
    fun clearLoadedImage() {
        cachedBytes = null
        clearMergedBitmap()
        width = 0
        height = 0
    }
}
