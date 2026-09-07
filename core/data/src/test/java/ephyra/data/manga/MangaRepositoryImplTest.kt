package ephyra.data.manga

import ephyra.data.room.daos.MangaDao
import ephyra.data.room.entities.MangaEntity
import ephyra.data.room.views.LibraryView
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaNotFoundException
import ephyra.domain.manga.model.MangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaRepositoryImplTest {

    private val mangaDao = mockk<MangaDao>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val repo = MangaRepositoryImpl(mangaDao, testDispatcher)

    private fun createMangaEntity(id: Long = 1L, title: String = "One Piece"): MangaEntity {
        return MangaEntity(
            id = id,
            source = 1L,
            url = "/manga/$id",
            artist = null,
            author = null,
            description = "Desc",
            genre = listOf("Action"),
            title = title,
            status = 1L,
            thumbnailUrl = null,
            favorite = true,
            lastUpdate = 0L,
            nextUpdate = 0L,
            initialized = true,
            viewerFlags = 0L,
            chapterFlags = 0L,
            coverLastModified = 0L,
            dateAdded = 0L,
            updateStrategy = 0,
            calculateInterval = 0,
            lastModifiedAt = 0L,
            favoriteModifiedAt = null,
            version = 1L,
            isSyncing = false,
            notes = "",
            metadataSource = null,
            metadataUrl = null,
            canonicalId = null,
            sourceStatus = 0,
            alternativeTitles = null,
            deadSince = null,
            contentType = 0,
            lockedFields = 0L,
        )
    }

    @Test
    fun `getMangaById returns mapped manga`() = runTest(testDispatcher) {
        val entity = createMangaEntity(42L, "Chainsaw Man")
        coEvery { mangaDao.getMangaById(42L) } returns entity

        val manga = repo.getMangaById(42L)

        assertEquals(42L, manga.id)
        assertEquals("Chainsaw Man", manga.title)
    }

    @Test
    fun `getMangaById throws MangaNotFoundException when not found`() = runTest(testDispatcher) {
        coEvery { mangaDao.getMangaById(999L) } returns null

        assertThrows(MangaNotFoundException::class.java) {
            runTest(testDispatcher) {
                repo.getMangaById(999L)
            }
        }
    }

    @Test
    fun `insertNetworkManga uses batch upsertAll`() = runTest(testDispatcher) {
        val manga1 = Manga.create().copy(source = 1L, url = "/manga/1", title = "Manga 1")
        val manga2 = Manga.create().copy(source = 1L, url = "/manga/2", title = "Manga 2")
        coEvery { mangaDao.upsertAll(any()) } returns listOf(101L, 102L)

        val result = repo.insertNetworkManga(listOf(manga1, manga2))

        assertEquals(2, result.size)
        assertEquals(101L, result[0].id)
        assertEquals(102L, result[1].id)
        coVerify(exactly = 1) { mangaDao.upsertAll(any()) }
    }

    @Test
    fun `updateAll executes batch lookup and updateAll`() = runTest(testDispatcher) {
        val entity1 = createMangaEntity(1L, "Old Title 1")
        val entity2 = createMangaEntity(2L, "Old Title 2")
        coEvery { mangaDao.getMangaByIds(listOf(1L, 2L)) } returns listOf(entity1, entity2)

        val update1 = MangaUpdate(id = 1L, title = "New Title 1")
        val update2 = MangaUpdate(id = 2L, title = "New Title 2")

        val success = repo.updateAll(listOf(update1, update2))

        assertTrue(success)
        coVerify(exactly = 1) { mangaDao.getMangaByIds(listOf(1L, 2L)) }
        coVerify(exactly = 1) {
            mangaDao.updateAll(
                match { list ->
                    list.size == 2 && list[0].title == "New Title 1" && list[1].title == "New Title 2"
                },
            )
        }
    }

    @Test
    fun `getFavorites maps and returns favorite list`() = runTest(testDispatcher) {
        val entities = listOf(createMangaEntity(1L, "Manga A"), createMangaEntity(2L, "Manga B"))
        coEvery { mangaDao.getFavorites() } returns entities

        val favorites = repo.getFavorites()

        assertEquals(2, favorites.size)
        assertEquals("Manga A", favorites[0].title)
        assertEquals("Manga B", favorites[1].title)
    }

    @Test
    fun `setMangaCategories delegates to dao`() = runTest(testDispatcher) {
        repo.setMangaCategories(5L, listOf(10L, 20L))
        coVerify(exactly = 1) { mangaDao.setMangaCategories(5L, listOf(10L, 20L)) }
    }
}
