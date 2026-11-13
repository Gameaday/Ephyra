package ephyra.app.image

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Cap-pinning tests for the decoded-image memory-cache bounds (app health audit P1/P3,
 * 2026-11-13). The bounds are constants by design; these pin the arithmetic so a refactor
 * cannot silently move the ceiling, the floor, or the no-grow correction.
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
    fun `a pressure trim halves the baseline`() {
        assertEquals(
            48L * 1024 * 1024,
            ImageCacheMemoryPolicy.trimTarget(96L * 1024 * 1024),
        )
    }

    @Test
    fun `a pressure trim never goes below the floor`() {
        assertEquals(
            ImageCacheMemoryPolicy.TRIM_FLOOR_BYTES,
            ImageCacheMemoryPolicy.trimTarget(20L * 1024 * 1024),
        )
    }

    @Test
    fun `a pressure trim never grows the cache, even when the floor exceeds half the baseline`() {
        assertEquals(
            12L * 1024 * 1024,
            ImageCacheMemoryPolicy.trimTarget(12L * 1024 * 1024),
        )
    }
}
