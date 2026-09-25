package ephyra.domain.series

import ephyra.domain.content.model.ContentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegacySeriesMigrationTest {

    @Test
    fun `migrates library, chapters, and history without merging by title`() {
        val result = LegacySeriesMigrationMapper.migrate(
            LegacySeriesMigrationInput(
                series = series(id = 10L, title = "Same Title", favorite = true),
                chapters = listOf(chapter(id = 100L, seriesId = 10L, read = true)),
                history = listOf(LegacyHistoryRecord(100L, 1234L, 56L)),
            ),
        )

        val plan = assertInstanceOf(LegacySeriesMigrationResult.Migrated::class.java, result).plan
        assertEquals("legacy-series:10", plan.targetLocalId)
        assertEquals("legacy:7", plan.sourceId)
        assertTrue(plan.inLibrary)
        assertEquals("Same Title", plan.snapshot.title)
        assertEquals("legacy-chapter:100", plan.chapters.single().targetLocalId)
        assertTrue(plan.chapters.single().read)
        assertEquals("legacy-chapter:100", plan.history.single().targetChapterLocalId)
        assertEquals(56L, plan.history.single().readDurationMillis)
    }

    @Test
    fun `source id and url are preserved in target identity`() {
        val result = LegacySeriesMigrationMapper.migrate(
            LegacySeriesMigrationInput(series(id = 11L), chapters = emptyList(), history = emptyList()),
        )

        val plan = assertInstanceOf(LegacySeriesMigrationResult.Migrated::class.java, result).plan
        assertEquals("legacy:7", plan.snapshot.identity.sourceId)
        assertEquals("https://legacy.example/series/11", plan.snapshot.identity.url)
    }

    @Test
    fun `invalid legacy identity is unresolved rather than guessed`() {
        val result = LegacySeriesMigrationMapper.migrate(
            LegacySeriesMigrationInput(
                series = series(id = 0L, title = "No ID"),
                chapters = emptyList(),
                history = emptyList(),
            ),
        )

        assertInstanceOf(LegacySeriesMigrationResult.Unresolved::class.java, result)
    }

    @Test
    fun `history for unknown chapter is not attached to another chapter`() {
        val result = LegacySeriesMigrationMapper.migrate(
            LegacySeriesMigrationInput(
                series = series(id = 12L),
                chapters = listOf(chapter(id = 120L, seriesId = 12L)),
                history = listOf(LegacyHistoryRecord(999L, 1L, 2L)),
            ),
        )

        val plan = assertInstanceOf(LegacySeriesMigrationResult.Migrated::class.java, result).plan
        assertTrue(plan.history.isEmpty())
    }

    private fun series(id: Long, title: String = "Series", favorite: Boolean = false) = LegacySeriesRecord(
        legacyId = id,
        sourceId = 7L,
        url = "https://legacy.example/series/$id",
        title = title,
        author = "Author",
        artist = "Artist",
        description = "Description",
        genres = listOf("Action"),
        status = 1L,
        thumbnailUrl = "https://legacy.example/cover.jpg",
        sourceRevision = 2L,
        inLibrary = favorite,
        dateAdded = 1000L,
        contentType = ContentType.MANGA,
    )

    private fun chapter(id: Long, seriesId: Long, read: Boolean = false) = LegacyChapterRecord(
        legacyId = id,
        seriesLegacyId = seriesId,
        url = "https://legacy.example/chapter/$id",
        title = "Chapter $id",
        scanlator = null,
        chapterNumber = 1.0,
        sourceOrder = 0L,
        read = read,
        bookmark = true,
        lastPageRead = 4L,
        dateFetch = 10L,
        dateUpload = 20L,
        lastModifiedAt = 30L,
        revision = 1L,
    )
}
