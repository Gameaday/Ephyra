package ephyra.app.startup

import ephyra.domain.source.registry.SourceRegistry
import ephyra.domain.source.service.SourceManager
import ephyra.source.local.LocalSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Provider

/**
 * Executes the `RECONCILE_SOURCES` startup step (`A3`; the gap the `B-034` audit found absent).
 *
 * Implemented on the **shipping** source authority — [SourceRegistry] and [SourceManager] — and
 * deliberately not through `ReconcileSourceRegistry`: its only `SourceLifecycleRepository`
 * implementation is the target-data store that `DATA-001H` forbids from production writes, so that
 * contract stays a DI-orphan by decision rather than oversight.
 *
 * Contract: [reconcile] **never throws**. A timeout, a registry failure or a disagreement is
 * outcome data, because a non-critical startup step must not be able to abort the launch it is
 * auditing.
 */
class SourceReconciler(
    private val sourceRegistry: SourceRegistry,
    private val sourceManagerProvider: Provider<SourceManager>,
) {

    /**
     * Primes the source map, waits (bounded) for it to become ready, and audits the registry's
     * registrations against the loaded catalogue.
     *
     * Constructing the manager primes its asynchronous map build at startup rather than at first
     * foreground — the same reasoning as `App.onStart`'s priming call, made unconditional.
     *
     * @param timeoutMs bound on waiting for [SourceManager.isInitialized]; must be positive.
     * @param expectedNonRegistryIds ids that legitimately appear in the map without a registry
     * registration — the local source, which is not an extension (`ADR-0017`).
     */
    suspend fun reconcile(
        timeoutMs: Long,
        expectedNonRegistryIds: Set<Long> = setOf(LocalSource.ID),
    ): SourceReconciliationOutcome {
        require(timeoutMs > 0) { "timeoutMs must be positive, was " + timeoutMs }
        return try {
            val sourceManager = sourceManagerProvider.get()
            val ready = withTimeoutOrNull(timeoutMs) { sourceManager.isInitialized.first { it } } ?: false
            val registrations = sourceRegistry.currentRegistrations()
            val loaded = sourceManager.getCatalogueSources().map { it.id }
            val registeredIds = registrations.map { it.id }.toSet()
            val loadedIds = loaded.toSet()
            SourceReconciliationOutcome(
                ready = ready,
                registryCount = registrations.size,
                mapCount = loaded.size,
                registeredButNotLoaded = (registeredIds - loadedIds).sorted(),
                loadedButNotRegistered = (loadedIds - registeredIds - expectedNonRegistryIds).sorted(),
            )
        } catch (e: Throwable) {
            SourceReconciliationOutcome(
                ready = false,
                registryCount = 0,
                mapCount = 0,
                registeredButNotLoaded = emptyList(),
                loadedButNotRegistered = emptyList(),
                error = e,
            )
        }
    }
}

/**
 * One reconciliation reading. A non-null [error] means the audit could not be taken at all; a
 * successful audit with disagreements is **not** an error — [registeredButNotLoaded] and
 * [loadedButNotRegistered] are exactly the data the audit exists to name.
 */
data class SourceReconciliationOutcome(
    val ready: Boolean,
    val registryCount: Int,
    val mapCount: Int,
    val registeredButNotLoaded: List<Long>,
    val loadedButNotRegistered: List<Long>,
    val error: Throwable? = null,
)
