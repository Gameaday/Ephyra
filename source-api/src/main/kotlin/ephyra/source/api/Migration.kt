package ephyra.source.api

/** A source item participating in a proposed migration. */
data class MigrationCandidate(
    val current: SourceContentItem,
    val target: SourceContentItem,
    val confidence: Double,
    val evidence: List<MigrationEvidence> = emptyList(),
    val discoveredAtMillis: Long? = null,
) {
    init {
        require(current.sourceId != target.sourceId) { "Migration must change source" }
        require(confidence in 0.0..1.0) { "Migration confidence must be between 0 and 1" }
    }
}

/** Explainable evidence supporting a migration candidate. */
sealed interface MigrationEvidence {
    data class ExactIdentity(val identity: String) : MigrationEvidence
    data class TitleSimilarity(val score: Double) : MigrationEvidence {
        init {
            require(score in 0.0..1.0) { "Title similarity must be between 0 and 1" }
        }
    }
    data class AuthorMatch(val author: String) : MigrationEvidence
    data class ChapterAvailability(val source: SourceId, val count: Int?) : MigrationEvidence
    data class UserSignal(val description: String) : MigrationEvidence {
        init {
            require(description.isNotBlank()) { "User signal must not be blank" }
        }
    }
}

/** Result of evaluating a candidate. Migration is never automatically approved. */
sealed interface MigrationReview {
    data class RequiresConfirmation(val candidate: MigrationCandidate) : MigrationReview
    data class InsufficientConfidence(val candidate: MigrationCandidate) : MigrationReview
    data class TargetCannotMigrate(val candidate: MigrationCandidate) : MigrationReview
}

object MigrationPolicy {
    const val DEFAULT_MINIMUM_CONFIDENCE = 0.65

    fun review(
        candidate: MigrationCandidate,
        targetSupportsMigration: Boolean,
        minimumConfidence: Double = DEFAULT_MINIMUM_CONFIDENCE,
    ): MigrationReview {
        require(minimumConfidence in 0.0..1.0) { "Minimum confidence must be between 0 and 1" }
        return when {
            !targetSupportsMigration -> MigrationReview.TargetCannotMigrate(candidate)
            candidate.confidence < minimumConfidence -> MigrationReview.InsufficientConfidence(candidate)
            else -> MigrationReview.RequiresConfirmation(candidate)
        }
    }

    fun rank(candidates: List<MigrationCandidate>): List<MigrationCandidate> = candidates
        .sortedWith(
            compareByDescending<MigrationCandidate> { it.confidence }
                .thenBy { it.current.sourceId.value }
                .thenBy { it.target.sourceId.value }
                .thenBy { it.current.url }
                .thenBy { it.target.url },
        )
}

/** A pure transaction record; persistence/UI layers own execution and storage. */
enum class MigrationTransactionState {
    DRAFT,
    CONFIRMED,
    APPLIED,
    ROLLED_BACK,
}

data class MigrationTransaction(
    val id: String,
    val candidate: MigrationCandidate,
    val state: MigrationTransactionState = MigrationTransactionState.DRAFT,
) {
    init {
        require(id.isNotBlank()) { "Migration transaction id must not be blank" }
    }

    fun confirm(): MigrationTransaction {
        require(state == MigrationTransactionState.DRAFT) { "Only draft migrations can be confirmed" }
        return copy(state = MigrationTransactionState.CONFIRMED)
    }

    fun apply(): MigrationTransaction {
        require(state == MigrationTransactionState.CONFIRMED) { "Migration must be confirmed before apply" }
        return copy(state = MigrationTransactionState.APPLIED)
    }

    fun rollback(): MigrationTransaction {
        require(state == MigrationTransactionState.APPLIED) { "Only applied migrations can be rolled back" }
        return copy(state = MigrationTransactionState.ROLLED_BACK)
    }
}
