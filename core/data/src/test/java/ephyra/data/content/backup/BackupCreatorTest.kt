package ephyra.data.content.backup

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hippo.unifile.UniFile
import ephyra.core.common.preference.Preference
import ephyra.data.backup.create.BackupCreator
import ephyra.data.backup.create.creators.CategoriesBackupCreator
import ephyra.data.backup.create.creators.ExtensionRepoBackupCreator
import ephyra.data.backup.create.creators.MangaBackupCreator
import ephyra.data.backup.create.creators.PreferenceBackupCreator
import ephyra.data.backup.create.creators.SourcesBackupCreator
import ephyra.data.backup.models.Backup
import ephyra.domain.backup.service.BackupPreferences
import ephyra.domain.manga.interactor.GetFavorites
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.storage.service.StorageManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Locks the destination-resolution contract behind the regularly-failing automatic
 * backups: primary directory with an app-specific fallback (never a hard failure
 * while any writable location exists), manual runs writing directly into the
 * SAF-created document, and the "last auto backup" timestamp recorded on success.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class BackupCreatorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val getFavorites = mockk<GetFavorites>()
    private val backupPreferences = mockk<BackupPreferences>()
    private val lastAutoBackup = mockk<Preference<Long>>(relaxed = true)
    private val mangaRepository = mockk<MangaRepository>(relaxed = true)
    private val categoriesBackupCreator = mockk<CategoriesBackupCreator>()
    private val mangaBackupCreator = mockk<MangaBackupCreator>()
    private val preferenceBackupCreator = mockk<PreferenceBackupCreator>(relaxed = true)
    private val extensionRepoBackupCreator = mockk<ExtensionRepoBackupCreator>()
    private val sourcesBackupCreator = mockk<SourcesBackupCreator>()
    private val storageManager = mockk<StorageManager>()

    private lateinit var creator: BackupCreator

    @Before
    fun setUp() {
        coEvery { getFavorites.await() } returns emptyList()
        coEvery { categoriesBackupCreator() } returns emptyList()
        coEvery { mangaBackupCreator(any(), any()) } returns emptyList()
        coEvery { extensionRepoBackupCreator() } returns emptyList()
        every { sourcesBackupCreator(any()) } returns emptyList()
        every { preferenceBackupCreator.createApp(any()) } returns emptyList()
        every { preferenceBackupCreator.createSource(any()) } returns emptyList()
        every { backupPreferences.lastAutoBackupTimestamp() } returns lastAutoBackup

        creator = BackupCreator(
            context = context,
            parser = ProtoBuf,
            getFavorites = getFavorites,
            backupPreferences = backupPreferences,
            mangaRepository = mangaRepository,
            categoriesBackupCreator = categoriesBackupCreator,
            mangaBackupCreator = mangaBackupCreator,
            preferenceBackupCreator = preferenceBackupCreator,
            extensionRepoBackupCreator = extensionRepoBackupCreator,
            sourcesBackupCreator = sourcesBackupCreator,
            storageManager = storageManager,
        )
    }

    @Test
    fun `automatic backup writes a decodable file into the primary storage directory`() {
        val primary = tmp.newFolder("primary")
        every { storageManager.getAutomaticBackupsDirectory() } returns UniFile.fromFile(primary)

        val uri = runBlocking { creator.createBackup() }

        val file = File(requireNotNull(uri.path))
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
        assertEquals(primary.canonicalFile, file.parentFile.canonicalFile)
        verify { lastAutoBackup.set(match { it > 0L }) }

        val decoded = file.inputStream().source().gzip().buffer().use { source ->
            ProtoBuf.decodeFromByteArray(Backup.serializer(), source.readByteArray())
        }
        assertTrue(decoded.backupManga.isEmpty())
    }

    @Test
    fun `automatic backup falls back to app-specific storage when the primary directory is unavailable`() {
        // The shipped failure mode: scoped storage never allows the default
        // shared-storage-root directory, so getAutomaticBackupsDirectory() is null on
        // every scheduled run. The backup must still land somewhere writable.
        every { storageManager.getAutomaticBackupsDirectory() } returns null

        val uri = runBlocking { creator.createBackup() }

        val file = File(requireNotNull(uri.path))
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
        assertEquals(
            context.getExternalFilesDir(StorageManager.AUTOMATIC_BACKUPS_PATH)!!.canonicalFile,
            file.parentFile.canonicalFile,
        )
        verify { lastAutoBackup.set(match { it > 0L }) }
    }

    @Test
    fun `manual backup writes directly into the picked document without creating children`() {
        val picked = File(tmp.newFolder("dest"), "picked_backup.tachibk")
        assertTrue(picked.createNewFile())

        val uri = runBlocking { creator.createBackup(Uri.fromFile(picked)) }

        assertEquals(picked.canonicalFile, File(requireNotNull(uri.path)).canonicalFile)
        assertTrue(picked.length() > 0)
        // The picked document is the target itself — nothing may be created under it.
        val siblings = picked.parentFile!!.list()!!.toList()
        assertEquals("unexpected siblings: $siblings", listOf(picked.name), siblings.sorted())
        // Manual runs must not touch the automatic-backup statistic.
        verify(exactly = 0) { lastAutoBackup.set(any()) }
    }
}
