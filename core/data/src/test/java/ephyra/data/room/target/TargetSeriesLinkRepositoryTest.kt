package ephyra.data.room.target

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.domain.content.model.ContentType
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TargetSeriesLinkRepositoryTest {

    private lateinit var database: TargetDatabase
    private lateinit var seriesRepository: TargetSeriesRepository
    private lateinit var linkRepository: TargetSeriesLinkRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TargetDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seriesRepository = TargetSeriesRepository(database, now = { 100L })
        linkRepository = TargetSeriesLinkRepository(database, now = { 200L })
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `proposal is idempotent and preserves evidence and content types`() = runBlocking {
        val canonical = createSeries("canonical", "https://canonical.test/series", ContentType.MANGA)
        val linked = createSeries("linked", "https://linked.test/series", ContentType.NOVEL)
        val candidate = candidate(canonical, linked)

        val first = linkRepository.propose(candidate)
        val second = linkRepository.propose(candidate)

        assertNotNull(first)
        assertEquals(first, second)
        assertEquals(CanonicalLinkState.PROPOSED, first!!.state)
        assertEquals(ContentType.MANGA, first.candidate.canonicalSourceIdentity.contentType)
        assertEquals(ContentType.NOVEL, first.candidate.sourceIdentity.contentType)
        assertTrue(first.candidate.evidence.any { it is CanonicalLinkEvidence.ExactExternalIdentity })
    }

    @Test
    fun `proposal requires both source representations to exist`() = runBlocking {
        val canonical = createSeries("canonical", "https://canonical.test/series", ContentType.MANGA)
        val missing = identity("missing", "https://missing.test/series", ContentType.MANGA)

        assertNull(linkRepository.propose(candidate(canonical, missing)))
    }

    @Test
    fun `link lifecycle persists confirmation rejection and revocation`() = runBlocking {
        val canonical = createSeries("canonical", "https://canonical.test/series", ContentType.MANGA)
        val linked = createSeries("linked", "https://linked.test/series", ContentType.MANGA)
        val proposed = linkRepository.propose(candidate(canonical, linked))!!

        val confirmed = linkRepository.confirm(proposed.id, 300L)!!
        assertEquals(CanonicalLinkState.CONFIRMED, confirmed.state)
        assertEquals(2L, confirmed.revision)
        assertTrue(confirmed.isActive)

        val revoked = linkRepository.revoke(proposed.id, 400L)!!
        assertEquals(CanonicalLinkState.REVOKED, revoked.state)
        assertEquals(3L, revoked.revision)

        val otherLinked = createSeries("other", "https://other.test/series", ContentType.MANGA)
        val rejectedProposal = linkRepository.propose(candidate(canonical, otherLinked))!!
        val rejected = linkRepository.reject(rejectedProposal.id, 500L)!!
        assertEquals(CanonicalLinkState.REJECTED, rejected.state)
        assertEquals(2L, rejected.revision)
    }

    @Test
    fun `missing link returns null`() = runBlocking {
        assertNull(linkRepository.find("missing"))
        assertNull(linkRepository.confirm("missing", 100L))
    }

    private suspend fun createSeries(
        sourceId: String,
        url: String,
        contentType: ContentType,
    ): DurableSeriesIdentity {
        val identity = identity(sourceId, url, contentType)
        seriesRepository.upsert(
            DurableSeriesSnapshot(
                identity = identity,
                title = "Series",
                sourceRevision = 1,
            ),
        ) as SeriesUpsertResult.Created
        return identity
    }

    private suspend fun candidate(
        canonical: DurableSeriesIdentity,
        linked: DurableSeriesIdentity,
    ) = CanonicalLinkCandidate(
        canonicalSeriesId = database.targetSeriesDao().getSeriesByExternalId(
            canonical.sourceId,
            canonical.externalId!!,
        )!!.seriesId,
        canonicalSourceIdentity = canonical,
        sourceIdentity = linked,
        confidence = 0.9,
        evidence = listOf(
            CanonicalLinkEvidence.ExactExternalIdentity(linked.sourceId, linked.externalId!!),
            CanonicalLinkEvidence.TitleSimilarity(0.95),
        ),
    )

    private fun identity(sourceId: String, url: String, contentType: ContentType) = DurableSeriesIdentity(
        sourceId = sourceId,
        externalId = "$sourceId-id",
        url = url,
        contentType = contentType,
    )
}
