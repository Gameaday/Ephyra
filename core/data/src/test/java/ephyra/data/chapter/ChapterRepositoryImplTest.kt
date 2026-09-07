package ephyra.data.chapter

import ephyra.data.room.daos.ChapterDao
import ephyra.data.room.entities.ChapterEntity
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.model.ChapterUpdate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterRepositoryImplTest {

    private val chapterDao = mockk<ChapterDao>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val repo = ChapterRepositoryImpl(chapterDao, testDispatcher)

    private fun createChapterEntity(id: Long = 1L, mangaId: Long = 10L, name: String = "Chapter 1"): ChapterEntity {
        return ChapterEntity(
            id = id,
            mangaId = mangaId,
            url = "/chapter/$id",
            name = name,
            scanlator = "ScanGroup",
            read = false,
            bookmark = false,
            lastPageRead = 0,
            chapterNumber = 1.0,
            sourceOrder = 0,
            dateFetch = 0L,
            dateUpload = 0L,
            lastModifiedAt = 0L,
            version = 1L,
            isSyncing = false,
        )
    }

    @Test
    fun `addAll executes batch insertAll`() = runTest(testDispatcher) {
        val chapter1 = Chapter.create().copy(mangaId = 10L, url = "/c/1", name = "Ch 1")
        val chapter2 = Chapter.create().copy(mangaId = 10L, url = "/c/2", name = "Ch 2")
        coEvery { chapterDao.insertAll(any()) } returns listOf(501L, 502L)

        val result = repo.addAll(listOf(chapter1, chapter2))

        assertEquals(2, result.size)
        assertEquals(501L, result[0].id)
        assertEquals(502L, result[1].id)
        coVerify(exactly = 1) { chapterDao.insertAll(any()) }
    }

    @Test
    fun `updateAll executes batch getChaptersByIds and updateAll`() = runTest(testDispatcher) {
        val entity1 = createChapterEntity(1L, 10L, "Ch 1")
        val entity2 = createChapterEntity(2L, 10L, "Ch 2")
        coEvery { chapterDao.getChaptersByIds(listOf(1L, 2L)) } returns listOf(entity1, entity2)

        val update1 = ChapterUpdate(id = 1L, read = true)
        val update2 = ChapterUpdate(id = 2L, bookmark = true)

        repo.updateAll(listOf(update1, update2))

        coVerify(exactly = 1) { chapterDao.getChaptersByIds(listOf(1L, 2L)) }
        coVerify(exactly = 1) {
            chapterDao.updateAll(
                match { list ->
                    list.size == 2 && list[0].read && list[1].bookmark
                },
            )
        }
    }

    @Test
    fun `getChapterById returns mapped chapter`() = runTest(testDispatcher) {
        val entity = createChapterEntity(42L, 10L, "Ch 42")
        coEvery { chapterDao.getChapterById(42L) } returns entity

        val chapter = repo.getChapterById(42L)

        assertEquals(42L, chapter?.id)
        assertEquals("Ch 42", chapter?.name)
    }

    @Test
    fun `getChapterById returns null when not found`() = runTest(testDispatcher) {
        coEvery { chapterDao.getChapterById(999L) } returns null

        val chapter = repo.getChapterById(999L)

        assertNull(chapter)
    }

    @Test
    fun `removeChaptersWithIds delegates to dao`() = runTest(testDispatcher) {
        repo.removeChaptersWithIds(listOf(1L, 2L, 3L))
        coVerify(exactly = 1) { chapterDao.removeChaptersWithIds(listOf(1L, 2L, 3L)) }
    }
}
