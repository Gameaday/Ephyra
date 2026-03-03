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
 * Returns an encoder function `(Bitmap, OutputStream) -> Unit` for the given [ImageFormat].
 *
 * PNG and WebP use Android's built-in [Bitmap.compress]; JXL uses `jxl-coder`'s native encoder.
 * All three produce **lossless** output.
 */
fun ImageFormat.encoder(): (Bitmap, OutputStream) -> Unit = when (this) {
    ImageFormat.PNG -> { bitmap, os ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
    }
    ImageFormat.WebP -> { bitmap, os ->
        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, os)
    }
    ImageFormat.JXL -> { bitmap, os ->
        val bytes = JxlCoder.encode(
            bitmap,
            channelsConfiguration = if (bitmap.hasAlpha()) {
                JxlChannelsConfiguration.RGBA
            } else {
                JxlChannelsConfiguration.RGB
            },
            compressionOption = JxlCompressionOption.LOSSLESS,
            effort = JxlEffort.SQUIRREL,
            quality = 100,
            decodingSpeed = JxlDecodingSpeed.SLOWEST,
        )
        os.write(bytes)
    }
}
