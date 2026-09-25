package ephyra.data.room.target

import ephyra.data.room.entities.ChapterEntity
import ephyra.data.room.entities.HistoryEntity
import ephyra.data.room.entities.MangaEntity
import ephyra.domain.series.LegacySeriesMigrationMapper
import ephyra.domain.series.LegacySeriesMigrationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class LegacyRoomMigrationAdapterTest {

    @Test
    fun `maps current Room entities into a reversible target migration plan`() {
        val manga = MangaEntity(
            id = 12L,
            source = 7L,
            url = "/series/12",
            artist = "Artist",
            author = "Author",
            description = "Description",
            genre = listOf("Action"),
            title = "Series",
            status = 1L,
            thumbnailUrl = "/cover.jpg",
            favorite = true,
            lastUpdate = 0L,
            nextUpdate = 0L,
            initialized = true,
            viewerFlags = 4L,
            chapterFlags = 8L,
            coverLastModified = 0L,
            dateAdded = 100L,
            updateStrategy = 0,
            calculateInterval = 0,
            lastModifiedAt = 200L,
            favoriteModifiedAt = null,
            version = 2L,
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
        val chapter = ChapterEntity(
            id = 120L,
            mangaId = 12L,
            url = "/series/12/chapter/1",
            name = "Chapter 1",
            scanlator = null,
            read = true,
            bookmark = true,
            lastPageRead = 3,
            chapterNumber = 1.0,
            sourceOrder = 0,
            dateFetch = 10L,
            dateUpload = 20L,
            lastModifiedAt = 30L,
            version = 1L,
            isSyncing = false,
        )
        val history = HistoryEntity(id = 1L, chapterId = 120L, lastRead = Date(1234L), timeRead = 60L)

        val input = LegacyRoomMigrationAdapter.toMigrationInput(manga, listOf(chapter), listOf(history))
        val result = LegacySeriesMigrationMapper.migrate(input) as LegacySeriesMigrationResult.Migrated
        val plan = result.plan

        assertEquals("legacy:7", plan.sourceId)
        assertEquals("/series/12", plan.snapshot.identity.url)
        assertTrue(plan.inLibrary)
        assertTrue(plan.chapters.single().read)
        assertTrue(plan.chapters.single().bookmark)
        assertEquals(3L, plan.chapters.single().lastPageRead)
        assertEquals(1234L, plan.history.single().lastReadAtMillis)
        assertEquals(60L, plan.history.single().readDurationMillis)
    }
}
