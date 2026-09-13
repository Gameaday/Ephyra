package ephyra.data.room.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import ephyra.data.room.entities.SourceProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceProfileDao {
    @Query("SELECT * FROM source_profiles")
    fun subscribeAll(): Flow<List<SourceProfileEntity>>

    @Query("SELECT * FROM source_profiles")
    suspend fun getAll(): List<SourceProfileEntity>

    @Query("SELECT * FROM source_profiles WHERE base_url = :baseUrl LIMIT 1")
    suspend fun get(baseUrl: String): SourceProfileEntity?

    @Upsert
    suspend fun upsert(profile: SourceProfileEntity)

    @Query("DELETE FROM source_profiles WHERE base_url = :baseUrl")
    suspend fun delete(baseUrl: String)

    @Query("SELECT EXISTS(SELECT 1 FROM source_profiles WHERE base_url = :baseUrl LIMIT 1)")
    suspend fun exists(baseUrl: String): Boolean

    @Query("SELECT base_url FROM source_profiles")
    suspend fun getAllBaseUrls(): List<String>
}
