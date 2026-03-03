package eu.kanade.tachiyomi.util.system

import android.graphics.Bitmap
import com.awxkee.jxlcoder.JxlChannelsConfiguration
import com.awxkee.jxlcoder.JxlCoder
import com.awxkee.jxlcoder.JxlCompressionOption
import com.awxkee.jxlcoder.JxlDecodingSpeed
import com.awxkee.jxlcoder.JxlEffort
import tachiyomi.domain.library.service.LibraryPreferences.ImageFormat
import java.io.OutputStream

/**
 * Returns a lossless encoder for **persisting** images to disk (covers, download splits, merges).
 *
 * JXL uses effort 7 ("squirrel") — best lossless compression for stored files.
 * PNG uses Android's built-in encoder — universal compatibility.
 *
 * This encoder is **not** used for transient reader buffers. Reader transforms
 * (split, rotate, merge) always encode to PNG via [ImageUtil.defaultEncoder][tachiyomi.core.common.util.system.ImageUtil.defaultEncoder]
 * because SubsamplingScaleImageView relies on [BitmapRegionDecoder] / [BitmapFactory],
 * which do not support JXL on API < 34.
 */
fun ImageFormat.encoder(): (Bitmap, OutputStream) -> Unit = when (this) {
    ImageFormat.PNG -> { bitmap, os ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
    }
    ImageFormat.JXL -> { bitmap, os ->
        os.write(jxlEncode(bitmap, JxlEffort.SQUIRREL, JxlDecodingSpeed.SLOWEST))
    }
}

private fun jxlEncode(bitmap: Bitmap, effort: JxlEffort, decodingSpeed: JxlDecodingSpeed): ByteArray {
    return JxlCoder.encode(
        bitmap,
        channelsConfiguration = if (bitmap.hasAlpha()) {
            JxlChannelsConfiguration.RGBA
        } else {
            JxlChannelsConfiguration.RGB
        },
        compressionOption = JxlCompressionOption.LOSSLESS,
        effort = effort,
        decodingSpeed = decodingSpeed,
    )
}
