package ephyra.domain.source.registry

import ephyra.domain.extension.model.Extension
import ephyra.domain.extension.service.ExtensionManager
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one owner of which sources exist and what each one's identity is. `SRC-011` / `ADR-0012`;
 * narrowed to extensions-only by `ADR-0017`, which deleted the profile path (JS scrapers and
 * heuristic sources) that used to be registered here alongside them.
 *
 * Consumers do not keep a second copy of either fact. They project from this registry, and the
 * diagnostics audit that projection against the source map: a source that exists in one place
 * and not the other is a defect the audit names, not a state the app silently tolerates.
 *
 * **Existence and health are separate facts** (`ADR-0012`): a failing source is
 * [SourceAvailability.DEGRADED], never absent, and nothing observed through this registry erases
 * a registration.
 */
@Singleton
class SourceRegistry @Inject constructor(
    private val extensionManager: ExtensionManager,
) {

    /**
     * Every registration, live. One row per installed extension source, in a deterministic
     * order — extensions in the order the manager reports them, deduped by id.
     */
    val registrations: Flow<List<SourceRegistration>> = extensionManager.installedExtensionsFlow.map { extensions ->
        buildRegistrations(extensions)
    }

    /** The registrations as of this call — for consumers that need one reading, not a stream. */
    suspend fun currentRegistrations(): List<SourceRegistration> =
        buildRegistrations(extensionManager.installedExtensionsFlow.value)

    /** The registration with this id, if the registry has one. */
    suspend fun registration(id: Long): SourceRegistration? =
        currentRegistrations().firstOrNull { it.id == id }

    /**
     * How one stored source id resolves against the registry — the closed answer to the question
     * a flat `get(id): Source?` cannot give: "does nothing exist here, or does something else
     * own this id?"
     */
    suspend fun resolveEntry(id: Long): SourceEntryResolution {
        val registration = registration(id) ?: return SourceEntryResolution.Absent(id)
        return SourceEntryResolution.Resolved(registration)
    }

    private fun buildRegistrations(extensions: List<Extension.Installed>): List<SourceRegistration> {
        // LinkedHashMap + putIfAbsent: dedupe by id while keeping the manager's order, so two
        // equal inputs always produce the same list.
        val byId = LinkedHashMap<Long, SourceRegistration>()
        extensions.forEach { extension ->
            extension.sources.forEach { source ->
                byId.putIfAbsent(
                    source.id,
                    SourceRegistration(
                        id = source.id,
                        displayName = source.name,
                        origin = (source as? HttpSource)?.baseUrl?.let { SourceOriginPolicy.canonicalize(it) },
                        availability = SourceAvailability.AVAILABLE,
                        extensionPkgName = extension.pkgName,
                    ),
                )
            }
        }
        return byId.values.toList()
    }
}

/**
 * Whether a registered source can currently be used. Existence and health are separate facts
 * (`ADR-0012`): a failing source is degraded, never absent, and no health observation erases a
 * registration.
 */
enum class SourceAvailability {
    /** Known-good: no recorded failures. */
    AVAILABLE,

    /** Registered but not currently serviceable — recorded failures. */
    DEGRADED,

    /**
     * Reserved for an explicit quarantine decision (e.g. a source serving hostile responses).
     * No producer exists yet; the value is part of the contract so health can worsen without
     * inventing a new state — and without any component reading existence out of it.
     */
    QUARANTINED,
}

/**
 * One source the registry says exists.
 *
 * `id` and `origin` are the facts every consumer must take from the registry rather than
 * re-derive.
 */
data class SourceRegistration(
    val id: Long,
    val displayName: String,
    val origin: SourceOrigin?,
    val availability: SourceAvailability,
    val extensionPkgName: String? = null,
)

/**
 * The closed set of ways a stored source id can resolve against the registry. A `when` over this
 * is exhaustive — the entry diagnostics do exactly that, so adding a case is a compile error
 * there until it is handled.
 */
sealed interface SourceEntryResolution {
    /** The id names this registration. */
    data class Resolved(val registration: SourceRegistration) : SourceEntryResolution

    /** The id names nothing the registry has. */
    data class Absent(val id: Long) : SourceEntryResolution
}
