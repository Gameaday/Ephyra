package ephyra.data.room.target

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.domain.content.model.ContentType
import ephyra.domain.series.DurableSeriesIdentity
import ephyra.domain.series.DurableSeriesSnapshot
import ephyra.domain.series.SeriesRefreshResult
import ephyra.domain.series.SeriesUpsertResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TargetSeriesRepositoryTest {

    private lateinit var database: TargetDatabase
    private lateinit var repository: TargetSeriesRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TargetDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TargetSeriesRepository(database, now = { 100L })
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `upsert creates and resolves by external id`() = runBlocking {
        val snapshot = snapshot(externalId = "stable-1", url = "https://example.test/one", revision = 1)
        val created = repository.upsert(snapshot) as SeriesUpsertResult.Created

        val found = repository.find(DurableSeriesIdentity("opds", "stable-1", "https://example.test/one"))
        assertEquals(created.record.localId, found!!.localId)
        assertEquals("Series", found.snapshot.title)
        assertEquals(false, found.inLibrary)
    }

    @Test
    fun `different source identities do not silently merge`() = runBlocking {
        val first = repository.upsert(
            snapshot(externalId = "stable-1", url = "https://one.test/series"),
        ) as SeriesUpsertResult.Created
        val second = repository.upsert(
            DurableSeriesSnapshot(
                identity = DurableSeriesIdentity(
                    sourceId = "other",
                    externalId = "stable-2",
                    url = "https://two.test/series",
                    contentType = ContentType.MANGA,
                ),
                title = "Series",
                sourceRevision = 1,
            ),
        ) as SeriesUpsertResult.Created

        assertNotEquals(first.record.localId, second.record.localId)
    }

    @Test
    fun `same revision and same snapshot is unchanged`() = runBlocking {
        val snapshot = snapshot(externalId = "stable-1", revision = 1)
        repository.upsert(snapshot)

        val result = repository.upsert(snapshot)
        assertTrue(result is SeriesUpsertResult.Unchanged)
    }

    @Test
    fun `newer revision refreshes source metadata without changing membership`() = runBlocking {
        val created = repository.upsert(snapshot(externalId = "stable-1", revision = 1)) as SeriesUpsertResult.Created
        repository.setLibraryMembership(created.record.localId, true)

        val result = repository.upsert(
            snapshot(
                externalId = "stable-1",
                url = "https://example.test/moved",
                title = "Updated",
                revision = 2,
            ),
        )

        assertTrue(result is SeriesUpsertResult.Updated)
        val record = (result as SeriesUpsertResult.Updated).record
        assertEquals("Updated", record.snapshot.title)
        assertEquals("https://example.test/moved", record.snapshot.identity.url)
        assertTrue(record.inLibrary)
    }

    @Test
    fun `stale revision is a conflict`() = runBlocking {
        val created = repository.upsert(snapshot(externalId = "stable-1", revision = 2)) as SeriesUpsertResult.Created

        val result = repository.refresh(
            created.record.snapshot.identity,
            snapshot(externalId = "stable-1", title = "Stale", revision = 1),
        )

        assertTrue(result is SeriesRefreshResult.Conflict)
        assertNotEquals("Stale", created.record.snapshot.title)
    }

    @Test
    fun `same revision metadata change is a conflict`() = runBlocking {
        val identity = DurableSeriesIdentity("opds", "stable-1", "https://example.test/one")
        repository.upsert(snapshot(externalId = "stable-1", revision = 1))

        val result = repository.refresh(identity, snapshot(externalId = "stable-1", title = "Changed", revision = 1))

        assertTrue(result is SeriesRefreshResult.Conflict)
    }

    @Test
    fun `library membership is explicit and reversible`() = runBlocking {
        val created = repository.upsert(snapshot(externalId = "stable-1", revision = 1)) as SeriesUpsertResult.Created
        val localId = created.record.localId

        assertTrue(repository.setLibraryMembership(localId, true)!!.inLibrary)
        assertTrue(!repository.setLibraryMembership(localId, false)!!.inLibrary)
        assertTrue(!repository.find(created.record.snapshot.identity)!!.inLibrary)
    }

    private fun snapshot(
        externalId: String? = null,
        url: String = "https://example.test/one",
        title: String = "Series",
        revision: Long = 1,
    ) = DurableSeriesSnapshot(
        identity = DurableSeriesIdentity("opds", externalId, url, ContentType.MANGA),
        title = title,
        author = "Author",
        genres = listOf("Action"),
        sourceRevision = revision,
    )
}
