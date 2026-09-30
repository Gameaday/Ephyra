package ephyra.feature.reader.loader

import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.service.ChapterCache
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Pins that the URL the loader actually asks for is absolute — the loader half of `DEF-027`.
 *
 * **The failure.** The owner saw `Expected URL scheme 'http' or 'https' but no scheme was found for …`
 * on a manga page. That exception is raised by OkHttp while a request is *built*, so the defect was
 * in the value, not in the network: nothing was sent, the host was never contacted, and the page
 * being available at the source site is not in tension with it.
 *
 * **Why the other two tests are not enough.** `ImageUrlPolicyResolveTest` pins the rule and
 * `HttpSourceImageUrlRequestTest` pins the default request builders, but neither proves the *loader*
 * applies it — and three paths reach the loader without passing through either: a source that
 * overrides `getImageUrl` and returns a relative string, a source that sets `Page.imageUrl` itself in
 * `pageListParse`, and a page whose URL was restored from the chapter cache by a previous session.
 * Each is one `imageUrl` away from the reported crash, so each is a case here.
 *
 * **What is asserted.** The address recorded by [ChapterCache.fetchAndCacheImage] — the last point
 * before bytes are requested, and the string the disk cache is keyed on. Asserting there rather than
 * on a returned value means the test observes the address the reader would really have used, and
 * cannot pass while the page merely stops erroring some other way.
 */
class HttpPageLoaderUrlResolutionTest {

    @TempDir
    lateinit var tempDir: Path

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    /**
     * The reported shape: the source resolves the page to a protocol-relative string. Before the fix
     * that string reached the request layer unchanged, and OkHttp refused to build a request from it.
     */
    @Test
    fun `a protocol-relative URL from the source is resolved before the image is requested`() {
        val fixture = Fixture(resolvingTo = "//cdn.example.com/data/1.jpg")

        assertEquals("https://cdn.example.com/data/1.jpg", fixture.loadAndRecordRequestedUrl())
    }

    @Test
    fun `a root-relative URL from the source is resolved before the image is requested`() {
        val fixture = Fixture(resolvingTo = "/data/1.jpg")

        assertEquals("https://mangadex.org/data/1.jpg", fixture.loadAndRecordRequestedUrl())
    }

    /**
     * A source may hand back the image URL directly from `pageListParse` — `img.attr("src")` rather
     * than `absUrl("src")` is ordinary source code — in which case `getImageUrl` is never called and
     * the `HttpSource` request builders are never reached. The loader has to be the one that
     * resolves it.
     */
    @Test
    fun `a relative image URL supplied with the page list is resolved by the loader`() {
        val fixture = Fixture(pageListImageUrl = "//cdn.example.com/data/1.jpg")

        assertEquals("https://cdn.example.com/data/1.jpg", fixture.loadAndRecordRequestedUrl())
    }

    /**
     * The page list is cached across sessions, so a URL stored by a build that did not resolve is
     * read back by one that does. The reader must not need its cache cleared to benefit from the
     * fix, and the disk-cache key has to be the absolute string either way or the same bytes are
     * stored twice under two spellings.
     */
    @Test
    fun `a relative URL restored from the page list cache is resolved by the loader`() {
        val fixture = Fixture(cachedPageImageUrl = "/data/1.jpg")

        assertEquals("https://mangadex.org/data/1.jpg", fixture.loadAndRecordRequestedUrl())
    }

    /**
     * The counterweight: an address that already works is passed through untouched. A fix that
     * canonicalised URLs would satisfy every test above while quietly invalidating the signed CDN
     * URLs `DEF-020` exists for, so the exact string is asserted rather than a normalised form.
     */
    @Test
    fun `an absolute URL reaches the cache exactly as the source spelled it`() {
        val signed = "https://cdn.example.com/1.jpg?token=aB3%2Fxyz&expires=1893456000"
        val fixture = Fixture(resolvingTo = signed)

        assertEquals(signed, fixture.loadAndRecordRequestedUrl())
    }

    /**
     * The page must actually reach `Ready`, not merely produce an absolute string. A resolver that
     * satisfied the assertions above by returning something the request layer then rejected would
     * leave the reader showing an error, which is the symptom being fixed.
     */
    @Test
    fun `the page reaches Ready rather than an error state`() {
        val fixture = Fixture(resolvingTo = "//cdn.example.com/data/1.jpg")

        val page = fixture.loadFirstPage()

        assertEquals(Page.State.Ready, page.status, "the reported failure ended in Page.State.Error")
        assertEquals("https://cdn.example.com/data/1.jpg", page.imageUrl)
    }

