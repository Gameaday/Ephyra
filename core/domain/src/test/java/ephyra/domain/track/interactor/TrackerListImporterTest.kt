package ephyra.domain.track.interactor

import ephyra.domain.chapter.interactor.GenerateAuthorityChapters
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.track.model.Track
import ephyra.domain.track.model.TrackSearch
import ephyra.domain.track.service.ReadingListTracker
import ephyra.domain.track.service.Tracker
import ephyra.domain.track.service.TrackerManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class TrackerListImporterTest {

    private val mangaRepository = mockk<MangaRepository>(relaxed = true)
    private val insertTrack = mockk<InsertTrack>(relaxed = true)
    private val trackerManager = mockk<TrackerManager>()
    private val generateAuthorityChapters = mockk<GenerateAuthorityChapters>(relaxed = true)

    private val importer = TrackerListImporter(
        mangaRepository = mangaRepository,
        insertTrack = insertTrack,
        trackerManager = trackerManager,
        generateAuthorityChapters = generateAuthorityChapters,
    )

    private interface TestReadingListTracker : Tracker, ReadingListTracker

    @Test
    fun `importFromAnilist populates library with 50 canonical cards`() = runTest {
        val mockTracker = mockk<TestReadingListTracker>()
        coEvery { mockTracker.id } returns TrackerManager.ANILIST
        coEvery { mockTracker.name } returns "AniList"
        coEvery { mockTracker.isLoggedIn() } returns true

        val items = (1..50).map { i ->
            TrackSearch(
                remote_id = i.toLong(),
                title = "AniList Title $i",
                tracker_id = TrackerManager.ANILIST,
                tracking_url = "https://anilist.co/manga/$i",
                summary = "Summary $i",
                cover_url = "https://img.anilist.co/$i.jpg",
                total_chapters = 25L,
                last_chapter_read = 10.0,
                status = 1L,
                score = 8.5,
                started_reading_date = 1000L,
                finished_reading_date = 0L,
            )
        }
        coEvery { mockTracker.getUserReadingList() } returns items
        coEvery { trackerManager.get(TrackerManager.ANILIST) } returns mockTracker

        coEvery {
            mangaRepository.getMangaByUrlAndSourceId(any(), TrackerListImporter.AUTHORITY_SOURCE_ID)
        } returns null
        coEvery { mangaRepository.insertNetworkManga(any()) } answers {
            val list = firstArg<List<Manga>>()
            list.mapIndexed { idx, m -> m.copy(id = 1000L + idx) }
        }

        val result = importer.importFromAnilist()

        assertTrue(result.isSuccess)
        assertEquals(50, result.imported)
        assertEquals(0, result.skipped)
        assertEquals(0, result.failed)
        assertEquals(50, result.total)

        coVerify(exactly = 50) { mangaRepository.insertNetworkManga(any()) }
        coVerify(exactly = 50) { insertTrack.await(any()) }
        coVerify(exactly = 50) { generateAuthorityChapters.await(any(), 25, 10) }
    }

    @Test
    fun `importFromMal updates existing library item and handles authority chapters`() = runTest {
        val mockTracker = mockk<TestReadingListTracker>()
        coEvery { mockTracker.id } returns TrackerManager.MYANIMELIST
        coEvery { mockTracker.name } returns "MyAnimeList"
        coEvery { mockTracker.isLoggedIn() } returns true

        val item = TrackSearch(
            remote_id = 42L,
            title = "Existing Manga",
            tracker_id = TrackerManager.MYANIMELIST,
            tracking_url = "https://myanimelist.net/manga/42",
            total_chapters = 12L,
            last_chapter_read = 6.0,
            status = 1L,
            score = 7.0,
        )
        coEvery { mockTracker.getUserReadingList() } returns listOf(item)
        coEvery { trackerManager.get(TrackerManager.MYANIMELIST) } returns mockTracker

        val existingManga = Manga.create().copy(
            id = 500L,
            url = "mal:42",
            title = "Existing Manga",
            source = TrackerListImporter.AUTHORITY_SOURCE_ID,
            favorite = false,
        )
        coEvery {
            mangaRepository.getMangaByUrlAndSourceId("mal:42", TrackerListImporter.AUTHORITY_SOURCE_ID)
        } returns existingManga

        val result = importer.importFromMal()

        assertTrue(result.isSuccess)
        assertEquals(1, result.imported)
        assertEquals(0, result.failed)

        coVerify { mangaRepository.update(match { it.id == 500L && it.favorite == true }) }
        coVerify(exactly = 0) { mangaRepository.insertNetworkManga(any()) }
        coVerify { insertTrack.await(match { it.mangaId == 500L && it.remoteId == 42L }) }
        coVerify { generateAuthorityChapters.await(500L, 12, 6) }
    }

    @Test
    fun `returns error when tracker is not logged in`() = runTest {
        val mockTracker = mockk<TestReadingListTracker>()
        coEvery { mockTracker.id } returns TrackerManager.ANILIST
        coEvery { mockTracker.name } returns "AniList"
        coEvery { mockTracker.isLoggedIn() } returns false
        coEvery { trackerManager.get(TrackerManager.ANILIST) } returns mockTracker

        val result = importer.importFromAnilist()

        assertFalse(result.isSuccess)
        assertEquals("Not logged in to AniList", result.error)
        assertEquals(0, result.imported)
    }

    @Test
    fun `returns error when tracker does not support reading list import`() = runTest {
        val mockTracker = mockk<Tracker>() // Does not implement ReadingListTracker
        coEvery { mockTracker.id } returns 99L
        coEvery { mockTracker.name } returns "Unsupported"
        coEvery { mockTracker.isLoggedIn() } returns true
        coEvery { trackerManager.get(99L) } returns mockTracker

        val result = importer.importFromTracker(99L)

        assertFalse(result.isSuccess)
        assertEquals("Unsupported does not support reading list import", result.error)
    }

    @Test
    fun `handles network exception gracefully`() = runTest {
        val mockTracker = mockk<TestReadingListTracker>()
        coEvery { mockTracker.id } returns TrackerManager.ANILIST
        coEvery { mockTracker.name } returns "AniList"
        coEvery { mockTracker.isLoggedIn() } returns true
        coEvery { mockTracker.getUserReadingList() } throws IOException("Connection timed out")
        coEvery { trackerManager.get(TrackerManager.ANILIST) } returns mockTracker

        val result = importer.importFromAnilist()

        assertFalse(result.isSuccess)
        assertEquals("Connection timed out", result.error)
    }
}
