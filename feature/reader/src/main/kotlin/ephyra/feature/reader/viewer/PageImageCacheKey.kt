package ephyra.feature.reader.viewer

import ephyra.domain.reader.media.BorderCropCacheKey
import ephyra.feature.reader.model.ReaderPage

/**
 * Stable Coil memory-cache key for a reader page.
 *
 * Must capture every request option that changes decoded pixels. The *crop borders* toggle does,
 * so omitting it makes Coil serve the un-cropped bitmap and the setting appears to do nothing.
 *
 * Setting `memoryCacheKey` **replaces** Coil's computed key, which is where a
 * `Transformation.cacheKey` normally appears — so [BorderCropCacheKey.VALUE] is appended
 * explicitly. Without it the key can say *cropped* but not *cropped by which algorithm*, and
 * bumping the crop algorithm would invalidate nothing.
 *
 * The constant comes from `core:domain`, not `core:data`, because importing the transformation
 * inverts the layering and fails the "no `ephyra.data.*` in feature sources" gate — and a second
 * hand-written copy here is the drift this avoids.
 *
 * Page identity is chapter id, index, and the resolving URLs; the URL component stops a page
 * whose source identity changed from reusing an older bitmap. Merged pages never reach Coil.
 */
internal fun readerPageMemoryCacheKey(page: ReaderPage, cropBorders: Boolean): String = buildString {
    append("page_")
    append(page.chapter.chapter.id)
    append('_')
    append(page.index)
    appendKeyPart(page.url)
    appendKeyPart(page.imageUrl)
    if (cropBorders) {
        // Length-prefixed like every other part, so no two different inputs can concatenate to the
        // same key by accident — the reason the URL parts above carry their lengths.
        appendKeyPart(BorderCropCacheKey.VALUE)
    }
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
