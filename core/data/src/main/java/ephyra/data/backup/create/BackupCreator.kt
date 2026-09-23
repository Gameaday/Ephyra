package ephyra.data.backup.create

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.util.system.logcat
import ephyra.core.data.BuildConfig
import ephyra.data.backup.create.creators.CategoriesBackupCreator
import ephyra.data.backup.create.creators.ExtensionRepoBackupCreator
import ephyra.data.backup.create.creators.MangaBackupCreator
import ephyra.data.backup.create.creators.PreferenceBackupCreator
import ephyra.data.backup.create.creators.SourcesBackupCreator
import ephyra.data.backup.models.Backup
import ephyra.data.backup.models.BackupCategory
import ephyra.data.backup.models.BackupExtensionRepos
import ephyra.data.backup.models.BackupManga
import ephyra.data.backup.models.BackupPreference
import ephyra.data.backup.models.BackupSource
import ephyra.data.backup.models.BackupSourcePreferences
import ephyra.domain.backup.service.BackupPreferences
import ephyra.domain.manga.interactor.GetFavorites
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.storage.service.StorageManager
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import okio.buffer
import okio.gzip
import okio.sink
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

class BackupCreator(
    private val context: Context,
    private val parser: ProtoBuf,
    private val getFavorites: GetFavorites,
    private val backupPreferences: BackupPreferences,
    private val mangaRepository: MangaRepository,
    private val categoriesBackupCreator: CategoriesBackupCreator,
    private val mangaBackupCreator: MangaBackupCreator,
    private val preferenceBackupCreator: PreferenceBackupCreator,
    private val extensionRepoBackupCreator: ExtensionRepoBackupCreator,
    private val sourcesBackupCreator: SourcesBackupCreator,
    private val storageManager: StorageManager,
) {

    suspend fun createBackup(uri: Uri? = null, options: BackupOptions? = null): Uri {
        val effectiveOptions = options ?: BackupOptions()
        val file = resolveTargetFile(uri, getFilename())

        val backupMangas = mangaBackupCreator(getFavorites.await(), effectiveOptions)
        val backup = Backup(
            backupManga = backupMangas,
            backupCategories = categoriesBackupCreator(),
            backupSources = sourcesBackupCreator(backupMangas),
            backupPreferences = preferenceBackupCreator.createApp(true),
            backupExtensionRepo = extensionRepoBackupCreator(),
            backupSourcePreferences = preferenceBackupCreator.createSource(true),
        )

        val byteArray = parser.encodeToByteArray(Backup.serializer(), backup)
        file.openOutputStream().sink().gzip().buffer().use {
            it.write(byteArray)
        }

        // Only automatic runs update the "last auto backup" stat; manual backups are
        // user-visible events and don't drive the Settings display.
        if (uri == null) {
            backupPreferences.lastAutoBackupTimestamp().set(System.currentTimeMillis())
        }
        return file.uri
    }

    /**
     * Resolves the destination file for a backup run.
     *
     * Manual runs receive the URI of a document SAF's CreateDocument already created
     * (and named), so it is the target file itself — creating a *child* under it fails
     * on every provider. Automatic runs resolve the configured storage directory, with
     * a fallback to app-specific external storage (always writable, needs no
     * permissions) because the platform default path at the shared-storage root cannot
     * be created under scoped storage, which is what made every scheduled backup fail.
     */
    private fun resolveTargetFile(uri: Uri?, filename: String): UniFile {
        if (uri != null) {
            return UniFile.fromUri(context, uri)
                ?: throw IllegalStateException("Invalid backup destination: $uri")
        }

        val primary = runCatching { storageManager.getAutomaticBackupsDirectory() }
            .onFailure { logcat(LogPriority.WARN, it) { "Configured backup directory unavailable" } }
            .getOrNull()
        if (primary != null) {
            runCatching { primary.createFile(filename) }
                .onFailure { logcat(LogPriority.WARN, it) { "Could not create backup file in ${primary.uri}" } }
                .getOrNull()?.let { return it }
        }

        val fallbackDir = context.getExternalFilesDir(StorageManager.AUTOMATIC_BACKUPS_PATH)
            ?.also { if (!it.exists()) it.mkdirs() }
            ?: throw IllegalStateException(
                "No writable backup directory. Pick a storage location in Settings \u2192 Data and storage.",
            )
        logcat(LogPriority.WARN, IllegalStateException("Using app-specific backup directory: $fallbackDir"))
        return runCatching { UniFile.fromFile(fallbackDir)?.createFile(filename) }.getOrNull()
            ?: throw IllegalStateException("Failed to create backup file in $fallbackDir")
    }

    companion object {
        fun getFilename(): String {
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ENGLISH).format(Date())
            return "${BuildConfig.APPLICATION_ID}_$date.tachibk"
        }
    }
}
