package ephyra.domain.content.source.interactor

import ephyra.domain.content.source.SourceType
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.source.service.SourceManager
import ephyra.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gets all available sources across all source types (extensions, the local source, repositories).
 *
 * ADR-0017 deleted the profile path (JS scrapers and heuristic sources), so the only sources
 * that exist are extension sources, the local source, and the extensions' own catalogue
 * sources. This interactor projects those into row models; it never decided existence and
 * holds no second copy of that fact.
 */
@Singleton
class GetAvailableSources @Inject constructor(
    private val sourceManager: SourceManager,
    private val extensionManager: ExtensionManager,
    private val sourcePreferences: SourcePreferences,
) {
    operator fun invoke(): Flow<List<UnifiedSource>> =
        combine(
            sourceManager.catalogueSources,
            extensionManager.installedExtensionsFlow,
        ) { catalogueSources, extensions ->
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

