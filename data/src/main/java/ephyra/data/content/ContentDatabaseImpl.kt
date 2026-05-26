package ephyra.data.content

import ephyra.core.common.util.Result
import ephyra.data.chapter.ChapterMapper
import ephyra.data.manga.MangaMapper
import ephyra.data.room.daos.ChapterDao
import ephyra.data.room.daos.MangaDao
import ephyra.data.room.entities.ChapterEntity
import ephyra.data.room.entities.MangaEntity
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentStatus
import ephyra.domain.content.model.ContentUnit
import ephyra.domain.content.model.toContentItem
import ephyra.domain.content.model.toContentUnit
import ephyra.domain.content.repository.ContentDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ContentDatabaseImpl(
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
) : ContentDatabase {

    override suspend fun insertItem(item: ContentItem): Result<Long> {
        return try {
            val entity = MangaEntity(
                id = if (item.id == -1L) 0L else item.id,
                source = item.sourceId,
                url = item.url,
                title = item.title,
                author = item.author,
                artist = item.artist,
                description = item.description,
                genre = item.genres,
                status = when (item.status) {
                    ContentStatus.Ongoing -> 1L
                    ContentStatus.Completed -> 2L
                    ContentStatus.Licensed -> 4L
                    ContentStatus.Cancelled -> 5L
                    ContentStatus.Hiatus -> 6L
                    else -> 0L
                },
                thumbnailUrl = item.thumbnailUrl,
                favorite = item.favorite,
                dateAdded = item.dateAdded,
                lastUpdate = item.lastUpdate,
                nextUpdate = item.metadata[ContentItem.META_DURATION_MINUTES]?.toLongOrNull() ?: 0L,
                initialized = item.initialized,
                viewerFlags = 0L,
                chapterFlags = 0L,
                coverLastModified = 0L,
                updateStrategy = 0,
                calculateInterval = 0,
                lastModifiedAt = System.currentTimeMillis(),
                favoriteModifiedAt = null,
                version = 0L,
                isSyncing = false,
                notes = "",
                metadataSource = null,
                metadataUrl = null,
                canonicalId = null,
                sourceStatus = 0,
                alternativeTitles = null,
                deadSince = null,
                contentType = item.contentType.value,
                lockedFields = 0L,
            )
            val id = mangaDao.upsert(entity)
            Result.Success(id)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun getItemById(id: Long): Result<ContentItem?> {
        return try {
            val item = mangaDao.getMangaById(id)?.let(MangaMapper::mapManga)?.toContentItem()
            Result.Success(item)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override fun getItemByIdAsFlow(id: Long): Flow<Result<ContentItem?>> {
        return mangaDao.getMangaByIdAsFlow(id).map { entity ->
            try {
                val item = entity?.let(MangaMapper::mapManga)?.toContentItem()
                Result.Success(item)
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }

    override suspend fun getFavorites(): Result<List<ContentItem>> {
        return try {
            val favorites = mangaDao.getFavorites().map { MangaMapper.mapManga(it).toContentItem() }
            Result.Success(favorites)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun updateItem(item: ContentItem): Result<Boolean> {
        return try {
            val existing = mangaDao.getMangaById(item.id)
            if (existing != null) {
                val updated = existing.copy(
                    source = item.sourceId,
                    url = item.url,
                    title = item.title,
                    author = item.author,
                    artist = item.artist,
                    description = item.description,
                    genre = item.genres,
                    status = when (item.status) {
                        ContentStatus.Ongoing -> 1L
                        ContentStatus.Completed -> 2L
                        ContentStatus.Licensed -> 4L
                        ContentStatus.Cancelled -> 5L
                        ContentStatus.Hiatus -> 6L
                        else -> 0L
                    },
                    thumbnailUrl = item.thumbnailUrl,
                    favorite = item.favorite,
                    dateAdded = item.dateAdded,
                    lastUpdate = item.lastUpdate,
                    initialized = item.initialized,
                    contentType = item.contentType.value,
                )
                mangaDao.update(updated)
                Result.Success(true)
            } else {
                Result.Success(false)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun deleteItem(id: Long): Result<Boolean> {
        return try {
            mangaDao.deleteMangaById(id)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun insertUnit(unit: ContentUnit): Result<Long> {
        return try {
            val entity = ChapterEntity(
                id = if (unit.id == -1L) 0L else unit.id,
                mangaId = unit.contentItemId,
                url = unit.url,
                name = unit.title,
                scanlator = unit.scanlator,
                read = unit.read,
                bookmark = unit.bookmark,
                lastPageRead = unit.progress.toInt(),
                chapterNumber = unit.number,
                sourceOrder = 0,
                dateFetch = System.currentTimeMillis(),
                dateUpload = unit.dateUpload,
                lastModifiedAt = unit.lastRead,
                version = 0L,
                isSyncing = false,
            )
            val id = chapterDao.insert(entity)
            Result.Success(id)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun getUnitsByItemId(itemId: Long): Result<List<ContentUnit>> {
        return try {
            val units = chapterDao.getChaptersByMangaId(itemId, false)
                .map { ChapterMapper.mapChapter(it).toContentUnit() }
            Result.Success(units)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override fun getUnitsByItemIdAsFlow(itemId: Long): Flow<Result<List<ContentUnit>>> {
        return chapterDao.getChaptersByMangaIdAsFlow(itemId, false).map { entities ->
            try {
                val units = entities.map { ChapterMapper.mapChapter(it).toContentUnit() }
                Result.Success(units)
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }

    override suspend fun updateUnit(unit: ContentUnit): Result<Boolean> {
        return try {
            val existing = chapterDao.getChapterById(unit.id)
            if (existing != null) {
                val updated = existing.copy(
                    mangaId = unit.contentItemId,
                    url = unit.url,
                    name = unit.title,
                    scanlator = unit.scanlator,
                    read = unit.read,
                    bookmark = unit.bookmark,
                    lastPageRead = unit.progress.toInt(),
                    chapterNumber = unit.number,
                    dateUpload = unit.dateUpload,
                    lastModifiedAt = unit.lastRead,
                )
                chapterDao.update(updated)
                Result.Success(true)
            } else {
                Result.Success(false)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
