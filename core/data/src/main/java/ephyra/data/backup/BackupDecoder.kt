package ephyra.data.backup

import android.content.Context
import android.net.Uri
import ephyra.data.backup.models.Backup
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.IOException

/**
 * Class used to decode a backup file.
 */
@OptIn(ExperimentalSerializationApi::class)
class BackupDecoder(
    private val context: Context,
    private val protoBuf: ProtoBuf,
) {

    /**
     * Decodes a backup file from the given uri.
     *
     * @param uri the uri of the backup file.
     * @return the decoded backup.
     */
    fun decode(uri: Uri): Backup {
        return (
            context.contentResolver.openInputStream(uri)
                ?: throw IOException("Unable to open input stream")
            ).use {
            decode(it)
        }
    }

    /**
     * Decodes a backup file from the given input stream.
     *
     * @param inputStream the input stream of the backup file.
     * @return the decoded backup.
     */
    fun decode(inputStream: java.io.InputStream): Backup {
        return try {
            protoBuf.decodeFromByteArray(Backup.serializer(), inputStream.readBytes())
        } catch (e: Exception) {
            throw IOException(e)
        }
    }
}
