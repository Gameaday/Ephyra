package ephyra.domain.source.diagnostics

import ephyra.core.common.util.system.logcat
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.registry.SourceEntryResolution
import ephyra.domain.source.registry.SourceOriginPolicy
import ephyra.domain.source.registry.SourceRegistrationKind
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
 * **Why this still exists after `SRC-011`.** The registry is now the one owner of existence and
 * identity (`ADR-0012`), but the map is a *projection* of it — `AndroidSourceManager` rebuilds
 * the map from registry registrations, and a projection can drift from its source (a map-build
 * failure for one profile, a constructor throwing for another). This instrument reports that
 * drift, so a user report answers "did the projection lose a source the registry has?" instead of
 * re-litigating which of three owners is lying. There is one, and this watches it.
 *
 * **Deliberate non-owner.** It reads both sides and re-derives nothing: the identity rule is the
 * registry's ([SourceRegistry.resolveEntry]), imported rather than re-implemented, because the
 * disagreement being audited is now projection disagreement, not rule disagreement. A diagnostic
 * that shares the rule it audits cannot detect a disagreement about that rule.
 *
 * **Removal condition.** A defect instrument, not architecture: it stays while `DEF-029`'s device
 * reproduction is owed, and it is deleted when the entry-resolution outcome it reports is
 * surfaced to the user directly.
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
     *
     * A registration with a *disabled* profile is a deliberate absence, not a disagreement — the
     * map is right to omit it. A payload-less registration is reported: the registry says the
     * source exists, so the map must say so too.
     */
    suspend fun inspectRegistration(): SourceRegistrationReport {
        val ready = withTimeoutOrNull(SOURCE_MANAGER_READY_TIMEOUT_MS) {
            sourceManager.isInitialized.first { it }
            true
        } ?: false

        val originRegistrations = sourceRegistry.currentRegistrations()
            .filter { it.kind == SourceRegistrationKind.ORIGIN_ADDRESSED }
        val expected = originRegistrations.filter { it.profile == null || it.profile.enabled }
        val onlineSources = sourceManager.getOnlineSources()

        val missing = expected.mapNotNull { registration ->
            val origin = registration.origin ?: return@mapNotNull null
            val registered = onlineSources.any { source ->
                SourceOriginPolicy.isSameOrigin(source.baseUrl, origin.value)
            }
            if (registered) null else origin.value
        }.toSet()

        val mismatched = expected.mapNotNull { registration ->
            val origin = registration.origin ?: return@mapNotNull null
            val registered = onlineSources.firstOrNull { source ->
                SourceOriginPolicy.isSameOrigin(source.baseUrl, origin.value)
            } ?: return@mapNotNull null
            if (registered.id != registration.id) origin.value else null
        }.toSet()

        return SourceRegistrationReport(
            sourceManagerReady = ready,
            profiledDomains = originRegistrations.mapNotNull { it.origin?.value }.toSet(),
            domainsWithoutRegisteredSource = missing,
            domainsWithIdentityMismatch = mismatched,
            registeredSourceIds = onlineSources.map { it.id }.toSet(),
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
            domainsClaimingId = (resolution as? SourceEntryResolution.SpellingChanged)
                ?.claimedOrigins
                ?.map { it.value }
                ?.toSet()
                ?: emptySet(),
        )
    }

    /**
     * Logs a registration reading. WARN when the projection disagrees with the registry, DEBUG
     * otherwise, and the full inventory at DEBUG in both cases so a user report can be matched
     * against the launch state.
     */
    suspend fun reportRegistration(label: String) {
        val report = inspectRegistration()
        val disagreeing = report.domainsWithoutRegisteredSource.isNotEmpty() ||
            report.domainsWithIdentityMismatch.isNotEmpty()

        if (disagreeing) {
            logcat(LogPriority.WARN) {
                "Source registration defect [$label]: ${report.profiledDomains.size} registered " +
                    "origin(s); without a registered source ${report.domainsWithoutRegisteredSource}; " +
                    "identity mismatch ${report.domainsWithIdentityMismatch}; " +
                    "${report.registeredSourceIds.size} registered source id(s); " +
                    "${report.stubSourceIds.size} stub source(s); " +
                    "${report.installedExtensions.size} installed extension(s)"
            }
        }

        logcat(LogPriority.DEBUG) {
            "Source registration [$label]: ready=${report.sourceManagerReady}, " +
                "origins=${report.profiledDomains}, " +
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
            is SourceEntryResolution.SpellingChanged -> logcat(LogPriority.WARN) {
                "Entry source id changed spelling (SRC-011, ADR-0012): sourceId=$sourceId is " +
                    "claimed by ${resolution.claimedOrigins.joinToString(",") { it.value }}; " +
                    "re-registering one of those origins recovers the entry"
            }
            is SourceEntryResolution.Absent -> logcat(LogPriority.WARN) {
                "Entry source is not registered and no registration claims its id: " +
                    "sourceId=$sourceId, stub=${report.isStub} (${report.stubName})"
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
