package ephyra.domain.source.diagnostics

/**
 * What the app's authorities say about the sources it has, captured for one reading.
 *
 * Produced by [SourceResolutionDiagnostics]. Every field is an observation of an existing owner —
 * nothing here is a new opinion about which sources exist.
 */
data class SourceRegistrationReport(
    /** Whether [ephyra.domain.source.service.SourceManager] had finished populating its map. */
    val sourceManagerReady: Boolean,
    /** What the profile cache claims is profiled — including its hardcoded fallback trio. */
    val profiledDomains: Set<String>,
    /** Profiled domains with no source registered under the same origin. `DEF-029`'s signature. */
    val domainsWithoutRegisteredSource: Set<String>,
    /** Domains whose registered source id disagrees with the identity rule derived from the origin. */
    val domainsWithIdentityMismatch: Set<String>,
    /** Every registered online source id. */
    val registeredSourceIds: Set<Long>,
    /** Ids held by `StubSource`, i.e. entries whose source is referenced but absent. */
    val stubSourceIds: Set<Long>,
    /** Installed legacy extensions and the source ids each contributes. */
    val installedExtensions: List<InstalledExtensionSummary>,
)

/** One installed extension, as far as source identity is concerned. */
data class InstalledExtensionSummary(
    val pkgName: String,
    val name: String,
    val versionName: String,
    val sourceIds: List<Long>,
)

/**
 * How one entry's stored source id resolves.
 *
 * [domainsClaimingId] is the decisive field: empty means the id was written against a base URL that
 * no longer exists in that spelling (the orphaning `ADR-0012` describes), non-empty means the source
 * is genuinely absent and re-registering it recovers the entry.
 */
data class SourceEntryReport(
    val sourceId: Long,
    val resolvedClass: String?,
    val isStub: Boolean,
    val stubName: String?,
    val domainsClaimingId: Set<String>,
)
