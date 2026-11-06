package ephyra.domain.content.source.interactor

import ephyra.domain.content.source.SourceType
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.source.registry.SourceRegistration
import ephyra.domain.source.registry.SourceRegistrationKind
import ephyra.domain.source.registry.SourceRegistry
import ephyra.domain.source.service.SourceManager
import ephyra.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gets all available sources across all source types (legacy extensions, JS scrapers, heuristics, repositories).
 *
 * SRC-011/ADR-0012: this interactor no longer decides which sources exist and no longer derives
 * their identity. Both facts come from [SourceRegistry] — the one owner — and this class only
 * projects them into row models. The id in every row is the registry's id, which is the same id
 * `DynamicHttpSource` carries, so a row tap resolves in the source map by construction instead
 * of by two components happening to hash the same spelling of a URL.
 */
@Singleton
class GetAvailableSources @Inject constructor(
    private val sourceManager: SourceManager,
    private val extensionManager: ExtensionManager,
    private val registry: SourceRegistry,
    private val sourcePreferences: SourcePreferences,
) {
    operator fun invoke(): Flow<List<UnifiedSource>> {
        val originRegistrationsFlow: Flow<List<SourceRegistration>> = registry.registrations
            .map { registrations ->
                // No hardcoded fallback: an empty registry means the user removed every
                // profile (or none was created yet) and injecting built-ins here
                // made removed sources resurrect and default sources undeletable.
                registrations.filter { it.kind == SourceRegistrationKind.ORIGIN_ADDRESSED }
            }

        return combine(
            sourceManager.catalogueSources,
            extensionManager.installedExtensionsFlow,
            originRegistrationsFlow,
        ) { catalogueSources, extensions, originRegistrations ->
            val unifiedSources = mutableListOf<UnifiedSource>()

            // Legacy extension sources - represent each installed extension package once
            val disabledSources = sourcePreferences.disabledSources().get()
            for (ext in extensions) {
                val primarySource = ext.sources.firstOrNull { it is CatalogueSource } ?: ext.sources.firstOrNull()
                val isEnabled = ext.sources.any { it.id.toString() !in disabledSources }
                unifiedSources.add(
                    UnifiedSource(
                        id = primarySource?.id ?: ext.pkgName.hashCode().toLong(),
                        name = ext.name,
                        baseUrl = (primarySource as? eu.kanade.tachiyomi.source.online.HttpSource)?.baseUrl ?: "",
                        sourceType = SourceType.REMOTE_EXTENSION,
                        enabled = isEnabled,
                        extensionId = ext.pkgName,
                        lastHealthCheck = 0,
                        failureCount = 0,
                    ),
                )
            }

            // Origin-addressed sources (JS scrapers, heuristics), projected from the registry.
            // Existence and identity are the registry's; this only shapes rows.
            for (registration in originRegistrations) {
                unifiedSources.add(
                    UnifiedSource(
                        id = registration.id,
                        name = registration.displayName,
                        baseUrl = registration.origin?.value ?: "",
                        sourceType = registration.sourceType,
                        enabled = registration.enabled,
                        extensionId = null,
                        lastHealthCheck = registration.lastHealthCheck,
                        failureCount = registration.failureCount,
                    ),
                )
            }

            // Index all source IDs belonging to installed extensions
            val extensionSourceIds = buildSet {
                for (ext in extensions) {
                    for (src in ext.sources) {
                        add(src.id)
                    }
                }
            }
            val sourceIdToExtensionPkg = buildMap {
                for (ext in extensions) {
                    for (src in ext.sources) {
                        put(src.id, ext.pkgName)
                    }
                }
            }

            // Add any standalone catalogue sources not already covered by an installed extension
            for (source in catalogueSources) {
                if (source.id == 0L || source.id in extensionSourceIds) continue
                val pkgName = sourceIdToExtensionPkg[source.id] ?: extensionManager.getExtensionPackage(source.id)
                val isAlreadyCovered = if (pkgName != null) {
                    unifiedSources.any { it.extensionId == pkgName }
                } else {
                    unifiedSources.any { it.id == source.id }
                }
                if (!isAlreadyCovered) {
                    val isEnabled = source.id.toString() !in disabledSources
                    unifiedSources.add(
                        UnifiedSource(
                            id = source.id,
                            name = source.name,
                            baseUrl = (source as? eu.kanade.tachiyomi.source.online.HttpSource)?.baseUrl ?: "",
                            sourceType = SourceType.REMOTE_EXTENSION,
                            enabled = isEnabled,
                            extensionId = pkgName,
                            lastHealthCheck = 0,
                            failureCount = 0,
                        ),
                    )
                }
            }

            unifiedSources.sortBy { it.name.lowercase() }
            unifiedSources
        }
    }
}

/**
 * Unified representation of a content source across all source types.
 */
data class UnifiedSource(
    val id: Long,
    val name: String,
    val baseUrl: String,
    val sourceType: SourceType,
    val enabled: Boolean,
    val extensionId: String?,
    val lastHealthCheck: Long,
    val failureCount: Int,
)
