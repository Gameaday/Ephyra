package ephyra.data.content

import ephyra.domain.content.model.toContentItem
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.repository.MangaRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fast JVM tests for [ContentRepositoryImpl] — the READ/WRITE path for library
 * content items. It maps [Manga] to the generic [ephyra.domain.content.model.ContentItem]
 * and delegates persistence to [MangaRepository]. No Room/Android runtime needed.
 */
class ContentRepositoryImplTest {

    private val mangaRepository = mockk<MangaRepository>()

    private val repo = ContentRepositoryImpl(mangaRepository)

    @Test
    fun `getContentItemById maps and returns the item`() = runTest {
        val manga = Manga.create().copy(id = 42L, source = 7L, title = "One Piece", url = "/manga/42")
        coEvery { mangaRepository.getMangaById(42L) } returns manga

        val item = repo.getContentItemById(42L)

        assertEquals(42L, item?.id)
        assertEquals("One Piece", item?.title)
        assertEquals(7L, item?.sourceId)
    }

    @Test
    fun `getContentItemById returns null on repository error`() = runTest {
        coEvery { mangaRepository.getMangaById(42L) } throws IllegalStateException("db error")

        assertNull(repo.getContentItemById(42L))
    }

    @Test
    fun `getContentItemByIdAsFlow maps the streamed item`() = runTest {
        val manga = Manga.create().copy(id = 1L, source = 2L, title = "Naruto")
        coEvery { mangaRepository.getMangaByIdAsFlow(1L) } returns flowOf(manga)

        val item = repo.getContentItemByIdAsFlow(1L).first()

        assertEquals(1L, item?.id)
        assertEquals("Naruto", item?.title)
    }

    @Test
    fun `getFavorites maps all favorite items`() = runTest {
        val mangas = listOf(
            Manga.create().copy(id = 1L, title = "One Piece"),
            Manga.create().copy(id = 2L, title = "Naruto"),
        )
        coEvery { mangaRepository.getFavorites() } returns mangas

        val items = repo.getFavorites()

        assertEquals(2, items.size)
        assertEquals(listOf("One Piece", "Naruto"), items.map { it.title })
    }

    @Test
    fun `update delegates to the manga repository and returns its result`() = runTest {
        val manga = Manga.create().copy(id = 1L, source = 2L, title = "One Piece")
        val item = manga.toContentItem()
        coEvery { mangaRepository.update(any()) } returns true

        val updated = repo.update(item)

        assertTrue(updated)
        coVerify(exactly = 1) { mangaRepository.update(any()) }
    }
}
