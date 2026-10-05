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
        // No fallback. This used to substitute a hardcoded trio of real sites whenever the table was
        // empty, which made "no sources configured" indistinguishable from "these three" — and it
        // named sites the app could not actually serve. With `ADR-0015` removing the only engine that
        // could create a profile, the table is always empty, so the fallback was permanently the
        // answer rather than a fallback at all.
        return dao.getAllBaseUrls().toSet()
    }

    override suspend fun exists(baseUrl: String): Boolean {
        return dao.exists(baseUrl)
    }

    override suspend fun getAll(): List<SourceProfile> {
        return dao.getAll().map { it.toDomain(json) }
    }
}
