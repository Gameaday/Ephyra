package ephyra.domain.series

import ephyra.domain.content.model.ContentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CanonicalSeriesLinkTest {

    @Test
    fun `high confidence still requires explicit confirmation`() {
        val candidate = candidate(confidence = 0.95)
        assertTrue(CanonicalLinkPolicy.review(candidate) is CanonicalLinkReview.RequiresConfirmation)
    }

    @Test
    fun `low confidence is not presented for confirmation`() {
        val candidate = candidate(confidence = 0.60, evidence = listOf(authorMatch()))
        assertTrue(CanonicalLinkPolicy.review(candidate) is CanonicalLinkReview.InsufficientConfidence)
    }

    @Test
    fun `weak title evidence is rejected`() {
        val candidate = candidate(
            confidence = 0.99,
            evidence = listOf(CanonicalLinkEvidence.TitleSimilarity(0.25)),
        )
        assertTrue(CanonicalLinkPolicy.review(candidate) is CanonicalLinkReview.InvalidEvidence)
    }

    @Test
    fun `exact identity ranks ahead of higher fuzzy confidence`() {
        val exact = candidate(confidence = 0.75, evidence = listOf(exactIdentity()))
        val fuzzy = candidate(confidence = 0.95, sourceId = "fuzzy")
        assertEquals(listOf(exact, fuzzy), CanonicalLinkPolicy.rank(listOf(fuzzy, exact)))
    }

    @Test
    fun `same source representations cannot form a cross-source link`() {
        val canonical = identity("one")
        assertThrows<IllegalArgumentException> {
            CanonicalLinkCandidate(
                canonicalSeriesId = "series:1",
                canonicalSourceIdentity = canonical,
                sourceIdentity = identity("one", externalId = "other-id"),
                confidence = 1.0,
                evidence = listOf(exactIdentity()),
            )
        }
    }

    @Test
    fun `self links are rejected`() {
        val identity = identity("one")
        assertThrows<IllegalArgumentException> {
            CanonicalLinkCandidate(
                canonicalSeriesId = "series:1",
                canonicalSourceIdentity = identity,
                sourceIdentity = identity,
                confidence = 1.0,
                evidence = listOf(exactIdentity()),
            )
        }
    }

    @Test
    fun `confirmation is explicit and active`() {
        val link = proposedLink().confirm(nowMillis = 200L)
        assertTrue(link.isActive)
        assertEquals(CanonicalLinkState.CONFIRMED, link.state)
        assertEquals(2L, link.revision)
        assertEquals(200L, link.decidedAtMillis)
    }

    @Test
    fun `rejection is auditable and terminal`() {
        val rejected = proposedLink().reject(nowMillis = 200L)
        assertFalse(rejected.isActive)
        assertEquals(CanonicalLinkState.REJECTED, rejected.state)
        assertThrows<IllegalArgumentException> { rejected.confirm(300L) }
        assertThrows<IllegalArgumentException> { rejected.revoke(300L) }
    }

    @Test
    fun `confirmed links can be revoked but not confirmed twice`() {
        val confirmed = proposedLink().confirm(200L)
        val revoked = confirmed.revoke(300L)
        assertEquals(CanonicalLinkState.REVOKED, revoked.state)
        assertEquals(3L, revoked.revision)
        assertThrows<IllegalArgumentException> { confirmed.confirm(400L) }
        assertThrows<IllegalArgumentException> { revoked.revoke(400L) }
    }

    private fun proposedLink() = CanonicalSeriesLink(
        id = "link:1",
        candidate = candidate(),
        createdAtMillis = 100L,
    )

    private fun candidate(
        confidence: Double = 0.9,
        sourceId: String = "two",
        evidence: List<CanonicalLinkEvidence> = listOf(authorMatch()),
    ) = CanonicalLinkCandidate(
        canonicalSeriesId = "series:1",
        canonicalSourceIdentity = identity("one"),
        sourceIdentity = identity(sourceId),
        confidence = confidence,
        evidence = evidence,
    )

    private fun identity(sourceId: String, externalId: String = "$sourceId-id") = DurableSeriesIdentity(
        sourceId = sourceId,
        externalId = externalId,
        url = "https://$sourceId.test/series",
        contentType = ContentType.MANGA,
    )

    private fun exactIdentity() = CanonicalLinkEvidence.ExactExternalIdentity("two", "two-id")
    private fun authorMatch() = CanonicalLinkEvidence.AuthorMatch("author")
}
