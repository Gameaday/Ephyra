package ephyra.data.content

import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.toContentItem
import ephyra.domain.content.model.toMangaUpdate
import ephyra.domain.content.repository.ContentRepository
import ephyra.domain.manga.repository.MangaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of [ContentRepository] delegating to [MangaRepository].
 * This avoids schema migrations by mapping 'mangas' SQLite tables to [ContentItem] dynamically.
 */
class ContentRepositoryImpl(
    private val mangaRepository: MangaRepository,
) : ContentRepository {

    override suspend fun getContentItemById(id: Long): ContentItem? {
        return try {
            mangaRepository.getMangaById(id).toContentItem()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getContentItemByIdAsFlow(id: Long): Flow<ContentItem?> {
        return mangaRepository.getMangaByIdAsFlow(id).map { it.toContentItem() }
    }

    override suspend fun getFavorites(): List<ContentItem> {
        return mangaRepository.getFavorites().map { it.toContentItem() }
    }

    override suspend fun update(contentItem: ContentItem): Boolean {
        return mangaRepository.update(contentItem.toMangaUpdate())
    }
}
