package ephyra.feature.reader.viewer.pager

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.ImageUtil
import ephyra.domain.reader.media.PixelRect
import ephyra.domain.reader.media.SampleSize
import ephyra.feature.reader.viewer.webtoon.WebtoonRegionDecoder
import ephyra.feature.reader.viewer.webtoon.WebtoonSlicer

/** Sentinel: the region decode has not produced a result yet (distinct from a null failure). */
private object Decoding

/**
 * Renders a **tall paged page** as an exact partition of region-decoded slices.
 *
 * A paged page taller than the GPU texture ceiling cannot be drawn as one `AsyncImage`: the
 * renderer downsamples it to fit `GL_MAX_TEXTURE_SIZE`, which is the blurry-strip defect. This
 * decodes the page into slices under that ceiling at the display width and stacks them, so every
 * slice stays sharp. It is the paged counterpart of the webtoon `SlicedWebtoonImage`, sized by each
 * slice's own source height (not `weight`) so it composes inside the pager's `verticalScroll`.
 *
 * [fallback] is drawn when the page is not sliceable (short, or unknown size) or when region
 * decoding is unsupported/fails. While decoding, a spinner is shown rather than the fallback —
 * the fallback's single oversized bitmap is exactly what this avoids.
 *
 * This composable renders **no zoom or gestures of its own**: the pager's `pagerZoomLayer` wraps
 * it, so the whole stack scales as one unit (the same rule `SlicedWebtoonImage` follows).
 */
@Composable
fun PagedSliceColumn(
    bytes: ByteArray,
    srcWidth: Int,
    srcHeight: Int,
    targetWidthPx: Int,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
) {
    val slices = remember(srcWidth, srcHeight, targetWidthPx) {
        planSlices(srcWidth, srcHeight, targetWidthPx)
    }
    if (slices == null) {
        fallback()
        return
    }

    val sample = remember(srcWidth, targetWidthPx) {
        val fit = WebtoonRegionDecoder.sampleSizeForWidth(srcWidth, targetWidthPx)
        SampleSize.entries.lastOrNull { it.inSampleSize <= fit } ?: SampleSize.ONE
    }

    val decoded by produceState<Any?>(Decoding, srcWidth, srcHeight, targetWidthPx, sample) {
        value = withIOContext {
            WebtoonRegionDecoder.decodeSlices(
                bytes = bytes,
                sliceRect = PixelRect(0, 0, srcWidth, srcHeight),
                slices = slices,
                sampleSize = sample,
            )
        }
    }

    when (val result = decoded) {
        Decoding -> CircularProgressIndicator(modifier = Modifier.size(48.dp))
        null -> fallback()
        is List<*> -> {
            @Suppress("UNCHECKED_CAST")
            val bitmaps = result as List<Bitmap?>
            if (bitmaps.size != slices.size || bitmaps.any { it == null || it.isRecycled }) {
                fallback()
                return
            }
            val density = LocalDensity.current
            val scale = targetWidthPx.toFloat() / srcWidth
            Column(modifier = modifier.fillMaxWidth()) {
                slices.forEachIndexed { index, slice ->
                    Image(
                        bitmap = bitmaps[index]!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(with(density) { (slice.srcHeight * scale).toDp() }),
                    )
                }
            }
        }
    }
}

/** The exact slices for a [srcWidth]×[srcHeight] page shown at [targetWidthPx], or null to fall back. */
private fun planSlices(srcWidth: Int, srcHeight: Int, targetWidthPx: Int): List<WebtoonSlicer.SliceRect>? {
    if (srcWidth <= 0 || srcHeight <= 0 || targetWidthPx <= 0) return null
    val textureLimit = ImageUtil.hardwareBitmapThreshold
    if (!WebtoonSlicer.needsSlicing(srcWidth, srcHeight, targetWidthPx, textureLimit)) return null
    return WebtoonSlicer.computeSlices(
        srcWidth = srcWidth,
        srcHeight = srcHeight,
        targetWidthPx = targetWidthPx,
        maxSliceDisplayPx = WebtoonSlicer.maxSliceDisplayPx(textureLimitPx = textureLimit),
    ).ifEmpty { null }
}
