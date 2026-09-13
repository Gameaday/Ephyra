package ephyra.data.room.daos

import androidx.paging.PagingSource
import androidx.room.*
import ephyra.data.room.entities.CategoryEntity
import ephyra.data.room.entities.MangaCategoryEntity
import ephyra.data.room.entities.MangaEntity
import ephyra.data.room.views.LibraryView
import kotlinx.coroutines.flow.Flow

data class SourceWithCountRecord(
    val source: Long,
    val count: Long,
)

data class MangaSourceUrlRecord(
    val source: Long,
    val url: String,
)

@Dao
interface MangaDao {

    @Query("SELECT * FROM mangas WHERE _id = :id")
    suspend fun getMangaById(id: Long): MangaEntity?

    @Query("SELECT * FROM mangas WHERE _id IN (:ids)")
    suspend fun getMangaByIds(ids: List<Long>): List<MangaEntity>

    @Query("SELECT * FROM mangas WHERE _id = :id")
    fun getMangaByIdAsFlow(id: Long): Flow<MangaEntity?>

    @Query("SELECT favorite FROM mangas WHERE _id = :id")
    suspend fun isMangaFavorite(id: Long): Boolean?

    @Query("SELECT * FROM mangas WHERE url = :url AND source = :source LIMIT 1")
    suspend fun getMangaByUrlAndSource(url: String, source: Long): MangaEntity?

    @Query("SELECT * FROM mangas WHERE url = :url AND source = :source LIMIT 1")
    fun getMangaByUrlAndSourceAsFlow(url: String, source: Long): Flow<MangaEntity?>

    @Query("SELECT * FROM mangas WHERE favorite = 1")
    suspend fun getFavorites(): List<MangaEntity>

    @Query("SELECT source, url FROM mangas")
    suspend fun getAllMangaSourceAndUrl(): List<MangaSourceUrlRecord>

    @Query("SELECT * FROM libraryView")
    fun getLibraryMangaAsFlow(): Flow<List<LibraryView>>

    @Query("SELECT * FROM libraryView")
    suspend fun getLibraryManga(): List<LibraryView>

    @Query("SELECT * FROM mangas WHERE favorite = 1 AND canonical_id = :canonicalId AND _id != :excludeMangaId")
    suspend fun getFavoritesByCanonicalId(canonicalId: String, excludeMangaId: Long): List<MangaEntity>

    @Query("SELECT * FROM mangas WHERE favorite = 1 AND dead_since < :deadSinceBefore")
    suspend fun getFavoritesByDeadSinceBefore(deadSinceBefore: Long): List<MangaEntity>

    @Query("SELECT * FROM mangas WHERE favorite = 0 AND _id IN (SELECT DISTINCT manga_id FROM chapters WHERE read = 1)")
    suspend fun getReadMangaNotInLibrary(): List<MangaEntity>

    @Query("SELECT * FROM mangas WHERE favorite = 1 AND source = :sourceId")
    fun getFavoritesBySourceIdAsFlow(sourceId: Long): Flow<List<MangaEntity>>

    @Query("SELECT * FROM mangas WHERE _id != :id AND title = :title AND favorite = 1")
    suspend fun getDuplicateLibraryManga(id: Long, title: String): List<MangaEntity>

    @Query("SELECT * FROM mangas WHERE next_update > 0 AND next_update < :epochMillis AND status IN (:statuses)")
    fun getUpcomingMangaAsFlow(epochMillis: Long, statuses: Set<Long>): Flow<List<MangaEntity>>

    @Query("UPDATE mangas SET viewer = 0")
    suspend fun resetViewerFlags()

    @Query("UPDATE mangas SET metadata_source = NULL, metadata_url = NULL WHERE _id = :mangaId")
    suspend fun clearMetadataSource(mangaId: Long)

