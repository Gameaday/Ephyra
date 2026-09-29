package ephyra.feature.reader.viewer

import ephyra.data.coil.BorderCropTransformation
import ephyra.domain.chapter.model.Chapter
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue

class PageImageCacheKeyTest {

    @Test
    fun `same page identity and options produce the same key`() {
        val page = page(url = "page", imageUrl = "image-a")

        assertEquals(
            readerPageMemoryCacheKey(page, cropBorders = false),
            readerPageMemoryCacheKey(page, cropBorders = false),
        )
    }

    @Test
    fun `source identity and crop options are separated`() {
        val page = page(url = "page-a", imageUrl = "image-a")

        assertNotEquals(
            readerPageMemoryCacheKey(page, cropBorders = false),
            readerPageMemoryCacheKey(page, cropBorders = true),
        )
        assertNotEquals(
            readerPageMemoryCacheKey(page, cropBorders = false),
            readerPageMemoryCacheKey(page(url = "page-b", imageUrl = "image-a"), cropBorders = false),
        )
        assertNotEquals(
            readerPageMemoryCacheKey(page, cropBorders = false),
            readerPageMemoryCacheKey(page(url = "page-a", imageUrl = "image-b"), cropBorders = false),
        )
    }

    /**
     * The key must carry *which* crop algorithm produced the bitmap, not merely *that* one did.
     *
     * Both reader call sites set `memoryCacheKey` explicitly, which replaces the key Coil computes
     * — and that computed key is the only place `BorderCropTransformation.cacheKey` would ever
     * appear. So before this the transformation's version was in no cache key on any production
     * path, and bumping it invalidated nothing: a crop algorithm fix shipped, and every page
     * decoded beforehand kept serving the old pixels with no error to explain it.
     *
     * Falsifiable by deletion: dropping the `CACHE_KEY` part fails the first assertion, and
     * replacing it with a bare `"_cropped"` marker fails it too, because the constant's *value* is
     * what is asserted rather than the presence of any suffix.
     */
    @Test
    fun `the cropped key carries the crop algorithm version`() {
        val page = page(url = "page", imageUrl = "image-a")

        val cropped = readerPageMemoryCacheKey(page, cropBorders = true)

        assertTrue(
            cropped.contains(BorderCropTransformation.CACHE_KEY),
            "a cropped key that does not name the algorithm cannot be invalidated when the algorithm changes",
        )
        assertFalse(
            readerPageMemoryCacheKey(page, cropBorders = false).contains(BorderCropTransformation.CACHE_KEY),
            "the un-cropped key must stay distinct, or cropping could collide with itself",
        )
    }

    /**
     * The version the reader writes into its key and the version Coil uses must be one value.
     *
     * Two constants is the defect restated: a reader key naming a crop version Coil does not use
     * would invalidate on every bump and never when it should.
     */
    @Test
    fun `the key the reader writes is the one the transformation reports`() {
        assertEquals(
            BorderCropTransformation.CACHE_KEY,
            BorderCropTransformation().cacheKey,
            "the transformation must report the constant the reader's cache key carries",
        )
    }

    private fun page(url: String, imageUrl: String): ReaderPage = ReaderPage(
        index = 2,
        url = url,
        imageUrl = imageUrl,
    ).apply {
        chapter = ReaderChapter(Chapter.create().copy(id = 42))
    }
}
