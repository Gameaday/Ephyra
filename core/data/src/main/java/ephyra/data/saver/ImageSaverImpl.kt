package ephyra.data.saver

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.contentValuesOf
import androidx.core.net.toUri
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.saver.Image
import ephyra.core.common.saver.ImageSaver
import ephyra.core.common.saver.Location
import ephyra.core.common.util.storage.DiskUtil
import ephyra.core.common.util.storage.cacheImageDir
import ephyra.core.common.util.storage.getUriCompat
import ephyra.core.common.util.system.ImageUtil
import ephyra.core.common.util.system.logcat
import ephyra.i18n.MR
import logcat.LogPriority
import okio.IOException
import java.io.File
import java.io.InputStream
import java.time.Instant

/**
 * Concrete implementation of [ImageSaver].
 *
 * This class lives in `:data` because it depends on Android MediaStore APIs and Context.
 * Feature modules receive [ImageSaver] (the interface from `:core/common`) via constructor
 * injection, keeping them free of `:data` imports.
 */
class ImageSaverImpl(
    private val context: Context,
) : ImageSaver {

    override fun save(image: Image): Uri {
        val data = image.data

        val type = ImageUtil.findImageType(data) ?: throw IllegalArgumentException("Not an image")
        val filename = DiskUtil.buildValidFilename("${image.name}.${type.extension}")

        if (image.location !is Location.Pictures) {
            return save(data(), image.location.directory(context), filename)
        }

        return saveApi29(image, type, filename, data)
    }

    private fun save(inputStream: InputStream, directory: File, filename: String): Uri {
        directory.mkdirs()

        val destFile = File(directory, filename)

        inputStream.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        DiskUtil.scanMedia(context, destFile.toUri())

        return destFile.getUriCompat(context)
    }

    private fun saveApi29(
        image: Image,
        type: ImageUtil.ImageType,
        filename: String,
        data: () -> InputStream,
    ): Uri {
        val isMimeTypeSupported = MimeTypeMap.getSingleton().hasMimeType(type.mime)

        val pictureDir = if (isMimeTypeSupported) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        val imageLocation = (image.location as Location.Pictures).relativePath
        val relativePath = listOf(
            if (isMimeTypeSupported) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOCUMENTS,
            context.stringResource(MR.strings.app_name),
            imageLocation,
        ).joinToString(File.separator)

        val contentValues = contentValuesOf(
            MediaStore.MediaColumns.RELATIVE_PATH to relativePath,
            MediaStore.MediaColumns.DISPLAY_NAME to if (isMimeTypeSupported) image.name else filename,
            MediaStore.MediaColumns.MIME_TYPE to type.mime,
            MediaStore.MediaColumns.DATE_MODIFIED to Instant.now().epochSecond,
        )

        val picture = findUriOrDefault(relativePath, filename) {
            context.contentResolver.insert(
                pictureDir,
                contentValues,
            ) ?: throw IOException(context.stringResource(MR.strings.error_saving_picture))
        }

        try {
            data().use { input ->
                val outputStream = context.contentResolver.openOutputStream(picture, "w")
                    ?: throw IOException(context.stringResource(MR.strings.error_saving_picture))
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            throw IOException(context.stringResource(MR.strings.error_saving_picture))
        }

        DiskUtil.scanMedia(context, picture)

        return picture
    }

    private fun findUriOrDefault(path: String, filename: String, default: () -> Uri): Uri {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )

        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"

        // Need to make sure it ends with the separator
        val normalizedPath = "${path.removeSuffix(File.separator)}${File.separator}"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf(normalizedPath, filename),
            null,
        ).use { cursor ->
            if (cursor != null && cursor.count >= 1) {
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        }

        return default()
    }
}

// ---------------------------------------------------------------------------
// Private Android helpers — not part of the public ImageSaver interface.
// ---------------------------------------------------------------------------

/**
 * Resolves this [Location] to a concrete [File] using the provided [Context].
 * Only used by [ImageSaverImpl]; callers that hold a [Location] reference from
 * `core/common` do not need (and cannot access) this extension.
 */
private fun Location.directory(context: Context): File = when (this) {
    is Location.Cache -> context.cacheImageDir
    is Location.Pictures -> {
        val base = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            context.stringResource(MR.strings.app_name),
        )
        if (relativePath.isNotEmpty()) File(base, relativePath) else base
    }
}
