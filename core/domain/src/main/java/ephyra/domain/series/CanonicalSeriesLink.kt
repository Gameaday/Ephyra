package ephyra.domain.series

/** Explainable evidence used to review a cross-source identity candidate. */
sealed interface CanonicalLinkEvidence {
    data class ExactExternalIdentity(val sourceLabel: String, val externalId: String) : CanonicalLinkEvidence {
        init {
            require(sourceLabel.isNotBlank()) { "Evidence source label must not be blank" }
            require(externalId.isNotBlank()) { "Evidence external id must not be blank" }
        }
    }

    data class TitleSimilarity(val score: Double) : CanonicalLinkEvidence {
        init {
            require(score in 0.0..1.0) { "Title similarity must be between 0 and 1" }
        }
    }

    data class AuthorMatch(val normalizedAuthor: String) : CanonicalLinkEvidence {
        init {
            require(normalizedAuthor.isNotBlank()) { "Normalized author must not be blank" }
        }
    }

    data class ChapterStructureMatch(val normalizedUnitCount: Int) : CanonicalLinkEvidence {
        init {
            require(normalizedUnitCount >= 0) { "Normalized unit count must not be negative" }
        }
    }

    data class UserSignal(val description: String) : CanonicalLinkEvidence {
        init {
            require(description.isNotBlank()) { "User signal must not be blank" }
        }
    }
}

/**
 * A proposed relationship between an existing canonical series and another source representation.
 * A candidate is never persisted as an active link by this model.
 */
data class CanonicalLinkCandidate(
    val canonicalSeriesId: String,
    val canonicalSourceIdentity: DurableSeriesIdentity,
    val sourceIdentity: DurableSeriesIdentity,
    val confidence: Double,
    val evidence: List<CanonicalLinkEvidence>,
) {
    init {
        require(canonicalSeriesId.isNotBlank()) { "Canonical series id must not be blank" }
        require(canonicalSourceIdentity.sourceId != sourceIdentity.sourceId) {
            "Canonical link must connect representations from different sources"
        }
        require(canonicalSourceIdentity.stableKey != sourceIdentity.stableKey) {
            "Canonical link must connect two different source representations"
        }
        require(confidence in 0.0..1.0) { "Canonical link confidence must be between 0 and 1" }
        require(evidence.isNotEmpty()) { "Canonical link candidate requires evidence" }
    }

    val hasExactExternalIdentity: Boolean
        get() = evidence.any { it is CanonicalLinkEvidence.ExactExternalIdentity }
}

sealed interface CanonicalLinkReview {
    data class RequiresConfirmation(val candidate: CanonicalLinkCandidate) : CanonicalLinkReview
    data class InsufficientConfidence(val candidate: CanonicalLinkCandidate) : CanonicalLinkReview
    data class InvalidEvidence(val reason: String) : CanonicalLinkReview
}

/** Pure review policy. It never approves or persists a link. */
object CanonicalLinkPolicy {
    const val DEFAULT_MINIMUM_CONFIDENCE = 0.70

    fun review(
        candidate: CanonicalLinkCandidate,
        minimumConfidence: Double = DEFAULT_MINIMUM_CONFIDENCE,
    ): CanonicalLinkReview {
        require(minimumConfidence in 0.0..1.0) { "Minimum confidence must be between 0 and 1" }
        if (candidate.evidence.any { it is CanonicalLinkEvidence.TitleSimilarity && it.score < 0.50 }) {
            return CanonicalLinkReview.InvalidEvidence("Weak title similarity is not valid linking evidence")
        }
        return when {
            candidate.confidence < minimumConfidence -> CanonicalLinkReview.InsufficientConfidence(candidate)
            else -> CanonicalLinkReview.RequiresConfirmation(candidate)
        }
    }

    fun rank(candidates: List<CanonicalLinkCandidate>): List<CanonicalLinkCandidate> = candidates.sortedWith(
        compareByDescending<CanonicalLinkCandidate> { it.hasExactExternalIdentity }
            .thenByDescending { it.confidence }
            .thenBy { it.sourceIdentity.sourceId }
            .thenBy { it.sourceIdentity.stableKey },
    )
}

enum class CanonicalLinkState {
    PROPOSED,
    CONFIRMED,
    REJECTED,
    REVOKED,
}

/**
 * Durable lifecycle for a user-reviewed cross-source relationship.
 *
 * Only [CONFIRMED] is active. Confirmation is explicit and cannot be reached automatically.
 * Rejection and revocation are retained as auditable terminal states rather than deleting history.
 */
data class CanonicalSeriesLink(
    val id: String,
    val candidate: CanonicalLinkCandidate,
    val state: CanonicalLinkState = CanonicalLinkState.PROPOSED,
    val revision: Long = 1L,
    val createdAtMillis: Long,
    val decidedAtMillis: Long? = null,
) {
    init {
        require(id.isNotBlank()) { "Canonical link id must not be blank" }
        require(revision > 0L) { "Canonical link revision must be positive" }
        if (state == CanonicalLinkState.CONFIRMED) {
            require(decidedAtMillis != null) { "Confirmed links require a decision timestamp" }
        }
    }

    val isActive: Boolean get() = state == CanonicalLinkState.CONFIRMED

    fun confirm(nowMillis: Long): CanonicalSeriesLink {
        require(state == CanonicalLinkState.PROPOSED) { "Only proposed links can be confirmed" }
        return copy(
            state = CanonicalLinkState.CONFIRMED,
            revision = revision + 1,
            decidedAtMillis = nowMillis,
        )
    }

    fun reject(nowMillis: Long): CanonicalSeriesLink {
        require(state == CanonicalLinkState.PROPOSED) { "Only proposed links can be rejected" }
        return copy(
            state = CanonicalLinkState.REJECTED,
            revision = revision + 1,
            decidedAtMillis = nowMillis,
        )
    }

    fun revoke(nowMillis: Long): CanonicalSeriesLink {
        require(state == CanonicalLinkState.CONFIRMED) { "Only confirmed links can be revoked" }
        return copy(
            state = CanonicalLinkState.REVOKED,
            revision = revision + 1,
            decidedAtMillis = nowMillis,
        )
    }
}
