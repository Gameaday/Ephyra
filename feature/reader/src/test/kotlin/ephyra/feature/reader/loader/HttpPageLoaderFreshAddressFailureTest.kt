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
import java.net.UnknownHostException
import java.nio.file.Path

/**
 * Pins that a fresh page-list fetch made from inside the recovery ladder's error path fails the
 * *page*, not the process.
 *
 * **The reported crash.** Coming back to Ephyra from another app and opening a chapter killed the
 * reader; the next launch showed the crash screen with:
 *
 * ```
java.net.UnknownHostException: Unable to resolve host "api.mangadex.org": No address associated with hostname
 at ephyra.feature.reader.loader.FreshPageAddresses.at(HttpPageLoader.kt:73)
 at ephyra.feature.reader.loader.HttpPageLoader.internalLoadPage(HttpPageLoader.kt:768)
```
 *
 * A device that has just returned from the background often does not have DNS ready again yet, so
 * the image request failed with `UnknownHostException`. That failure is meant to be recoverable — the
 * ladder drops the URL and asks the source for a fresh page list — but the replacement fetch is
 * itself network I/O made from inside the `catch` block, and its own `UnknownHostException`
 * (the API host this time) escaped the retry loop, the worker coroutine (a standalone coroutine
 * with no `CoroutineExceptionHandler`) and the process.
 *
 * **What is asserted.** That the page settles into `Page.State.Error` — impossible if the worker
 * died — and that the surfaced error is the one that actually stopped the page (the image request,
 * `img.example.com`) rather than the machinery of the failed replacement fetch (`api.example.org`).
 * The page is left with no address and its fresh-address flag still set, so a Retry re-asks the
 * source for a list rather than re-requesting the URL the ladder indicted.
 */
class HttpPageLoaderFreshAddressFailureTest {

    @TempDir
    lateinit var tempDir: Path

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `a replacement fetch that fails on the error path fails the page not the reader`() =
        runBlocking {
            val fixture = Fixture()

            val page = fixture.loadFirstPage()

            val status = page.status
            assertTrue(status is Page.State.Error, "expected the page to settle into an error, got " + status)

            // The failure surfaced is the one that stopped the page — the image request — and not
            // the replacement fetch's own failure, which belongs in the log, not on the page.
            val message = (status as Page.State.Error).error.message.orEmpty()
            assertTrue(
                message.contains(IMAGE_HOST),
                "expected the image request's failure, got: " + message,
            )
            assertTrue(
                !message.contains(API_HOST),
                "the replacement fetch's failure must not replace the page's own: " + message,
            )

            // The replacement fetch was attempted (the guarded call this test exists for), and the
            // page it could not repair is left asking for a fresh list.
            assertTrue(
                fixture.pageListCalls >= 2,
                "the replacement fetch was never attempted",
            )
            assertTrue(page.needsFreshAddress, "the page must stay flagged for a fresh list")
            assertEquals(null, page.imageUrl)
        }

    /**
     * One loader, one page, and the source and cache that produce the reported failure shape.
     */
    private inner class Fixture {
        private val source = FlakyPageListSource()
        private val cache = MissingImageCache(tempDir)
        private val chapter = ReaderChapter(Chapter.create().copy(id = 1, name = "Ch 1"))
        private val loader = HttpPageLoader(chapter, source, cache)

        /** How many times the source was asked for a page list, successful or not. */
        val pageListCalls: Int get() = source.pageListCalls

        suspend fun loadFirstPage(): ReaderPage {
            val pages = loader.getPages()
            chapter.state = ReaderChapter.State.Loaded(pages)
            chapter.pageLoader = loader
            val page = pages.first().also { it.chapter = chapter }

            scope.launch { loader.loadPage(page) }

            val deadline = System.currentTimeMillis() + SETTLE_TIMEOUT_MS
            while (page.status !is Page.State.Ready && page.status !is Page.State.Error) {
                assertTrue(
                    System.currentTimeMillis() < deadline,
                    "the page never settled; last status was " + page.status,
                )
                delay(POLL_INTERVAL_MS)
            }
            loader.recycle()
            return page
        }
    }

    /**
     * A source shaped like the reported failure: the first page list succeeds (the chapter opened
     * normally), and every later one fails the way a device resume does — the API host does not
     * resolve, because DNS is not ready again yet.
     *
     * `getPageList` is stateful on purpose: the loader memoises a *successful* list, so the
     * replacement attempts must fail at the source rather than be served the first answer.
     */
    private open class FlakyPageListSource : HttpSource() {
        override val baseUrl: String = "https://example.org"
        override val name: String = "Flaky"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override fun headersBuilder(): Headers.Builder = Headers.Builder()

        var pageListCalls: Int = 0
            private set

        override suspend fun getPageList(chapter: SChapter): List<Page> {
            pageListCalls++
            return if (pageListCalls == 1) {
                listOf(Page(0, "/page/1.jpg", "https://" + IMAGE_HOST + "/data/1.jpg"))
            } else {
                throw UnknownHostException(
                    "Unable to resolve host " + API_HOST + ": No address associated with hostname",
                )
            }
        }

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
    }

    /**
     * A cache that misses the page list (so the loader asks the source) and fails the image fetch
     * the way the device did — the image host does not resolve. Thrown at the `fetchAndCacheImage`
     * boundary, the call the loader makes for the image, so no network and no `OkHttpClient` are
     * needed: the exception surfaces from exactly where the loader's catch block will see it.
     */
    private class MissingImageCache(tempDir: Path) : ChapterCache {
        private val image = tempDir.resolve("1.jpg").toFile().apply { writeBytes(byteArrayOf(1)) }

        override fun removeChapter(chapter: Chapter): Boolean = false

        override fun getPageListFromCache(chapter: Chapter): List<Page> =
            throw NoSuchElementException("no page list cached")

        override fun putPageListToCache(chapter: Chapter, pages: List<Page>) = Unit

        override fun isImageInCache(imageUrl: String): Boolean = false

        override suspend fun fetchAndCacheImage(imageUrl: String, fetchImage: suspend () -> Response) {
            throw UnknownHostException(
                "Unable to resolve host " + IMAGE_HOST + ": No address associated with hostname",
            )
        }

        override fun getImageFile(imageUrl: String): File = image

        override suspend fun getReadableSize(): String = "0"

        override fun clear(): Int = 0
    }

    private companion object {
        const val SETTLE_TIMEOUT_MS = 15_000L
        const val POLL_INTERVAL_MS = 10L

        /** The image host, whose failure is the one that stopped the page. */
        const val IMAGE_HOST = "img.example.com"

        /** The API host, whose failure is the replacement fetch's — reported, not surfaced. */
        const val API_HOST = "api.example.org"
    }
}
