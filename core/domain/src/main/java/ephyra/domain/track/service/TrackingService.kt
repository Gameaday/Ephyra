package ephyra.domain.track.service

import ephyra.core.common.util.Result
import ephyra.domain.content.model.ContentItem

/**
 * Clean media-tracker sync contract abstracting AniList, MAL, and third-party tracking APIs.
 * Operates strictly with [Result] wrappers to manage sync results safely and explicitly.
 */
interface TrackingService {
    suspend fun search(query: String): Result<List<ContentItem>>
    suspend fun linkTracker(contentItem: ContentItem, trackerId: Long): Result<Boolean>
    suspend fun syncProgress(contentItem: ContentItem, progress: Long): Result<Boolean>
    suspend fun fetchStatus(contentItem: ContentItem): Result<ContentItem>
}
