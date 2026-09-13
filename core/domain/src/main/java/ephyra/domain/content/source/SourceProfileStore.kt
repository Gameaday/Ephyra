package ephyra.domain.content.source

/**
 * Interface for persistent storage of [SourceProfile] instances.
 */
interface SourceProfileStore {
    suspend fun get(baseUrl: String): SourceProfile?
    suspend fun save(profile: SourceProfile)
    suspend fun invalidate(baseUrl: String)
    suspend fun getAllProfiledDomains(): Set<String>
    suspend fun exists(baseUrl: String): Boolean
    suspend fun getAll(): List<SourceProfile>
}
