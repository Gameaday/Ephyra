package ephyra.data.room.target

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.domain.content.model.ContentType
import ephyra.domain.series.LegacyCategoryRecord
import ephyra.domain.series.LegacyChapterRecord
import ephyra.domain.series.LegacyHistoryRecord
import ephyra.domain.series.LegacySeriesMigrationInput
import ephyra.domain.series.LegacySeriesMigrationMapper
import ephyra.domain.series.LegacySeriesMigrationResult
import ephyra.domain.series.LegacySeriesRecord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * DATA-001I rehearsal fixture. This is intentionally an isolated target database test, not a
 * production cutover or a claim that unsupported legacy capabilities are migrated.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TargetMigrationRehearsalTest {

    private lateinit var database: TargetDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            TargetDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `multi series source rehearsal preserves user state without title merging`() = runBlocking {
        val first = plan(
            seriesId = 10L,
            sourceId = 7L,
            title = "Same Title",
            favorite = true,
            categories = listOf(LegacyCategoryRecord(0L, "Default", -1L, 3L, isSystem = true)),
            seriesCategoryIds = listOf(0L),
        )
        val second = plan(seriesId = 11L, sourceId = 8L, title = "Same Title", favorite = false)
        val writer = TargetMigrationWriter(database)

        writer.write(first)
        writer.write(second)
        writer.write(first)
        writer.write(second)

        val dao = database.targetSeriesDao()
        assertNotEquals(first.targetLocalId, second.targetLocalId)
        assertEquals(1, dao.getSourceReferences(first.targetLocalId).size)
        assertEquals(1, dao.getSourceReferences(second.targetLocalId).size)
        assertTrue(dao.getLibraryEntry(first.targetLocalId) != null)
        assertTrue(dao.getLibraryEntry(second.targetLocalId) == null)
        assertEquals(1, dao.getCategories().size)
        assertEquals("Default", dao.getCategories().single().name)
        assertEquals(listOf("legacy-category:0"), dao.getSeriesCategories(first.targetLocalId).map { it.categoryId })
        assertEquals(2, dao.getChapters(first.targetLocalId).size)
        assertEquals(2, dao.getChapters(second.targetLocalId).size)
        assertTrue(dao.getChapterState("legacy-chapter:100")!!.isRead)
        assertEquals(4L, dao.getChapterState("legacy-chapter:100")!!.lastPageRead)
        assertEquals(1234L, dao.getHistory("legacy-chapter:100")!!.lastReadAt)
    }

    private fun plan(
        seriesId: Long,
        sourceId: Long,
        title: String,
        favorite: Boolean,
        categories: List<LegacyCategoryRecord> = emptyList(),
        seriesCategoryIds: List<Long> = emptyList(),
    ) =
        (
            LegacySeriesMigrationMapper.migrate(
                LegacySeriesMigrationInput(
                    series = LegacySeriesRecord(
                        legacyId = seriesId,
                        sourceId = sourceId,
                        url = "https://legacy.example/series/$seriesId",
                        title = title,
                        author = "Author",
                        artist = "Artist",
                        description = "Description",
                        genres = listOf("Action"),
                        status = 1L,
                        thumbnailUrl = "https://legacy.example/cover-$seriesId.jpg",
                        sourceRevision = 2L,
                        inLibrary = favorite,
                        dateAdded = 1000L,
                        contentType = ContentType.MANGA,
                    ),
                    chapters = listOf(
                        LegacyChapterRecord(
                            legacyId = seriesId * 10,
                            seriesLegacyId = seriesId,
                            url = "https://legacy.example/series/$seriesId/chapter/1",
                            title = "Chapter 1",
                            scanlator = null,
                            chapterNumber = 1.0,
                            sourceOrder = 0L,
                            read = true,
                            bookmark = true,
                            lastPageRead = 4L,
                            dateFetch = 10L,
                            dateUpload = 20L,
                            lastModifiedAt = 30L,
                            revision = 1L,
                        ),
                        LegacyChapterRecord(
                            legacyId = seriesId * 10 + 1,
                            seriesLegacyId = seriesId,
                            url = "https://legacy.example/series/$seriesId/chapter/2",
                            title = "Chapter 2",
                            scanlator = null,
                            chapterNumber = 2.0,
                            sourceOrder = 1L,
                            read = false,
                            bookmark = false,
                            lastPageRead = 0L,
                            dateFetch = 11L,
                            dateUpload = 21L,
                            lastModifiedAt = 31L,
                            revision = 1L,
                        ),
                    ),
                    history = listOf(LegacyHistoryRecord(seriesId * 10, 1234L, 56L)),
                    categories = categories,
                    seriesCategoryIds = seriesCategoryIds,
                ),
            ) as LegacySeriesMigrationResult.Migrated
            ).plan
}
