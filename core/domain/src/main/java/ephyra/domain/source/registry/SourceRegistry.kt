package ephyra.domain.source.registry

import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceProfileCache
import ephyra.domain.content.source.SourceType
import ephyra.domain.extension.model.Extension
import ephyra.domain.extension.service.ExtensionManager
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one owner of which sources exist and what each one's identity is. `SRC-011` / `ADR-0012`.
 *
 * Before this existed, three components answered "which sources exist" — `AndroidSourceManager`
 * and `GetAvailableSources` each read the `profiled_domains_list` preference directly, and
 * `SourceProfileCache` kept its own copy of the answer — while nobody answered "what is this
 * source's identity": every consumer hashed whatever spelling of a URL it happened to see. That
 * is the mechanism behind `DEF-029`: an entry that resolved yesterday orphans today because a
 * trailing slash changed the hash.
 *
 * Consumers do not keep a second copy of either fact. They project from this registry:
 * `AndroidSourceManager` builds the source map from registrations, `GetAvailableSources` shapes
 * them into rows, `RemoveCustomSource` erases through [unregister], and the diagnostics audit
 * the projection. `SourceRegistryOwnershipTest` fails the build if any production code reads the
 * preference directly again.
 *
 * **Existence and health are separate facts** (`ADR-0012`): a failing or payload-less source is
 * [SourceAvailability.DEGRADED], never absent, and nothing observed through this registry erases
 * a registration — only an explicit [unregister] does.
 */
@Singleton
class SourceRegistry @Inject constructor(
    private val extensionManager: ExtensionManager,
    preferenceStore: PreferenceStore,
    private val profileCache: SourceProfileCache,
) {

    /**
     * The profiled-domains preference — the registry is its only production reader.
     *
     * Held as one instance and captured once, deliberately: `unregister` writes through the same
     * [Preference] whose [changes][Preference.changes] feed [registrations], so an erase is
     * visible on the flow by construction rather than by two components agreeing to notify each
     * other.
     */
    private val profiledDomains: Preference<Set<String>> by lazy {
        preferenceStore.getStringSet(PROFILED_DOMAINS_KEY, emptySet())
    }

    /**
     * Every registration, live. Extensions first (the installed, trusted set), then
     * origin-addressed sources in a deterministic order.
     */
    val registrations: Flow<List<SourceRegistration>> = combine(
        extensionManager.installedExtensionsFlow,
        profiledDomains.changes(),
    ) { extensions, domains -> buildRegistrations(extensions, domains) }

    /** The registrations as of this call — for consumers that need one reading, not a stream. */
    suspend fun currentRegistrations(): List<SourceRegistration> =
        buildRegistrations(extensionManager.installedExtensionsFlow.value, profiledDomains.get())

    /** The registration with this id, if the registry has one. */
    suspend fun registration(id: Long): SourceRegistration? =
        currentRegistrations().firstOrNull { it.id == id }

    /**
     * How one stored source id resolves against the registry — the closed answer to the question
     * `DEF-029` asked about a flat `get(id): Source?`.
     *
     * A pre-`SRC-011` entry whose stored spelling was not canonical resolves as
     * [SourceEntryResolution.SpellingChanged] naming the origin that claims it, which is the
     * difference between "re-register this origin and the entry recovers" and an unexplained
     * "source not registered".
     */
    suspend fun resolveEntry(id: Long): SourceEntryResolution {
        val registrations = currentRegistrations()
        registrations.firstOrNull { it.id == id }?.let { return SourceEntryResolution.Resolved(it) }
        val claiming = registrations.filter {
            it.kind == SourceRegistrationKind.ORIGIN_ADDRESSED && it.legacyId == id
        }
        if (claiming.isNotEmpty()) {
            return SourceEntryResolution.SpellingChanged(
                id = id,
                claimedOrigins = claiming.mapNotNull { it.origin }.toSet(),
            )
        }
        return SourceEntryResolution.Absent(id)
    }

    /**
     * Unregisters every spelling of [rawOrigin], and only [rawOrigin].
     *
     * Returns false when nothing matched. Spelling-insensitive on purpose: the stored set may
     * hold the origin in any spelling, and a removal that missed one spelling resurrected the
     * source on the next launch — the exact resurrection `RemoveCustomSource` used to guard
     * against with a second copy of the normalization rule.
     */
    suspend fun unregister(rawOrigin: String): Boolean {
        val domains = profiledDomains.get()
        val toRemove = domains.filter { SourceOriginPolicy.isSameOrigin(it, rawOrigin) }
        if (toRemove.isEmpty()) return false
        profiledDomains.set(domains - toRemove.toSet())
        return true
    }

    private suspend fun buildRegistrations(
        extensions: List<Extension.Installed>,
        domains: Set<String>,
    ): List<SourceRegistration> {
        // LinkedHashMap + putIfAbsent: dedupe by id while keeping a deterministic order —
        // extensions first, then domains sorted, so two equal inputs always produce the same list.
        val byId = LinkedHashMap<Long, SourceRegistration>()
        extensions.forEach { extension ->
            extension.sources.forEach { source ->
                byId.putIfAbsent(
                    source.id,
                    SourceRegistration(
                        id = source.id,
                        kind = SourceRegistrationKind.EXTENSION_APK,
                        displayName = source.name,
                        origin = (source as? HttpSource)?.baseUrl?.let { SourceOriginPolicy.canonicalize(it) },
                        availability = SourceAvailability.AVAILABLE,
                        extensionPkgName = extension.pkgName,
                    ),
                )
            }
        }
        domains.sorted().forEach { rawDomain ->
            val profile = profileCache.get(rawDomain)
            val id = SourceIdentityPolicy.identityOf(rawDomain)
            byId.putIfAbsent(
                id,
                SourceRegistration(
                    id = id,
                    kind = SourceRegistrationKind.ORIGIN_ADDRESSED,
                    displayName = profile?.displayName ?: rawDomain,
                    origin = SourceOriginPolicy.canonicalize(rawDomain),
                    // Health never erases existence (ADR-0012): no payload or recorded failures
                    // degrades the registration, it does not remove it.
                    availability = if (profile == null || profile.failureCount > 0) {
                        SourceAvailability.DEGRADED
                    } else {
                        SourceAvailability.AVAILABLE
                    },
                    legacyId = SourceIdentityPolicy.legacyIdentityOf(rawDomain),
                    profile = profile,
                ),
            )
        }
        return byId.values.toList()
    }

    private companion object {
        const val PROFILED_DOMAINS_KEY = "profiled_domains_list"
    }
}

