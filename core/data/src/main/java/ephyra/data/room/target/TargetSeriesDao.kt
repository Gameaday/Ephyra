package ephyra.data.room.target

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Isolated target persistence contract. This DAO is intentionally not exposed by
 * [ephyra.data.room.EphyraDatabase] until the migration matrix is approved.
 */
@Dao
interface TargetSeriesDao {

    @Query("SELECT * FROM target_series WHERE localId = :localId LIMIT 1")
    suspend fun getSeries(localId: String): TargetSeriesEntity?

    @Query("SELECT * FROM target_series_sources WHERE sourceId = :sourceId AND externalId = :externalId LIMIT 1")
    suspend fun getSeriesByExternalId(sourceId: String, externalId: String): TargetSeriesSourceEntity?

    @Query("SELECT * FROM target_series_sources WHERE sourceId = :sourceId AND url = :url LIMIT 1")
    suspend fun getSeriesByUrl(sourceId: String, url: String): TargetSeriesSourceEntity?

    @Query("SELECT * FROM target_library_entries WHERE seriesId = :seriesId LIMIT 1")
    fun observeLibraryEntry(seriesId: String): Flow<TargetLibraryEntryEntity?>

    @Query("SELECT * FROM target_series WHERE localId IN (:localIds)")
    suspend fun getSeriesBatch(localIds: List<String>): List<TargetSeriesEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSeries(series: TargetSeriesEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSourceReference(reference: TargetSeriesSourceEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLibraryEntry(entry: TargetLibraryEntryEntity)

    @Query("UPDATE target_library_entries SET updateEnabled = :enabled WHERE seriesId = :seriesId")
    suspend fun setLibraryUpdateEnabled(seriesId: String, enabled: Boolean)

    @Transaction
    suspend fun addToLibrary(
        series: TargetSeriesEntity,
        reference: TargetSeriesSourceEntity,
        entry: TargetLibraryEntryEntity,
    ) {
        insertSeries(series)
        insertSourceReference(reference)
        insertLibraryEntry(entry)
    }
}
