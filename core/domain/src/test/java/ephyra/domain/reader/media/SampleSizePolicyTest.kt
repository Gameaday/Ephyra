package ephyra.domain.reader.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Ported verbatim in behaviour from the retired `TileScalePolicyTest`; only the type and the method
 * names changed. The assertions were not loosened, because a migration that quietly weakens its
 * tests is how a deletion becomes a regression.
 *
 * Semantics used throughout: `devicePixelsPerImagePixel` (d) is how many device pixels one source
 * pixel covers. The ideal sample size is therefore `1/d`:
 *
 *  - d = 0.25 -> one device pixel covers four source pixels -> keep 1 in 4 -> sample 4
 *  - d = 1.0  -> 1:1 -> sample 1
 *  - d = 4.0  -> magnified 4x -> full resolution, sample 1 (clamped)
 *
 * The sample holds while `1/d` stays inside `[sample*lower, sample*upper]`, so for sample 4 the
 * stable range of d is roughly 0.217 to 0.294.
 */
class SampleSizePolicyTest {

    private val policy = SampleSizePolicy()

    /** Drives [policy] to its settled sample for [d], then returns it. */
    private fun settledFor(d: Float, start: SampleSize = SampleSize.FOUR): SampleSize {
        var sample = start
        repeat(8) { sample = policy.sampleFor(sample, d) }
        return sample
    }

    @Test
    fun `shrinking the page coarsens the sample`() {
        // d = 0.25 means one device pixel covers four source pixels, so the ideal sample size is 4
        // and the policy must settle on FOUR, not continue past it to EIGHT.
        assertEquals(SampleSize.TWO, policy.sampleFor(SampleSize.ONE, devicePixelsPerImagePixel = 0.25f))
        assertEquals(SampleSize.FOUR, settledFor(0.25f, start = SampleSize.ONE))
    }

    @Test
    fun `magnifying the page refines the sample`() {
        // At 4x magnification the page needs full resolution, not one in eight.
        assertEquals(SampleSize.FOUR, policy.sampleFor(SampleSize.EIGHT, devicePixelsPerImagePixel = 4f))
        assertEquals(SampleSize.ONE, settledFor(4f))
    }

    @Test
    fun `a settled zoom keeps its sample across repeated identical updates`() {
        // The entire point: a still gesture must stop changing what is decoded.
        var sample = settledFor(0.25f)
        repeat(30) { sample = policy.sampleFor(sample, 0.25f) }
        assertEquals(SampleSize.FOUR, sample)
    }

    @Test
    fun `a small nudge inside the deadband never changes the sample`() {
        // Without hysteresis this flip invalidates every cached region on every frame.
        var sample = SampleSize.FOUR
        repeat(60) { index ->
            val jitter = 0.25f + (index % 7) * 0.001f
            sample = policy.sampleFor(sample, jitter)
            assertEquals(SampleSize.FOUR, sample, "iteration=$index jitter=$jitter")
        }
    }

    @Test
    fun `the sample only ever moves one step at a time`() {
        // A jump to a distant sample discards every intermediate scale at once.
        assertEquals(SampleSize.TWO, policy.sampleFor(SampleSize.ONE, devicePixelsPerImagePixel = 0.0001f))
    }

    @Test
    fun `the finest sample cannot be refined further`() {
        assertEquals(SampleSize.ONE, policy.sampleFor(SampleSize.ONE, devicePixelsPerImagePixel = 1000f))
    }

    @Test
    fun `the coarsest sample cannot be coarsened further`() {
        assertEquals(SampleSize.EIGHT, policy.sampleFor(SampleSize.EIGHT, devicePixelsPerImagePixel = 0.0001f))
    }

