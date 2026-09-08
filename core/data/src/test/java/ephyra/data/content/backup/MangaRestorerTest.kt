package ephyra.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.data.backup.models.BackupManga
import ephyra.data.backup.models.BackupTracking
import ephyra.data.backup.restore.restorers.MangaRestorer
import ephyra.data.room.EphyraDatabase
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.chapter.interactor.GetChaptersByMangaId
import ephyra.domain.chapter.repository.ChapterRepository
import ephyra.domain.history.interactor.UpsertHistory
import ephyra.domain.history.repository.HistoryRepository
import ephyra.domain.manga.interactor.FetchInterval
import ephyra.domain.manga.interactor.GetExcludedScanlators
import ephyra.domain.manga.interactor.GetMangaByUrlAndSourceId
import ephyra.domain.manga.interactor.SetExcludedScanlators
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.track.interactor.GetTracks
import ephyra.domain.track.interactor.InsertTrack
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MangaRestorerTest {

    private lateinit var database: EphyraDatabase
    private val mangaRepository = mockk<MangaRepository>(relaxed = true)
    private val chapterRepository = mockk<ChapterRepository>(relaxed = true)
    private val historyRepository = mockk<HistoryRepository>(relaxed = true)
    private val upsertHistory = mockk<UpsertHistory>(relaxed = true)
    private val getCategories = mockk<GetCategories>(relaxed = true)
    private val getMangaByUrlAndSourceId = mockk<GetMangaByUrlAndSourceId>(relaxed = true)
    private val getChaptersByMangaId = mockk<GetChaptersByMangaId>(relaxed = true)
    private val updateManga = mockk<UpdateManga>(relaxed = true)
    private val getTracks = mockk<GetTracks>(relaxed = true)
    private val insertTrack = mockk<InsertTrack>(relaxed = true)
    private val getExcludedScanlators = mockk<GetExcludedScanlators>(relaxed = true)
    private val setExcludedScanlators = mockk<SetExcludedScanlators>(relaxed = true)
    private val fetchInterval = mockk<FetchInterval>(relaxed = true)

    private lateinit var restorer: MangaRestorer

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EphyraDatabase::class.java,
        ).allowMainThreadQueries().build()

        restorer = MangaRestorer(
            database = database,
            mangaRepository = mangaRepository,
            chapterRepository = chapterRepository,
            historyRepository = historyRepository,
            upsertHistory = upsertHistory,
            getCategories = getCategories,
            getMangaByUrlAndSourceId = getMangaByUrlAndSourceId,
            getChaptersByMangaId = getChaptersByMangaId,
            updateManga = updateManga,
            getTracks = getTracks,
            insertTrack = insertTrack,
            getExcludedScanlators = getExcludedScanlators,
            setExcludedScanlators = setExcludedScanlators,
            fetchInterval = fetchInterval,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `restore manga with tracking but no canonicalId automatically links canonical ID`() = runTest {
        val existingManga = Manga.create().copy(
            id = 42L,
            url = "/manga/attack-on-titan",
            source = 1L,
            title = "Attack on Titan",
            canonicalId = null,
        )
        coEvery { getMangaByUrlAndSourceId.await("/manga/attack-on-titan", 1L) } returns existingManga

        val backupManga = BackupManga(
            source = 1L,
            url = "/manga/attack-on-titan",
            title = "Attack on Titan",
            canonicalId = null,
            tracking = listOf(
                BackupTracking(
                    syncId = 2, // AniList
                    libraryId = 0L,
                    mediaId = 53390L,
                ),
            ),
        )

        restorer.restore(backupManga, emptyList())

        coVerify {
            updateManga.await(
                match<MangaUpdate> { update ->
                    update.id == 42L && update.canonicalId == "al:53390"
                },
            )
        }
    }

    @Test
    fun `restore manga with existing canonicalId preserves it without overwriting`() = runTest {
        val existingManga = Manga.create().copy(
            id = 42L,
            url = "/manga/attack-on-titan",
            source = 1L,
            title = "Attack on Titan",
            canonicalId = "al:existing-id",
        )
        coEvery { getMangaByUrlAndSourceId.await("/manga/attack-on-titan", 1L) } returns existingManga

        val backupManga = BackupManga(
            source = 1L,
            url = "/manga/attack-on-titan",
            title = "Attack on Titan",
            canonicalId = "al:existing-id",
            tracking = listOf(
                BackupTracking(
                    syncId = 1, // MAL
                    libraryId = 0L,
                    mediaId = 99999L,
                ),
            ),
        )

        restorer.restore(backupManga, emptyList())

        coVerify(exactly = 0) {
            updateManga.await(
                match<MangaUpdate> { update ->
                    update.canonicalId == "mal:99999"
                },
            )
        }
    }
}
