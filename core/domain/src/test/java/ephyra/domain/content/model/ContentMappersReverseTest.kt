package ephyra.domain.content.model

import ephyra.domain.chapter.model.Chapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Continuation of [ContentMappersTest] covering the reverse and unit mappings:
 * [ContentItem] to [MangaUpdate], [Chapter] to [ContentUnit], and
 * [ContentUnit] to [ChapterUpdate].
 */
class ContentMappersReverseTest {

    // ── ContentItem → MangaUpdate ────────────────────────────────────────

    @Test
    fun `toMangaUpdate maps core fields`() {
        val update = item().toMangaUpdate()

        assertEquals(42L, update.id)
        assertEquals(7L, update.source)
        assertEquals("/manga/42", update.url)
        assertEquals("One Piece", update.title)
        assertEquals("Eiichiro Oda", update.author)
        assertEquals("Oda", update.artist)
        assertEquals(listOf("Action", "Adventure"), update.genre)
        assertEquals(1L, update.status)
        assertEquals("https://img/op.jpg", update.thumbnailUrl)
        assertEquals(true, update.favorite)
        assertEquals(1000L, update.dateAdded)
        assertEquals(2000L, update.lastUpdate)
        assertEquals(true, update.initialized)
        assertEquals(ContentType.MANGA, update.contentType)
    }

    @Test
    fun `toMangaUpdate maps Completed ContentStatus to legacy value 2`() {
        val item = item().copy(status = ContentStatus.Completed)
        assertEquals(2L, item.toMangaUpdate().status)
    }

    @Test
    fun `toMangaUpdate maps Licensed ContentStatus to legacy value 4`() {
        val item = item().copy(status = ContentStatus.Licensed)
        assertEquals(4L, item.toMangaUpdate().status)
    }

    @Test
    fun `toMangaUpdate maps Cancelled ContentStatus to legacy value 5`() {
        val item = item().copy(status = ContentStatus.Cancelled)
        assertEquals(5L, item.toMangaUpdate().status)
    }

    @Test
    fun `toMangaUpdate maps Hiatus ContentStatus to legacy value 6`() {
        val item = item().copy(status = ContentStatus.Hiatus)
        assertEquals(6L, item.toMangaUpdate().status)
    }

    @Test
    fun `toMangaUpdate maps unknown ContentStatus to 0`() {
        val item = item().copy(status = ContentStatus.Unknown)
        assertEquals(0L, item.toMangaUpdate().status)
    }

    // ── Chapter → ContentUnit ────────────────────────────────────────────

    @Test
    fun `toContentUnit maps chapter fields and progress`() {
        val chapter = Chapter(
            id = 9L,
            mangaId = 42L,
            read = true,
            bookmark = false,
            lastPageRead = 10L,
            dateFetch = 1L,
            sourceOrder = 0L,
            url = "/ch/9",
            name = "Chapter 9",
            dateUpload = 500L,
            chapterNumber = 9.0,
            scanlator = "Group X",
            lastModifiedAt = 700L,
            version = 1L,
        )

        val unit = chapter.toContentUnit(progress = 12L, totalLength = 20L)

        assertEquals(9L, unit.id)
        assertEquals(42L, unit.contentItemId)
        assertEquals("/ch/9", unit.url)
        assertEquals("Chapter 9", unit.title)
        assertEquals(9.0, unit.number)
        assertEquals(500L, unit.dateUpload)
        assertEquals(12L, unit.progress)
        assertEquals(20L, unit.totalLength)
        assertEquals(700L, unit.lastRead)
        assertEquals(true, unit.read)
        assertFalse(unit.bookmark)
        assertEquals("Group X", unit.scanlator)
    }

    @Test
    fun `toContentUnit falls back to lastPageRead when progress is zero`() {
        val chapter = Chapter.create().copy(
            mangaId = 42L,
            lastPageRead = 15L,
        )

        assertEquals(15L, chapter.toContentUnit().progress)
    }

    @Test
    fun `toContentUnit preserves explicit progress over lastPageRead`() {
        val chapter = Chapter.create().copy(
            mangaId = 42L,
            lastPageRead = 15L,
        )
        assertEquals(30L, chapter.toContentUnit(progress = 30L).progress)
    }

    @Test
    fun `progressRatio computes completion fraction`() {
        val chapter = Chapter.create().copy(mangaId = 1L)
        val unit = chapter.toContentUnit(progress = 5L, totalLength = 20L)
        assertEquals(0.25f, unit.progressRatio)
    }

    @Test
    fun `hasStarted is true when progress is non zero and not read`() {
        val chapter = Chapter.create().copy(mangaId = 1L)
        val unit = chapter.toContentUnit(progress = 3L, totalLength = 10L)
        assertTrue(unit.hasStarted)
    }

    // ── ContentUnit → ChapterUpdate ──────────────────────────────────────

    @Test
    fun `toChapterUpdate maps unit fields`() {
        val unit = ContentUnit(
            id = 9L,
            contentItemId = 42L,
            url = "/ch/9",
            title = "Chapter 9",
            number = 9.0,
            dateUpload = 500L,
            progress = 12L,
            totalLength = 20L,
            lastRead = 700L,
            read = true,
            bookmark = true,
            scanlator = "Group X",
        )

        val update = unit.toChapterUpdate()

        assertEquals(9L, update.id)
        assertEquals(true, update.read)
        assertEquals(true, update.bookmark)
        assertEquals(12L, update.lastPageRead)
    }

    private fun item() = ContentItem(
        id = 42L,
        sourceId = 7L,
        url = "/manga/42",
        title = "One Piece",
        author = "Eiichiro Oda",
        artist = "Oda",
        description = "Sea adventure",
        genres = listOf("Action", "Adventure"),
        status = ContentStatus.Ongoing,
        thumbnailUrl = "https://img/op.jpg",
        favorite = true,
        dateAdded = 1000L,
        lastUpdate = 2000L,
        initialized = true,
        contentType = ContentType.MANGA,
    )
}