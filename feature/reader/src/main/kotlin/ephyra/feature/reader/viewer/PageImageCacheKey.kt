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
 * The page identity is the owning chapter id, page index, and the source URLs used to
 * resolve the image. The URL component prevents a page whose source identity changes (for
 * example after a refreshed online page list) from reusing an older decoded bitmap under
 * the same chapter/index key. Merged (smart-combine) pages never reach Coil because they are
 * rendered from [ReaderPage.mergedBitmap] directly.
 */
internal fun readerPageMemoryCacheKey(page: ReaderPage, cropBorders: Boolean): String = buildString {
    append("page_")
    append(page.chapter.chapter.id)
    append('_')
    append(page.index)
    appendKeyPart(page.url)
    appendKeyPart(page.imageUrl)
    if (cropBorders) append("_cropped")
}

private fun StringBuilder.appendKeyPart(value: String?) {
    if (value.isNullOrEmpty()) {
        append("_")
        return
    }
    append('_')
    append(value.length)
    append(':')
    append(value)
}
