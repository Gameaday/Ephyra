package ephyra.data.coil

import android.graphics.Bitmap
import coil3.asImage
import coil3.fetch.SourceFetchResult
import coil3.request.allowRgb565
import coil3.request.bitmapConfig
import coil3.size.Dimension
import coil3.size.Scale
import coil3.size.Size
import coil3.size.pxOrElse
import com.awxkee.jxlcoder.JxlCoder
import com.awxkee.jxlcoder.JxlResizeFilter
import com.awxkee.jxlcoder.PreferredColorConfig
import com.awxkee.jxlcoder.ScaleMode
import ephyra.core.common.util.system.ImageUtil
import kotlinx.coroutines.runInterruptible
import okio.Buffer
import coil3.ImageLoader as Coil3ImageLoader
import coil3.decode.DecodeResult as Coil3DecodeResult
import coil3.decode.Decoder as Coil3Decoder
import coil3.request.Options as Coil3Options

/**
 * Coil 3 bridge over the awxkee JXL native decoder.
 *
 * `io.github.awxkee:jxl-coder-coil:2.2.0` ships a **Coil 2** decoder (verified against the
 * published AAR: `coil.fetch.SourceResult`, `coil.ImageLoader`), which cannot be registered
 * on this app's Coil 3 pipeline — so this bridge calls the underlying `jxl-coder` native
 * API (`JxlCoder.decodeSampled`) directly and adapts the result to Coil 3.
 *
 * Covers static JXL, which the platform cannot decode even at API 34+. Animated JXL is
 * out of scope: [ImageUtil.isAnimatedAndSupported] does not detect JXL animation and the
 * webtoon region slicer bypasses JXL, so animated JXL renders as its first frame here
 * (same as the retired Tachiyomi decoder did).
 *
 * Oversized sources are downgraded to software [Bitmap.Config.ARGB_8888] per
 * [ImageUtil.hardwareBitmapThreshold] so long-strip JXL pages survive the GPU texture
 * ceiling. libjxl applies the embedded color profile internally, so the retired custom
 * ICC display-profile override is gone by design.
 */
class JxlCoilBridgeDecoder(
    private val bytes: ByteArray,
    private val options: Coil3Options,
) : Coil3Decoder {

    override suspend fun decode(): Coil3DecodeResult? = runInterruptible {
        runCatching {
            // `bytes` here is the peeked copy taken in Factory.create — safe to hold.
            val sampled: Boolean
            val dstWidth: Int
            val dstHeight: Int
            if (options.size == Size.ORIGINAL ||
                (
                    options.size.width is Dimension.Undefined &&
                        options.size.height is Dimension.Undefined
                    )
            ) {
                dstWidth = -1
                dstHeight = -1
                sampled = false
            } else {
                dstWidth = options.size.width.pxOrElse { 0 }
                dstHeight = options.size.height.pxOrElse { 0 }
                sampled = true
            }
            var bitmap = JxlCoder.decodeSampled(
                byteArray = bytes,
                width = dstWidth,
                height = dstHeight,
                preferredColorConfig = options.toPreferredConfig(),
                scaleMode = if (options.scale == Scale.FILL) ScaleMode.FILL else ScaleMode.FIT,
                jxlResizeFilter = JxlResizeFilter.BILINEAR,
            )
            if (options.bitmapConfig == Bitmap.Config.HARDWARE) {
                val target = if (ImageUtil.canUseHardwareBitmap(bitmap)) {
                    Bitmap.Config.HARDWARE
                } else {
                    Bitmap.Config.ARGB_8888
                }
                bitmap.copy(target, false)?.let { bitmap = it }
            }
            Coil3DecodeResult(image = bitmap.asImage(), isSampled = sampled)
        }.getOrNull()
    }

    class Factory : Coil3Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Coil3Options,
            imageLoader: Coil3ImageLoader,
        ): Coil3Decoder? {
            // NB: `sourceOrNull()` is the shared stream the fetch result owns — reading it
            // directly would consume bytes the downstream pipeline may still need. Peek a
            // copy for magic-byte sniffing, then hand the full bytes to the bridge decoder
            // (the bridge owns its copy, so nothing downstream is affected).
            val bytes = result.source.sourceOrNull()?.peek()?.readByteArray() ?: return null
            return if (bytes.isJxl()) JxlCoilBridgeDecoder(bytes, options) else null
        }

        override fun equals(other: Any?) = other is Factory

        override fun hashCode() = javaClass.hashCode()
    }
}

private fun Coil3Options.toPreferredConfig(): PreferredColorConfig {
    val base = when (bitmapConfig) {
        Bitmap.Config.ALPHA_8 -> PreferredColorConfig.RGBA_8888
        Bitmap.Config.RGB_565 ->
            if (allowRgb565) PreferredColorConfig.RGB_565 else PreferredColorConfig.DEFAULT
        Bitmap.Config.ARGB_8888 -> PreferredColorConfig.RGBA_8888
        else -> PreferredColorConfig.DEFAULT
    }
    return when {
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            bitmapConfig == Bitmap.Config.RGBA_F16 -> PreferredColorConfig.RGBA_F16
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            bitmapConfig == Bitmap.Config.HARDWARE -> PreferredColorConfig.HARDWARE
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            bitmapConfig == Bitmap.Config.RGBA_1010102 -> PreferredColorConfig.RGBA_1010102
        else -> base
    }
}

private fun ByteArray.isJxl(): Boolean {
    if (size < 2) return false
    if (this[0] == 0xFF.toByte() && this[1] == 0x0A.toByte()) return true
    if (size < 12) return false
    return this[4] == 0x4A.toByte() && this[5] == 0x58.toByte() &&
        this[6] == 0x4C.toByte() && this[7] == 0x20.toByte() &&
        this[10] == 0x87.toByte() && this[11] == 0x0A.toByte() &&
        Buffer().write(this).rangeEquals(
            0,
            okio.ByteString.of(0x0, 0x0, 0x0, 0x0C),
        )
}
