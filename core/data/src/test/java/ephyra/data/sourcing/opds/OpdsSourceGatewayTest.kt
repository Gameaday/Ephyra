package ephyra.data.sourcing.opds

import ephyra.domain.content.model.CatalogEntry
import ephyra.domain.content.model.ChapterInfo
import ephyra.domain.content.model.ContentPage
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.FilterSet
import ephyra.domain.content.source.UnifiedContentSource
import ephyra.source.api.ContentReference
import ephyra.source.api.SourceCapability
import ephyra.source.api.SourceResult
import ephyra.source.api.SourceSearchRequest
import ephyra.source.api.UnitReference
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpdsSourceGatewayTest {

    @Test
    fun `descriptor declares only gateway capabilities`() {
        val gateway = OpdsSourceGateway(FakeOpdsSource())

        assertEquals("opds-test", gateway.descriptor.id.value)
        assertTrue(gateway.descriptor.supports(SourceCapability.SEARCH))
        assertTrue(gateway.descriptor.supports(SourceCapability.DETAILS))
        assertTrue(gateway.descriptor.supports(SourceCapability.UNITS))
        assertTrue(gateway.descriptor.supports(SourceCapability.RESOURCES))
        assertEquals(
            false,
            gateway.descriptor.supports(SourceCapability.CATALOG),
        )
        assertEquals(
            false,
            gateway.descriptor.supports(SourceCapability.POPULAR),
        )
    }

    @Test
    fun `search maps results and rejects unsupported filters`() = runTest {
        val gateway = OpdsSourceGateway(FakeOpdsSource())

        val success = gateway.search(SourceSearchRequest("book")) as SourceResult.Success
        assertEquals("Book 1", success.value.items.single().title)
        assertEquals("2", success.value.nextCursor)

        assertEquals(
            SourceResult.Unsupported(SourceCapability.SEARCH),
            gateway.search(SourceSearchRequest("book", filters = mapOf("genre" to "fantasy"))),
        )
    }

    @Test
    fun `details units and resources preserve OPDS identity`() = runTest {
        val gateway = OpdsSourceGateway(FakeOpdsSource())

        val details = gateway.getDetails(ContentReference("book-1")) as SourceResult.Success
        assertEquals("book-1", details.value.externalId)

        val units = gateway.getUnits(ContentReference("book-1")) as SourceResult.Success
        assertEquals("book-1/chapter-1", units.value.items.single().externalId)

        val resources = gateway.getResources(UnitReference("book-1/chapter-1")) as SourceResult.Success
        assertEquals(
            "https://catalog.test/book-1/chapter-1/page-1.jpg",
            resources.value.single().url,
        )
        assertArrayEquals(byteArrayOf(1, 2, 3), resources.value.single().inlineBytes)
    }

    @Test
    fun `missing details and invalid cursor are typed failures`() = runTest {
        val gateway = OpdsSourceGateway(FakeOpdsSource())

        assertTrue(gateway.getDetails(ContentReference("missing")) is SourceResult.PermanentFailure)
        assertTrue(
            gateway.search(SourceSearchRequest("book", cursor = "not-a-page"))
                is SourceResult.PermanentFailure,
        )
    }

    private class FakeOpdsSource : UnifiedContentSource {
        override val id = "opds-test"
        override val name = "Test OPDS"
        override val supportedTypes = setOf(ContentType.BOOK)

        override suspend fun getCatalog(page: Int, filter: FilterSet): List<CatalogEntry> = listOf(
            CatalogEntry(
                key = "book-$page",
                title = "Book $page",
                url = "book-$page",
                coverUrl = "https://catalog.test/book-$page.jpg",
                type = ContentType.BOOK,
            ),
        ).filter { filter.query.isBlank() || it.title.contains(filter.query, ignoreCase = true) }

        override suspend fun getChapterManifest(entryKey: String): List<ChapterInfo> = listOf(
            ChapterInfo(
                key = "$entryKey/chapter-1",
                title = "Chapter 1",
                number = 1.0,
                url = "$entryKey/chapter-1",
            ),
        )

        override suspend fun loadPage(chapterKey: String, pageIndex: Int): ContentPage =
            loadPages(chapterKey)[pageIndex]

        override suspend fun loadPages(chapterKey: String): List<ContentPage> = listOf(
            ContentPage.ImagePage(
                index = 0,
                imageUrl = "https://catalog.test/$chapterKey/page-1.jpg",
                imageBytes = byteArrayOf(1, 2, 3),
            ),
        )
    }
}