/**
 * Whether a registered source can currently be used. Existence and health are separate facts
 * (`ADR-0012`): a failing source is degraded, never absent, and no health observation erases a
 * registration.
 */
enum class SourceAvailability {
    /** Known-good: has a payload and no recorded failures. */
    AVAILABLE,

    /** Registered but not currently serviceable — no cached payload, or recorded failures. */
    DEGRADED,

    /**
     * Reserved for an explicit quarantine decision (e.g. a source serving hostile responses).
     * No producer exists yet; the value is part of the contract so health can worsen without
     * inventing a new state — and without any component reading existence out of it.
     */
    QUARANTINED,
}

/** How a registration came to exist. */
enum class SourceRegistrationKind {
    /** From an installed extension APK: the extension's own source id is the identity. */
    EXTENSION_APK,

    /** Addressed by an origin the user registered (a profiled domain): identity is the registry's. */
    ORIGIN_ADDRESSED,
}

/**
 * One source the registry says exists.
 *
 * `id`, `kind` and `origin` are the facts every consumer must take from the registry rather than
 * re-derive. The computed properties are projections of [profile] with honest defaults, so a
 * payload-less registration still has a shape the Sources list and the reader can render.
 */
data class SourceRegistration(
    val id: Long,
    val kind: SourceRegistrationKind,
    val displayName: String,
    val origin: SourceOrigin?,
    val availability: SourceAvailability,
    val extensionPkgName: String? = null,
    /**
     * The id the pre-`SRC-011` rule would have minted from the stored spelling. Orphan detection
     * only: it lets `resolveEntry` recognize an entry written before the registry existed. It is
     * never used to mint identity.
     */
    val legacyId: Long? = null,
    val profile: SourceProfile? = null,
) {
    val sourceType: SourceType get() = profile?.sourceType ?: SourceType.REMOTE_EXTENSION

    /** A registration with no payload is still a registration; it is enabled until configured off. */
    val enabled: Boolean get() = profile?.enabled ?: true

    val lastHealthCheck: Long get() = profile?.lastHealthCheck ?: 0L

    val failureCount: Int get() = profile?.failureCount ?: 0
}

/**
 * The closed set of ways a stored source id can resolve against the registry. A `when` over this
 * is exhaustive — the entry diagnostics do exactly that, so adding a case is a compile error
 * there until it is handled.
 */
sealed interface SourceEntryResolution {
    /** The id names this registration. */
    data class Resolved(val registration: SourceRegistration) : SourceEntryResolution

    /**
     * The id was written against a spelling this registry no longer produces. The named origins
     * claim it, and re-registering one recovers the entry — this is the `DEF-029` signature,
     * made detectable instead of an unexplained "source not registered".
     */
    data class SpellingChanged(val id: Long, val claimedOrigins: Set<SourceOrigin>) : SourceEntryResolution

    /** The id names nothing the registry has. */
    data class Absent(val id: Long) : SourceEntryResolution
}
