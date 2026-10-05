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
 * No engine is bound for [sourceType], and none is bound at all.
 *
 * **Why an exception rather than a silent fallback.** The orchestrator previously guaranteed a
 * fallback existed, because the heuristic engine was always bound. Removing it (`ADR-0015`) made an
 * empty registry a real and ordinary state — the correct state until Jellyfin lands. Encoding that
 * as an exception rather than substituting some other engine is the point: a source with no engine
 * must say so, and naming the type is what makes the fix obvious.
 *
 * @property sourceType the unbound type, or null when nothing is bound and no type can be named.
 */
class NoEngineBoundException(
    val sourceType: SourceType?,
) : IllegalStateException(
    if (sourceType != null) {
        "No ContentSourceEngine is bound for SourceType.$sourceType"
    } else {
        "No ContentSourceEngine is bound. Sources are provided by extension APKs today; a " +
            "profile-based source needs an engine registered."
    },
)

/**
 * Central orchestrator for resolving content from URLs, implementing [RemoteSource].
 *
 * Implements the "try known → fall back to heuristic → report failure" pipeline.
 * The app core calls this single class; it never touches engines directly.
 * All return values are explicitly wrapped in [Result] structures for Clean UDF execution.
 *
 * **Engine selection is a registry, not a `when`.** [enginesByType] is built by asking each engine
 * which [SourceType]s it serves, so adding a source type means writing one engine and binding it —
 * not editing an exhaustive branch here. That branch used to exist and every source type added since
 * had to be remembered by it; `JS_SCRAPER` existed only because the transpiler fed it, and
 * `REMOTE_EXTENSION` was routed to the script engine on the stated grounds that "remote extensions use
 * script engine via mapping", which was never true of the APK path.
 */
