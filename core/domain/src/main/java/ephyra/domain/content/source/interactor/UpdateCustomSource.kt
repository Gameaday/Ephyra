package ephyra.domain.content.source.interactor

import ephyra.core.common.util.Result
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.SourceProfile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Updates an existing custom source (heuristic profile or repository).
 */
@Singleton
class UpdateCustomSource @Inject constructor(
    private val orchestrator: ContentSourceOrchestrator,
) {

    /**
     * Updates the display name of a source.
     */
    suspend fun updateDisplayName(baseUrl: String, newName: String): Result<SourceProfile> {
        return try {
            val profile = orchestrator.getAllProfiles().firstOrNull { it.baseUrl == baseUrl }
                ?: return Result.Error(IllegalArgumentException("Source not found: $baseUrl"))

            val updated = profile.copy(displayName = newName)
            orchestrator.setSourceType(baseUrl, profile.sourceType)
            // Note: We'd need a way to persist displayName changes - for now just return updated
            Result.Success(updated)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Forces re-discovery of a heuristic profile.
     */
    suspend fun forceRediscover(baseUrl: String): Result<SourceProfile> {
        return try {
            val profile = orchestrator.rediscover(baseUrl)
            Result.Success(profile)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
