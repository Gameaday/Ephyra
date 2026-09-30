package ephyra.feature.reader.viewer

import ephyra.domain.reader.media.BorderCropCacheKey
import ephyra.feature.reader.model.ReaderPage

/**
 * Stable Coil memory-cache key for a reader page.
 *
 * The key must capture every request option that changes the decoded pixels. The reader's
 * *Crop borders* toggle does exactly that — it changes the decoded bitmap — so it has to be
 * part of the key. Omitting it makes Coil serve the previously cached, un-cropped bitmap and
 * the setting appears to do nothing until the cache is cleared.
 *
 * **Why the crop toggle alone is not enough, and why this carries the algorithm version.**
 * Setting `memoryCacheKey` on an `ImageRequest` *replaces* the key Coil would otherwise compute,
 * and the computed key is the only place a `Transformation.cacheKey` normally appears. Both reader
 * call sites set it. So `BorderCropTransformation.cacheKey` was in no cache key on any production
 * path: the key could say *cropped* but not *cropped by which algorithm*. Changing the crop
 * algorithm and bumping that constant — the ordinary way to ship a fix to a transformation — would
 * have invalidated nothing, and every page decoded before the upgrade kept serving the old pixels
 * with no error anywhere. Carrying [BorderCropCacheKey.VALUE] here is what makes the bump mean
 * something.
 *
 * **Why the value comes from `core:domain` and not from `core:data`.** The transformation that
 * produces the pixels lives in `core:data`, and the obvious thing to do was import it for its
 * constant — which inverted the layering and failed `build.yml`'s "no `ephyra.data.*` imports in
 * feature production sources" gate. The alternative, keeping a second hand-written copy of the
 * version string here, is the defect being fixed: two copies of a value that must agree, with
 * nothing at compile time to stop them drifting, so a bump in one place silently leaves the other
 * serving the previous algorithm's pixels. `BorderCropCacheKey` is therefore the single definition
 * and both sides reference it, which satisfies the gate and removes the drift risk at once. The
 * duplication alternative was considered and rejected on those grounds, not overlooked.
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