class ContentSourceOrchestrator(
    private val profileCache: SourceProfileCache,
    engines: List<ContentSourceEngine>,
    private val preferenceStore: PreferenceStore,
) : RemoteSource {

    /** Every engine by the types it claims, first registration wins on an overlap. */
    private val enginesByType: Map<SourceType, ContentSourceEngine> = buildMap {
        engines.forEach { engine ->
            engine.handles.forEach { type -> putIfAbsent(type, engine) }
        }
    }

    /**
     * The engine every unbound source type falls back to, or `null` when none is bound.
     *
     * **Nullable on purpose.** The heuristic engine was removed (`ADR-0015`) and it was the only
     * bound one, so the registry is legitimately empty until Jellyfin lands. Reading
     * `enginesByType[HEURISTIC] ?: engines.first()` here would have thrown `NoSuchElementException`
     * inside a constructor, turning "no engine yet" into a startup crash — the worst possible
     * encoding of an ordinary state.
     *
     * A null fallback is also what makes the failure *legible*: a source with no engine reports that
     * no engine serves its type, which names the fix, instead of being misrouted somewhere that will
     * fail less clearly.
     */
    private val fallbackEngine: ContentSourceEngine? =
        enginesByType.values.firstOrNull()

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
            val items = withEngineFallback(
                profile = profile,
                primary = { engine -> engine.search(profile, query, page) },
                fallback = { engine -> engine.search(profile, query, page) },
                fallbackOnEmpty = { it.isEmpty() },
            )
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
            val item = withEngineFallback(
                profile = profile,
                primary = { engine -> engine.getItem(profile, itemUrl) },
                fallback = { engine -> engine.getItem(profile, itemUrl) },
            )
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
            val items = withEngineFallback(
                profile = profile,
                primary = { engine -> engine.getPopular(profile, page) },
                fallback = { engine -> engine.getPopular(profile, page) },
                fallbackOnEmpty = { it.isEmpty() },
            )
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
            val items = withEngineFallback(
                profile = profile,
                primary = { engine -> engine.getLatest(profile, page) },
                fallback = { engine -> engine.getLatest(profile, page) },
                fallbackOnEmpty = { it.isEmpty() },
            )
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
            val chapters = withEngineFallback(
                profile = profile,
                primary = { engine -> engine.getChapters(profile, itemUrl) },
                fallback = { engine -> engine.getChapters(profile, itemUrl) },
                fallbackOnEmpty = { it.isEmpty() },
            )
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
            val pages = withEngineFallback(
                profile = profile,
                primary = { engine -> engine.getPages(profile, unitUrl) },
                fallback = { engine -> engine.getPages(profile, unitUrl) },
                fallbackOnEmpty = { it.isEmpty() },
            )
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
     * Updates the source type of a profile.
     */
    suspend fun setSourceType(
        baseUrl: String,
        sourceType: SourceType,
    ): Result<SourceProfile> {
        return try {
            val profile =
                profileCache.get(baseUrl) ?: return Result.Error(IllegalArgumentException("Source not found: $baseUrl"))
            val updated = profile.copy(
                sourceType = sourceType,
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

    /**
     * Picks the engine that serves [profile]'s type, or fails legibly.
     *
     * @throws NoEngineBoundException when nothing serves the type and nothing is bound at all. Naming
     *   the unbound type is the whole point: the alternative — quietly substituting some other engine —
     *   is what turns a missing binding into content from the wrong place.
     */
    private fun resolveEngineForProfile(profile: SourceProfile): ContentSourceEngine {
        return enginesByType[profile.sourceType] ?: fallbackEngine
            ?: throw NoEngineBoundException(profile.sourceType)
    }

    private suspend fun resolveEngine(baseUrl: String): ContentSourceEngine {
        val cached = profileCache.get(baseUrl)
        if (cached != null) {
            return resolveEngineForProfile(cached)
        }

        return fallbackEngine ?: throw NoEngineBoundException(null)
    }

    /**
     * Runs an operation against the profile's primary engine, falling back to the heuristic
     * engine on either failure or (for collection results) an empty primary response.
     *
     * Keeping this policy in one place prevents search, detail, chapter, and page operations
     * from drifting apart as new source engines are added.
     */
    private suspend fun <T> withEngineFallback(
        profile: SourceProfile,
        primary: suspend (ContentSourceEngine) -> T,
        fallback: suspend (ContentSourceEngine) -> T,
        fallbackOnEmpty: ((T) -> Boolean)? = null,
    ): T {
        val primaryEngine = resolveEngineForProfile(profile)
        return try {
            val result = primary(primaryEngine)
            if (fallbackOnEmpty != null && fallbackEngine != null && primaryEngine !== fallbackEngine &&
                fallbackOnEmpty(result)
            ) {
                val fallbackResult = fallbackEngine?.let { fallback(it) } ?: result
                if (fallbackOnEmpty(fallbackResult)) result else fallbackResult
            } else {
                result
            }
        } catch (primaryEx: Exception) {
            // With no fallback engine bound, the primary failure is the only thing to report. Rethrowing
            // it preserves the real cause, which is what the caller needs; there is nothing to fall
            // back to, so falling back would mean inventing a substitute.
            if (fallbackEngine != null && primaryEngine !== fallbackEngine) {
                fallback(fallbackEngine)
            } else {
                throw primaryEx
            }
        }
    }

    /**
     * The type an engine's own discovery result should be recorded under.
     *
     * Previously `if (engine === scriptEngine) JS_SCRAPER else HEURISTIC`, which could only ever
     * produce two of the four types and silently filed a repository as a heuristic profile. It now
     * reads the engine's declaration: a single-type engine names its type, and an engine claiming
     * several types (or none) records as [SourceType.REMOTE_EXTENSION] — the only type with a
     * working provider behind it today.
     */
    private fun inferSourceType(engine: ContentSourceEngine): SourceType {
        return engine.handles.singleOrNull() ?: SourceType.REMOTE_EXTENSION
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
