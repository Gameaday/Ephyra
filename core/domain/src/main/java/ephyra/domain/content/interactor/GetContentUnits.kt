package ephyra.domain.content.interactor

import ephyra.core.common.util.system.logcat
import ephyra.domain.content.model.ContentUnit
import ephyra.domain.content.repository.ContentUnitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import logcat.LogPriority

/**
 * Interactor to retrieve and subscribe to [ContentUnit]s (chapters/episodes/sections) for a given [ContentItem].
 * This is a media-agnostic replacement for GetChaptersByMangaId.
 */
class GetContentUnits(
    private val contentUnitRepository: ContentUnitRepository,
) {

    suspend fun await(contentItemId: Long): List<ContentUnit> {
        return try {
            contentUnitRepository.getUnitsByContentItemId(contentItemId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    suspend fun subscribe(contentItemId: Long): Flow<List<ContentUnit>> {
        return contentUnitRepository.getUnitsByContentItemIdAsFlow(contentItemId)
            .catch { e ->
                logcat(LogPriority.ERROR, e) {
                    "Unexpected error in getUnitsByContentItemIdAsFlow(contentItemId=$contentItemId)"
                }
                throw e
            }
    }
}
