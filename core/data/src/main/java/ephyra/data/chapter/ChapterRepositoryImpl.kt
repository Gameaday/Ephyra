package ephyra.data.chapter

import ephyra.core.common.di.IoDispatcher
import ephyra.core.common.util.system.logcat
import ephyra.data.room.daos.ChapterDao
import ephyra.data.room.entities.ChapterEntity
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.model.ChapterUpdate
import ephyra.domain.chapter.repository.ChapterRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import logcat.LogPriority
import javax.inject.Inject

class ChapterRepositoryImpl @Inject constructor(
    private val chapterDao: ChapterDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ChapterRepository {

    override suspend fun addAll(chapters: List<Chapter>): List<Chapter> = withContext(ioDispatcher) {
        if (chapters.isEmpty()) return@withContext emptyList()
        try {
            val entities = chapters.map { chapter ->
                ChapterEntity(
                    id = 0,
                    mangaId = chapter.mangaId,
                    url = chapter.url,
                    name = chapter.name,
                    scanlator = chapter.scanlator,
                    read = chapter.read,
                    bookmark = chapter.bookmark,
                    lastPageRead = chapter.lastPageRead.toInt(),
                    chapterNumber = chapter.chapterNumber,
                    sourceOrder = chapter.sourceOrder.toInt(),
                    dateFetch = chapter.dateFetch,
                    dateUpload = chapter.dateUpload,
                    lastModifiedAt = chapter.lastModifiedAt,
                    version = chapter.version,
                    isSyncing = false,
                )
            }
            val ids = chapterDao.insertAll(entities)
            chapters.zip(ids) { chapter, id -> chapter.copy(id = id) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    override suspend fun update(chapterUpdate: ChapterUpdate): Unit = withContext(ioDispatcher) {
        partialUpdate(chapterUpdate)
    }

    override suspend fun updateAll(chapterUpdates: List<ChapterUpdate>): Unit = withContext(ioDispatcher) {
        partialUpdate(*chapterUpdates.toTypedArray())
    }

    private suspend fun partialUpdate(vararg chapterUpdates: ChapterUpdate) = withContext(ioDispatcher) {
        if (chapterUpdates.isEmpty()) return@withContext
        val ids = chapterUpdates.map { it.id }
        val existingMap = chapterDao.getChaptersByIds(ids).associateBy { it.id }
        val updatedList = chapterUpdates.mapNotNull { chapterUpdate ->
            val existing = existingMap[chapterUpdate.id] ?: return@mapNotNull null
            existing.copy(
                mangaId = chapterUpdate.mangaId ?: existing.mangaId,
                url = chapterUpdate.url ?: existing.url,
                name = chapterUpdate.name ?: existing.name,
                scanlator = chapterUpdate.scanlator ?: existing.scanlator,
                read = chapterUpdate.read ?: existing.read,
                bookmark = chapterUpdate.bookmark ?: existing.bookmark,
                lastPageRead = chapterUpdate.lastPageRead?.toInt() ?: existing.lastPageRead,
                chapterNumber = chapterUpdate.chapterNumber ?: existing.chapterNumber,
                sourceOrder = chapterUpdate.sourceOrder?.toInt() ?: existing.sourceOrder,
                dateFetch = chapterUpdate.dateFetch ?: existing.dateFetch,
                dateUpload = chapterUpdate.dateUpload ?: existing.dateUpload,
                version = chapterUpdate.version ?: existing.version,
                isSyncing = false,
            )
        }
        if (updatedList.isNotEmpty()) {
            chapterDao.updateAll(updatedList)
        }
    }

    override suspend fun removeChaptersWithIds(chapterIds: List<Long>): Unit = withContext(ioDispatcher) {
        try {
            chapterDao.removeChaptersWithIds(chapterIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override suspend fun getChapterByMangaId(mangaId: Long, applyScanlatorFilter: Boolean): List<Chapter> = withContext(
        ioDispatcher,
    ) {
        chapterDao.getChaptersByMangaId(mangaId, applyScanlatorFilter).map(ChapterMapper::mapChapter)
    }

    override suspend fun getScanlatorsByMangaId(mangaId: Long): List<String> = withContext(ioDispatcher) {
        chapterDao.getScanlatorsByMangaId(mangaId)
    }

    override fun getScanlatorsByMangaIdAsFlow(mangaId: Long): Flow<List<String>> {
        return chapterDao.getScanlatorsByMangaIdAsFlow(mangaId).flowOn(ioDispatcher)
    }

    override suspend fun getBookmarkedChaptersByMangaId(mangaId: Long): List<Chapter> = withContext(ioDispatcher) {
        chapterDao.getBookmarkedChaptersByMangaId(mangaId).map(ChapterMapper::mapChapter)
    }

    override suspend fun getChapterById(id: Long): Chapter? = withContext(ioDispatcher) {
        chapterDao.getChapterById(id)?.let(ChapterMapper::mapChapter)
    }

    override suspend fun getChapterByMangaIdAsFlow(mangaId: Long, applyScanlatorFilter: Boolean): Flow<List<Chapter>> {
        return chapterDao.getChaptersByMangaIdAsFlow(mangaId, applyScanlatorFilter)
            .map { chapters -> chapters.map(ChapterMapper::mapChapter) }
            .flowOn(ioDispatcher)
    }

    override suspend fun getChapterByUrlAndMangaId(
        url: String,
        mangaId: Long,
    ): Chapter? = withContext(ioDispatcher) {
        chapterDao.getChapterByUrlAndMangaId(url, mangaId)?.let(ChapterMapper::mapChapter)
    }

    override suspend fun getChapterByUrl(url: String): Chapter? = withContext(ioDispatcher) {
        chapterDao.getChapterByUrl(url)?.let(ChapterMapper::mapChapter)
    }
}