    @Query("UPDATE mangas SET canonical_id = NULL WHERE _id = :mangaId")
    suspend fun clearCanonicalId(mangaId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(manga: MangaEntity): Long

    @Update
    suspend fun update(manga: MangaEntity)

    @Update
    suspend fun updateAll(mangas: List<MangaEntity>)

    @Query("DELETE FROM mangas WHERE favorite = 0 AND source IN (:sourceIds)")
    suspend fun deleteNonLibraryManga(sourceIds: List<Long>)

    @Query("DELETE FROM mangas WHERE _id = :id")
    suspend fun deleteMangaById(id: Long)

    @Query("DELETE FROM mangas_categories WHERE manga_id = :mangaId")
    suspend fun deleteMangaCategoriesByMangaId(mangaId: Long)

    @Insert
    suspend fun insertMangaCategory(mangaCategory: MangaCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMangaCategories(mangaCategories: List<MangaCategoryEntity>)

    @Transaction
    suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        deleteMangaCategoriesByMangaId(mangaId)
        if (categoryIds.isNotEmpty()) {
            insertMangaCategories(categoryIds.map { categoryId -> MangaCategoryEntity(0, mangaId, categoryId) })
        }
    }

    @Query("SELECT source, count(*) as count FROM mangas WHERE favorite = 1 GROUP BY source")
    fun getSourceIdWithFavoriteCount(): Flow<List<SourceWithCountRecord>>

    @Query(
        "SELECT source, count(*) as count FROM mangas WHERE favorite = 0 AND _id IN (SELECT DISTINCT manga_id FROM chapters WHERE read = 1) GROUP BY source",
    )
    fun getSourceIdsWithNonLibraryManga(): Flow<List<SourceWithCountRecord>>

    @Transaction
    suspend fun upsert(manga: MangaEntity): Long {
        val existing = getMangaByUrlAndSource(manga.url, manga.source)
        return if (existing != null) {
            val merged = existing.copy(
                title = if (existing.favorite &&
                    ephyra.domain.manga.model.LockedField.isLocked(
                        existing.lockedFields,
                        ephyra.domain.manga.model.LockedField.TITLE,
                    )
                ) {
                    existing.title
                } else {
                    manga.title.takeIf { it.isNotBlank() } ?: existing.title
                },
                thumbnailUrl = if (existing.favorite &&
                    ephyra.domain.manga.model.LockedField.isLocked(
                        existing.lockedFields,
                        ephyra.domain.manga.model.LockedField.COVER,
                    )
                ) {
                    existing.thumbnailUrl
                } else {
                    manga.thumbnailUrl ?: existing.thumbnailUrl
                },
                artist = if (existing.favorite &&
                    ephyra.domain.manga.model.LockedField.isLocked(
                        existing.lockedFields,
                        ephyra.domain.manga.model.LockedField.ARTIST,
                    )
                ) {
                    existing.artist
                } else {
                    manga.artist ?: existing.artist
                },
                author = if (existing.favorite &&
                    ephyra.domain.manga.model.LockedField.isLocked(
                        existing.lockedFields,
                        ephyra.domain.manga.model.LockedField.AUTHOR,
                    )
                ) {
                    existing.author
                } else {
                    manga.author ?: existing.author
                },
                description = if (existing.favorite &&
                    ephyra.domain.manga.model.LockedField.isLocked(
                        existing.lockedFields,
                        ephyra.domain.manga.model.LockedField.DESCRIPTION,
                    )
                ) {
                    existing.description
                } else {
                    manga.description ?: existing.description
                },
                genre = if (existing.favorite &&
                    ephyra.domain.manga.model.LockedField.isLocked(
                        existing.lockedFields,
                        ephyra.domain.manga.model.LockedField.GENRE,
                    )
                ) {
                    existing.genre
                } else {
                    manga.genre ?: existing.genre
                },
                status = if (existing.favorite &&
                    ephyra.domain.manga.model.LockedField.isLocked(
                        existing.lockedFields,
                        ephyra.domain.manga.model.LockedField.STATUS,
                    )
                ) {
                    existing.status
                } else {
                    if (manga.status != 0L) manga.status else existing.status
                },
                updateStrategy = if (manga.updateStrategy != 0) manga.updateStrategy else existing.updateStrategy,
                initialized = existing.initialized || manga.initialized,
                favorite = existing.favorite || manga.favorite,
                dateAdded = if (existing.dateAdded != 0L) existing.dateAdded else manga.dateAdded,
            )
            if (merged != existing) {
                update(merged)
            }
            existing.id
        } else {
            insert(manga)
        }
    }

    @Transaction
    suspend fun upsertAll(mangas: List<MangaEntity>): List<Long> {
        return mangas.map { upsert(it) }
    }

    @Transaction
    suspend fun insertNetworkManga(entities: List<MangaEntity>): List<MangaEntity> {
        return entities.map { manga ->
            val existing = getMangaByUrlAndSource(manga.url, manga.source)
            if (existing != null) {
                val merged = existing.copy(
                    title = if (existing.favorite &&
                        ephyra.domain.manga.model.LockedField.isLocked(
                            existing.lockedFields,
                            ephyra.domain.manga.model.LockedField.TITLE,
                        )
                    ) {
                        existing.title
                    } else {
                        manga.title.takeIf { it.isNotBlank() } ?: existing.title
                    },
                    thumbnailUrl = if (existing.favorite &&
                        ephyra.domain.manga.model.LockedField.isLocked(
                            existing.lockedFields,
                            ephyra.domain.manga.model.LockedField.COVER,
                        )
                    ) {
                        existing.thumbnailUrl
                    } else {
                        manga.thumbnailUrl ?: existing.thumbnailUrl
                    },
                    author = if (existing.favorite &&
                        ephyra.domain.manga.model.LockedField.isLocked(
                            existing.lockedFields,
                            ephyra.domain.manga.model.LockedField.AUTHOR,
                        )
                    ) {
                        existing.author
                    } else {
                        manga.author ?: existing.author
                    },
                    artist = if (existing.favorite &&
                        ephyra.domain.manga.model.LockedField.isLocked(
                            existing.lockedFields,
                            ephyra.domain.manga.model.LockedField.ARTIST,
                        )
                    ) {
                        existing.artist
                    } else {
                        manga.artist ?: existing.artist
                    },
                    description = if (existing.favorite &&
                        ephyra.domain.manga.model.LockedField.isLocked(
                            existing.lockedFields,
                            ephyra.domain.manga.model.LockedField.DESCRIPTION,
                        )
                    ) {
                        existing.description
                    } else {
                        manga.description ?: existing.description
                    },
                    genre = if (existing.favorite &&
                        ephyra.domain.manga.model.LockedField.isLocked(
                            existing.lockedFields,
                            ephyra.domain.manga.model.LockedField.GENRE,
                        )
                    ) {
                        existing.genre
                    } else {
                        manga.genre ?: existing.genre
                    },
                    status = if (existing.favorite &&
                        ephyra.domain.manga.model.LockedField.isLocked(
                            existing.lockedFields,
                            ephyra.domain.manga.model.LockedField.STATUS,
                        )
                    ) {
                        existing.status
                    } else {
                        if (manga.status != 0L) manga.status else existing.status
                    },
                    updateStrategy = if (manga.updateStrategy != 0) manga.updateStrategy else existing.updateStrategy,
                    initialized = existing.initialized || manga.initialized,
                )
                if (merged != existing) {
                    update(merged)
                }
                merged
            } else {
                val newId = insert(manga)
                manga.copy(id = newId)
            }
        }
    }
}
