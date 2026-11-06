package ephyra.domain.source.diagnostics

import ephyra.domain.source.registry.SourceEntryResolution

/**
 * What the app's authorities say about the sources it has, captured for one reading.
 *
 * Produced by [SourceResolutionDiagnostics]. Every field is an observation of an existing owner —
 * nothing here is a new opinion about which sources exist.
 */
data class SourceRegistrationReport(
    /** Whether [ephyra.domain.source.service.SourceManager] had finished populating its map. */
    val sourceManagerReady: Boolean,
    /** The origins the registry registers — what existence actually is, post-`SRC-011`. */
    val profiledDomains: Set<String>,
    /** Registry origins with no source in the map under the same origin. `DEF-029`'s signature. */
    val domainsWithoutRegisteredSource: Set<String>,
    /** Origins where the map holds a source whose id disagrees with the registry's identity. */
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
 * [resolution] is the answer. [domainsClaimingId] is its derived projection — non-empty exactly
 * when the resolution is [SourceEntryResolution.SpellingChanged] — kept so log readers and tests
 * can read the claim without re-matching the sealed type.
 */
data class SourceEntryReport(
    val sourceId: Long,
    val resolvedClass: String?,
    val isStub: Boolean,
    val stubName: String?,
    val resolution: SourceEntryResolution,
    val domainsClaimingId: Set<String>,
)
