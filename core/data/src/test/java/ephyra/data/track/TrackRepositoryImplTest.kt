package ephyra.data.track

import ephyra.data.room.daos.TrackDao
import ephyra.data.room.entities.TrackEntity
import ephyra.domain.track.model.Track
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackRepositoryImplTest {

    private val trackDao = mockk<TrackDao>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val repo = TrackRepositoryImpl(trackDao, testDispatcher)

    private fun createTrack(id: Long = 1L, mangaId: Long = 10L, trackerId: Long = 1L): Track {
        return Track(
            id = id,
            mangaId = mangaId,
            trackerId = trackerId,
            remoteId = 12345L,
            libraryId = null,
            title = "MyAnimeList Track",
            lastChapterRead = 5.0,
            totalChapters = 100L,
            status = 1L,
            score = 8.5,
            remoteUrl = "https://myanimelist.net/manga/12345",
            startDate = 0L,
            finishDate = 0L,
            isPrivate = false,
        )
    }

    @Test
    fun `insertAll maps entities and executes batch insertAll`() = runTest(testDispatcher) {
        val track1 = createTrack(1L, 10L, 1L)
        val track2 = createTrack(2L, 10L, 2L)

        repo.insertAll(listOf(track1, track2))

        coVerify(exactly = 1) {
            trackDao.insertAll(
                match { list ->
                    list.size == 2 && list[0].syncId == 1L && list[1].syncId == 2L
                },
            )
        }
    }

    @Test
    fun `insert delegates to insertAll`() = runTest(testDispatcher) {
        val track = createTrack(1L, 10L, 1L)

        repo.insert(track)

        coVerify(exactly = 1) {
            trackDao.insertAll(
                match { list -> list.size == 1 && list[0].id == 1L },
            )
        }
    }

    @Test
    fun `getTrackById returns mapped track`() = runTest(testDispatcher) {
        val entity = TrackEntity(
            id = 42L,
            mangaId = 10L,
            syncId = 1L,
            remoteId = 123L,
            libraryId = null,
            title = "AniList",
            lastChapterRead = 12.0,
            totalChapters = 50L,
            status = 2L,
            score = 9.0,
            remoteUrl = "https://anilist.co/manga/123",
            startDate = 0L,
            finishDate = 0L,
            isPrivate = false,
        )
        coEvery { trackDao.getTrackById(42L) } returns entity

        val track = repo.getTrackById(42L)

        assertEquals(42L, track?.id)
        assertEquals("AniList", track?.title)
        assertEquals(12.0, track?.lastChapterRead)
    }

    @Test
    fun `getTrackById returns null when not found`() = runTest(testDispatcher) {
        coEvery { trackDao.getTrackById(999L) } returns null

        val track = repo.getTrackById(999L)

        assertNull(track)
    }

    @Test
    fun `delete delegates to trackDao`() = runTest(testDispatcher) {
        repo.delete(10L, 1L)
        coVerify(exactly = 1) { trackDao.delete(10L, 1L) }
    }
}
