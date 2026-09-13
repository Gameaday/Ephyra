package ephyra.data.sourcing

import ephyra.core.common.preference.PreferenceStore
import ephyra.data.room.daos.SourceProfileDao
import ephyra.data.room.entities.SourceProfileEntity
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceProfileStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Room-backed persistent store for [SourceProfile]s with automatic migration
 * from legacy [PreferenceStore] on first run.
 */
class RoomSourceProfileStore(
    private val dao: SourceProfileDao,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val legacyPreferenceStore: PreferenceStore? = null,
) : SourceProfileStore {

    private val migrationMutex = Mutex()
    private var migrationChecked = false

    private companion object {
        val DEFAULT_DOMAINS = setOf(
            "https://mangadex.org",
            "https://manganato.com",
            "https://asuratoons.com",
        )
    }

    private suspend fun ensureMigrated() {
        if (migrationChecked || legacyPreferenceStore == null) return
        migrationMutex.withLock {
            if (migrationChecked) return
            try {
                val existingCount = dao.getAll().size
                if (existingCount == 0) {
                    val legacyDomains = legacyPreferenceStore.getStringSet("profiled_domains_list", emptySet()).get()
                    for (domain in legacyDomains) {
                        val key = cacheKey(domain)
                        val encoded = legacyPreferenceStore.getString(key, "").get()
                        if (encoded.isNotBlank()) {
                            try {
                                val entity = json.decodeFromString<SourceProfileEntity>(encoded)
                                dao.upsert(entity)
                            } catch (e: Exception) {
                                // Skip corrupted legacy entries
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // If migration fails, proceed without blocking database operations
            } finally {
                migrationChecked = true
            }
        }
    }

    override suspend fun get(baseUrl: String): SourceProfile? {
        ensureMigrated()
        return dao.get(baseUrl)?.toDomain(json)
    }

    override suspend fun save(profile: SourceProfile) {
        ensureMigrated()
        val entity = SourceProfileEntity.fromDomain(profile, json)
        dao.upsert(entity)
    }

    override suspend fun invalidate(baseUrl: String) {
        ensureMigrated()
        dao.delete(baseUrl)
    }

    override suspend fun getAllProfiledDomains(): Set<String> {
        ensureMigrated()
        val urls = dao.getAllBaseUrls().toSet()
        return if (urls.isEmpty()) DEFAULT_DOMAINS else urls
    }

    override suspend fun exists(baseUrl: String): Boolean {
        ensureMigrated()
        return dao.exists(baseUrl)
    }

    override suspend fun getAll(): List<SourceProfile> {
        ensureMigrated()
        return dao.getAll().map { it.toDomain(json) }
    }

    private fun cacheKey(baseUrl: String): String {
        return "source_profile_" + baseUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
            .replace("/", "_")
            .replace(".", "_")
    }
}
