package ephyra.data.sourcing.scraper

import ephyra.domain.content.model.ContentPage
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.FilterSet
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UnifiedScraperSourceTest {

    private val delegate = mockk<CatalogueSource>()
    private lateinit var source: UnifiedScraperSource

    @Before
    fun setUp() {
        every { delegate.id } returns 12345L
        every { delegate.name } returns "Test Scraper"
        every { delegate.supportsLatest } returns true
        source = UnifiedScraperSource(delegate)
    }

    @Test
    fun `source metadata conforms to contract`() {
        assertEquals("12345", source.id)
        assertEquals("Test Scraper", source.name)
        assertTrue(source.supportedTypes.contains(ContentType.MANGA))
    }

    @Test
    fun `getCatalog maps SManga to CatalogEntry at boundary`() = runTest {
        val sManga = SManga.create().apply {
            url = "/manga/one-piece"
            title = "One Piece"
            thumbnail_url = "https://example.com/op.jpg"
            author = "Eiichiro Oda"
            genre = "Action, Adventure, Shounen"
        }

        coEvery { delegate.getPopularManga(1) } returns MangasPage(listOf(sManga), false)

        val catalog = source.getCatalog(1, FilterSet(sortOrder = FilterSet.SortOrder.POPULAR))
        assertEquals(1, catalog.size)
        val entry = catalog[0]
        assertEquals("/manga/one-piece", entry.url)
        assertEquals("One Piece", entry.title)
        assertEquals("https://example.com/op.jpg", entry.coverUrl)
        assertEquals("Eiichiro Oda", entry.author)
        assertEquals(ContentType.MANGA, entry.type)
        assertEquals(listOf("Action", "Adventure", "Shounen"), entry.genres)
    }

    @Test
    fun `getChapterManifest maps SChapter to ChapterInfo at boundary`() = runTest {
        val ch1 = SChapter.create().apply {
            url = "/manga/one-piece/ch1"
            name = "Chapter 1: Romance Dawn"
            chapter_number = 1f
            date_upload = 1700000000L
            scanlator = "Official"
        }

        coEvery { delegate.getChapterList(any()) } returns listOf(ch1)

        val manifest = source.getChapterManifest("/manga/one-piece")
        assertEquals(1, manifest.size)
        val info = manifest[0]
        assertEquals("/manga/one-piece/ch1", info.url)
        assertEquals("Chapter 1: Romance Dawn", info.title)
        assertEquals(1.0, info.number, 0.001)
        assertEquals("Official", info.scanlator)
    }

    @Test
    fun `loadPages maps legacy Page to ContentPage_ImagePage at boundary`() = runTest {
        val page1 = Page(0, "https://example.com/ch1", "https://images.example.com/page1.png")
        val page2 = Page(1, "https://example.com/ch1", "https://images.example.com/page2.png")

        coEvery { delegate.getPageList(any()) } returns listOf(page1, page2)

        val pages = source.loadPages("/manga/one-piece/ch1")
        assertEquals(2, pages.size)

        val p1 = pages[0] as ContentPage.ImagePage
        assertEquals(0, p1.index)
        assertEquals("https://images.example.com/page1.png", p1.imageUrl)

        val p2 = pages[1] as ContentPage.ImagePage
        assertEquals(1, p2.index)
        assertEquals("https://images.example.com/page2.png", p2.imageUrl)
    }
}
