package ephyra.domain.content.interactor

import ephyra.core.common.util.system.logcat
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.repository.ContentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import logcat.LogPriority

/**
 * Interactor to retrieve a [ContentItem] or subscribe to its updates.
 * This is a media-agnostic replacement for GetManga.
 */
class GetContentItem(
    private val contentRepository: ContentRepository,
) {

    suspend fun await(id: Long): ContentItem? {
        return try {
            contentRepository.getContentItemById(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    suspend fun isFavorite(id: Long): Boolean {
        return try {
            contentRepository.getContentItemById(id)?.favorite ?: false
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    suspend fun subscribe(id: Long): Flow<ContentItem?> {
        return contentRepository.getContentItemByIdAsFlow(id)
            .catch { e ->
                logcat(LogPriority.ERROR, e) { "Unexpected error in getContentItemByIdAsFlow(id=$id)" }
                throw e
            }
    }
}
