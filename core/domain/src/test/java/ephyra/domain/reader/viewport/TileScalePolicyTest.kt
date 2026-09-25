package ephyra.domain.reader.viewport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Semantics used throughout: `devicePixelsPerImagePixel` (d) is how many device pixels one image
 * pixel covers. The ideal sample size is therefore `1/d`:
 *
 *  - d = 0.25 -> one device pixel covers four image pixels -> keep 1 in 4 -> sample 4
 *  - d = 1.0  -> 1:1 -> sample 1
 *  - d = 4.0  -> magnified 4x -> full resolution, sample 1 (clamped)
 *
 * The bucket holds while `1/d` stays inside `[sample*lower, sample*upper]`, so for sample 4 the
 * stable range of d is roughly 0.217 to 0.294.
 */
class TileScalePolicyTest {

    private val policy = TileScalePolicy()

    /** Drives [policy] to its settled bucket for [d], then returns it. */
    private fun settledFor(d: Float, start: ScaleBucket = ScaleBucket.FOUR): ScaleBucket {
        var bucket = start
        repeat(8) { bucket = policy.bucketFor(bucket, d) }
        return bucket
    }

    @Test
    fun `shrinking the page coarsens the sample`() {
        // d = 0.25 means one device pixel covers four image pixels, so the ideal sample size is 4
        // and the policy must settle on FOUR, not continue past it to EIGHT.
        assertEquals(ScaleBucket.TWO, policy.bucketFor(ScaleBucket.ONE, devicePixelsPerImagePixel = 0.25f))
        assertEquals(ScaleBucket.FOUR, settledFor(0.25f, start = ScaleBucket.ONE))
    }

    @Test
    fun `magnifying the page refines the sample`() {
        // At 4x magnification the page needs full resolution, not one in eight.
        assertEquals(ScaleBucket.FOUR, policy.bucketFor(ScaleBucket.EIGHT, devicePixelsPerImagePixel = 4f))
        assertEquals(ScaleBucket.ONE, settledFor(4f))
    }

    @Test
    fun `a settled zoom keeps its bucket across repeated identical updates`() {
        // The entire point: a still gesture must stop changing what is decoded.
        var bucket = settledFor(0.25f)
        repeat(30) { bucket = policy.bucketFor(bucket, 0.25f) }
        assertEquals(ScaleBucket.FOUR, bucket)
    }

    @Test
    fun `a small nudge inside the deadband never changes the bucket`() {
        // Without hysteresis this flip invalidates every cached tile on every frame.
        var bucket = ScaleBucket.FOUR
        repeat(60) { index ->
            val jitter = 0.25f + (index % 7) * 0.001f
            bucket = policy.bucketFor(bucket, jitter)
            assertEquals(ScaleBucket.FOUR, bucket, "iteration=$index jitter=$jitter")
        }
    }

    @Test
    fun `the bucket only ever moves one step at a time`() {
        // A jump to a distant bucket discards every intermediate scale at once.
        assertEquals(ScaleBucket.TWO, policy.bucketFor(ScaleBucket.ONE, devicePixelsPerImagePixel = 0.0001f))
    }

    @Test
    fun `the finest bucket cannot be refined further`() {
        assertEquals(ScaleBucket.ONE, policy.bucketFor(ScaleBucket.ONE, devicePixelsPerImagePixel = 1000f))
    }

    @Test
    fun `the coarsest bucket cannot be coarsened further`() {
        assertEquals(ScaleBucket.EIGHT, policy.bucketFor(ScaleBucket.EIGHT, devicePixelsPerImagePixel = 0.0001f))
    }

    @Test
    fun `zooming in and back out converges to a stable finer bucket`() {
        // Hysteresis is not symmetric: returning to the original d does not return to the original
        // bucket, because the band is defined in sample-size space, not in d space. What must hold
        // is that the result is stable and no coarser than where it started.
        var bucket = ScaleBucket.FOUR
        repeat(6) { bucket = policy.bucketFor(bucket, 0.2f) }
        val coarse = bucket
        repeat(6) { bucket = policy.bucketFor(bucket, 5f) }

        assertTrue(
            bucket.sampleSize <= coarse.sampleSize,
            "magnifying must not leave a coarser bucket: $bucket after $coarse",
        )
        val settled = bucket
        repeat(10) { bucket = policy.bucketFor(bucket, 5f) }
        assertEquals(settled, bucket, "the round trip must settle")
    }

    @Test
    fun `every settled zoom lands on a single stable bucket`() {
        // Convergence is the property that stops continuous re-decoding mid-gesture.
        listOf(0.1f, 0.25f, 0.5f, 0.75f, 1f, 1.5f, 3f, 8f).forEach { d ->
            val settled = settledFor(d)
            var bucket = settled
            repeat(10) { bucket = policy.bucketFor(bucket, d) }
            assertEquals(settled, bucket, "d=$d did not converge")
        }
    }

