package ephyra.data.room.target

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.domain.content.model.ContentType
import ephyra.domain.series.CanonicalAggregateIssue
import ephyra.domain.series.CanonicalLinkCandidate
import ephyra.domain.series.CanonicalLinkEvidence
import ephyra.domain.series.CanonicalLinkState
import ephyra.domain.series.DurableSeriesIdentity
import ephyra.domain.series.DurableSeriesSnapshot
import ephyra.domain.series.SeriesUpsertResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TargetCanonicalSeriesReadRepositoryTest {

    private lateinit var database: TargetDatabase
    private lateinit var seriesRepository: TargetSeriesRepository
    private lateinit var linkRepository: TargetSeriesLinkRepository
    private lateinit var readRepository: TargetCanonicalSeriesReadRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            TargetDatabase::class.java,
        ).allowMainThreadQueries().build()
        seriesRepository = TargetSeriesRepository(database, now = { 100L })
        linkRepository = TargetSeriesLinkRepository(database, now = { 200L })
        readRepository = TargetCanonicalSeriesReadRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `confirmed links add representations with their own content type`() = runBlocking {
        val canonical = createSeries("opds", ContentType.MANGA, "Canonical title")
        val linked = createSeries("local", ContentType.NOVEL, "Linked title")
        val link = linkRepository.propose(candidate(canonical, linked))!!
        linkRepository.confirm(link.id, 300L)

        val result = readRepository.get(
            canonical.record.localId,
        ) as ephyra.domain.series.CanonicalSeriesAggregateResult.Found

        assertEquals(2, result.aggregate.representations.size)
        assertEquals(ContentType.MANGA, result.aggregate.representations.first().identity.contentType)
        assertEquals(ContentType.NOVEL, result.aggregate.representations.last().identity.contentType)
        assertEquals("Linked title", result.aggregate.representations.last().displayTitle)
    }

    @Test
    fun `proposed links do not enter the active aggregate`() = runBlocking {
        val canonical = createSeries("opds", ContentType.MANGA, "Canonical title")
        val linked = createSeries("local", ContentType.NOVEL, "Linked title")
        linkRepository.propose(candidate(canonical, linked))

        val result = readRepository.get(
            canonical.record.localId,
        ) as ephyra.domain.series.CanonicalSeriesAggregateResult.Found

        assertEquals(1, result.aggregate.representations.size)
        assertTrue(result.aggregate.issues.isEmpty())
    }

    @Test
    fun `missing source becomes an explicit issue`() = runBlocking {
        val canonical = createSeries("opds", ContentType.MANGA, "Canonical title")
        val linkId = "link:missing"
        database.targetSeriesDao().upsertLink(
            TargetSeriesLinkEntity(
                linkId = linkId,
                canonicalSeriesId = canonical.record.localId,
                canonicalSourceId = "opds",
                canonicalExternalId = "opds-id",
                canonicalUrl = canonical.record.snapshot.identity.url,
                canonicalContentType = ContentType.MANGA.name,
                sourceId = "local",
                externalId = "deleted-id",
                url = "https://local.test/deleted",
                contentType = ContentType.NOVEL.name,
                confidence = 1.0,
                evidenceJson = "[]",
                state = CanonicalLinkState.CONFIRMED.name,
                revision = 1L,
                createdAt = 1L,
                decidedAt = 2L,
            ),
        )

        val result = readRepository.get(
            canonical.record.localId,
        ) as ephyra.domain.series.CanonicalSeriesAggregateResult.Found

        assertEquals(1, result.aggregate.representations.size)
        assertEquals(1, result.aggregate.issues.size)
        assertTrue(result.aggregate.issues.single() is CanonicalAggregateIssue.MissingLinkedSource)
    }

    private suspend fun createSeries(
        sourceId: String,
        contentType: ContentType,
        title: String,
    ): SeriesUpsertResult.Created {
        val identity = DurableSeriesIdentity(sourceId, "$sourceId-id", "https://$sourceId.test/series", contentType)
        return seriesRepository.upsert(
            DurableSeriesSnapshot(identity = identity, title = title, sourceRevision = 1L),
        ) as SeriesUpsertResult.Created
    }

    private fun candidate(
        canonical: SeriesUpsertResult.Created,
        linked: SeriesUpsertResult.Created,
    ) = CanonicalLinkCandidate(
        canonicalSeriesId = canonical.record.localId,
        canonicalSourceIdentity = canonical.record.snapshot.identity,
        sourceIdentity = linked.record.snapshot.identity,
        confidence = 1.0,
        evidence = listOf(
            CanonicalLinkEvidence.ExactExternalIdentity(linked.record.snapshot.identity.sourceId, "linked-id"),
        ),
    )
}
