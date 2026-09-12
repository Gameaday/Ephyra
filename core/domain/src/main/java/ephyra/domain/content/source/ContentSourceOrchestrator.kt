package ephyra.domain.content.source

import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.Result
import ephyra.core.common.util.getOrThrow
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentUnit
import ephyra.domain.content.model.toManga
import ephyra.domain.manga.interactor.TitleNormalizer
import ephyra.domain.manga.model.Manga
import ephyra.domain.migration.models.MigrationCandidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Central orchestrator for resolving content from URLs, implementing [RemoteSource].
 *
 * Implements the "try known → fall back to heuristic → report failure" pipeline.
 * The app core calls this single class; it never touches engines directly.
 * All return values are explicitly wrapped in [Result] structures for Clean UDF execution.
 */
class ContentSourceOrchestrator(
    private val profileCache: SourceProfileCache,
    private val heuristicEngine: ContentSourceEngine,
    private val scriptEngine: ContentSourceEngine,
    private val preferenceStore: PreferenceStore,
) : RemoteSource {

    override suspend fun discover(baseUrl: String): Result<SourceProfile> {
        return try {
            profileCache.invalidate(baseUrl)
            val engine = resolveEngine(baseUrl)
            val profile = engine.discover(baseUrl)
            // Preserve sourceType from cached profile if it exists, otherwise infer from engine
            val cachedProfile = profileCache.get(baseUrl)
            val finalProfile = cachedProfile?.let { cached ->
                profile.copy(
                    sourceType = cached.sourceType,
                    enabled = cached.enabled,
                    scraperFilename = cached.scraperFilename,
                    repositoryId = cached.repositoryId,
                    lastUpdated = System.currentTimeMillis(),
                )
            } ?: profile.copy(
                sourceType = inferSourceType(engine),
                lastUpdated = System.currentTimeMillis(),
            )
            profileCache.save(finalProfile)
            Result.Success(finalProfile)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun search(baseUrl: String, query: String, page: Int): Result<List<ContentItem>> {
        return try {
            val profile = resolveProfile(baseUrl)
            if (!profile.enabled) {
                return Result.Error(IllegalStateException("Source is disabled: $baseUrl"))
            }
            val primaryEngine = resolveEngineForProfile(profile)
            val items = try {
                val result = primaryEngine.search(profile, query, page)
                if (result.isEmpty() && primaryEngine !== heuristicEngine) {
                    heuristicEngine.search(profile, query, page).ifEmpty { result }
                } else {
                    result
                }
            } catch (primaryEx: Exception) {
                if (primaryEngine !== heuristicEngine) {
                    heuristicEngine.search(profile, query, page)
                } else {
                    throw primaryEx
                }
            }
            updateProfileHealth(profile, success = true)
            Result.Success(items)
        } catch (e: Exception) {
            updateProfileHealth(baseUrl, success = false)
            Result.Error(e)
        }
    }

    override suspend fun getItem(baseUrl: String, itemUrl: String): Result<ContentItem> {
        return try {
            val profile = resolveProfile(baseUrl)
            if (!profile.enabled) {
                return Result.Error(IllegalStateException("Source is disabled: $baseUrl"))
            }
            val primaryEngine = resolveEngineForProfile(profile)
            val item = try {
                primaryEngine.getItem(profile, itemUrl)
            } catch (primaryEx: Exception) {
                if (primaryEngine !== heuristicEngine) {
                    heuristicEngine.getItem(profile, itemUrl)
                } else {
                    throw primaryEx
                }
            }
            updateProfileHealth(profile, success = true)
            Result.Success(item)
        } catch (e: Exception) {
            updateProfileHealth(baseUrl, success = false)
            Result.Error(e)
        }
    }

    override suspend fun getPopular(baseUrl: String, page: Int): Result<List<ContentItem>> {
        return try {
            val profile = resolveProfile(baseUrl)
            if (!profile.enabled) {
                return Result.Error(IllegalStateException("Source is disabled: $baseUrl"))
            }
            val primaryEngine = resolveEngineForProfile(profile)
            val items = try {
                val result = primaryEngine.getPopular(profile, page)
                if (result.isEmpty() && primaryEngine !== heuristicEngine) {
                    heuristicEngine.getPopular(profile, page).ifEmpty { result }
                } else {
                    result
                }
            } catch (primaryEx: Exception) {
                if (primaryEngine !== heuristicEngine) {
                    heuristicEngine.getPopular(profile, page)
                } else {
                    throw primaryEx
                }
            }
            updateProfileHealth(profile, success = true)
            Result.Success(items)
        } catch (e: Exception) {
            updateProfileHealth(baseUrl, success = false)
            Result.Error(e)
        }
    }

    override suspend fun getLatest(baseUrl: String, page: Int): Result<List<ContentItem>> {
        return try {
            val profile = resolveProfile(baseUrl)
            if (!profile.enabled) {
                return Result.Error(IllegalStateException("Source is disabled: $baseUrl"))
            }
            val primaryEngine = resolveEngineForProfile(profile)
            val items = try {
                val result = primaryEngine.getLatest(profile, page)
                if (result.isEmpty() && primaryEngine !== heuristicEngine) {
                    heuristicEngine.getLatest(profile, page).ifEmpty { result }
                } else {
                    result
                }
            } catch (primaryEx: Exception) {
                if (primaryEngine !== heuristicEngine) {
                    heuristicEngine.getLatest(profile, page)
                } else {
                    throw primaryEx
                }
            }
            updateProfileHealth(profile, success = true)
            Result.Success(items)
        } catch (e: Exception) {
            updateProfileHealth(baseUrl, success = false)
            Result.Error(e)
        }
    }

    override suspend fun getChapters(baseUrl: String, itemUrl: String): Result<List<ContentUnit>> {
        return try {
            val profile = resolveProfile(baseUrl)
            if (!profile.enabled) {
                return Result.Error(IllegalStateException("Source is disabled: $baseUrl"))
            }
            val primaryEngine = resolveEngineForProfile(profile)
            val chapters = try {
                val result = primaryEngine.getChapters(profile, itemUrl)
                if (result.isEmpty() && primaryEngine !== heuristicEngine) {
                    heuristicEngine.getChapters(profile, itemUrl).ifEmpty { result }
                } else {
                    result
                }
            } catch (primaryEx: Exception) {
                if (primaryEngine !== heuristicEngine) {
                    heuristicEngine.getChapters(profile, itemUrl)
                } else {
                    throw primaryEx
                }
            }
            updateProfileHealth(profile, success = true)
            Result.Success(chapters)
        } catch (e: Exception) {
            updateProfileHealth(baseUrl, success = false)
            Result.Error(e)
        }
    }

    override suspend fun getPages(baseUrl: String, unitUrl: String): Result<List<String>> {
        return try {
            val profile = resolveProfile(baseUrl)
            if (!profile.enabled) {
                return Result.Error(IllegalStateException("Source is disabled: $baseUrl"))
            }
            val primaryEngine = resolveEngineForProfile(profile)
            val pages = try {
                val result = primaryEngine.getPages(profile, unitUrl)
                if (result.isEmpty() && primaryEngine !== heuristicEngine) {
                    heuristicEngine.getPages(profile, unitUrl).ifEmpty { result }
                } else {
                    result
                }
            } catch (primaryEx: Exception) {
                if (primaryEngine !== heuristicEngine) {
                    heuristicEngine.getPages(profile, unitUrl)
                } else {
                    throw primaryEx
                }
            }
            updateProfileHealth(profile, success = true)
            Result.Success(pages)
        } catch (e: Exception) {
            updateProfileHealth(baseUrl, success = false)
            Result.Error(e)
        }
    }

    /**
     * Force re-discovery of a source (clears cached profile).
     * Kept for backward compatibility with existing screen models.
     */
    suspend fun rediscover(baseUrl: String): SourceProfile = discover(baseUrl).getOrThrow()

    /**
     * Updates the enabled state of a source profile.
     */
    suspend fun setSourceEnabled(baseUrl: String, enabled: Boolean): Result<SourceProfile> {
        return try {
            val profile =
                profileCache.get(baseUrl) ?: return Result.Error(IllegalArgumentException("Source not found: $baseUrl"))
            val updated = profile.copy(enabled = enabled)
            profileCache.save(updated)
            Result.Success(updated)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Updates the source type of a profile (e.g., switch from heuristic to JS scraper).
     */
    suspend fun setSourceType(
        baseUrl: String,
        sourceType: SourceType,
        scraperFilename: String? = null,
    ): Result<SourceProfile> {
        return try {
            val profile =
                profileCache.get(baseUrl) ?: return Result.Error(IllegalArgumentException("Source not found: $baseUrl"))
            val updated = profile.copy(
                sourceType = sourceType,
                scraperFilename = scraperFilename,
                lastUpdated = System.currentTimeMillis(),
            )
            profileCache.save(updated)
            Result.Success(updated)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Gets all cached source profiles.
     */
    suspend fun getAllProfiles(): List<SourceProfile> {
        return profileCache.getAll()
    }

    /**
     * Invalidates a cached profile (removes it from cache).
     */
    suspend fun invalidateProfile(baseUrl: String) {
        profileCache.invalidate(baseUrl)
    }

    /**
     * Recommends migration candidates for a manga from healthy sources.
     * Searches active, healthy profiles (failureCount < 3) and scores matches
     * using [TitleNormalizer].
     */
    fun suggestMigration(manga: Manga): Flow<MigrationCandidate> = flow {
        val profiles = profileCache.getAll().filter { it.enabled && it.failureCount < 3 }
        for (profile in profiles) {
            // Avoid querying the source if it matches the current manga's source URL
            if (manga.url.contains(normalizeUrl(profile.baseUrl))) continue

            val searchResult = search(profile.baseUrl, manga.title, page = 1)
            if (searchResult is Result.Success) {
                for (item in searchResult.data) {
                    val sim = TitleNormalizer.similarity(manga.title, item.title)
                    if (sim >= 0.65) {
                        emit(
                            MigrationCandidate(
                                manga = item.toManga(),
                                sourceProfile = profile,
                                sourceName = profile.displayName,
                                sourceId = item.sourceId,
                                confidence = sim,
                            ),
                        )
                    }
                }
            }
        }
    }

    // ── Private helpers ──────────────────────────────────────────

    private suspend fun resolveProfile(baseUrl: String): SourceProfile {
        val cached = profileCache.get(baseUrl)
        if (cached != null) return cached

        val engine = resolveEngine(baseUrl)
        val profile = engine.discover(baseUrl).copy(
            sourceType = inferSourceType(engine),
            lastUpdated = System.currentTimeMillis(),
        )
        profileCache.save(profile)
        return profile
    }

    private fun resolveEngineForProfile(profile: SourceProfile): ContentSourceEngine {
        return when (profile.sourceType) {
            SourceType.JS_SCRAPER -> scriptEngine
            SourceType.LEGACY_EXTENSION -> scriptEngine // Legacy extensions use script engine via mapping
            SourceType.REPOSITORY -> heuristicEngine // Repositories use heuristic for now
            SourceType.HEURISTIC -> heuristicEngine
        }
    }

    private suspend fun resolveEngine(baseUrl: String): ContentSourceEngine {
        val normalized = normalizeUrl(baseUrl)
        val mapped = preferenceStore.getString("baseUrl_scraper_mapping_$normalized", "").get()

        // Check if there's a cached profile with explicit source type
        val cached = profileCache.get(baseUrl)
        if (cached != null) {
            return resolveEngineForProfile(cached)
        }

        return if (mapped.isNotBlank()) {
            scriptEngine
        } else {
            heuristicEngine
        }
    }

    private fun inferSourceType(engine: ContentSourceEngine): SourceType {
        return if (engine === scriptEngine) SourceType.JS_SCRAPER else SourceType.HEURISTIC
    }

    private suspend fun updateProfileHealth(profile: SourceProfile, success: Boolean) {
        val updated = if (success) {
            profile.copy(
                lastHealthCheck = System.currentTimeMillis(),
                failureCount = 0,
                verified = true,
            )
        } else {
            profile.copy(
                failureCount = profile.failureCount + 1,
                verified = false,
            )
        }
        profileCache.save(updated)
    }

    private suspend fun updateProfileHealth(baseUrl: String, success: Boolean) {
        val profile = profileCache.get(baseUrl)
        profile?.let { updateProfileHealth(it, success) }
    }

    private fun normalizeUrl(url: String): String {
        return url
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
            .trim()
    }
}
