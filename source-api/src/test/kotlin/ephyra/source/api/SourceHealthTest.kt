package ephyra.source.api

import ephyra.domain.content.model.ContentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourceHealthTest {

    @Test
    fun `success clears failures and makes source healthy`() {
        val initial = SourceHealthPolicy.initial(SourceId("source"))
        val failed = SourceHealthPolicy.recordFailure(initial, 100L, "network")
        val healthy = SourceHealthPolicy.recordSuccess(failed, 200L)

        assertEquals(SourceHealthStatus.HEALTHY, healthy.status)
        assertEquals(0, healthy.consecutiveFailures)
        assertEquals(200L, healthy.lastSuccessAtMillis)
        assertEquals(null, healthy.reason)
    }

    @Test
    fun `failures degrade and then quarantine without auto retry`() {
        var health = SourceHealthPolicy.initial(SourceId("source"))
        health = SourceHealthPolicy.recordFailure(health, 1L, "first")
        assertEquals(SourceHealthStatus.UNKNOWN, health.status)
        health = SourceHealthPolicy.recordFailure(health, 2L, "second")
        assertEquals(SourceHealthStatus.DEGRADED, health.status)
        health = SourceHealthPolicy.recordFailure(health, 3L, "third")
        health = SourceHealthPolicy.recordFailure(health, 4L, "fourth")
        health = SourceHealthPolicy.recordFailure(health, 5L, "fifth")

        assertEquals(SourceHealthStatus.QUARANTINED, health.status)
        assertFalse(health.isAvailable(5L))
    }

    @Test
    fun `rate limiting is distinct and recovers after retry time`() {
        val initial = SourceHealthPolicy.initial(SourceId("source"))
        val limited = SourceHealthPolicy.recordRateLimit(initial, 1_000L, 500L)

        assertEquals(SourceHealthStatus.RATE_LIMITED, limited.status)
        assertFalse(limited.isAvailable(1_499L))
        assertTrue(limited.isAvailable(1_500L))
        assertEquals(0, limited.consecutiveFailures)
    }

    @Test
    fun `disabled source is unavailable`() {
        val disabled = SourceHealthPolicy.initial(SourceId("source"), enabled = false)

        assertEquals(SourceHealthStatus.DISABLED, disabled.status)
        assertFalse(disabled.isAvailable(0L))
    }

    @Test
    fun `discovery filters by requested capabilities and health snapshot`() {
        val searchOnly = gateway("search", setOf(SourceCapability.SEARCH))
        val browse = gateway("browse", setOf(SourceCapability.SEARCH, SourceCapability.CATALOG))
        val health = mapOf(
            SourceId("search") to SourceHealthPolicy.recordFailure(
                SourceHealthPolicy.initial(SourceId("search")),
                1L,
                "failed",
            ),
        )

        val entries = SourceDiscoveryPolicy.entries(
            listOf(searchOnly, browse),
            health,
            requestedCapabilities = setOf(SourceCapability.SEARCH, SourceCapability.CATALOG),
        )

        assertEquals(listOf("browse"), entries.map { it.descriptor.id.value })
        assertTrue(entries.single().isAvailable(1L))
    }

    private fun gateway(id: String, capabilities: Set<SourceCapability>): SourceGateway = object : SourceGateway {
        override val descriptor = SourceDescriptor(
            id = SourceId(id),
            displayName = id,
            kind = SourceKind.NATIVE,
            revision = 1,
            capabilities = capabilities,
            contentTypes = setOf(ContentType.MANGA),
        )

        override suspend fun search(request: SourceSearchRequest): SourceResult<SourcePage<SourceContentItem>> =
            SourceResult.Empty

        override suspend fun getDetails(reference: ContentReference): SourceResult<SourceContentItem> =
            SourceResult.Unsupported(SourceCapability.DETAILS)

        override suspend fun getUnits(reference: ContentReference): SourceResult<SourcePage<SourceContentUnit>> =
            SourceResult.Unsupported(SourceCapability.UNITS)

        override suspend fun getResources(reference: UnitReference): SourceResult<List<SourceResource>> =
            SourceResult.Unsupported(SourceCapability.RESOURCES)
    }
}
