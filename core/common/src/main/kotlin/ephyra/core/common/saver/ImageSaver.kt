package ephyra.core.common.saver

import android.graphics.Bitmap
import android.net.Uri
import okio.Buffer
import java.io.InputStream

/**
 * Domain-safe interface for saving images to the device.
 *
 * Lives in `core/common` so that feature modules (`feature/reader`, `feature/manga`) can
 * depend on it without taking a direct dependency on the `data` layer.
 * The concrete implementation ([ephyra.data.saver.ImageSaverImpl]) lives in `:data` and is
 * wired via Koin in `:app`.
 */
interface ImageSaver {
    /**
     * Saves [image] to the location specified inside it.
     * @return the [Uri] of the saved file.
     */
    fun save(image: Image): Uri
}

// ---------------------------------------------------------------------------
// Value types
// ---------------------------------------------------------------------------

/**
 * Represents an image that should be saved by [ImageSaver].
 *
 * All implementations carry a lazy `data` accessor that returns the raw bytes
 * as an [InputStream].  This keeps the sealed class hierarchy free of Android
 * I/O details.
 */
sealed class Image(
    open val name: String,
    open val location: Location,
) {

    /**
     * A manga cover image represented as an in-memory [Bitmap].
     *
     * [encoder] defaults to a lossless WebP encoder — callers may override it
     * to select a different format or quality.
     */
    data class Cover(
        val bitmap: Bitmap,
        override val name: String,
        override val location: Location,
        val encoder: (Bitmap, java.io.OutputStream) -> Unit = { bmp, os ->
            @Suppress("DEPRECATION")
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os)
        },
    ) : Image(name, location)

    /**
     * A reader page image available as a lazy [InputStream].
     */
    data class Page(
        val inputStream: () -> InputStream,
        override val name: String,
        override val location: Location,
    ) : Image(name, location)

    /** Returns the image bytes as a lazy [InputStream] supplier. */
    val data: () -> InputStream
        get() = when (this) {
            is Cover -> {
                {
                    val buffer = Buffer()
                    encoder(bitmap, buffer.outputStream())
                    buffer.inputStream()
                }
            }
            is Page -> inputStream
        }
}

/**
 * Describes the save destination for an [Image].
 *
 * The concrete helper `Location.directory(Context)` that resolves this sealed value
 * to a [java.io.File] lives in `data/saver/ImageSaverImpl.kt` to keep Android framework
 * types (Environment, cacheImageDir) out of this interface.
 */
sealed interface Location {

    /**
     * Saves to the device's shared Pictures folder (MediaStore on API 29+).
     * [relativePath] is appended beneath the app-named Pictures sub-directory.
     */
    @ConsistentCopyVisibility
    data class Pictures private constructor(val relativePath: String) : Location {
        companion object {
            fun create(relativePath: String = ""): Pictures = Pictures(relativePath)
        }
    }

    /** Saves to the app's internal image cache. */
    data object Cache : Location
}
