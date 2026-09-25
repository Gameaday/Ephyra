package ephyra.source.local

import ephyra.domain.content.model.CatalogEntry
import ephyra.domain.content.model.ChapterInfo
import ephyra.domain.content.model.ContentPage
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.FilterSet
import ephyra.domain.content.source.UnifiedContentSource
import ephyra.source.api.ContentReference
import ephyra.source.api.ResourceKind
import ephyra.source.api.SourceCapability
import ephyra.source.api.SourceKind
import ephyra.source.api.SourceResult
import ephyra.source.api.SourceSearchRequest
import ephyra.source.api.UnitReference
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalSourceGatewayTest {

    @Test
    fun `descriptor exposes native local capabilities`() {
        val gateway = LocalSourceGateway(FakeLocalSource())

        assertEquals("local-test", gateway.descriptor.id.value)
        assertEquals(SourceKind.LOCAL, gateway.descriptor.kind)
        assertTrue(gateway.descriptor.supports(SourceCapability.SEARCH))
        assertTrue(gateway.descriptor.supports(SourceCapability.CATALOG))
        assertTrue(gateway.descriptor.supports(SourceCapability.RESOURCES))
    }

    @Test
    fun `search maps catalog entries and pagination cursor`() = runTest {
        val source = FakeLocalSource()
        val gateway = LocalSourceGateway(source)

        val first = gateway.search(SourceSearchRequest("")) as SourceResult.Success
        assertEquals(50, first.value.items.size)
        assertEquals("50", first.value.nextCursor)

        val second = gateway.search(SourceSearchRequest("", cursor = "50")) as SourceResult.Success
        assertEquals(1, second.value.items.size)
        assertEquals(null, second.value.nextCursor)
    }

    @Test
    fun `details units and resources preserve local identities and bytes`() = runTest {
        val gateway = LocalSourceGateway(FakeLocalSource())

        val details = gateway.getDetails(ContentReference("Item 1")) as SourceResult.Success
        assertEquals("Item 1", details.value.externalId)
        assertEquals("Item 1", details.value.title)

        val units = gateway.getUnits(ContentReference("Item 1")) as SourceResult.Success
        assertEquals("Item 1/Chapter 1", units.value.items.single().externalId)

        val resources = gateway.getResources(UnitReference("Item 1/Chapter 1")) as SourceResult.Success
        val resource = resources.value.single()
        assertEquals(ResourceKind.IMAGE, resource.kind)
        assertArrayEquals(byteArrayOf(1, 2, 3), resource.inlineBytes)
        assertTrue(resource.metadata.getValue("chapter").startsWith("Item 1/"))
    }

    @Test
    fun `unsupported filters are not silently dropped`() = runTest {
        val gateway = LocalSourceGateway(FakeLocalSource())

        val result = gateway.search(SourceSearchRequest("a", filters = mapOf("genre" to "action")))

        assertEquals(SourceResult.Unsupported(SourceCapability.SEARCH), result)
    }

    @Test
    fun `missing local content is a permanent failure`() = runTest {
        val gateway = LocalSourceGateway(FakeLocalSource())

        val result = gateway.getDetails(ContentReference("Missing"))

        assertTrue(result is SourceResult.PermanentFailure)
    }

    private class FakeLocalSource : UnifiedContentSource {
        override val id = "local-test"
        override val name = "Local test"
        override val supportedTypes = setOf(ContentType.MANGA, ContentType.BOOK)

        override suspend fun getCatalog(page: Int, filter: FilterSet): List<CatalogEntry> = (1..51)
            .map { index -> CatalogEntry("Item $index", "Item $index", "Item $index", type = ContentType.MANGA) }
            .filter { filter.query.isBlank() || it.title.contains(filter.query, ignoreCase = true) }

        override suspend fun getChapterManifest(entryKey: String): List<ChapterInfo> = listOf(
            ChapterInfo("$entryKey/Chapter 1", "Chapter 1", 1.0, url = "$entryKey/Chapter 1"),
        )

        override suspend fun loadPage(chapterKey: String, pageIndex: Int): ContentPage =
            loadPages(chapterKey).getOrNull(pageIndex) ?: ContentPage.ImagePage(index = pageIndex)

        override suspend fun loadPages(chapterKey: String): List<ContentPage> = listOf(
            ContentPage.ImagePage(index = 0, imageBytes = byteArrayOf(1, 2, 3)),
        )
    }
}
