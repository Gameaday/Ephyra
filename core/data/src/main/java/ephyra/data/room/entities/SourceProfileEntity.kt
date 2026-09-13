package ephyra.data.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.source.AuthType
import ephyra.domain.content.source.DataField
import ephyra.domain.content.source.Endpoint
import ephyra.domain.content.source.EndpointPattern
import ephyra.domain.content.source.HttpMethod
import ephyra.domain.content.source.PaginationType
import ephyra.domain.content.source.ResponseType
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "source_profiles")
data class SourceProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "base_url")
    val baseUrl: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "content_type")
    val contentType: String,

    @ColumnInfo(name = "source_type")
    val sourceType: String,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean,

    @ColumnInfo(name = "response_type")
    val responseType: String,

    @ColumnInfo(name = "pagination")
    val pagination: String,

    @ColumnInfo(name = "failure_count")
    val failureCount: Int,

    @ColumnInfo(name = "verified")
    val verified: Boolean,

    @ColumnInfo(name = "last_health_check")
    val lastHealthCheck: Long,

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long,

    @ColumnInfo(name = "scraper_filename")
    val scraperFilename: String?,

    @ColumnInfo(name = "repository_id")
    val repositoryId: String?,

    @ColumnInfo(name = "endpoints_json")
    val endpointsJson: String,

    @ColumnInfo(name = "selectors_json")
    val selectorsJson: String?,

    @ColumnInfo(name = "json_path_json")
    val jsonPathJson: String?,

    @ColumnInfo(name = "headers_json")
    val headersJson: String,

    @ColumnInfo(name = "auth_type")
    val authType: String,

    @ColumnInfo(name = "rate_limit_ms")
    val rateLimitMs: Long,
) {
    fun toDomain(json: Json = Json { ignoreUnknownKeys = true }): SourceProfile {
        val decodedEndpoints = try {
            json.decodeFromString<Map<String, EndpointPatternDto>>(endpointsJson)
                .mapNotNull { (key, dto) ->
                    val endpoint = try {
                        Endpoint.valueOf(key)
                    } catch (e: Exception) {
                        null
                    }
                    endpoint?.let {
                        it to EndpointPattern(
                            pathTemplate = dto.pathTemplate,
                            method = try {
                                HttpMethod.valueOf(dto.method)
                            } catch (e: Exception) {
                                HttpMethod.GET
                            },
                            responseType = try {
                                ResponseType.valueOf(dto.responseType)
                            } catch (e: Exception) {
                                ResponseType.AUTO
                            },
                            bodyTemplate = dto.bodyTemplate,
                        )
                    }
                }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }

        val decodedSelectors = selectorsJson?.let {
            try {
                json.decodeFromString<Map<String, String>>(it)
                    .mapNotNull { (key, value) ->
                        try {
                            DataField.valueOf(key) to value
                        } catch (e: Exception) {
                            null
                        }
                    }.toMap()
            } catch (e: Exception) {
                null
            }
        }

        val decodedJsonPath = jsonPathJson?.let {
            try {
                json.decodeFromString<Map<String, String>>(it)
                    .mapNotNull { (key, value) ->
                        try {
                            DataField.valueOf(key) to value
                        } catch (e: Exception) {
                            null
                        }
                    }.toMap()
            } catch (e: Exception) {
                null
            }
        }

        val decodedHeaders = try {
            json.decodeFromString<Map<String, String>>(headersJson)
        } catch (e: Exception) {
            emptyMap()
        }

        return SourceProfile(
            baseUrl = baseUrl,
            contentType = try {
                ContentType.valueOf(contentType)
            } catch (e: Exception) {
                ContentType.UNKNOWN
            },
            sourceType = try {
                SourceType.valueOf(sourceType)
            } catch (e: Exception) {
                SourceType.HEURISTIC
            },
            enabled = enabled,
            endpoints = decodedEndpoints,
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
            selectors = decodedSelectors,
            jsonPath = decodedJsonPath,
            headers = decodedHeaders,
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
            scraperFilename = scraperFilename,
            repositoryId = repositoryId,
        )
    }

    companion object {
        fun fromDomain(
            profile: SourceProfile,
            json: Json = Json { ignoreUnknownKeys = true },
        ): SourceProfileEntity {
            val endpointsMap = profile.endpoints.mapKeys { it.key.name }.mapValues { (_, ep) ->
                EndpointPatternDto(
                    pathTemplate = ep.pathTemplate,
                    method = ep.method.name,
                    responseType = ep.responseType.name,
                    bodyTemplate = ep.bodyTemplate,
                )
            }
            val endpointsStr = json.encodeToString(endpointsMap)
            val selectorsStr = profile.selectors?.mapKeys { it.key.name }?.let { json.encodeToString(it) }
            val jsonPathStr = profile.jsonPath?.mapKeys { it.key.name }?.let { json.encodeToString(it) }
            val headersStr = json.encodeToString(profile.headers)

            return SourceProfileEntity(
                baseUrl = profile.baseUrl,
                displayName = profile.displayName,
                contentType = profile.contentType.name,
                sourceType = profile.sourceType.name,
                enabled = profile.enabled,
                responseType = profile.responseType.name,
                pagination = profile.pagination.name,
                failureCount = profile.failureCount,
                verified = profile.verified,
                lastHealthCheck = profile.lastHealthCheck,
                lastUpdated = profile.lastUpdated,
                scraperFilename = profile.scraperFilename,
                repositoryId = profile.repositoryId,
                endpointsJson = endpointsStr,
                selectorsJson = selectorsStr,
                jsonPathJson = jsonPathStr,
                headersJson = headersStr,
                authType = profile.authType.name,
                rateLimitMs = profile.rateLimitMs,
            )
        }
    }
}

@Serializable
internal data class EndpointPatternDto(
    val pathTemplate: String,
    val method: String = "GET",
    val responseType: String = "AUTO",
    val bodyTemplate: String? = null,
)