    @Test
    fun `a monotonic zoom in walks buckets monotonically coarser to finer`() {
        var bucket = ScaleBucket.EIGHT
        val seen = mutableListOf(bucket)
        // Increasing d means magnifying, which needs finer samples.
        listOf(0.5f, 1f, 2f, 4f, 8f).forEach { d ->
            repeat(4) {
                bucket = policy.bucketFor(bucket, d)
                if (seen.last() != bucket) seen.add(bucket)
            }
        }
        val sampleSizes = seen.map { it.sampleSize }
        assertEquals(
            sampleSizes.sortedDescending(),
            sampleSizes,
            "sample sizes must never increase while magnifying, was $sampleSizes",
        )
    }

    @Test
    fun `buckets step in both directions`() {
        assertEquals(ScaleBucket.ONE, ScaleBucket.TWO.finer())
        assertEquals(ScaleBucket.FOUR, ScaleBucket.TWO.coarser())
        assertEquals(null, ScaleBucket.ONE.finer())
        assertEquals(null, ScaleBucket.EIGHT.coarser())
    }

    @Test
    fun `sample sizes are strictly increasing powers of two`() {
        val sizes = ScaleBucket.entries.map { it.sampleSize }
        assertEquals(listOf(1, 2, 4, 8), sizes)
        sizes.forEach { size ->
            assertTrue(size > 0 && (size and (size - 1)) == 0, "$size is not a power of two")
        }
    }

    @Test
    fun `a non finite or non positive ratio is rejected`() {
        assertThrows<IllegalArgumentException> {
            policy.bucketFor(ScaleBucket.ONE, devicePixelsPerImagePixel = 0f)
        }
        assertThrows<IllegalArgumentException> {
            policy.bucketFor(ScaleBucket.ONE, devicePixelsPerImagePixel = Float.NaN)
        }
        assertThrows<IllegalArgumentException> {
            policy.bucketFor(ScaleBucket.ONE, devicePixelsPerImagePixel = Float.POSITIVE_INFINITY)
        }
    }

    @Test
    fun `an inverted deadband is rejected`() {
        assertThrows<IllegalArgumentException> { TileScalePolicy(deadbandLower = 1.5f, deadbandUpper = 1.0f) }
        assertThrows<IllegalArgumentException> { TileScalePolicy(deadbandLower = 0f, deadbandUpper = 1f) }
    }

    @Test
    fun `a deadband as wide as the bucket spacing is rejected`() {
        // A band spanning 2x swallows every possible change: the bucket would never move and the
        // policy would be a no-op that looks like it is working.
        assertThrows<IllegalArgumentException> {
            TileScalePolicy(deadbandLower = 0.7f, deadbandUpper = 1.4f)
        }
        assertThrows<IllegalArgumentException> {
            TileScalePolicy(deadbandLower = 0.5f, deadbandUpper = 1.0f)
        }
    }
}

class TileKeyTest {

    @Test
    fun `the same tile at the same scale has a stable key`() {
        val key = TileKey("page-3", row = 2, col = 1, sampleSize = 2, sourceRevision = "r1")
        assertEquals(key.cacheKey(), key.copy().cacheKey())
    }

    @Test
    fun `a different source revision is a different key`() {
        // The re-upload case: same page, same tile, different bytes.
        assertNotEquals(
            TileKey("p", 0, 0, 1, "r1").cacheKey(),
            TileKey("p", 0, 0, 1, "r2").cacheKey(),
        )
    }

    @Test
    fun `tile coordinates and sample size all affect the key`() {
        val base = TileKey("p", 1, 1, 1, "r1").cacheKey()
        assertNotEquals(base, TileKey("p", 2, 1, 1, "r1").cacheKey())
        assertNotEquals(base, TileKey("p", 1, 2, 1, "r1").cacheKey())
        assertNotEquals(base, TileKey("p", 1, 1, 2, "r1").cacheKey())
        assertNotEquals(base, TileKey("q", 1, 1, 1, "r1").cacheKey())
    }

    @Test
    fun `a page id cannot impersonate another through separators`() {
        assertNotEquals(
            TileKey("a#1", 0, 0, 1, "r").cacheKey(),
            TileKey("a", 1, 0, 1, "r").cacheKey(),
        )
    }

    @Test
    fun `invalid tile coordinates are rejected`() {
        assertThrows<IllegalArgumentException> { TileKey("", 0, 0, 1, "r") }
        assertThrows<IllegalArgumentException> { TileKey("p", -1, 0, 1, "r") }
        assertThrows<IllegalArgumentException> { TileKey("p", 0, -1, 1, "r") }
        assertThrows<IllegalArgumentException> { TileKey("p", 0, 0, 0, "r") }
    }
}