    @Test
    fun `zooming in and back out converges to a stable finer sample`() {
        // Hysteresis is not symmetric: returning to the original d does not return to the original
        // sample, because the band is defined in sample-size space, not in d space. What must hold
        // is that the result is stable and no coarser than where it started.
        var sample = SampleSize.FOUR
        repeat(6) { sample = policy.sampleFor(sample, 0.2f) }
        val coarse = sample
        repeat(6) { sample = policy.sampleFor(sample, 5f) }

        assertTrue(
            sample.inSampleSize <= coarse.inSampleSize,
            "magnifying must not leave a coarser sample: $sample after $coarse",
        )
        val settled = sample
        repeat(10) { sample = policy.sampleFor(sample, 5f) }
        assertEquals(settled, sample, "the round trip must settle")
    }

    @Test
    fun `every settled zoom lands on a single stable sample`() {
        // Convergence is the property that stops continuous re-decoding mid-gesture.
        listOf(0.1f, 0.25f, 0.5f, 0.75f, 1f, 1.5f, 3f, 8f).forEach { d ->
            val settled = settledFor(d)
            var sample = settled
            repeat(10) { sample = policy.sampleFor(sample, d) }
            assertEquals(settled, sample, "d=$d did not converge")
        }
    }

    @Test
    fun `a monotonic zoom in walks samples monotonically coarser to finer`() {
        var sample = SampleSize.EIGHT
        val seen = mutableListOf(sample)
        // Increasing d means magnifying, which needs finer samples.
        listOf(0.5f, 1f, 2f, 4f, 8f).forEach { d ->
            repeat(4) {
                sample = policy.sampleFor(sample, d)
                if (seen.last() != sample) seen.add(sample)
            }
        }
        val sampleSizes = seen.map { it.inSampleSize }
        assertEquals(
            sampleSizes.sortedDescending(),
            sampleSizes,
            "sample sizes must never increase while magnifying, was $sampleSizes",
        )
    }

    @Test
    fun `samples step in both directions`() {
        assertEquals(SampleSize.ONE, SampleSize.TWO.finer())
        assertEquals(SampleSize.FOUR, SampleSize.TWO.coarser())
        assertEquals(null, SampleSize.ONE.finer())
        assertEquals(null, SampleSize.EIGHT.coarser())
    }

    @Test
    fun `sample sizes are strictly increasing powers of two`() {
        val sizes = SampleSize.entries.map { it.inSampleSize }
        assertEquals(listOf(1, 2, 4, 8), sizes)
        sizes.forEach { size ->
            assertTrue(size > 0 && (size and (size - 1)) == 0, "$size is not a power of two")
        }
    }

    @Test
    fun `a non finite or non positive ratio is rejected`() {
        assertThrows<IllegalArgumentException> {
            policy.sampleFor(SampleSize.ONE, devicePixelsPerImagePixel = 0f)
        }
        assertThrows<IllegalArgumentException> {
            policy.sampleFor(SampleSize.ONE, devicePixelsPerImagePixel = Float.NaN)
        }
        assertThrows<IllegalArgumentException> {
            policy.sampleFor(SampleSize.ONE, devicePixelsPerImagePixel = Float.POSITIVE_INFINITY)
        }
    }

    @Test
    fun `an inverted deadband is rejected`() {
        assertThrows<IllegalArgumentException> { SampleSizePolicy(deadbandLower = 1.5f, deadbandUpper = 1.0f) }
        assertThrows<IllegalArgumentException> { SampleSizePolicy(deadbandLower = 0f, deadbandUpper = 1f) }
    }

    @Test
    fun `a deadband as wide as the sample spacing is rejected`() {
        // A band spanning 2x swallows every possible change: the sample would never move and the
        // policy would be a no-op that looks like it is working.
        assertThrows<IllegalArgumentException> {
            SampleSizePolicy(deadbandLower = 0.7f, deadbandUpper = 1.4f)
        }
        assertThrows<IllegalArgumentException> {
            SampleSizePolicy(deadbandLower = 0.5f, deadbandUpper = 1.0f)
        }
    }
}
