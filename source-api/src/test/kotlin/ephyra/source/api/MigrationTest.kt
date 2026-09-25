package ephyra.source.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MigrationTest {
    @Test
    fun `candidate requires explicit confirmation even at high confidence`() {
        val candidate = candidate(confidence = 0.95)

        val review = MigrationPolicy.review(candidate, targetSupportsMigration = true)

        assertTrue(review is MigrationReview.RequiresConfirmation)
    }

    @Test
    fun `review rejects low confidence and unsupported target migration`() {
        val candidate = candidate(confidence = 0.4)

        assertTrue(
            MigrationPolicy.review(candidate, targetSupportsMigration = true) is MigrationReview.InsufficientConfidence,
        )
        assertTrue(
            MigrationPolicy.review(
                candidate.copy(confidence = 0.95),
                targetSupportsMigration = false,
            ) is MigrationReview.TargetCannotMigrate,
        )
    }

    @Test
    fun `ranking is deterministic and confidence first`() {
        val low = candidate(confidence = 0.7, currentId = "a", targetId = "z")
        val high = candidate(confidence = 0.9, currentId = "b", targetId = "y")
        val tied = candidate(confidence = 0.7, currentId = "a", targetId = "b")

        assertEquals(
            listOf(high, tied, low),
            MigrationPolicy.rank(listOf(tied, low, high)),
        )
    }

    @Test
    fun `transaction requires confirmation before apply and applied before rollback`() {
        val draft = MigrationTransaction("tx-1", candidate())
        val confirmed = draft.confirm()
        val applied = confirmed.apply()
        val rolledBack = applied.rollback()

        assertEquals(MigrationTransactionState.ROLLED_BACK, rolledBack.state)
        assertThrowsIllegalArgument { draft.apply() }
        assertThrowsIllegalArgument { draft.rollback() }
    }

    @Test
    fun `candidate identity and evidence are validated`() {
        assertThrowsIllegalArgument { candidate(currentId = "same", targetId = "same") }
        assertThrowsIllegalArgument { candidate(confidence = 1.1) }
        assertThrowsIllegalArgument { candidate(evidence = listOf(MigrationEvidence.UserSignal(" "))) }
    }

    private fun candidate(
        confidence: Double = 0.8,
        currentId: String = "source-a",
        targetId: String = "source-b",
        evidence: List<MigrationEvidence> = listOf(MigrationEvidence.TitleSimilarity(0.8)),
    ): MigrationCandidate = MigrationCandidate(
        current = SourceContentItem(SourceId(currentId), url = "/current", title = "Current"),
        target = SourceContentItem(SourceId(targetId), url = "/target", title = "Current"),
        confidence = confidence,
        evidence = evidence,
    )

    private inline fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
