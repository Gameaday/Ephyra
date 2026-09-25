package ephyra.domain.reader.media

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Which decode path a page should use.
 *
 * The two paths are not interchangeable. [HARDWARE] allocates an `AHardwareBuffer` and is capped
 * by the device's maximum texture size, which long-strip webtoon pages routinely exceed. [SOFTWARE]
 * allocates a normal heap bitmap with no such cap but costs more memory and is slower to upload.
 *
 * Requesting hardware unconditionally and silently failing is the defect this replaces: the request
 * must be planned against the known intrinsic size so the fallback is a decision rather than an
 * error state.
 */
enum class DecodePath {
    HARDWARE,
    SOFTWARE,
}

/** Why a decode plan chose what it chose. Recorded so a fallback is explainable, not mysterious. */
enum class DecodeReason {
    /** Within the device texture limit. */
    FITS_HARDWARE_LIMIT,

    /** Exceeds the texture limit; software is required to render at all. */
    EXCEEDS_TEXTURE_LIMIT,

    /** The page is animated, so the platform frame decoder is used. */
    ANIMATED_CONTENT,

    /** The format has no hardware path. */
    NO_HARDWARE_PATH,
}

/**
 * How large a page should be decoded, quantised so repeated requests reuse the same bitmap.
 *
 * Decoding at the exact display size would produce a new bitmap for every pan and zoom step and
 * defeat caching entirely. Snapping to a bucket means a small range of view scales shares one
 * decode, which is what makes zoom feel continuous rather than re-decoding constantly.
 *
 * The bucket is always a power-of-two multiple of [baseBucket], so a decoded result at one scale
 * can be resampled for another without a full decode.
 */
data class ScaleBucket(val baseBucket: Int) {
    init {
        require(baseBucket > 0) { "baseBucket must be positive" }
    }

    fun decodeSize(intrinsic: PixelSize): PixelSize {
        val scale = baseBucket.toFloat() / 64f
        return PixelSize(
            width = max(1, (intrinsic.width * scale).roundToInt()),
            height = max(1, (intrinsic.height * scale).roundToInt()),
        )
    }
}

/**
 * The complete decode decision for one page, made before any bytes are read.
 *
 * Planning the decode separately from performing it is what makes the expensive decisions testable
 * and stable. Nothing here touches disk, network, or the platform decoder.
 */
data class DecodePlan(
    val path: DecodePath,
    val reason: DecodeReason,
    val targetSize: PixelSize,
    val bucket: ScaleBucket,
    val allowsHardware: Boolean,
) {
    /**
     * Stable cache key for a decode result.
     *
     * Every field that changes the produced pixels has to appear, which is why the page identity,
     * the content rect, and the bucket are all included. Omitting the content rect is the specific
     * bug this guards: crop changes the pixels, so a cached un-cropped bitmap served under this
     * key would make the toggle appear to do nothing.
     */
    fun cacheKey(identity: PageSourceId, metadata: PageMetadata, transformTag: String): String =
        buildString {
            append("decode_")
            append(identity.cacheComponent())
            append('|')
            append(metadata.contentRect?.let { "${it.left},${it.top},${it.right},${it.bottom}" } ?: "full")
            append('|')
            append(bucket.baseBucket)
            append('|')
            append(targetSize.width)
            append('x')
            append(targetSize.height)
            append('|')
            append(path.name)
            append('|')
            append(transformTag)
        }
}

/**
 * Plans decodes against a device texture limit.
 *
 * This is deliberately pure: the limit is passed in rather than read from the platform, so the
 * exact rule can be tested for any device instead of only the one running the test.
 */
object DecodePlanner {
    /** Scale-bucket denominator. 64 is one whole "unit"; 128 and so on give finer steps. */
    const val BASE_BUCKET: Int = 64

    /**
     * Chooses a decode plan for [metadata].
     *
     * Animated content always uses [DecodePath.SOFTWARE]: the platform frame decoder manages its
     * own buffers, and a hardware-bitmap request for animation is not honoured anyway.
     *
     * Otherwise the choice is made by comparing the largest edge against [maxTextureSize]. A page
     * is oversized when *either* edge exceeds the limit, because a texture that is too wide fails
     * just as hard as one that is too tall.
     */
    fun plan(
        metadata: PageMetadata,
        maxTextureSize: Int,
        bucket: ScaleBucket = ScaleBucket(BASE_BUCKET),
    ): DecodePlan {
        require(maxTextureSize > 0) { "maxTextureSize must be positive" }

        if (metadata.animation == AnimationKind.ANIMATED) {
            return DecodePlan(
                path = DecodePath.SOFTWARE,
                reason = DecodeReason.ANIMATED_CONTENT,
                targetSize = metadata.displaySize,
                bucket = bucket,
                allowsHardware = false,
            )
        }

        val exceedsLimit = max(
            metadata.displaySize.width,
            metadata.displaySize.height,
        ) > maxTextureSize

        return if (exceedsLimit) {
            DecodePlan(
                path = DecodePath.SOFTWARE,
                reason = DecodeReason.EXCEEDS_TEXTURE_LIMIT,
                targetSize = metadata.displaySize,
                bucket = bucket,
                allowsHardware = false,
            )
        } else {
            DecodePlan(
                path = DecodePath.HARDWARE,
                reason = DecodeReason.FITS_HARDWARE_LIMIT,
                targetSize = bucket.decodeSize(metadata.displaySize),
                bucket = bucket,
                allowsHardware = true,
            )
        }
    }
}
