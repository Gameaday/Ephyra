package ephyra.data.sourcing

import ephyra.data.room.daos.SourceProfileDao
import ephyra.data.room.entities.SourceProfileEntity
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceProfileStore
import kotlinx.serialization.json.Json

/**
 * Room-backed persistent store for [SourceProfile]s.
 */
class RoomSourceProfileStore(
    private val dao: SourceProfileDao,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SourceProfileStore {

    private companion object {
        val DEFAULT_DOMAINS = setOf(
            "https://mangadex.org",
            "https://manganato.com",
            "https://asuratoons.com",
        )
    }

    override suspend fun get(baseUrl: String): SourceProfile? {
        return dao.get(baseUrl)?.toDomain(json)
    }

    override suspend fun save(profile: SourceProfile) {
        val entity = SourceProfileEntity.fromDomain(profile, json)
        dao.upsert(entity)
    }

    override suspend fun invalidate(baseUrl: String) {
        dao.delete(baseUrl)
    }

    override suspend fun getAllProfiledDomains(): Set<String> {
        val urls = dao.getAllBaseUrls().toSet()
        return if (urls.isEmpty()) DEFAULT_DOMAINS else urls
    }

    override suspend fun exists(baseUrl: String): Boolean {
        return dao.exists(baseUrl)
    }

    override suspend fun getAll(): List<SourceProfile> {
        return dao.getAll().map { it.toDomain(json) }
    }
}
