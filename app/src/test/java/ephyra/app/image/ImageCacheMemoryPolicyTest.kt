package ephyra.app.image

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Cap-pinning tests for the decoded-image memory-cache bounds (app health audit P1/P3,
 * 2026-11-13). The bounds are constants by design; these pin the arithmetic so a refactor
 * cannot silently move the hard cap or the baseline clamp.
 */
class ImageCacheMemoryPolicyTest {

    @Test
    fun `the hard cap clamps an oversized configured cache`() {
        assertEquals(
            ImageCacheMemoryPolicy.MEMORY_CACHE_HARD_CAP_BYTES,
            ImageCacheMemoryPolicy.cappedBaseline(192L * 1024 * 1024),
        )
    }

    @Test
    fun `the hard cap leaves an ordinary configured cache untouched`() {
        assertEquals(
            64L * 1024 * 1024,
            ImageCacheMemoryPolicy.cappedBaseline(64L * 1024 * 1024),
        )
    }

    @Test
    fun `the hard cap is a fixed 96 MiB`() {
        assertEquals(96L * 1024 * 1024, ImageCacheMemoryPolicy.MEMORY_CACHE_HARD_CAP_BYTES)
    }
}
