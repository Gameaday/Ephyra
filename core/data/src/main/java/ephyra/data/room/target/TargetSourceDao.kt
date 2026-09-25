package ephyra.data.room.target

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TargetSourceDao {
    @Query("SELECT * FROM target_sources WHERE source_id = :sourceId LIMIT 1")
    suspend fun get(sourceId: String): TargetSourceEntity?

    @Query("SELECT * FROM target_sources ORDER BY display_name, source_id")
    suspend fun getAll(): List<TargetSourceEntity>

    @Query(
        "SELECT * FROM target_sources WHERE installation_state = 'INSTALLED' AND enabled = 1 ORDER BY display_name, source_id",
    )
    fun observeEnabled(): Flow<List<TargetSourceEntity>>

    @Upsert
    suspend fun upsert(source: TargetSourceEntity)
}
