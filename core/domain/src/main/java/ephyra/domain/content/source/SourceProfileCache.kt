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
    private val preferenceStore: PreferenceStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private companion object {
        const val PREFIX = "source_profile_"
    }

    /**
     * Retrieve a cached profile for a source URL.
     * @return The cached [SourceProfile], or null if not found.
     */
    suspend fun get(baseUrl: String): SourceProfile? {
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
    suspend fun save(profile: SourceProfile) {
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
    suspend fun invalidate(baseUrl: String) {
        val key = cacheKey(baseUrl)
        preferenceStore.getString(key, "").delete()

        // Remove from dynamic list of profiled domains
        val currentDomains = preferenceStore.getStringSet("profiled_domains_list", emptySet()).get()
        preferenceStore.getStringSet("profiled_domains_list", emptySet()).set(currentDomains - baseUrl)
    }

    /**
     * Retrieve all profiled domains dynamically, falling back to a pre-populated default set.
     */
    suspend fun getAllProfiledDomains(): Set<String> {
        val customList = preferenceStore.getStringSet("profiled_domains_list", emptySet()).get()
        if (customList.isEmpty()) {
            return setOf(
                "https://mangadex.org",
                "https://manganato.com",
                "https://asuratoons.com",
            )
        }
        return customList
    }

    /** Check if a profile exists in cache. */
    suspend fun exists(baseUrl: String): Boolean {
        val key = cacheKey(baseUrl)
        return preferenceStore.getString(key, "").isSet()
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
) {
    companion object {
        fun fromDomain(profile: SourceProfile): SerializableProfile {
            return SerializableProfile(
                baseUrl = profile.baseUrl,
                contentType = profile.contentType.name,
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
