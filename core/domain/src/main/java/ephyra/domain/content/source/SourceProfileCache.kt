package ephyra.domain.content.source

import ephyra.core.common.preference.PreferenceStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistent cache for discovered [SourceProfile]s.
 *
 * Once a source URL has been probed and profiled by any [ContentSourceEngine],
 * the profile is cached here so the heuristic discovery does not need to
 * run again on subsequent launches. Profiles can be invalidated when they
 * fail (e.g., the source changed its API).
 */
class SourceProfileCache(
    private val store: SourceProfileStore,
) : SourceProfileStore by store {

    /**
     * Legacy constructor providing [PreferenceStore] backed persistence.
     * Reserved for test fixtures. Production uses RoomSourceProfileStore.
     */
    @Deprecated("Use RoomSourceProfileStore for production persistence", ReplaceWith("SourceProfileCache(store)"))
    constructor(
        preferenceStore: PreferenceStore,
        json: Json = Json { ignoreUnknownKeys = true },
    ) : this(PreferenceSourceProfileStore(preferenceStore, json))
}

/**
 * Default [PreferenceStore]-backed implementation of [SourceProfileStore] used for
 * JVM unit testing in domain modules and fallback environments.
 */
class PreferenceSourceProfileStore(
    private val preferenceStore: PreferenceStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SourceProfileStore {
    private companion object {
        const val PREFIX = "source_profile_"
    }

    /**
     * Retrieve a cached profile for a source URL.
     * @return The cached [SourceProfile], or null if not found.
     */
    override suspend fun get(baseUrl: String): SourceProfile? {
        val key = cacheKey(baseUrl)
        val encoded = preferenceStore.getString(key, "").get()
        if (encoded.isBlank()) return null
        return try {
            json.decodeFromString<SerializableProfile>(encoded).toDomain()
        } catch (e: Exception) {
            null // Corrupted entry — will re-discover
        }
    }

    /**
     * Store a discovered [SourceProfile] for future use.
     */
    override suspend fun save(profile: SourceProfile) {
        val key = cacheKey(profile.baseUrl)
        preferenceStore.getString(key, "").set(
            json.encodeToString(SerializableProfile.fromDomain(profile)),
        )

        // Save to dynamic list of profiled domains
        val currentDomains = preferenceStore.getStringSet("profiled_domains_list", emptySet()).get()
        preferenceStore.getStringSet("profiled_domains_list", emptySet()).set(currentDomains + profile.baseUrl)
    }

    /**
     * Remove a cached profile (e.g., after a failure suggesting the API changed).
     */
    override suspend fun invalidate(baseUrl: String) {
        val key = cacheKey(baseUrl)
        preferenceStore.getString(key, "").delete()

        // Remove from dynamic list of profiled domains
        val currentDomains = preferenceStore.getStringSet("profiled_domains_list", emptySet()).get()
        preferenceStore.getStringSet("profiled_domains_list", emptySet()).set(currentDomains - baseUrl)
    }

    /**
     * Retrieve the domains that actually have a profile, from the preference alone.
     *
     * **The hardcoded fallback is gone.** This used to return mangadex.org / manganato.com /
     * asuratoons.com whenever the preference was empty. That was a hidden default doing two kinds of
     * damage at once: it made "no sources configured" indistinguishable from "these three", and it
     * named real sites the app could not actually serve. Since `ADR-0015` removed the only engine
     * that could create a profile, the real list is now *always* empty — so the fallback was not a
     * fallback at all, it was the permanent answer, and the Sources list showed three sources that
     * could never load.
     *
     * An empty set is the honest answer, and it is what the rest of the code already handles: the
     * sources list renders empty and the user installs an extension APK to populate it.
     */
    override suspend fun getAllProfiledDomains(): Set<String> {
        return preferenceStore.getStringSet("profiled_domains_list", emptySet()).get()
    }

    /** Check if a profile exists in cache. */
    override suspend fun exists(baseUrl: String): Boolean {
        val key = cacheKey(baseUrl)
        return preferenceStore.getString(key, "").isSet()
    }

    /**
     * Get all cached source profiles.
     */
    override suspend fun getAll(): List<SourceProfile> {
        val domains = getAllProfiledDomains()
        return domains.mapNotNull { get(it) }
    }

    private fun cacheKey(baseUrl: String): String {
        return PREFIX + baseUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
            .replace("/", "_")
            .replace(".", "_")
    }
}

/**
 * Serializable representation of [SourceProfile] for DataStore persistence.
 */
@Serializable
internal data class SerializableProfile(
    val baseUrl: String,
    val contentType: String,
    val sourceType: String = "HEURISTIC",
    val enabled: Boolean = true,
    val endpoints: Map<String, SerializableEndpointPattern> = emptyMap(),
    val responseType: String,
    val pagination: String = "PAGE_BASED",
    val selectors: Map<String, String>? = null,
    val jsonPath: Map<String, String>? = null,
    val headers: Map<String, String> = emptyMap(),
    val authType: String = "NONE",
    val rateLimitMs: Long = 0L,
    val displayName: String,
    val verified: Boolean = false,
    val lastHealthCheck: Long = 0,
    val lastUpdated: Long = 0,
    val failureCount: Int = 0,
    val repositoryId: String? = null,
) {
    companion object {
        fun fromDomain(profile: SourceProfile): SerializableProfile {
            return SerializableProfile(
                baseUrl = profile.baseUrl,
                contentType = profile.contentType.name,
                sourceType = profile.sourceType.name,
                enabled = profile.enabled,
                endpoints = profile.endpoints.mapKeys { it.key.name }.mapValues { (_, ep) ->
                    SerializableEndpointPattern(
                        pathTemplate = ep.pathTemplate,
                        method = ep.method.name,
                        responseType = ep.responseType.name,
                        bodyTemplate = ep.bodyTemplate,
                    )
                },
                responseType = profile.responseType.name,
                pagination = profile.pagination.name,
                selectors = profile.selectors?.mapKeys { it.key.name },
                jsonPath = profile.jsonPath?.mapKeys { it.key.name },
                headers = profile.headers,
                authType = profile.authType.name,
                rateLimitMs = profile.rateLimitMs,
                displayName = profile.displayName,
                verified = profile.verified,
                lastHealthCheck = profile.lastHealthCheck,
                lastUpdated = profile.lastUpdated,
                failureCount = profile.failureCount,
                repositoryId = profile.repositoryId,
            )
        }
    }

    fun toDomain(): SourceProfile {
        return SourceProfile(
            baseUrl = baseUrl,
            contentType = try {
                ephyra.domain.content.model.ContentType.valueOf(contentType)
            } catch (
                e: Exception,
            ) {
                ephyra.domain.content.model.ContentType.UNKNOWN
            },
            sourceType = try {
                SourceType.valueOf(sourceType)
            } catch (e: Exception) {
                // An unrecognised persisted name means a build older than this one wrote it, or a
                // retired type (`JS_SCRAPER`, `HEURISTIC`). Both map to the extension path via
                // `fromString`, which is also where the retired names are folded in deliberately.
                SourceType.fromString(sourceType)
            },
            enabled = enabled,
            endpoints = endpoints.mapKeys {
                try {
                    Endpoint.valueOf(it.key)
                } catch (
                    e: Exception,
                ) {
                    return@mapKeys null
                }
            }.filterKeys {
                it !=
                    null
            }.mapKeys { it.key!! }.mapValues { (_, ep) ->
                EndpointPattern(
                    pathTemplate = ep.pathTemplate,
                    method = try {
                        HttpMethod.valueOf(ep.method)
                    } catch (e: Exception) {
                        HttpMethod.GET
                    },
                    responseType = try {
                        ResponseType.valueOf(ep.responseType)
                    } catch (
                        e: Exception,
                    ) {
                        ResponseType.AUTO
                    },
                    bodyTemplate = ep.bodyTemplate,
                )
            },
            responseType = try {
                ResponseType.valueOf(responseType)
            } catch (e: Exception) {
                ResponseType.AUTO
            },
            pagination = try {
                PaginationType.valueOf(pagination)
            } catch (e: Exception) {
                PaginationType.PAGE_BASED
            },
            selectors = selectors?.mapKeys {
                try {
                    DataField.valueOf(it.key)
                } catch (
                    e: Exception,
                ) {
                    return@mapKeys null
                }
            }?.filterKeys {
                it !=
                    null
            }?.mapKeys { it.key!! },
            jsonPath = jsonPath?.mapKeys {
                try {
                    DataField.valueOf(it.key)
                } catch (
                    e: Exception,
                ) {
                    return@mapKeys null
                }
            }?.filterKeys {
                it !=
                    null
            }?.mapKeys { it.key!! },
            headers = headers,
            authType = try {
                AuthType.valueOf(authType)
            } catch (e: Exception) {
                AuthType.NONE
            },
            rateLimitMs = rateLimitMs,
            displayName = displayName,
            verified = verified,
            lastHealthCheck = lastHealthCheck,
            lastUpdated = lastUpdated,
            failureCount = failureCount,
            repositoryId = repositoryId,
        )
    }
}

@Serializable
internal data class SerializableEndpointPattern(
    val pathTemplate: String,
    val method: String = "GET",
    val responseType: String = "AUTO",
    val bodyTemplate: String? = null,
)
