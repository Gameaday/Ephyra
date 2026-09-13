package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import rx.Observable

class SourceBridgeTest {

    private class TestCatalogueSource : CatalogueSource {
        override val id: Long = 12345L
        override val name: String = "Test Source"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override fun fetchPopularManga(page: Int): Observable<MangasPage> {
            val manga = SManga.create().apply {
                url = "/manga/popular"
                title = "Popular Manga $page"
            }
            return Observable.just(MangasPage(listOf(manga), false))
        }

        override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
            val manga = SManga.create().apply {
                url = "/manga/search"
                title = "Search: $query"
            }
            return Observable.just(MangasPage(listOf(manga), false))
        }

        override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
            val manga = SManga.create().apply {
                url = "/manga/latest"
                title = "Latest Manga $page"
            }
            return Observable.just(MangasPage(listOf(manga), false))
        }

        override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
            val details = SManga.create().apply {
                url = manga.url
                title = "Updated ${manga.title}"
                author = "Author"
            }
            return Observable.just(details)
        }

        override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
            val chapter = SChapter.create().apply {
                url = "/chapter/1"
                name = "Chapter 1"
            }
            return Observable.just(listOf(chapter))
        }

        override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
            return Observable.just(listOf(Page(0, "url0", "img0")))
        }
    }

    @Test
    fun `CatalogueSource bridge correctly converts Observable fetch to suspend get`() = runBlocking {
        val source = TestCatalogueSource()

        val popular = source.getPopularManga(1)
        assertEquals("Popular Manga 1", popular.mangas.first().title)

        val search = source.getSearchManga(1, "yugioh", FilterList())
        assertEquals("Search: yugioh", search.mangas.first().title)

        val latest = source.getLatestUpdates(1)
        assertEquals("Latest Manga 1", latest.mangas.first().title)

        val dummyManga = SManga.create().apply {
            url = "/manga/dummy"
            title = "Original"
        }
        val update = source.getMangaUpdate(dummyManga, emptyList(), fetchDetails = true, fetchChapters = true)
        assertEquals("Updated Original", update.manga.title)
        assertEquals(1, update.chapters.size)
        assertEquals("Chapter 1", update.chapters.first().name)

        val pages = source.getPageList(SChapter.create().apply { url = "/chapter/dummy" })
        assertEquals(1, pages.size)
        assertEquals("img0", pages.first().imageUrl)
    }

    @Test
    fun `Source getMangaDetails and getChapterList default methods work via getMangaUpdate`() = runBlocking {
        val source = TestCatalogueSource()
        val dummyManga = SManga.create().apply {
            url = "/manga/dummy"
            title = "Direct"
        }

        val details = source.getMangaDetails(dummyManga)
        assertEquals("Updated Direct", details.title)

        val chapters = source.getChapterList(dummyManga)
        assertEquals(1, chapters.size)
        assertEquals("Chapter 1", chapters.first().name)
    }

    private class TestHttpSource16 : eu.kanade.tachiyomi.source.online.HttpSource() {
        override val baseUrl: String = "https://example.com"
        override val name: String = "Test 1.6 Extension"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): eu.kanade.tachiyomi.source.model.SMangaUpdate {
            val updatedManga = SManga.create().apply {
                url = manga.url
                title = "Updated ${manga.title}"
                author = "Author 1.6"
            }
            val ch1 = SChapter.create().apply {
                url = "/chapter/1"
                name = "Chapter 1"
            }
            val ch2 = SChapter.create().apply {
                url = "/chapter/2"
                name = "Chapter 2"
            }
            return eu.kanade.tachiyomi.source.model.SMangaUpdate(
                if (fetchDetails) updatedManga else manga,
                if (fetchChapters) listOf(ch1, ch2) else chapters,
            )
        }

        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
    }

    @Test
    fun `HttpSource 1_6 extension resolves getChapterList and getMangaDetails via getMangaUpdate`() =
        runBlocking {
            val source = TestHttpSource16()
            val dummyManga = SManga.create().apply {
                url = "/manga/sample"
                title = "Sample"
            }

            // Must resolve without UnsupportedOperationException
            val chapters = source.getChapterList(dummyManga)
            assertEquals(2, chapters.size)
            assertEquals("Chapter 1", chapters[0].name)
            assertEquals("Chapter 2", chapters[1].name)

            val details = source.getMangaDetails(dummyManga)
            assertEquals("Updated Sample", details.title)
            assertEquals("Author 1.6", details.author)
        }
}
