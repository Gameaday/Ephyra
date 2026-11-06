package ephyra.domain.source.diagnostics

import ephyra.core.common.util.system.logcat
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.registry.SourceEntryResolution
import ephyra.domain.source.registry.SourceRegistry
import ephyra.domain.source.service.SourceManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audits the registry-to-map projection: whether the sources [SourceRegistry] says exist are the
 * sources the legacy [SourceManager] map actually holds.
 *
 * **Why this still exists.** The registry is the one owner of existence and identity
 * (`ADR-0012`), but the map is a *projection* of it, and a projection can drift from its source
 * (a map-build failure for one extension, a constructor throwing for another). This instrument
 * reports that drift, so a user report answers "did the projection lose a source the registry
 * has?" instead of re-litigating which of the two owners is lying. There is one, and this
 * watches it. `ADR-0017` deleted the profile path this instrument originally watched over; the
 * audit remains because the projection risk does not.
 *
 * **Deliberate non-owner.** It reads both sides and re-derives nothing: the identity rule is the
 * registry's ([SourceRegistry.resolveEntry]), imported rather than re-implemented, because a
 * diagnostic that shares the rule it audits cannot detect a disagreement about that rule.
 */
@Singleton
class SourceResolutionDiagnostics @Inject constructor(
    private val sourceManager: SourceManager,
    private val extensionManager: ExtensionManager,
    private val sourceRegistry: SourceRegistry,
) {

    /**
     * Reports whether the source map agrees with the registry.
     *
     * Awaits [SourceManager] readiness with a bounded wait: reading the map before it is
     * populated would report every registration as unregistered, and a false positive on the one
     * defect this exists to detect is worse than no reading at all.
     */
    suspend fun inspectRegistration(): SourceRegistrationReport {
        val ready = withTimeoutOrNull(SOURCE_MANAGER_READY_TIMEOUT_MS) {
            sourceManager.isInitialized.first { it }
            true
        } ?: false

        val registrations = sourceRegistry.currentRegistrations()
        val onlineSources = sourceManager.getOnlineSources()

        val mapIds = onlineSources.map { it.id }.toSet()
        val registrationIds = registrations.map { it.id }.toSet()

        return SourceRegistrationReport(
            sourceManagerReady = ready,
            registrationIds = registrationIds,
            registrationsWithoutMapSource = registrationIds - mapIds,
            mapSourcesWithoutRegistration = mapIds - registrationIds,
            registeredSourceIds = mapIds,
            stubSourceIds = sourceManager.getStubSources().map { it.id }.toSet(),
            installedExtensions = extensionManager.installedExtensionsFlow.value.map { extension ->
                InstalledExtensionSummary(
                    pkgName = extension.pkgName,
                    name = extension.name,
                    versionName = extension.versionName,
                    sourceIds = extension.sources.map { it.id },
                )
            },
        )
    }

    /**
     * Reports how one entry's stored source id resolves — the registry's answer plus the map view
     * (resolved class / stub) for the same reading.
     */
    suspend fun inspectEntry(sourceId: Long): SourceEntryReport {
        val resolution = sourceRegistry.resolveEntry(sourceId)
        val resolved = sourceManager.get(sourceId)
        val stub = resolved as? StubSource
            ?: sourceManager.getStubSources().firstOrNull { it.id == sourceId }
        return SourceEntryReport(
            sourceId = sourceId,
            resolvedClass = resolved?.javaClass?.name,
            isStub = stub != null,
            stubName = stub?.name,
            resolution = resolution,
        )
    }

    /**
     * Logs a registration reading. WARN when the projection disagrees with the registry, DEBUG
     * otherwise, and the full inventory at DEBUG in both cases so a user report can be matched
     * against the launch state.
     */
    suspend fun reportRegistration(label: String) {
        val report = inspectRegistration()
        val disagreeing = report.registrationsWithoutMapSource.isNotEmpty() ||
            report.mapSourcesWithoutRegistration.isNotEmpty()

        if (disagreeing) {
            logcat(LogPriority.WARN) {
                "Source registration defect [$label]: ${report.registrationIds.size} registration(s); " +
                    "without a map source ${report.registrationsWithoutMapSource}; " +
                    "map sources without registration ${report.mapSourcesWithoutRegistration}; " +
                    "${report.registeredSourceIds.size} registered source id(s); " +
                    "${report.stubSourceIds.size} stub source(s); " +
                    "${report.installedExtensions.size} installed extension(s)"
            }
        }

        logcat(LogPriority.DEBUG) {
            "Source registration [$label]: ready=${report.sourceManagerReady}, " +
                "registrations=${report.registrationIds}, " +
                "extensions=${report.installedExtensions.joinToString { it.pkgName + ":" + it.sourceIds }}"
        }
    }

    /**
     * Logs an entry-source reading — exhaustive over [SourceEntryResolution], so a new case is a
     * compile error here until it is reported.
     */
    suspend fun reportEntry(sourceId: Long) {
        val report = inspectEntry(sourceId)
        when (val resolution = report.resolution) {
            is SourceEntryResolution.Resolved -> logcat(LogPriority.DEBUG) {
                "Entry source resolved: sourceId=$sourceId -> ${report.resolvedClass}"
            }
            is SourceEntryResolution.Absent -> logcat(LogPriority.WARN) {
                "Entry source is not registered: sourceId=$sourceId, stub=${report.isStub} (${report.stubName})"
            }
        }
    }

    private companion object {
        /**
         * How long to wait for the source map to be populated before reporting its absence. The
         * observer is a `StateFlow` that several consumers already gate on, so the ordinary case
         * resolves immediately; this bound only stops a cold start reporting a false defect.
         */
        const val SOURCE_MANAGER_READY_TIMEOUT_MS = 5_000L
    }
}
