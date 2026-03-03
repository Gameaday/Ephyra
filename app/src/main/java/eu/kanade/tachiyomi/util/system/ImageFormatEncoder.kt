package eu.kanade.tachiyomi.util.system

import android.graphics.Bitmap
import tachiyomi.domain.library.service.LibraryPreferences.ImageFormat
import java.io.OutputStream

/**
 * Returns a lossless encoder for **persisting** images to disk (covers, download splits, merges).
 *
 * WebP lossless uses Android's native encoder — hardware-accelerated decode, ~25 % smaller
 * than PNG, and fast to encode.
 * PNG uses Android's built-in encoder — universal compatibility.
 *
 * This encoder is **not** used for transient reader buffers. Reader transforms
 * (split, rotate, merge) return [Bitmap] directly since SubsamplingScaleImageView
 * accepts bitmaps via ImageSource.bitmap().
 */
fun ImageFormat.encoder(): (Bitmap, OutputStream) -> Unit = when (this) {
    ImageFormat.PNG -> { bitmap, os ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
    }
    ImageFormat.WEBP -> { bitmap, os ->
        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, os)
    }
}
