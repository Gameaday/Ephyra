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
    /** The ids the registry registers — what existence actually is. */
    val registrationIds: Set<Long>,
    /** Registry registrations with no source in the map under the same id: a lost projection. */
    val registrationsWithoutMapSource: Set<Long>,
    /** Map online sources the registry does not register: the projection invented a source. */
    val mapSourcesWithoutRegistration: Set<Long>,
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
 * [resolution] is the answer — the registry's typed verdict, not a re-derived rule.
 */
data class SourceEntryReport(
    val sourceId: Long,
    val resolvedClass: String?,
    val isStub: Boolean,
    val stubName: String?,
    val resolution: SourceEntryResolution,
)
