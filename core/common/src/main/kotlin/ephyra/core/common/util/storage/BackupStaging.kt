package ephyra.core.common.util.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import ephyra.core.common.util.system.logcat
import logcat.LogPriority
import java.io.File
import java.io.IOException

/**
 * Utility to stage selected backup files from Storage Access Framework (SAF),
 * ExternalStorageProvider, or content URIs into the application's private cache.
 *
 * Android SAF grants transient read permissions to the foreground Activity that
 * launched the file picker. Passing a raw SAF URI across Compose navigation,
 * ViewModels, and WorkManager background workers often fails with permission
 * denials because:
 * 1. Storage Access Framework blocks persistable grants on autobackup/primary directories.
 * 2. Background jobs and ViewModel instances injected with ApplicationContext do not
 *    inherit transient Activity Intent URI permissions.
 *
 * By copying the stream immediately into [context]'s private cache directory upon
 * file selection, we guarantee frictionless restoration across all Android versions
 * (11 through 15) and cloud/OEM providers.
 */
object BackupStaging {

    private const val STAGING_DIR_NAME = "backup_restore"
    private const val DEFAULT_BACKUP_NAME = "restore.tachibk"

    fun getStagedBackupDir(context: Context): File {
        val dir = File(context.cacheDir, STAGING_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Copies the content from [uri] into a temporary staging file in [context]'s private cache.
     * Must be invoked while the foreground context holds transient read access to [uri].
     *
     * @param context Active context (e.g. Activity) with permission to open [uri].
     * @param uri URI of the backup file chosen by the user.
     * @return A `file://` [Uri] pointing directly to the staged file on local storage.
     */
    fun stageBackupFile(context: Context, uri: Uri): Uri {
        val stagingDir = getStagedBackupDir(context)

        // Clear any previous staged files
        try {
            stagingDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to clean old staged backup files" }
        }

        val rawName = queryDisplayName(context, uri) ?: DEFAULT_BACKUP_NAME
        val sanitizedName = DiskUtil.buildValidFilename(rawName).ifBlank { DEFAULT_BACKUP_NAME }
        val destFile = File(stagingDir, sanitizedName)

        val inputStream = when {
            uri.scheme == "file" && uri.path != null -> File(uri.path!!).inputStream()
            else -> context.contentResolver.openInputStream(uri)
        } ?: throw IOException("Unable to open input stream for backup URI: $uri")

        inputStream.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return destFile.toUri()
    }

    /**
     * Deletes all staged backup files from [context]'s private cache.
     */
    fun clearStagedBackup(context: Context) {
        try {
            val stagingDir = File(context.cacheDir, STAGING_DIR_NAME)
            if (stagingDir.exists()) {
                stagingDir.deleteRecursively()
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to delete staged backup directory" }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (col != -1) {
                            val name = cursor.getString(col)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG, e) { "Could not query display name for $uri" }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
