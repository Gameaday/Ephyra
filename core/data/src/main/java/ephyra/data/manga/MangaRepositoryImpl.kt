package ephyra.data.manga

import ephyra.core.common.di.IoDispatcher
import ephyra.core.common.util.system.logcat
import ephyra.data.room.daos.MangaDao
import ephyra.data.room.entities.MangaEntity
import ephyra.domain.library.model.LibraryManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaNotFoundException
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.model.MangaWithChapterCount
import ephyra.domain.manga.repository.MangaRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import logcat.LogPriority
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class MangaRepositoryImpl @Inject constructor(
    private val mangaDao: MangaDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MangaRepository {

    override suspend fun getMangaById(id: Long): Manga = withContext(ioDispatcher) {
        mangaDao.getMangaById(id)?.let(MangaMapper::mapManga) ?: throw MangaNotFoundException(id)
    }

    override suspend fun isMangaFavorite(id: Long): Boolean = withContext(ioDispatcher) {
        mangaDao.isMangaFavorite(id) ?: false
    }

    override suspend fun getMangaByIdAsFlow(id: Long): Flow<Manga> {
        return mangaDao.getMangaByIdAsFlow(id)
            .map { it?.let(MangaMapper::mapManga) ?: throw MangaNotFoundException(id) }
            .flowOn(ioDispatcher)
    }

    override suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga? = withContext(ioDispatcher) {
        mangaDao.getMangaByUrlAndSource(url, sourceId)?.let(MangaMapper::mapManga)
    }

    override fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Manga?> {
        return mangaDao.getMangaByUrlAndSourceAsFlow(url, sourceId)
            .map { it?.let(MangaMapper::mapManga) }
            .flowOn(ioDispatcher)
    }

    override suspend fun getFavoritesByCanonicalId(
        canonicalId: String,
        excludeMangaId: Long,
    ): List<Manga> = withContext(ioDispatcher) {
        mangaDao.getFavoritesByCanonicalId(canonicalId, excludeMangaId).map(MangaMapper::mapManga)
    }

    override suspend fun getDeadFavorites(deadSinceBefore: Long): List<Manga> = withContext(ioDispatcher) {
        mangaDao.getFavoritesByDeadSinceBefore(deadSinceBefore).map(MangaMapper::mapManga)
    }

    override suspend fun getFavorites(): List<Manga> = withContext(ioDispatcher) {
        mangaDao.getFavorites().map(MangaMapper::mapManga)
    }

    override suspend fun getReadMangaNotInLibrary(): List<Manga> = withContext(ioDispatcher) {
        mangaDao.getReadMangaNotInLibrary().map(MangaMapper::mapManga)
    }

    override suspend fun getLibraryManga(): List<LibraryManga> = withContext(ioDispatcher) {
        mangaDao.getLibraryManga().map(MangaMapper::mapLibraryManga)
    }

    override fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>> {
        return mangaDao.getLibraryMangaAsFlow()
            .map { list -> list.map(MangaMapper::mapLibraryManga) }
            .flowOn(ioDispatcher)
    }

    override fun getFavoritesBySourceId(sourceId: Long): Flow<List<Manga>> {
        return mangaDao.getFavoritesBySourceIdAsFlow(sourceId)
            .map { list -> list.map(MangaMapper::mapManga) }
            .flowOn(ioDispatcher)
    }

    override suspend fun getDuplicateLibraryManga(
        id: Long,
        title: String,
    ): List<MangaWithChapterCount> = withContext(ioDispatcher) {
        mangaDao.getDuplicateLibraryManga(id, title)
            .map { MangaWithChapterCount(MangaMapper.mapManga(it), 0) }
    }

    override suspend fun getUpcomingManga(statuses: Set<Long>): Flow<List<Manga>> {
        val epochMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
        return mangaDao.getUpcomingMangaAsFlow(epochMillis, statuses)
            .map { list -> list.map(MangaMapper::mapManga) }
            .flowOn(ioDispatcher)
    }

    override suspend fun resetViewerFlags(): Boolean = withContext(ioDispatcher) {
        try {
            mangaDao.resetViewerFlags()
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun setMangaCategories(
        mangaId: Long,
        categoryIds: List<Long>,
    ): Unit = withContext(ioDispatcher) {
        mangaDao.setMangaCategories(mangaId, categoryIds)
    }

    override suspend fun update(update: MangaUpdate): Boolean = withContext(ioDispatcher) {
        try {
            partialUpdate(update)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun updateAll(mangaUpdates: List<MangaUpdate>): Boolean = withContext(ioDispatcher) {
        try {
            partialUpdate(*mangaUpdates.toTypedArray())
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun clearMetadataSource(mangaId: Long): Boolean = withContext(ioDispatcher) {
        try {
            mangaDao.clearMetadataSource(mangaId)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun clearCanonicalId(mangaId: Long): Boolean = withContext(ioDispatcher) {
        try {
            mangaDao.clearCanonicalId(mangaId)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun insertNetworkManga(manga: List<Manga>): List<Manga> = withContext(ioDispatcher) {
        if (manga.isEmpty()) return@withContext emptyList()
        val entities = manga.map {
            MangaEntity(
                id = 0,
                source = it.source,
                url = it.url,
                artist = it.artist,
                author = it.author,
                description = it.description,
                genre = it.genre,
                title = it.title,
                status = it.status,
                thumbnailUrl = it.thumbnailUrl,
                favorite = it.favorite,
                lastUpdate = it.lastUpdate,
                nextUpdate = it.nextUpdate,
                initialized = it.initialized,
                viewerFlags = it.viewerFlags,
                chapterFlags = it.chapterFlags,
                coverLastModified = it.coverLastModified,
                dateAdded = it.dateAdded,
                updateStrategy = it.updateStrategy.ordinal,
                calculateInterval = it.fetchInterval,
                lastModifiedAt = it.lastModifiedAt,
                favoriteModifiedAt = it.favoriteModifiedAt,
                version = it.version,
                isSyncing = false,
                notes = it.notes,
                metadataSource = it.metadataSource,
                metadataUrl = it.metadataUrl,
                canonicalId = it.canonicalId,
                sourceStatus = it.sourceStatus,
                alternativeTitles = MangaMapper.serializeAlternativeTitles(it.alternativeTitles),
                deadSince = it.deadSince,
                contentType = it.contentType.value,
                lockedFields = it.lockedFields,
            )
        }
        val ids = mangaDao.upsertAll(entities)
        manga.zip(ids) { item, id -> item.copy(id = id) }
    }

    private suspend fun partialUpdate(vararg mangaUpdates: MangaUpdate) = withContext(ioDispatcher) {
        if (mangaUpdates.isEmpty()) return@withContext
        val ids = mangaUpdates.map { it.id }
        val existingMap = mangaDao.getMangaByIds(ids).associateBy { it.id }
        val updatedList = mangaUpdates.mapNotNull { value ->
            val existing = existingMap[value.id] ?: return@mapNotNull null
            existing.copy(
                source = value.source ?: existing.source,
                url = value.url ?: existing.url,
                artist = value.artist ?: existing.artist,
                author = value.author ?: existing.author,
                description = value.description ?: existing.description,
                genre = value.genre ?: existing.genre,
                title = value.title ?: existing.title,
                status = value.status ?: existing.status,
                thumbnailUrl = value.thumbnailUrl ?: existing.thumbnailUrl,
                favorite = value.favorite ?: existing.favorite,
                lastUpdate = value.lastUpdate ?: existing.lastUpdate,
                nextUpdate = value.nextUpdate ?: existing.nextUpdate,
                calculateInterval = value.fetchInterval ?: existing.calculateInterval,
                initialized = value.initialized ?: existing.initialized,
                viewerFlags = value.viewerFlags ?: existing.viewerFlags,
                chapterFlags = value.chapterFlags ?: existing.chapterFlags,
                coverLastModified = value.coverLastModified ?: existing.coverLastModified,
                dateAdded = value.dateAdded ?: existing.dateAdded,
                updateStrategy = value.updateStrategy?.ordinal ?: existing.updateStrategy,
                version = value.version ?: existing.version,
                notes = value.notes ?: existing.notes,
                metadataSource = value.metadataSource ?: existing.metadataSource,
                metadataUrl = value.metadataUrl ?: existing.metadataUrl,
                canonicalId = value.canonicalId ?: existing.canonicalId,
                sourceStatus = value.sourceStatus ?: existing.sourceStatus,
                alternativeTitles = value.alternativeTitles?.let { MangaMapper.serializeAlternativeTitles(it) }
                    ?: existing.alternativeTitles,
                deadSince = value.deadSince ?: existing.deadSince,
                contentType = value.contentType?.value ?: existing.contentType,
                lockedFields = value.lockedFields ?: existing.lockedFields,
            )
        }
        if (updatedList.isNotEmpty()) {
            mangaDao.updateAll(updatedList)
        }
    }

    override suspend fun deleteNonLibraryManga(
        sourceIds: List<Long>,
        keepReadManga: Long,
    ): Unit = withContext(ioDispatcher) {
        mangaDao.deleteNonLibraryManga(sourceIds)
    }

    override suspend fun getAllMangaSourceAndUrl(): List<Pair<Long, String>> = withContext(ioDispatcher) {
        mangaDao.getAllMangaSourceAndUrl().map { Pair(it.source, it.url) }
    }
}
