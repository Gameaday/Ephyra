package ephyra.source.api

import ephyra.domain.content.model.ContentType
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegacySourceGatewayTest {

    @Test
    fun `adapter exposes legacy source through target contract`() = runBlocking {
        val gateway = LegacySourceGateway(FakeCatalogue())

        assertEquals("legacy:42", gateway.descriptor.id.value)
        assertEquals(SourceKind.LEGACY_COMPATIBILITY, gateway.descriptor.kind)
        assertTrue(gateway.descriptor.supports(SourceCapability.SEARCH))
        assertTrue(gateway.descriptor.supports(SourceCapability.LATEST))
        assertEquals(SourceCompatibilityLevel.LEGACY, gateway.descriptor.compatibilityLevel)

        val result = gateway.search(SourceSearchRequest("title"))
        val page = (result as SourceResult.Success).value
        assertEquals("2", page.nextCursor)
        assertEquals("manga", page.items.single().title)
        assertEquals(ContentType.MANGA, page.items.single().contentType)
    }

    @Test
    fun `adapter rejects lossless legacy filter loss instead of silently ignoring it`() = runBlocking {
        val gateway = LegacySourceGateway(FakeCatalogue())

        val result = gateway.search(SourceSearchRequest("title", filters = mapOf("genre" to "action")))

        assertEquals(
            SourceResult.Unsupported(SourceCapability.SEARCH),
            result,
        )
    }

    @Test
    fun `adapter maps details units and resources without exposing legacy DTOs`() = runBlocking {
        val gateway = LegacySourceGateway(FakeCatalogue())

        val details = gateway.getDetails(ContentReference("/manga"))
        assertEquals("manga", (details as SourceResult.Success).value.title)

        val units = gateway.getUnits(ContentReference("/manga"))
        val unitPage = (units as SourceResult.Success).value
        assertEquals(1.0, unitPage.items.single().number)

        val resources = gateway.getResources(UnitReference("/chapter"))
        val resourceList = (resources as SourceResult.Success).value
        assertEquals("https://images.example/page.jpg", resourceList.single().url)
    }

    @Test
    fun `adapter maps legacy unsupported operation to typed outcome`() = runBlocking {
        val gateway = LegacySourceGateway(FakeCatalogue(failSearch = true))

        val result = gateway.search(SourceSearchRequest("title"))

        assertEquals(SourceResult.Unsupported(SourceCapability.SEARCH), result)
    }

    private class FakeCatalogue(
        private val failSearch: Boolean = false,
    ) : CatalogueSource {
        override val id: Long = 42L
        override val name: String = "Legacy"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)

        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
            if (failSearch) throw UnsupportedOperationException("search")
            return MangasPage(
                listOf(
                    SManga.create().apply {
                        url = "/manga"
                        title = "manga"
                        genre = "action, adventure"
                    },
                ),
                hasNextPage = true,
            )
        }

        override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)

        override suspend fun getMangaDetails(manga: SManga): SManga = manga.copy().apply {
            title = "manga"
        }

        override suspend fun getChapterList(manga: SManga): List<SChapter> = listOf(
            SChapter.create().apply {
                url = "/chapter"
                name = "Chapter 1"
                chapter_number = 1f
            },
        )

        override suspend fun getPageList(chapter: SChapter): List<Page> = listOf(
            Page(0, url = "https://images.example/page.jpg", imageUrl = "https://images.example/page.jpg"),
        )
    }
}
