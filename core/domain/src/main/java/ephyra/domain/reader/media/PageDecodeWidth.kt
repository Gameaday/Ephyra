package ephyra.domain.reader.media

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Whether the reader can zoom past the displayed size of a page.
 *
 * This is the input that decides a page's decode width, and the two readers answer differently on
 * purpose. Conflating them is how a memory optimisation turns into a soft image.
 */
enum class PageZoomPolicy {
    /**
     * The viewport can magnify the page, so the decode must carry detail beyond the display size
     * or zooming in reveals blur. The paged reader is this case.
     */
    ZOOMABLE,

    /**
     * The page is only ever shown at its laid-out size — a continuous strip is fitted to the viewport
     * width and the gesture scales the *container*, never the source pixels. The webtoon reader is
     * this case, and bounding its decode is worth real memory on a long strip.
     */
    FIXED_SCALE,
}

/**
 * How many pixels wide a page should be decoded.
 *
 * **Why this is its own decision and not a [ScaleBucket].** [ScaleBucket] expresses a downscale
 * *factor* applied to the source's intrinsic size, which is the right axis for re-decoding a page as
 * a zoom gesture crosses scale steps. This expresses a decode *target* derived from how the page will
 * be laid out, which is a different question with a different input. Reusing [ScaleBucket] here
 * would have meant inverting it -- solving for a factor from a pixel target -- and a wrong answer
 * there is a wrong-sized bitmap, which costs memory silently.
 *
 * The decision existed in two hand-rolled forms and they had drifted: the webtoon reader computed
 * physical display width inline, and the paged reader asked for [androidx.coil3.size.Size.ORIGINAL]
 * with no statement of why the two answers differ. Both now ask here, so the difference is an
 * argument rather than an omission.
 */
object PageDecodeWidth {

    /**
     * Physical pixels per layout pixel.
     *
     * Floored at 1 deliberately. A density below 1 is a display setting, and decoding *fewer* pixels
     * than the layout asks for is the "blurry strip" defect: the decode cannot invent detail that was
     * never fetched.
     */
    private const val MIN_DENSITY = 1f

    /**
     * The decode width for one page.
     *
     * @param intrinsicWidth the source's own width in pixels, or `0`/negative when not yet known.
     * @param displayWidthPx the width the page will be laid out at, in layout pixels.
     * @param densityScale the display's pixel density.
     * @param zoom whether the viewport can magnify past the display size.
     * @return a width in pixels, always at least 1.
     */
    fun plan(
        intrinsicWidth: Int,
        displayWidthPx: Float,
        densityScale: Float,
        zoom: PageZoomPolicy,
    ): Int {
        val target = when (zoom) {
            // Zoom has to be able to resolve detail the display size does not carry, so the source's
            // own width is the floor, not the ceiling.
            PageZoomPolicy.ZOOMABLE -> intrinsicWidth.toFloat()
            PageZoomPolicy.FIXED_SCALE -> {
                val physical = ceil(displayWidthPx * densityScale.coerceAtLeast(MIN_DENSITY))
                // Never above the source: asking a decoder for more pixels than the image has costs
                // nothing and gains nothing. The inline version this replaces relied on the decoder
                // to make that same clamp, which made the behaviour untestable and invisible.
                if (intrinsicWidth > 0) minOf(physical, intrinsicWidth.toFloat()) else physical
            }
        }
        return if (target.isFinite() && target >= 1f) target.roundToInt().coerceAtLeast(1) else 1
    }
}
