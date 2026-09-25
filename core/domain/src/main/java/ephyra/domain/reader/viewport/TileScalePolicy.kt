package ephyra.domain.reader.viewport

/**
 * A tile decode request, with a stable identity.
 *
 * The identity is what the decoded-tile cache keys on, so it has to capture every input that
 * changes the resulting pixels: which page, which tile, at which sample size, from which source
 * revision. Omitting the revision is the specific bug that lets a re-uploaded page reuse a stale
 * tile under the same coordinates.
 *
 * This lives beside the geometry rather than in the media module because a tile is a viewport
 * concept: the rectangle it covers comes from [partitionIntoTiles]. The two identities are
 * different on purpose. [ephyra.domain.reader.media.PageSourceId] identifies the page's bytes;
 * this identifies one decoded region of them.
 */
data class TileKey(
    val pageId: String,
    val row: Int,
    val col: Int,
    val sampleSize: Int,
    val sourceRevision: String,
) {
    init {
        require(pageId.isNotBlank()) { "pageId must not be blank" }
        require(row >= 0 && col >= 0) { "tile row/col must be non-negative" }
        require(sampleSize > 0) { "sampleSize must be positive" }
    }

    /**
     * The single string form used as a cache key.
     *
     * [pageId] is length-prefixed so a page id containing a separator cannot be repartitioned into
     * a different id plus different coordinates, which is the same reasoning as
     * [ephyra.domain.reader.media.PageSourceId.cacheComponent].
     */
    fun cacheKey(): String = buildString {
        append(pageId.length).append(':').append(pageId).append('#')
        append(row).append(':').append(col).append('@')
        append(sampleSize).append('~').append(sourceRevision)
    }
}

/**
 * The sample sizes a tile may be decoded at.
 *
 * Powers of two because that is the only granularity `BitmapFactory` accepts, so a tile can only
 * be decoded at 1/1, 1/2, 1/4, or 1/8 of its pixels.
 */
enum class ScaleBucket(val sampleSize: Int) {
    /** Full resolution. */
    ONE(1),

    /** Half resolution. */
    TWO(2),

    /** Quarter resolution. */
    FOUR(4),

    /** Eighth resolution. */
    EIGHT(8),
    ;

    /** The next coarser bucket, or null at the coarsest. */
    fun coarser(): ScaleBucket? = entries.getOrNull(ordinal + 1)

    /** The next finer bucket, or null at the finest. */
    fun finer(): ScaleBucket? = entries.getOrNull(ordinal - 1)
}

/**
 * Chooses a tile's sample size for the current zoom.
 *
 * The deadband is the important part. Without it, a pinch hovering at exactly one threshold flips
 * the bucket every frame, and each flip invalidates every cached tile at the previous scale. The
 * strip is then re-decoded continuously for the whole gesture and never settles, which reads to
 * the user as a stuttering zoom rather than a sharp one. Between the thresholds the current bucket
 * is kept, so a gesture has to move decisively to pay the re-decode.
 *
 * This is the cause-side counterpart to the zoom transform: [DocumentViewport] decides *where*
 * content is drawn, this decides *how finely* it is fetched. They have to agree on scale or tiles
 * are decoded sharper than they are displayed, which costs memory for no visible gain.
 */
class TileScalePolicy(
    private val deadbandLower: Float = 0.85f,
    private val deadbandUpper: Float = 1.15f,
) {
    init {
        require(deadbandLower > 0f) { "deadbandLower must be positive" }
        require(deadbandUpper > deadbandLower) { "deadbandUpper must exceed deadbandLower" }
        // Consecutive buckets differ by a factor of two, so a deadband spanning 2x or more would
        // swallow every possible change and the bucket would never move. This is what makes the
        // constants a hysteresis rather than a no-op.
        require(deadbandUpper / deadbandLower < 2f) {
            "deadband must be narrower than the 2x bucket spacing, was " +
                "${deadbandUpper / deadbandLower}"
        }
    }

    /**
     * Returns the bucket to use for [current].
     *
     * [devicePixelsPerImagePixel] is how many device pixels one image pixel currently occupies.
     * Above 1 the image is magnified and a finer sample is needed; below 1 it is shrunk and the
     * current sample size oversamples.
     *
     * The ideal sample size is the reciprocal. The deadband is applied by comparing that ideal
     * against the current bucket, so a change is only taken once the required scale has moved
     * decisively past a threshold.
     */
    fun bucketFor(
        current: ScaleBucket,
        devicePixelsPerImagePixel: Float,
    ): ScaleBucket {
        require(devicePixelsPerImagePixel.isFinite() && devicePixelsPerImagePixel > 0f) {
            "devicePixelsPerImagePixel must be finite and positive"
        }

        val ideal = 1f / devicePixelsPerImagePixel
        val currentSample = current.sampleSize.toFloat()

        // A larger ideal means more samples per image pixel are needed, which is a *coarser*
        // bucket. Getting this direction wrong walks the bucket away from the ideal rather than
        // toward it, so the two cases are deliberately spelled out separately.
        return when {
            ideal > currentSample * deadbandUpper -> current.coarser() ?: current
            ideal < currentSample * deadbandLower -> current.finer() ?: current
            else -> current
        }
    }
}
