package ephyra.data.room.target

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Isolated target persistence contract. This DAO is intentionally not exposed by
 * [ephyra.data.room.EphyraDatabase] until the migration matrix is approved.
 */
@Dao
interface TargetSeriesDao {

    @Query("SELECT * FROM target_series WHERE local_id = :localId LIMIT 1")
    suspend fun getSeries(localId: String): TargetSeriesEntity?

    @Query("SELECT * FROM target_series_sources WHERE source_id = :sourceId AND external_id = :externalId LIMIT 1")
    suspend fun getSeriesByExternalId(sourceId: String, externalId: String): TargetSeriesSourceEntity?

    @Query("SELECT * FROM target_series_sources WHERE source_id = :sourceId AND url = :url LIMIT 1")
    suspend fun getSeriesByUrl(sourceId: String, url: String): TargetSeriesSourceEntity?

    @Query("SELECT * FROM target_library_entries WHERE series_id = :seriesId LIMIT 1")
    suspend fun getLibraryEntry(seriesId: String): TargetLibraryEntryEntity?

    @Query("SELECT * FROM target_series_sources WHERE series_id = :seriesId AND source_id = :sourceId LIMIT 1")
    suspend fun getSourceReference(seriesId: String, sourceId: String): TargetSeriesSourceEntity?

    @Query("SELECT * FROM target_series_sources WHERE series_id = :seriesId ORDER BY source_id")
    suspend fun getSourceReferences(seriesId: String): List<TargetSeriesSourceEntity>

    @Query("SELECT * FROM target_series_source_links WHERE link_id = :linkId LIMIT 1")
    suspend fun getLink(linkId: String): TargetSeriesLinkEntity?

    @Query("SELECT * FROM target_series_source_links WHERE canonical_series_id = :seriesId ORDER BY created_at DESC")
    suspend fun getLinksForSeries(seriesId: String): List<TargetSeriesLinkEntity>

    @Upsert
    suspend fun upsertLink(link: TargetSeriesLinkEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLinkIfMissing(link: TargetSeriesLinkEntity): Long

    @Query("DELETE FROM target_library_entries WHERE series_id = :seriesId")
    suspend fun deleteLibraryEntry(seriesId: String)

    @Query("SELECT * FROM target_library_entries WHERE series_id = :seriesId LIMIT 1")
    fun observeLibraryEntry(seriesId: String): Flow<TargetLibraryEntryEntity?>

    @Query("SELECT * FROM target_series WHERE local_id IN (:localIds)")
    suspend fun getSeriesBatch(localIds: List<String>): List<TargetSeriesEntity>

    @Query("SELECT * FROM target_chapters WHERE series_id = :seriesId ORDER BY sort_key")
    suspend fun getChapters(seriesId: String): List<TargetChapterEntity>

    @Query("SELECT * FROM target_chapter_states WHERE chapter_id = :chapterId LIMIT 1")
    suspend fun getChapterState(chapterId: String): TargetChapterStateEntity?

    @Query("SELECT * FROM target_history WHERE target_chapter_local_id = :chapterId LIMIT 1")
    suspend fun getHistory(chapterId: String): TargetHistoryEntity?

    @Query("SELECT * FROM target_categories ORDER BY sort_order, category_id")
    suspend fun getCategories(): List<TargetCategoryEntity>

    @Query("SELECT * FROM target_series_categories WHERE series_id = :seriesId ORDER BY category_id")
    suspend fun getSeriesCategories(seriesId: String): List<TargetSeriesCategoryEntity>

    @Upsert
    suspend fun upsertCategory(category: TargetCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeriesCategoryIfMissing(category: TargetSeriesCategoryEntity): Long

    @Upsert
    suspend fun upsertSeries(series: TargetSeriesEntity)

    @Upsert
    suspend fun upsertSourceReference(reference: TargetSeriesSourceEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLibraryEntryIfMissing(entry: TargetLibraryEntryEntity): Long

    @Upsert
    suspend fun upsertChapter(chapter: TargetChapterEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChapterStateIfMissing(state: TargetChapterStateEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHistoryIfMissing(history: TargetHistoryEntity): Long

    @Query("UPDATE target_library_entries SET update_enabled = :enabled WHERE series_id = :seriesId")
    suspend fun setLibraryUpdateEnabled(seriesId: String, enabled: Boolean)
}