    /** One loader, one page, and the recording cache they share. */
    private inner class Fixture(
        resolvingTo: String? = null,
        pageListImageUrl: String? = null,
        cachedPageImageUrl: String? = null,
    ) {
        private val cache = RecordingChapterCache(tempDir, cachedPageImageUrl)
        private val source = TestSource(
            resolvedImageUrl = resolvingTo,
            pageListImageUrl = pageListImageUrl,
        )
        private val chapter = ReaderChapter(Chapter.create().copy(id = 1, name = "Ch 1"))
        private val loader = HttpPageLoader(chapter, source, cache)

        /**
         * Loads the page and returns the address the loader asked for. The status is checked first:
         * a page that ended in `Error` never issued a request, so a bare URL assertion could pass
         * against a stale recording or report a null for a reason that has nothing to do with the
         * rule under test.
         */
        fun loadAndRecordRequestedUrl(): String {
            val page = loadFirstPage()
            assertEquals(
                Page.State.Ready,
                page.status,
                "the page ended in ${page.status} rather than Ready, so the URL was never requested",
            )
            return requireNotNull(cache.requested) { "the image was never requested" }
        }

        fun loadFirstPage(): ReaderPage {
            val pages = runBlocking { loader.getPages() }
            chapter.state = ReaderChapter.State.Loaded(pages)
            chapter.pageLoader = loader
            val page = pages.first().also { it.chapter = chapter }

            scope.launch { loader.loadPage(page) }

            val deadline = System.currentTimeMillis() + SETTLE_TIMEOUT_MS
            while (page.status !is Page.State.Ready && page.status !is Page.State.Error) {
                assertTrue(
                    System.currentTimeMillis() < deadline,
                    "the page never settled; last status was ${page.status}",
                )
                delay(POLL_INTERVAL_MS)
            }
            loader.recycle()
            return page
        }
    }

    private companion object {
        const val SETTLE_TIMEOUT_MS = 15_000L
        const val POLL_INTERVAL_MS = 10L
    }

    /**
     * Records the address the loader asks for and hands back a real file so the page can reach
     * `Ready`. The image lambda is never invoked, which is what keeps this test off the network: it
     * asserts the address, and the address is the whole defect.
     */
    private class RecordingChapterCache(
        tempDir: Path,
        cachedPageImageUrl: String?,
    ) : ChapterCache {
        private val image = tempDir.resolve("1.jpg").toFile().apply { writeBytes(byteArrayOf(1)) }

        var requested: String? = null
            private set

        /**
         * Returning a cached list is what selects the "restored from cache" path; throwing sends the
         * loader to the source instead. The distinction is the point — the two routes reach the
         * request layer by different means and both have to end up absolute.
         */
        override fun getPageListFromCache(chapter: Chapter): List<Page> =
            cachedPageImageUrl?.let { listOf(Page(0, "/page/1.jpg", it)) }
                ?: throw NoSuchElementException("no page list cached")

        override fun putPageListToCache(chapter: Chapter, pages: List<Page>) = Unit

        override fun isImageInCache(imageUrl: String): Boolean = false

        override suspend fun fetchAndCacheImage(imageUrl: String, fetchImage: suspend () -> Response) {
            requested = imageUrl
        }

        override fun getImageFile(imageUrl: String): File = image

        override suspend fun getReadableSize(): String = "0"

        override fun clear(): Int = 0
    }

    /**
     * `headersBuilder` is overridden because the base implementation reads
     * `network.defaultUserAgentProvider()`, and `network` comes from the Injekt service locator — a
     * test that had to stand one up to assert a string join could fail for unrelated reasons.
     */
    private class TestSource(
        private val resolvedImageUrl: String?,
        private val pageListImageUrl: String?,
    ) : HttpSource() {
        override val baseUrl: String = "https://mangadex.org"
        override val name: String = "Test"
        override val lang: String = "en"

        override fun headersBuilder(): Headers.Builder = Headers.Builder()

        override suspend fun getPageList(chapter: SChapter): List<Page> =
            listOf(Page(0, "/page/1.jpg", pageListImageUrl))

        override suspend fun getImageUrl(page: Page): String =
            requireNotNull(resolvedImageUrl) { "this fixture was not given a URL to resolve" }

        override suspend fun getPopularManga(page: Int): MangasPage = throw UnsupportedOperationException()

        override suspend fun getSearchManga(
            page: Int,
            query: String,
            filters: FilterList,
        ): MangasPage = throw UnsupportedOperationException()

        override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

        override suspend fun getMangaDetails(manga: SManga): SManga = throw UnsupportedOperationException()

        override suspend fun getChapterList(manga: SManga): List<SChapter> = throw UnsupportedOperationException()

        override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

        override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

        override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

        override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

        override fun searchMangaRequest(
            page: Int,
            query: String,
            filters: FilterList,
        ): Request = throw UnsupportedOperationException()

        override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

        override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

        override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

        override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

        override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
    }
}
