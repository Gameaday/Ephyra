package ephyra.feature.reader.model

import android.graphics.Bitmap
import eu.kanade.tachiyomi.source.model.Page
import java.io.InputStream

open class ReaderPage(
    index: Int,
    url: String = "",
    imageUrl: String? = null,
    var stream: (() -> InputStream)? = null,
) : Page(index, url, imageUrl, null) {

    open lateinit var chapter: ReaderChapter

    /**
     * Cached [Bitmap] of a smart-combine merge with a following stub page.
     * Non-null once the merge has succeeded; subsequent renders hand this bitmap to Compose
     * via `asImageBitmap()` without any encoding or decoding.
     *
     * **Lifecycle:** cleared via [clearMergedBitmap] when the page is retried or when the owning
     * [ReaderChapter] is disposed. Dropping the reference is sufficient — the pixel buffer is
     * reclaimed by ART, and explicit recycling is unsafe because a Compose snapshot may still
     * reference the bitmap.
     */
    @Volatile
    var mergedBitmap: Bitmap? = null

    /**
     * Drops the cached merge so that the next render triggers a fresh merge.
     *
     * The previous bitmap is deliberately **not** recycled: see [mergedBitmap].
     */
    fun clearMergedBitmap() {
        mergedBitmap = null
    }

    /**
     * Drops all heavy page payloads (bytes + merges) and resets measured dims. Called only
     * at chapter boundaries (chapter falls out of the prev/curr/next window, or the reader
     * screen is left) — never inside the active chapter, so up/down scrolling within a
     * chapter can never trigger a re-download.
     */
    fun releasePageResources() {
        cachedBytes = null
        clearMergedBitmap()
        width = 0
        height = 0
    }

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

    /**
     * Intrinsic image dimensions recorded once decoded, enabling LazyColumn
     * to preserve exact item height during reverse scrolling.
     */
    @Volatile
    var width: Int = 0

    @Volatile
    var height: Int = 0

    val aspectRatio: Float?
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else null

    /**
     * In-memory cache of the image payload bytes to prevent redundant disk I/O on recomposition.
     */
    @Volatile
    var cachedBytes: ByteArray? = null
}
