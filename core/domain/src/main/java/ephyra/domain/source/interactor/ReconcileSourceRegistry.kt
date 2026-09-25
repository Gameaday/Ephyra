package ephyra.domain.source.interactor

import ephyra.domain.source.repository.SourceLifecycleRepository
import ephyra.source.api.NativeSourceRegistry
import ephyra.source.api.SourceId
import ephyra.source.api.SourceLifecycleChange
import ephyra.source.api.SourceLifecycleTransition

/** Reconciles authoritative native descriptors into lifecycle persistence without changing user state. */
class ReconcileSourceRegistry(
    private val repository: SourceLifecycleRepository,
) {
    suspend fun reconcile(
        registry: NativeSourceRegistry,
        atMillis: Long,
    ): SourceRegistryReconciliationResult {
        require(atMillis >= 0L) { "Source reconciliation time must not be negative" }
        val outcomes = registry.descriptors.map { descriptor ->
            descriptor.id to repository.discover(descriptor, atMillis)
        }
        val knownIds = outcomes.mapTo(mutableSetOf()) { (id, _) -> id }
        val missing = repository.getAll()
            .map { it.descriptor.id }
            .filterNot(knownIds::contains)
            .distinct()
            .sortedBy { it.value }

        val created = outcomes.countApplied { it == SourceLifecycleChange.CREATED }
        val updated = outcomes.countApplied { it == SourceLifecycleChange.METADATA_UPDATED }
        val unchanged = outcomes.countApplied { it == SourceLifecycleChange.UNCHANGED }
        val rejected = outcomes.mapNotNull { (id, transition) ->
            (transition as? SourceLifecycleTransition.Rejected)?.let { SourceRejection(id, it.reason) }
        }
        return SourceRegistryReconciliationResult(
            discovered = outcomes.size,
            created = created,
            updated = updated,
            unchanged = unchanged,
            rejected = rejected,
            missingFromRegistry = missing,
        )
    }

    private fun List<Pair<SourceId, SourceLifecycleTransition>>.countApplied(
        predicate: (SourceLifecycleChange) -> Boolean,
    ): Int = count { (_, transition) ->
        transition is SourceLifecycleTransition.Applied && predicate(transition.change)
    }
}

data class SourceRejection(val sourceId: SourceId, val reason: String)

data class SourceRegistryReconciliationResult(
    val discovered: Int,
    val created: Int,
    val updated: Int,
    val unchanged: Int,
    val rejected: List<SourceRejection>,
    val missingFromRegistry: List<SourceId>,
)
