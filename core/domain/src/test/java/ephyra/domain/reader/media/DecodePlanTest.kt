package ephyra.domain.reader.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DecodePlanTest {

    private fun still(
        width: Int = 1080,
        height: Int = 1920,
        contentRect: PixelRect? = null,
    ) = PageMetadata(
        intrinsicSize = PixelSize(width, height),
        animation = AnimationKind.STATIC,
        format = PageImageFormat.JPEG,
        contentRect = contentRect,
    )

    private fun animated(width: Int = 480, height: Int = 480) = PageMetadata(
        intrinsicSize = PixelSize(width, height),
        animation = AnimationKind.ANIMATED,
        format = PageImageFormat.ANIMATED,
    )

    @Test
    fun `a page within the texture limit uses hardware decoding`() {
        val plan = DecodePlanner.plan(still(width = 1080, height = 1920), maxTextureSize = 4096)
        assertEquals(DecodePath.HARDWARE, plan.path)
        assertEquals(DecodeReason.FITS_HARDWARE_LIMIT, plan.reason)
        assertTrue(plan.allowsHardware)
    }

    @Test
    fun `a page exceeding the texture limit falls back to software`() {
        // The defect this replaces: a hardware request that fails and renders as an error.
        val plan = DecodePlanner.plan(still(width = 800, height = 12000), maxTextureSize = 4096)
        assertEquals(DecodePath.SOFTWARE, plan.path)
        assertEquals(DecodeReason.EXCEEDS_TEXTURE_LIMIT, plan.reason)
        assertFalse(plan.allowsHardware)
    }

    @Test
    fun `exceeding the limit on width alone still falls back`() {
        // A texture that is too wide fails exactly as hard as one that is too tall.
        val plan = DecodePlanner.plan(still(width = 9000, height = 100), maxTextureSize = 4096)
        assertEquals(DecodePath.SOFTWARE, plan.path)
        assertEquals(DecodeReason.EXCEEDS_TEXTURE_LIMIT, plan.reason)
    }

    @Test
    fun `a page exactly at the texture limit stays on hardware`() {
        val plan = DecodePlanner.plan(still(width = 4096, height = 4096), maxTextureSize = 4096)
        assertEquals(DecodePath.HARDWARE, plan.path)
    }

    @Test
    fun `animated content never takes the hardware path`() {
        val plan = DecodePlanner.plan(animated(), maxTextureSize = 4096)
        assertEquals(DecodePath.SOFTWARE, plan.path)
        assertEquals(DecodeReason.ANIMATED_CONTENT, plan.reason)
        assertFalse(plan.allowsHardware)
    }

    @Test
    fun `animation is reported even when the page would fit the hardware limit`() {
        val plan = DecodePlanner.plan(animated(64, 64), maxTextureSize = 4096)
        assertEquals(DecodeReason.ANIMATED_CONTENT, plan.reason)
    }

    @Test
    fun `cropping is judged against the content size not the intrinsic size`() {
        // A page whose border pushes it past the limit, but whose artwork fits, should fit.
        val metadata = PageMetadata(
            intrinsicSize = PixelSize(4100, 4100),
            animation = AnimationKind.STATIC,
            format = PageImageFormat.JPEG,
            contentRect = PixelRect(4, 4, 4092, 4092),
        )
        val plan = DecodePlanner.plan(metadata, maxTextureSize = 4096)
        assertEquals(DecodePath.HARDWARE, plan.path)
    }

    @Test
    fun `software decode is never downscaled by the bucket`() {
        // Downscaling an oversized page would defeat the purpose of the fallback.
        val plan = DecodePlanner.plan(still(width = 800, height = 12000), maxTextureSize = 4096)
        assertEquals(12000, plan.targetSize.height)
    }

    @Test
    fun `the cache key changes when cropping changes the pixels`() {
        // The specific bug: an un-cropped bitmap served under a cropped key.
        val id = PageSourceId("src", "p1", "r1")
        val uncropped = still()
        val cropped = still(contentRect = PixelRect(4, 4, 1076, 1916))
        val plan = DecodePlanner.plan(cropped, maxTextureSize = 8192)
        assertNotEquals(
            plan.cacheKey(id, uncropped, "t"),
            plan.cacheKey(id, cropped, "t"),
        )
    }

    @Test
    fun `the cache key changes with identity, bucket, size, path, and transform`() {
        val id = PageSourceId("src", "p1", "r1")
        val other = PageSourceId("src", "p1", "r2")
        val metadata = still()
        val plan = DecodePlanner.plan(metadata, maxTextureSize = 8192)

        val base = plan.cacheKey(id, metadata, "t")
        assertNotEquals(base, plan.cacheKey(other, metadata, "t"))
        assertNotEquals(base, plan.cacheKey(id, metadata, "t2"))

        val bigger = DecodePlanner.plan(metadata, 8192, ScaleBucket(128))
        assertNotEquals(base, bigger.cacheKey(id, metadata, "t"))

        val software = DecodePlanner.plan(metadata, maxTextureSize = 16)
        assertNotEquals(base, software.cacheKey(id, metadata, "t"))
    }

    @Test
    fun `an identical plan and identity produce a stable key`() {
        val id = PageSourceId("src", "p1", "r1")
        val metadata = still()
        val first = DecodePlanner.plan(metadata, 8192).cacheKey(id, metadata, "t")
        val second = DecodePlanner.plan(metadata, 8192).cacheKey(id, metadata, "t")
        assertEquals(first, second)
    }

    @Test
    fun `a larger bucket decodes a larger image`() {
        val metadata = still(width = 1000, height = 1000)
        val small = ScaleBucket(64).decodeSize(metadata.displaySize)
        val large = ScaleBucket(128).decodeSize(metadata.displaySize)
        assertTrue(large.width > small.width)
        assertEquals(1000, small.width)
    }

    @Test
    fun `bucket scaling never produces a zero dimension`() {
        val tiny = PixelSize(1, 1)
        val size = ScaleBucket(32).decodeSize(tiny)
        assertTrue(size.width >= 1 && size.height >= 1)
    }
}
