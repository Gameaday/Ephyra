package ephyra.source.api

import ephyra.domain.content.model.ContentType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourceGatewayContractTest {

    @Test
    fun `descriptor identity and capabilities are explicit`() {
        val descriptor = SourceDescriptor(
            id = SourceId("local-library"),
            displayName = "Local library",
            kind = SourceKind.LOCAL,
            revision = 1,
            capabilities = setOf(SourceCapability.SEARCH, SourceCapability.DETAILS),
            contentTypes = setOf(ContentType.MANGA),
        )

        assertEquals("local-library", descriptor.id.value)
        assertTrue(descriptor.supports(SourceCapability.SEARCH))
        assertFalse(descriptor.supports(SourceCapability.POPULAR))
    }

    @Test
    fun `blank source identity is rejected`() {
        assertThrowsIllegalArgument {
            SourceId(" ")
        }
        assertThrowsIllegalArgument {
            SourceDescriptor(
                id = SourceId("source"),
                displayName = "",
                kind = SourceKind.NATIVE,
                revision = 1,
                capabilities = emptySet(),
            )
        }
        assertThrowsIllegalArgument {
            SourceDescriptor(
                id = SourceId("source"),
                displayName = "Source",
                kind = SourceKind.NATIVE,
                revision = 0,
                capabilities = emptySet(),
            )
        }
    }

    @Test
    fun `typed outcomes preserve empty unsupported and failure states`() {
        assertEquals(SourceResult.Empty, SourceResult.Empty)
        val unsupported = SourceResult.Unsupported(SourceCapability.SEARCH)
        assertEquals(SourceCapability.SEARCH, unsupported.capability)
        val rateLimited = SourceResult.RateLimited(250L)
        assertEquals(250L, rateLimited.retryAfterMillis)
        assertEquals(SourceResult.RateLimited(250L), SourceResult.RateLimited(250L))
    }

    @Test
    fun `resource accepts inline bytes for local sources`() {
        val resource = SourceResource("", ResourceKind.IMAGE, inlineBytes = byteArrayOf(1, 2, 3))

        assertTrue(resource.inlineBytes!!.contentEquals(byteArrayOf(1, 2, 3)))
        assertThrowsIllegalArgument {
            SourceResource("", ResourceKind.IMAGE)
        }
    }

    @Test
    fun `gateway exposes only the four core operation boundaries`() = runBlocking {
        val gateway = FakeGateway()
        val item = SourceContentItem(SourceId("fake"), url = "/item", title = "Item")
        val unit = SourceContentUnit(SourceId("fake"), url = "/unit", title = "Chapter 1", number = 1.0)
        val resource = SourceResource("/page.jpg", ResourceKind.IMAGE)

        assertEquals(
            SourceResult.Success(SourcePage(listOf(item), "next")),
            gateway.search(SourceSearchRequest("item")),
        )
        assertEquals(SourceResult.Success(item), gateway.getDetails(ContentReference("/item")))
        assertEquals(SourceResult.Success(SourcePage(listOf(unit))), gateway.getUnits(ContentReference("/item")))
        assertEquals(SourceResult.Success(listOf(resource)), gateway.getResources(UnitReference("/unit")))
    }

    private class FakeGateway : SourceGateway {
        override val descriptor = SourceDescriptor(
            id = SourceId("fake"),
            displayName = "Fake",
            kind = SourceKind.NATIVE,
            revision = 1,
            capabilities = setOf(
                SourceCapability.SEARCH,
                SourceCapability.DETAILS,
                SourceCapability.UNITS,
                SourceCapability.RESOURCES,
            ),
        )

        override suspend fun search(request: SourceSearchRequest): SourceResult<SourcePage<SourceContentItem>> =
            SourceResult.Success(
                SourcePage(
                    listOf(SourceContentItem(SourceId("fake"), url = "/item", title = "Item")),
                    "next",
                ),
            )

        override suspend fun getDetails(reference: ContentReference): SourceResult<SourceContentItem> =
            SourceResult.Success(SourceContentItem(SourceId("fake"), url = reference.url, title = "Item"))

        override suspend fun getUnits(reference: ContentReference): SourceResult<SourcePage<SourceContentUnit>> =
            SourceResult.Success(
                SourcePage(
                    listOf(SourceContentUnit(SourceId("fake"), url = "/unit", title = "Chapter 1", number = 1.0)),
                ),
            )

        override suspend fun getResources(reference: UnitReference): SourceResult<List<SourceResource>> =
            SourceResult.Success(listOf(SourceResource("/page.jpg", ResourceKind.IMAGE)))
    }

    private inline fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
