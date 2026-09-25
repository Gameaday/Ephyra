package ephyra.data.room.target

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.domain.content.model.ContentType
import ephyra.domain.series.DurableSeriesIdentity
import ephyra.domain.series.DurableSeriesSnapshot
import ephyra.domain.series.SeriesUpsertResult
import ephyra.domain.track.TargetTrackingRecord
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
class TargetTrackingRepositoryTest {
    private lateinit var database: TargetDatabase
    private lateinit var seriesRepository: TargetSeriesRepository
    private lateinit var trackingRepository: TargetTrackingRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            TargetDatabase::class.java,
        ).allowMainThreadQueries().build()
        seriesRepository = TargetSeriesRepository(database)
        trackingRepository = TargetTrackingRepositoryImpl(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `tracking requires an existing series`() = runBlocking {
        val failure = runCatching { trackingRepository.upsert(tracking("missing-series", "tracker:anilist")) }
            .exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `tracking upsert is idempotent per series and tracker`() = runBlocking {
        val seriesId = createSeriesId()
        val first = tracking(seriesId, "tracker:anilist", title = "First", score = 5.0)
        val updated = first.copy(title = "Updated", score = 9.0, updatedAt = 200L)
        assertEquals(first, trackingRepository.upsert(first))
        assertEquals(updated, trackingRepository.upsert(updated))
        assertEquals(listOf(updated), trackingRepository.get(seriesId))
    }

    @Test
    fun `tracking delete is isolated by tracker`() = runBlocking {
        val seriesId = createSeriesId()
        val first = tracking(seriesId, "tracker:anilist")
        val second = tracking(seriesId, "tracker:mal", remoteId = "77")
        trackingRepository.upsert(first)
        trackingRepository.upsert(second)
        trackingRepository.delete(seriesId, "tracker:anilist")
        assertEquals(listOf(second), trackingRepository.get(seriesId))
    }

    private suspend fun createSeriesId(): String {
        val snapshot = DurableSeriesSnapshot(
            identity = DurableSeriesIdentity("opds", "tracking-series", "https://example.test/one", ContentType.MANGA),
            title = "Series",
            sourceRevision = 1,
        )
        return (seriesRepository.upsert(snapshot) as SeriesUpsertResult.Created).record.localId
    }

    private fun tracking(
        seriesId: String,
        trackerId: String,
        title: String = "Remote title",
        score: Double = 8.0,
        remoteId: String = "99",
    ) = TargetTrackingRecord(
        seriesId = seriesId,
        trackerId = trackerId,
        remoteId = remoteId,
        libraryId = "library-1",
        title = title,
        lastChapterRead = 12.0,
        totalChapters = 24L,
        status = "reading",
        score = score,
        remoteUrl = "https://tracker.test/$remoteId",
        startedAt = 1_000L,
        finishedAt = 0L,
        isPrivate = true,
        updatedAt = 100L,
    )
}
