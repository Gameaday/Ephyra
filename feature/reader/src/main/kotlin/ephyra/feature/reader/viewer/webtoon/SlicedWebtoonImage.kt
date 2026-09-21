package ephyra.feature.reader.viewer.webtoon

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.ImageUtil
import ephyra.feature.reader.model.ReaderPage

/**
 * Renders a long webtoon strip as a [Column] of full-resolution slices.
 *
 * Each slice is region-decoded from [bytes] at screen width, so no global downscale is needed
 * and every slice stays under the GPU texture ceiling. Slices overlap by a few display pixels
 * ([WebtoonSlicer.OVERLAP_DISPLAY_PX]) so no hairline seams appear.
 *
 * Calls [fallback] (the regular single-image path) when slicing does not apply: short pages,
 * animated images, border-cropped pages, or when region decoding fails / would need more than
 * [WebtoonSlicer.MAX_SLICES] slices.
 */
@Composable
fun SlicedWebtoonImage(
    page: ReaderPage,
    bytes: ByteArray,
    srcWidth: Int,
    srcHeight: Int,
    targetWidthPx: Float,
    viewportHeightPx: Float?,
    cropBorders: Boolean,
    isAnimated: Boolean,
    onLongTap: () -> Unit,
    fallback: @Composable () -> Unit,
) {
    val targetWidth = targetWidthPx.toInt().coerceAtLeast(1)
    val slicingEnabled = !cropBorders && !isAnimated &&
        bytes.isNotEmpty() && srcWidth > 0 && srcHeight > 0 && targetWidth > 0

    val slices = remember(bytes, srcWidth, srcHeight, targetWidth, viewportHeightPx, slicingEnabled) {
        if (!slicingEnabled) {
            null
        } else {
            val textureLimit = ImageUtil.hardwareBitmapThreshold
            if (!WebtoonSlicer.needsSlicing(srcWidth, srcHeight, targetWidth, textureLimit)) {
                null
            } else {
                val maxSlice = WebtoonSlicer.maxSliceDisplayPx(
                    textureLimitPx = textureLimit,
                    viewportHeightPx = viewportHeightPx?.toInt(),
                )
                WebtoonSlicer.computeSlices(srcWidth, srcHeight, targetWidth, maxSlice)
                    .ifEmpty { null }
            }
        }
    }

    if (slices == null) {
        fallback()
        return
    }

    val decoded by produceState<List<android.graphics.Bitmap?>?>(
        initialValue = null,
        key1 = bytes.contentHashCode(),
        key2 = "$srcWidth:$srcHeight:$targetWidth:${slices.hashCode()}",
    ) {
        value = withIOContext {
            slices.map { slice ->
                WebtoonRegionDecoder.decodeSlice(bytes, srcWidth, slice, targetWidth)
            }
        }
    }

    if (decoded == null) {
        // Still decoding: render the fallback (single AsyncImage path with its own
        // placeholder) rather than a blank gap.
        fallback()
        return
    }
    val bitmaps = decoded!!
    if (bitmaps.any { it == null || it.isRecycled }) {
        // Region decode unsupported/failed for this format: single-image fallback.
        fallback()
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        slices.forEachIndexed { index, slice ->
            val bitmap = bitmaps[index] ?: return@forEachIndexed
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Page ${page.number} part ${index + 1}/${slices.size}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    // Exact per-slice aspect ratio: reserves final height on first layout,
                    // no LazyColumn jump when bitmaps arrive.
                    .aspectRatio(
                        srcWidth.toFloat() / slice.srcHeight.coerceAtLeast(1),
                        matchHeightConstraintsFirst = false,
                    )
                    .pointerInput(page, index) {
                        detectTapGestures(onLongPress = { onLongTap() })
                    },
            )
        }
    }
}
