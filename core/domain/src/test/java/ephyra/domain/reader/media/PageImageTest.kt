package ephyra.domain.reader.media

import ephyra.domain.reader.viewport.DocumentRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PageImageTest {

    private fun image(
        width: Int = 1000,
        height: Int = 1000,
        contentRect: PixelRect? = null,
        animation: AnimationKind = AnimationKind.STATIC,
    ): PageImage {
        val metadata = PageMetadata(
            intrinsicSize = PixelSize(width, height),
            animation = animation,
            format = PageImageFormat.JPEG,
            contentRect = contentRect,
        )
        return PageImage(
            identity = PageSourceId("src", "p1", "r1"),
            source = PageSource.LocalFile("/tmp/p1.jpg"),
            metadata = metadata,
            plan = DecodePlanner.plan(metadata, maxTextureSize = 8192),
        )
    }

    @Test
    fun `the page height follows the display aspect ratio, not the intrinsic one`() {
        // Cropping changes the ratio; using intrinsic here is what makes a page visibly resize
        // when crop is toggled.
        val cropped = image(width = 1000, height = 1000, contentRect = PixelRect(0, 0, 500, 1000))
        val placement = cropped.toDocumentPage(index = 0, pageId = "p1", documentWidth = 800f, documentTop = 0f)
        assertEquals(800f, placement.rect.width, 1e-3f)
        assertEquals(1600f, placement.rect.height, 1e-3f)
    }

    @Test
    fun `an uncropped square page occupies a square of document space`() {
        val placement = image().toDocumentPage(0, "p1", 800f, 0f)
        assertEquals(DocumentRect(0f, 0f, 800f, 800f), placement.rect)
    }

    @Test
    fun `pages stack without gaps when placed in sequence`() {
        val first = image().toDocumentPage(0, "p1", 800f, 0f)
        val second = image().toDocumentPage(1, "p2", 800f, first.rect.bottom)
        assertEquals(first.rect.bottom, second.rect.top)
    }

    @Test
    fun `a non positive document width is rejected`() {
        val page = image()
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            page.toDocumentPage(0, "p1", 0f, 0f)
        }
    }

    @Test
    fun `the memory cache key is stable for one page`() {
        val page = image()
        assertEquals(page.memoryCacheKey(), page.memoryCacheKey())
    }

    @Test
    fun `changing the transform tag changes the cache key`() {
        val page = image()
        assertNotEquals(
            page.memoryCacheKey("none"),
            page.memoryCacheKey("crop"),
        )
    }

    @Test
    fun `a different page identity gives a different cache key`() {
        val first = image()
        val second = first.copy(identity = PageSourceId("src", "p1", "r2"))
        assertNotEquals(first.memoryCacheKey(), second.memoryCacheKey())
    }

    @Test
    fun `cropping changes the cache key through the page`() {
        val uncropped = image()
        val cropped = image(contentRect = PixelRect(0, 0, 900, 900))
        assertNotEquals(uncropped.memoryCacheKey(), cropped.memoryCacheKey())
    }

    @Test
    fun `display size follows the content rect`() {
        assertEquals(PixelSize(1000, 1000), image().displaySize)
        assertEquals(
            PixelSize(900, 900),
            image(contentRect = PixelRect(0, 0, 900, 900)).displaySize,
        )
    }

    @Test
    fun `slicing is refused for animated pages`() {
        assertTrue(image().supportsSlicing)
        assertFalse(image(animation = AnimationKind.ANIMATED).supportsSlicing)
    }
}
