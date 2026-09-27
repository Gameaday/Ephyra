package ephyra.domain.reader.media

/**
 * A decode sample size, with a stable identity.
 *
 * Powers of two because that is the only granularity `BitmapFactory`/`BitmapRegionDecoder` accept,
 * so a region can only be decoded at 1/1, 1/2, 1/4 or 1/8 of its pixels.
 *
 * **This is a different type from [ScaleBucket] and the two must not be conflated.** [ScaleBucket]
 * quantises a *target output size* for a whole-page decode. [SampleSize] is the `inSampleSize`
 * passed to a region decode, and it is chosen by how finely the current zoom needs the source
 * sampled. Merging them would produce a single type with two unrelated units, and the resulting
 * arithmetic errors would be silent — a wrong sample size costs memory, not correctness.
 */
enum class SampleSize(val inSampleSize: Int) {
    /** Full resolution. */
    ONE(1),

    /** Half resolution. */
    TWO(2),

    /** Quarter resolution. */
    FOUR(4),

    /** Eighth resolution. */
    EIGHT(8),
    ;

    /** The next coarser sample, or null at the coarsest. */
    fun coarser(): SampleSize? = entries.getOrNull(ordinal + 1)

    /** The next finer sample, or null at the finest. */
    fun finer(): SampleSize? = entries.getOrNull(ordinal - 1)
}

/**
 * Chooses a region's `inSampleSize` for the current zoom.
 *
 * Migrated from the retired `TileScalePolicy` (ADR-0010) rather than rewritten, so the hysteresis
 * behaviour and its tests carry over intact. What changed is the surface it serves: the slice
 * renderer decodes fixed-height regions through `BitmapRegionDecoder`, and this decides how finely
 * each region is fetched.
 *
 * **The deadband is the important part, and it is why this exists at all.** Without it, a pinch
 * hovering at exactly one threshold flips the sample size every frame, and each flip invalidates
 * every cached region at the previous scale. The strip is then re-decoded continuously for the
 * whole gesture and never settles, which reads to the user as a stuttering zoom rather than a
 * sharp one. Between the thresholds the current sample is kept, so a gesture has to move
 * decisively to pay the re-decode.
 *
 * This is the cause-side counterpart to the zoom transform: the viewport decides *where* content is
 * drawn, this decides *how finely* it is fetched. They have to agree, or a slice is decoded
 * sharper than it is displayed, which costs memory for no visible gain.
 */
class SampleSizePolicy(
    private val deadbandLower: Float = 0.85f,
    private val deadbandUpper: Float = 1.15f,
) {
    init {
        require(deadbandLower > 0f) { "deadbandLower must be positive" }
        require(deadbandUpper > deadbandLower) { "deadbandUpper must exceed deadbandLower" }
        // Consecutive samples differ by a factor of two, so a deadband spanning 2x or more would
        // swallow every possible change and the sample would never move. This is what makes the
        // constants a hysteresis rather than a no-op.
        require(deadbandUpper / deadbandLower < 2f) {
            "deadband must be narrower than the 2x sample spacing, was " +
                "${deadbandUpper / deadbandLower}"
        }
    }

    /**
     * Returns the sample size to use for [current].
     *
     * [devicePixelsPerImagePixel] is how many device pixels one source pixel currently occupies.
     * Above 1 the source is magnified and a finer sample is needed; below 1 it is shrunk and the
     * current sample oversamples. The ideal sample size is the reciprocal, and the deadband is
     * applied by comparing that ideal against the current sample, so a change is only taken once
     * the required scale has moved decisively past a threshold.
     */
    fun sampleFor(
        current: SampleSize,
        devicePixelsPerImagePixel: Float,
    ): SampleSize {
        require(devicePixelsPerImagePixel.isFinite() && devicePixelsPerImagePixel > 0f) {
            "devicePixelsPerImagePixel must be finite and positive"
        }

        val ideal = 1f / devicePixelsPerImagePixel
        val currentSample = current.inSampleSize.toFloat()

        // A larger ideal means more samples per source pixel are needed, which is a *coarser*
        // sample. Getting this direction wrong walks the sample away from the ideal rather than
        // toward it, so the two cases are deliberately spelled out separately.
        return when {
            ideal > currentSample * deadbandUpper -> current.coarser() ?: current
            ideal < currentSample * deadbandLower -> current.finer() ?: current
            else -> current
        }
    }
}
