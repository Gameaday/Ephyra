package ephyra.data.content

import ephyra.domain.chapter.repository.ChapterRepository
import ephyra.domain.content.model.ContentUnit
import ephyra.domain.content.model.toChapterUpdate
import ephyra.domain.content.model.toContentUnit
import ephyra.domain.content.repository.ContentUnitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of [ContentUnitRepository] delegating to [ChapterRepository].
 * Transparently maps 'chapters' database structures to generic sequential [ContentUnit] entities.
 */
class ContentUnitRepositoryImpl(
    private val chapterRepository: ChapterRepository,
) : ContentUnitRepository {

    override suspend fun getUnitsByContentItemId(contentItemId: Long): List<ContentUnit> {
        return chapterRepository.getChapterByMangaId(contentItemId).map { it.toContentUnit() }
    }

    override suspend fun getUnitsByContentItemIdAsFlow(contentItemId: Long): Flow<List<ContentUnit>> {
        return chapterRepository.getChapterByMangaIdAsFlow(contentItemId).map { list ->
            list.map { it.toContentUnit() }
        }
    }

    override suspend fun update(unit: ContentUnit): Boolean {
        chapterRepository.update(unit.toChapterUpdate())
        return true
    }

    override suspend fun updateAll(units: List<ContentUnit>): Boolean {
        chapterRepository.updateAll(units.map { it.toChapterUpdate() })
        return true
    }
}
