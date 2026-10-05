package ephyra.domain.source.diagnostics

import ephyra.core.common.util.system.logcat
import ephyra.domain.content.source.SourceProfileCache
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.service.SourceManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Instruments the one fact this app has three owners for: which sources exist, and what each
 * source's identity is.
 *
 * **Why this exists.** `DEF-029` is owner-reported as "an entry that opened yesterday cannot be
 * opened today, and the error it produces exists nowhere in this repository". Reading the tree
 * produced the mechanism but no reproduction: `AndroidSourceManager` builds the source map from the
 * preference set `profiled_domains_list` alone, `GetAvailableSources` reads the same set for the UI,
 * and `SourceProfileCache.getAllProfiledDomains()` returns that set *or* a hardcoded domain list —
 * while identity is derived as `baseUrl.hashCode()` in two independent places, and one failed health
 * check deletes the domain outright. Each of those is a *state* condition, so the symptom is a
 * function of preference and database state at launch rather than of the build, which is why it
 * presents as "worked yesterday".
 *
 * **What it does, and does not do.** It only observes: it reads the authorities the failing path
 * reads and reports where they disagree, so one reproduction names the owner that lost the fact. It
 * deliberately re-derives the legacy `hashCode()` identity instead of importing it, because a
 * diagnostic that shares the rule it audits cannot detect a disagreement about that rule. The
 * mapping lookup uses the `baseUrl_scraper_mapping_<origin>` key idiom the sourcing hub used;
 * `SRC-011` moves that read into the registry and this call site moves with it.
 *
 * **Removal condition.** A defect instrument, not architecture: `DEF-029` closes with a reproduction
 * that names the owner, and `SRC-011` makes the disagreement it reports unrepresentable.
 */
@Singleton
class SourceResolutionDiagnostics @Inject constructor(
    private val sourceManager: SourceManager,
    private val extensionManager: ExtensionManager,
    private val profileCache: SourceProfileCache,
) {

    /**
     * Reports whether the source map agrees with the profiled-domain list.
     *
     * Awaits [SourceManager] readiness with a bounded wait: reading the map before it is populated
     * would report every domain as unregistered, and a false positive on the one defect this exists
     * to detect is worse than no reading at all.
     */
    suspend fun inspectRegistration(): SourceRegistrationReport {
        val ready = withTimeoutOrNull(SOURCE_MANAGER_READY_TIMEOUT_MS) {
            sourceManager.isInitialized.first { it }
            true
        } ?: false

        val domains = profileCache.getAllProfiledDomains()
        val onlineSources = sourceManager.getOnlineSources()
        val sourceByOrigin = onlineSources.associateBy { it.baseUrl }
        val idByOrigin = onlineSources.associate { it.baseUrl to it.id }

        return SourceRegistrationReport(
            sourceManagerReady = ready,
            profiledDomains = domains,
            domainsWithoutRegisteredSource = domains.filterNot { sourceByOrigin.containsKey(it) }.toSet(),
            domainsWithIdentityMismatch = domains
                .filter { origin -> idByOrigin[origin]?.let { it != legacyIdentity(origin) } == true }
                .toSet(),
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
     * Reports how one entry's stored source id resolves — the entry-level half of `DEF-029`.
     *
     * [SourceEntryReport.domainsClaimingId] is what separates the two causes: empty means the id was
     * written against a base URL that no longer exists in that spelling (the orphaning `ADR-0012`
     * describes), non-empty means the source is genuinely absent and re-registering it recovers the
     * entry.
     */
    suspend fun inspectEntry(sourceId: Long): SourceEntryReport {
        val resolved = sourceManager.get(sourceId)
        val stub = resolved as? StubSource
            ?: sourceManager.getStubSources().firstOrNull { it.id == sourceId }
        return SourceEntryReport(
            sourceId = sourceId,
            resolvedClass = resolved?.javaClass?.name,
            isStub = stub != null,
            stubName = stub?.name,
            domainsClaimingId = profileCache.getAllProfiledDomains()
                .filter { legacyIdentity(it) == sourceId }
                .toSet(),
        )
    }

    /**
     * Logs a registration reading. WARN when the authorities disagree, DEBUG otherwise, and the full
     * inventory at DEBUG in both cases so a user report can be matched against the launch state.
     */
    suspend fun reportRegistration(label: String) {
        val report = inspectRegistration()
        val disagreeing = report.domainsWithoutRegisteredSource.isNotEmpty() ||
            report.domainsWithIdentityMismatch.isNotEmpty()

        if (disagreeing) {
            logcat(LogPriority.WARN) {
                "Source registration defect [$label]: ${report.profiledDomains.size} profiled " +
                    "domain(s); without a registered source ${report.domainsWithoutRegisteredSource}; " +
                    "identity mismatch ${report.domainsWithIdentityMismatch}; " +
                    "${report.registeredSourceIds.size} registered source id(s); " +
                    "${report.stubSourceIds.size} stub source(s); " +
                    "${report.installedExtensions.size} installed extension(s)"
            }
        }

        logcat(LogPriority.DEBUG) {
            "Source registration [$label]: ready=${report.sourceManagerReady}, " +
                "domains=${report.profiledDomains}, " +
                "extensions=${report.installedExtensions.joinToString { it.pkgName + ":" + it.sourceIds }}"
        }
    }

    /**
     * Logs an entry-source reading. WARN when the entry's source is absent, DEBUG when it resolves.
     */
    suspend fun reportEntry(sourceId: Long) {
        val report = inspectEntry(sourceId)
        val claiming = report.domainsClaimingId.joinToString(",").ifEmpty { "none" }

        if (report.resolvedClass == null) {
            logcat(LogPriority.WARN) {
                "Entry source is not registered: sourceId=$sourceId, stub=${report.isStub} " +
                    "(${report.stubName}), profiled domains claiming this id=$claiming; " +
                    "empty claim means the entry's id came from a baseUrl whose spelling changed " +
                    "(SRC-011, ADR-0012)"
            }
        } else {
            logcat(LogPriority.DEBUG) {
                "Entry source resolved: sourceId=$sourceId -> ${report.resolvedClass}"
            }
        }
    }

    /**
     * The identity rule this app currently applies, re-derived rather than imported.
     *
     * Kept verbatim on purpose: it must go stale *with* `DynamicHttpSource` and
     * `GetAvailableSources`, and the day those two change is the day this reports an identity
     * mismatch instead of silently agreeing with them.
     */
    private fun legacyIdentity(origin: String): Long = origin.hashCode().toLong()

    private companion object {
        /**
         * How long to wait for the source map to be populated before reporting its absence. The
         * observer is a `StateFlow` that several consumers already gate on, so the ordinary case
         * resolves immediately; this bound only stops a cold start reporting a false defect.
         */
        const val SOURCE_MANAGER_READY_TIMEOUT_MS = 5_000L
    }
}
