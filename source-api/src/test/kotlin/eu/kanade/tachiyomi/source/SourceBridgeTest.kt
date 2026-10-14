package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins how `getMangaUpdate` is split between `Source`, `CatalogueSource` and `HttpSource`.
 *
 * **What this replaced.** The class used to be an RxJava bridge test: it built a `CatalogueSource`
 * that implemented only the deprecated `fetch*` methods and asserted that the inherited suspend
 * `getPopularManga` / `getSearchManga` / … resolved through them. That bridge is gone with the
 * 1.4/1.5 ABI, so there is nothing left to bridge and the fixtures implement the suspend API
 * directly.
 *
 * What remains worth pinning is the routing the app depends on. `LibraryUpdateJob`,
 * `MetadataUpdateJob`, the migration flow and the deeplink handler all call `getMangaUpdate`,
 * `getMangaDetails` or `getChapterList` directly, so a source that overrides only one of them must
 * still be served through it, and a failed details fetch must not cost the caller the chapter list
 * it asked for in the same call.
 */
class SourceBridgeTest {

    private class TestCatalogueSource : CatalogueSource {
        override val id: Long = 12345L
        override val name: String = "Test Source"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(
            listOf(SManga.create().apply { url = "/manga/popular"; title = "Popular Manga $page" }),
            false,
        )

        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
            MangasPage(
                listOf(SManga.create().apply { url = "/manga/search"; title = "Search: $query" }),
                false,
            )

        override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(
            listOf(SManga.create().apply { url = "/manga/latest"; title = "Latest Manga $page" }),
            false,
        )

        override suspend fun getMangaDetails(manga: SManga): SManga = SManga.create().apply {
            url = manga.url
            title = "Updated ${manga.title}"
            author = "Author"
        }

        override suspend fun getChapterList(manga: SManga): List<SChapter> =
            listOf(SChapter.create().apply { url = "/chapter/1"; name = "Chapter 1" })

        override suspend fun getPageList(chapter: SChapter): List<Page> =
            listOf(Page(0, "url0", "img0"))
    }

    @Test
    fun `CatalogueSource answers every catalogue call from the suspend API`() = runBlocking {
        val source = TestCatalogueSource()

        assertEquals("Popular Manga 1", source.getPopularManga(1).mangas.first().title)
        assertEquals("Search: yugioh", source.getSearchManga(1, "yugioh", FilterList()).mangas.first().title)
        assertEquals("Latest Manga 1", source.getLatestUpdates(1).mangas.first().title)

        val dummyManga = SManga.create().apply { url = "/manga/dummy"; title = "Original" }
        val update = source.getMangaUpdate(dummyManga, emptyList(), fetchDetails = true, fetchChapters = true)
        assertEquals("Updated Original", update.manga().title)
        assertEquals(1, update.chapters().size)
        assertEquals("Chapter 1", update.chapters().first().name)

        val pages = source.getPageList(SChapter.create().apply { url = "/chapter/dummy" })
        assertEquals(1, pages.size)
        assertEquals("img0", pages.first().imageUrl)
    }

    @Test
    fun `Source getMangaDetails and getChapterList default methods work via getMangaUpdate`() = runBlocking {
        val source = TestCatalogueSource()
        val dummyManga = SManga.create().apply { url = "/manga/dummy"; title = "Direct" }

        assertEquals("Updated Direct", source.getMangaDetails(dummyManga).title)
        assertEquals(1, source.getChapterList(dummyManga).size)
        assertEquals("Chapter 1", source.getChapterList(dummyManga).first().name)
    }

    /** A source whose details endpoint is broken but whose chapter list is fine. */
    private class DetailsFailSource : CatalogueSource {
        override val id: Long = 999L
        override val name: String = "Broken details"
        override val lang: String = "en"
        override val supportsLatest: Boolean = false

        override suspend fun getMangaDetails(manga: SManga): SManga =
            throw IllegalStateException("details endpoint is down")

        override suspend fun getChapterList(manga: SManga): List<SChapter> =
            listOf(SChapter.create().apply { url = "/chapter/1"; name = "Chapter 1" })

        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
    }

    /**
     * The tolerance the override exists for: a details failure is answered with the manga we were
     * given, so the chapter refresh the caller actually asked for still completes.
     */
    @Test
    fun `a failed details fetch does not cost the caller the chapter list`() = runBlocking {
        val source = DetailsFailSource()
        val dummyManga = SManga.create().apply { url = "/manga/dummy"; title = "Original" }

        val update = source.getMangaUpdate(dummyManga, emptyList(), fetchDetails = true, fetchChapters = true)

        assertEquals("Original", update.manga().title)
        assertEquals(1, update.chapters().size)
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
        ): SMangaUpdate {
            val updatedManga = SManga.create().apply {
                url = manga.url
                title = "Updated ${manga.title}"
                author = "Author 1.6"
            }
            val ch1 = SChapter.create().apply { url = "/chapter/1"; name = "Chapter 1" }
            val ch2 = SChapter.create().apply { url = "/chapter/2"; name = "Chapter 2" }
            return SMangaUpdate(
                if (fetchDetails) updatedManga else manga,
                if (fetchChapters) listOf(ch1, ch2) else chapters,
            )
        }

        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
    }

    /**
     * An extension that implements only the combined API must still have its details and chapters
     * served when the app calls the split methods — that is the routing `HttpSource` preserves by
     * delegating `getMangaDetails`/`getChapterList` back through `getMangaUpdate`.
     */
    @Test
    fun `HttpSource 1_6 extension resolves getChapterList and getMangaDetails via getMangaUpdate`() =
        runBlocking {
            val source = TestHttpSource16()
            val dummyManga = SManga.create().apply { url = "/manga/sample"; title = "Sample" }

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

