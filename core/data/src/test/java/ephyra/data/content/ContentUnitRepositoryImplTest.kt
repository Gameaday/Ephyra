package ephyra.data.content

import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.repository.ChapterRepository
import ephyra.domain.content.model.toContentUnit
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fast JVM tests for [ContentUnitRepositoryImpl] — the READ/WRITE path for
 * sequential content units (chapters/episodes). It maps [Chapter] to the generic
 * [ephyra.domain.content.model.ContentUnit] and delegates to [ChapterRepository].
 * No Room/Android runtime needed.
 */
class ContentUnitRepositoryImplTest {

    private val chapterRepository = mockk<ChapterRepository>()

    private val repo = ContentUnitRepositoryImpl(chapterRepository)

    private fun chapter(id: Long, mangaId: Long, name: String) = Chapter.create().copy(
        id = id,
        mangaId = mangaId,
        url = "/ch/$id",
        name = name,
        chapterNumber = id.toDouble(),
    )

    @Test
    fun `getUnitsByContentItemId maps chapters to content units`() = runTest {
        val chapters = listOf(
            chapter(1, mangaId = 42L, name = "Ch 1"),
            chapter(2, mangaId = 42L, name = "Ch 2"),
        )
        coEvery { chapterRepository.getChapterByMangaId(42L) } returns chapters

        val units = repo.getUnitsByContentItemId(42L)

        assertEquals(2, units.size)
        assertEquals(listOf("Ch 1", "Ch 2"), units.map { it.title })
        assertEquals(42L, units.first().contentItemId)
    }

    @Test
    fun `getUnitsByContentItemIdAsFlow maps the streamed units`() = runTest {
        coEvery { chapterRepository.getChapterByMangaIdAsFlow(42L) } returns flowOf(
            listOf(chapter(1, mangaId = 42L, name = "Ch 1")),
        )

        val units = repo.getUnitsByContentItemIdAsFlow(42L).first()

        assertEquals(1, units.size)
        assertEquals("Ch 1", units.first().title)
    }

    @Test
    fun `update delegates to the chapter repository`() = runTest {
        val unit = chapter(1, mangaId = 42L, name = "Ch 1").toContentUnit()
        coEvery { chapterRepository.update(any()) } returns Unit

        val updated = repo.update(unit)

        assertTrue(updated)
        coVerify(exactly = 1) { chapterRepository.update(any()) }
    }

    @Test
    fun `updateAll delegates to the chapter repository`() = runTest {
        val units = listOf(
            chapter(1, mangaId = 42L, name = "Ch 1").toContentUnit(),
            chapter(2, mangaId = 42L, name = "Ch 2").toContentUnit(),
        )
        coEvery { chapterRepository.updateAll(any()) } returns Unit

        val updated = repo.updateAll(units)

        assertTrue(updated)
        coVerify(exactly = 1) { chapterRepository.updateAll(any()) }
    }
}
