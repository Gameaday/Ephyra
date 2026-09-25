package ephyra.data.room.target

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.domain.content.model.ContentType
import ephyra.domain.series.LegacyChapterRecord
import ephyra.domain.series.LegacyHistoryRecord
import ephyra.domain.series.LegacySeriesMigrationInput
import ephyra.domain.series.LegacySeriesMigrationMapper
import ephyra.domain.series.LegacySeriesMigrationResult
import ephyra.domain.series.LegacySeriesRecord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TargetMigrationWriterTest {

    private lateinit var database: TargetDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TargetDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `writes target state and rerun is idempotent`() = runBlocking {
        val result = LegacySeriesMigrationMapper.migrate(
            LegacySeriesMigrationInput(
                series = LegacySeriesRecord(
                    legacyId = 10L,
                    sourceId = 7L,
                    url = "https://legacy.example/series/10",
                    title = "Series",
                    author = "Author",
                    artist = "Artist",
                    description = "Description",
                    genres = listOf("Action"),
                    status = 1L,
                    thumbnailUrl = "https://legacy.example/cover.jpg",
                    sourceRevision = 2L,
                    inLibrary = true,
                    dateAdded = 1000L,
                    contentType = ContentType.MANGA,
                ),
                chapters = listOf(
                    LegacyChapterRecord(
                        legacyId = 100L,
                        seriesLegacyId = 10L,
                        url = "https://legacy.example/chapter/100",
                        title = "Chapter 100",
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
                ),
                history = listOf(LegacyHistoryRecord(100L, 1234L, 56L)),
                tracking = listOf(
                    ephyra.domain.series.LegacyTrackingRecord(
                        legacyMangaId = 10L,
                        legacyTrackerId = 3L,
                        remoteId = 99L,
                        libraryId = null,
                        title = "Remote title",
                        lastChapterRead = 12.0,
                        totalChapters = 24L,
                        status = 2L,
                        score = 8.5,
                        remoteUrl = "https://tracker.test/99",
                        startDate = 1000L,
                        finishDate = 0L,
                        isPrivate = true,
                    ),
                ),
            ),
        )
        val plan = (result as LegacySeriesMigrationResult.Migrated).plan
        val writer = TargetMigrationWriter(database)

        writer.write(plan)
        writer.write(
            plan.copy(
                inLibrary = false,
                snapshot = plan.snapshot.copy(title = "Refreshed title"),
                chapters = plan.chapters.map { it.copy(read = false) },
                history = plan.history.map { it.copy(readDurationMillis = 0L) },
            ),
        )

        val dao = database.targetSeriesDao()
        assertEquals("Refreshed title", dao.getSeries(plan.targetLocalId)?.title)
        assertTrue(dao.getSeriesByUrl("legacy:7", "https://legacy.example/series/10") != null)
        assertTrue(dao.getLibraryEntry(plan.targetLocalId) != null)
        assertEquals(1, dao.getChapters(plan.targetLocalId).size)
        assertTrue(dao.getChapterState("legacy-chapter:100")!!.isRead)
        assertEquals(4L, dao.getChapterState("legacy-chapter:100")!!.lastPageRead)
        assertEquals(1234L, dao.getHistory("legacy-chapter:100")!!.lastReadAt)
        assertEquals(56L, dao.getHistory("legacy-chapter:100")!!.readDurationMs)
        val tracking = dao.getTracking(plan.targetLocalId).single()
        assertEquals("legacy-tracker:3", tracking.trackerId)
        assertEquals("99", tracking.remoteId)
        assertEquals("Remote title", tracking.title)
        assertEquals(12.0, tracking.lastChapterRead, 0.0)
        assertTrue(tracking.isPrivate)
    }
}
