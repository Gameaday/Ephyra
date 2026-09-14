package ephyra.feature.reader.viewer

import ephyra.feature.reader.model.ReaderPage

/**
 * Stable Coil memory-cache key for a reader page.
 *
 * The key must capture every request option that changes the decoded pixels. The reader's
 * *Crop borders* toggle does exactly that — it changes the decoded bitmap — so it has to be
 * part of the key. Omitting it makes Coil serve the previously cached, un-cropped bitmap and
 * the setting appears to do nothing until the cache is cleared.
 *
 * The page identity is the owning chapter id plus the page index; merged (smart-combine)
 * pages never reach Coil because they are rendered from [ReaderPage.mergedBitmap] directly.
 */
internal fun readerPageMemoryCacheKey(page: ReaderPage, cropBorders: Boolean): String = buildString {
    append("page_")
    append(page.chapter.chapter.id)
    append('_')
    append(page.index)
    if (cropBorders) append("_cropped")
}
