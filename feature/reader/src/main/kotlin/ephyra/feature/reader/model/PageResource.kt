package ephyra.feature.reader.model

import android.graphics.Bitmap
import java.io.InputStream

/**
 * The mutable payload of one reader page, behind [ReaderPage.resource] (R‑C, Stage 4).
 *
 * Before Stage 4 these fields lived on [ReaderPage] itself (audit F5): a stream lambda, a merged
 * bitmap, mutable dims and a byte array, all reachable from every adapter, viewer and dialog
 * holding the page. The page is a long‑lived *identity* — compose keys and the session record hold
 * it for as long as the chapter is open — so payload there made payload lifetime equal page
 * lifetime. [ReaderPage] is metadata plus this handle; every payload access goes through it.
 *
 * **Lifecycle, in three named places:** [PageByteStoreOwner] is the only thing that retains and
 * evicts [cachedBytes] while the chapter is open, and its eviction callback clears the field
 * through this handle so accounting and heap cannot disagree; the loaders and smart‑merge scan
 * write [stream] and [mergedBitmap]; chapter disposal ([ReaderChapter.unref]) calls
 * [clearLoadedImage] on every page. The handle is created with the page and never replaced.
 *
 * Session flags (`isAbsorbed`, `isBlockedByFilter`, `needsFreshAddress`) and the extension‑facing
 * `Page.status` stay on the page — navigation metadata with their own pinned contracts.
 *
 * Members stay `@Volatile`: decode writes from several IO threads, the merge scan from its own IO
 * job, and the render path reads from Main.
 */
class PageResource {

    /**
     * Byte source of the page image: a fresh‑open lambda over the disk cache, an archive entry, a
     * content URI or an EPUB path. Written by the loaders; read by the render path, the merge scan,
     * the pre‑processor and the image‑action dialog. Nulled by reload and by loader recycle so the
     * captured addresses can be collected.
     */
    @Volatile
    var stream: (() -> InputStream)? = null

    /**
     * In-memory cache of the encoded image payload, avoiding redundant disk I/O on recomposition.
     * Retained **only** through the chapter's bounded byte store; a direct assignment here bypasses
     * the budget and is what `PageByteWriteSiteTest` fails the build for.
     */
    @Volatile
    var cachedBytes: ByteArray? = null

    /**
     * Cached [Bitmap] of a smart‑combine merge. Non‑null once merged; renders hand it to Compose
     * via `asImageBitmap()` with no encode or decode.
     *
     * Cleared by [clearMergedBitmap] on retry and on chapter disposal. The previous bitmap is
     * deliberately never recycled — a Compose snapshot may still reference it.
     */
    @Volatile
    var mergedBitmap: Bitmap? = null

    /**
     * Intrinsic dimensions recorded once decoded, letting LazyColumn preserve exact item height
     * during reverse scrolling. `0` means unknown.
     */
    @Volatile
    var width: Int = 0

    @Volatile
    var height: Int = 0

    /** The intrinsic aspect from the recorded dims, or null while unknown. */
    val aspectRatio: Float?
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else null

    /**
     * True once a viewer recorded decoded dimensions — proof a surface was produced, not just bytes
     * cached. The automatic background tint samples through this (audit F7).
     */
    val hasRecordedSurface: Boolean
        get() = width > 0 && height > 0

    /**
     * Bytes of the rendered surface, or null until one was recorded.
     *
     * The background tint reads this rather than [cachedBytes], which sampled the wrong page and
     * un‑rendered pages alike. A merged page whose bytes were never cached reports null.
     */
    val renderedSurfaceBytes: ByteArray?
        get() = if (hasRecordedSurface) cachedBytes else null

    /** Drops the cached merge so the next render triggers a fresh one. */
    fun clearMergedBitmap() {
        mergedBitmap = null
    }

    /**
     * Clears the loaded image payload and derived dimensions, keeping page identity.
     * Used before re‑fetching an online page, and at chapter disposal.
     */
    fun clearLoadedImage() {
        cachedBytes = null
        clearMergedBitmap()
        width = 0
        height = 0
    }
}
