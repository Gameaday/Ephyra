package ephyra.domain.content.source.interactor

import ephyra.core.common.preference.PreferenceStore
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceProfileCache
import ephyra.domain.content.source.SourceType
import ephyra.domain.extension.service.ExtensionManager
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
 */
@Singleton
class GetAvailableSources @Inject constructor(
    private val sourceManager: SourceManager,
    private val extensionManager: ExtensionManager,
    private val orchestrator: ContentSourceOrchestrator,
    private val profileCache: SourceProfileCache,
    private val preferenceStore: PreferenceStore,
    private val sourcePreferences: SourcePreferences,
) {
    operator fun invoke(): Flow<List<UnifiedSource>> {
        val heuristicProfilesFlow: Flow<List<SourceProfile>> = preferenceStore.getStringSet(
            "profiled_domains_list",
            emptySet(),
        )
            .changes()
            .map { domains ->
                val actualDomains = if (domains.isEmpty()) {
                    setOf(
                        "https://mangadex.org",
                        "https://manganato.com",
                        "https://asuratoons.com",
                    )
                } else {
                    domains
                }
                actualDomains.mapNotNull { profileCache.get(it) }
            }

        return combine(
            sourceManager.catalogueSources,
            extensionManager.installedExtensionsFlow,
            heuristicProfilesFlow,
        ) { catalogueSources, extensions, heuristicProfiles ->
            val unifiedSources = mutableListOf<UnifiedSource>()

            // Legacy extension sources
            for (ext in extensions) {
                for (source in ext.sources) {
                    if (source is CatalogueSource) {
                        val isEnabled = source.id.toString() !in sourcePreferences.disabledSources().get()
                        unifiedSources.add(
                            UnifiedSource(
                                id = source.id,
                                name = source.name,
                                baseUrl = (source as? eu.kanade.tachiyomi.source.online.HttpSource)?.baseUrl ?: "",
                                sourceType = SourceType.LEGACY_EXTENSION,
                                enabled = isEnabled,
                                extensionId = ext.pkgName,
                                lastHealthCheck = 0,
                                failureCount = 0,
                            ),
                        )
                    }
                }
            }

            // Heuristic/JS scraper profiles
            for (profile in heuristicProfiles) {
                unifiedSources.add(
                    UnifiedSource(
                        id = profile.baseUrl.hashCode().toLong(),
                        name = profile.displayName,
                        baseUrl = profile.baseUrl,
                        sourceType = profile.sourceType,
                        enabled = profile.enabled,
                        extensionId = null,
                        lastHealthCheck = profile.lastHealthCheck,
                        failureCount = profile.failureCount,
                    ),
                )
            }

            // Add any catalogue sources not already covered
            for (source in catalogueSources) {
                if (!unifiedSources.any { it.id == source.id && it.sourceType == SourceType.LEGACY_EXTENSION }) {
                    val isEnabled = source.id.toString() !in sourcePreferences.disabledSources().get()
                    unifiedSources.add(
                        UnifiedSource(
                            id = source.id,
                            name = source.name,
                            baseUrl = (source as? eu.kanade.tachiyomi.source.online.HttpSource)?.baseUrl ?: "",
                            sourceType = SourceType.LEGACY_EXTENSION,
                            enabled = isEnabled,
                            extensionId = null,
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
