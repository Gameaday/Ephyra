package ephyra.domain.reader.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PageMetadataTest {

    @Test
    fun `an uncropped page displays at its intrinsic size`() {
        val metadata = PageMetadata(PixelSize(800, 1200), AnimationKind.STATIC, PageImageFormat.JPEG)
        assertEquals(PixelSize(800, 1200), metadata.displaySize)
        assertFalse(metadata.isCropped)
        assertNull(metadata.contentRect)
    }

    @Test
    fun `a cropped page displays at its content size`() {
        val metadata = PageMetadata(
            intrinsicSize = PixelSize(800, 1200),
            animation = AnimationKind.STATIC,
            format = PageImageFormat.JPEG,
            contentRect = PixelRect(10, 20, 790, 1180),
        )
        assertEquals(PixelSize(780, 1160), metadata.displaySize)
        assertTrue(metadata.isCropped)
    }

    @Test
    fun `cropping changes aspect ratio so layout must use display size`() {
        val metadata = PageMetadata(
            intrinsicSize = PixelSize(1000, 1000),
            animation = AnimationKind.STATIC,
            format = PageImageFormat.JPEG,
            contentRect = PixelRect(0, 0, 800, 1000),
        )
        assertEquals(1f, metadata.intrinsicSize.aspectRatio)
        assertEquals(0.8f, metadata.displaySize.aspectRatio, 1e-5f)
    }

    @Test
    fun `only static pages support slicing`() {
        val still = PageMetadata(PixelSize(10, 10), AnimationKind.STATIC, PageImageFormat.GIF)
        val moving = PageMetadata(PixelSize(10, 10), AnimationKind.ANIMATED, PageImageFormat.GIF)
        assertTrue(still.supportsSlicing)
        assertFalse(moving.supportsSlicing)
    }

    @Test
    fun `an unsupported format is still sliceable in principle`() {
        // A decode failure is handled by falling back to a whole-image request, so the format
        // itself is not a reason to refuse slicing at planning time.
        val metadata = PageMetadata(PixelSize(10, 10), AnimationKind.STATIC, PageImageFormat.UNSUPPORTED)
        assertTrue(metadata.supportsSlicing)
    }

    @Test
    fun `a webp page takes its animation kind from the bytes not the container`() {
        // Both fixtures are `.webp`; only one is animated. Proves the type does not infer
        // animation from the format enum.
        val asStatic = PageMetadata(PixelSize(10, 10), AnimationKind.STATIC, PageImageFormat.WEBP)
        val asAnimated = PageMetadata(PixelSize(10, 10), AnimationKind.ANIMATED, PageImageFormat.WEBP)
        assertNotEqualsSame(asStatic.supportsSlicing, asAnimated.supportsSlicing)
    }

    @Test
    fun `non positive and inverted dimensions are rejected`() {
        assertThrows<IllegalArgumentException> {
            PixelSize(0, 100)
        }
        assertThrows<IllegalArgumentException> {
            PixelSize(100, -1)
        }
        assertThrows<IllegalArgumentException> {
            PixelRect(10, 0, 10, 100)
        }
    }

    @Test
    fun `a content rect cannot start outside the image`() {
        assertThrows<IllegalArgumentException> {
            PixelRect(-1, 0, 10, 10)
        }
    }

    private fun assertNotEqualsSame(first: Boolean, second: Boolean) =
        assertTrue(first != second, "expected the two booleans to differ")
}
