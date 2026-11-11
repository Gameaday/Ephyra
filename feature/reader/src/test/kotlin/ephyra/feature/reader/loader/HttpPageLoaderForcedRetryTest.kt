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
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Pins the **forced retry**: the user's Retry button re-downloads a page whose bytes are already
 * in the cache, while every automatic path keeps serving the cache.
 *
 * **The defect shape this prevents.** A page can fail *after* its bytes were cached. The loader
 * served a cache hit, the viewer's decode of those bytes failed — a source that answered `200`
 * with an HTML error page, or a truncated entry written by an older build — and the viewer showed
 * its error view with a Retry button. Before this change the retry re-queued the page, the reload
 * found the bad entry still in the cache, skipped the download, handed out the identical bytes,
 * and the page failed identically — forever, because the recovery ladder only runs for a page
 * that is not `Ready`. The user's Retry was a verbatim repeat of a failure they cannot get out
 * of, the reader-side twin of `DEF-023`'s "even after retry".
 *
 * **The rule, and its counterweight.** Upstream Mihon's `retryPage` passes `force = true` to
 * `internalLoadPage`, so a user retry re-downloads past the cache; the automatic paths — the
 * preload enqueues and the eviction reload — stay unforced, because nobody asked them to spend
 * the user's bandwidth re-fetching bytes that were never implicated. Both halves are pinned here:
 * the forced retry re-fetches (first test), and an automatic load of a fully-cached chapter never
 * touches the network (second test). Without the second assertion, a fix that forced *every*
 * load would pass the first and silently double the app's traffic.
 *
 * **Why behavioural after all.** `PageRetryUrlPolicyStructuralTest` explains why the *URL policy*
 * is gated structurally — the loader there needed a live Coil `DiskCache`. This test does not:
 * the cache boundary is the fake, the source is the fake, and the property asserted —
 * `fetchAndCacheImage` called, or not — is observable at exactly the boundary the fix moves. No
 * device is claimed: this proves the wiring, not a recovered page on hardware.
 */
class HttpPageLoaderForcedRetryTest {

    @TempDir
    lateinit var tempDir: Path

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `a user retry re-downloads a page whose bytes are already cached`() = runBlocking {
        val fixture = Fixture(pages = 1)

        val page = fixture.loadFirstPage()

        // Baseline: the first load was a pure cache hit — the network was never touched, which is
        // the state the decode failure happened in.
        assertEquals(0, fixture.fetchCalls, "the first load must be served from the cache")
        assertTrue(page.status is Page.State.Ready, "expected the page ready, got " + page.status)

        // The reported scenario: the viewer failed to decode the cached bytes and the user pressed
        // Retry. The loader still considers the page Ready — the failure lived below its ladder —
        // so this exercises retryPage on a Ready page, exactly as the viewer calls it.
        fixture.retry(page)

        val status = page.status
        assertTrue(status is Page.State.Ready, "expected the retried page to settle ready, got " + status)

        // The fix: the retry went past the cache and asked the source for new bytes.
        assertEquals(1, fixture.fetchCalls, "the retry must re-download past the cached entry")
        assertEquals(1, fixture.getImageCalls, "the re-download must come from the source")

        // And the counterweight inside the retry itself: the error was not the URL's fault, so the
        // resolved address survives — the retry re-fetches bytes, not a new address.
        assertEquals(Fixtures.IMAGE_URL, page.imageUrl, "a forced retry must not drop a URL that was not at fault")
    }

    @Test
    fun `an automatic load of a fully cached chapter never re-downloads`() = runBlocking {
        val fixture = Fixture(pages = 2)

        fixture.loadFirstPageAndPreload()

        // Both pages settled Ready out of the cache. Every enqueue on this path — the load itself,
        // the forward preload — is automatic, so none of them may force a download past the cache.
        assertEquals(0, fixture.fetchCalls, "an automatic path must serve cached bytes as-is")
        assertEquals(0, fixture.getImageCalls, "no automatic path may touch the network for cached bytes")
    }

    /**
     * One loader over a cache that already holds every image, plus the source and page plumbing
     * that produces the cached-hit shape.
     */
    private inner class Fixture(pages: Int) {
        private val source = CountingSource(pages)
        private val cache = PreloadedImageCache(tempDir, pages)
        private val chapter = ReaderChapter(Chapter.create().copy(id = 1, name = "Ch 1"))
        private val loader = HttpPageLoader(chapter, source, cache)

        /** How many times the cache was asked to fetch-and-store an image. */
        val fetchCalls: Int get() = cache.fetchCalls

        /** How many times the source's image request was actually built and returned. */
        val getImageCalls: Int get() = source.getImageCalls

        suspend fun loadFirstPage(): ReaderPage {
            val pages = loader.getPages()
            chapter.state = ReaderChapter.State.Loaded(pages)
            chapter.pageLoader = loader
            val page = pages.first().also { it.chapter = chapter }

            scope.launch { loader.loadPage(page) }
            settle(page)
            return page
        }

        suspend fun loadFirstPageAndPreload(): List<ReaderPage> {
            val pages = loader.getPages()
            chapter.state = ReaderChapter.State.Loaded(pages)
            chapter.pageLoader = loader
            val page = pages.first().also { it.chapter = chapter }
            pages.forEach { it.chapter = chapter }

            scope.launch { loader.loadPage(page) }
            pages.forEach { settle(it) }
            loader.recycle()
            return pages
        }

        /** Presses Retry the way [ephyra.feature.reader.viewer.ZoomableMangaPage] does, then settles. */
        suspend fun retry(page: ReaderPage) {
            loader.retryPage(page)
            settle(page)
            loader.recycle()
        }

        private suspend fun settle(page: ReaderPage) {
            val deadline = System.currentTimeMillis() + SETTLE_TIMEOUT_MS
            while (page.status !is Page.State.Ready && page.status !is Page.State.Error) {
                assertTrue(
                    System.currentTimeMillis() < deadline,
                    "the page never settled; last status was " + page.status,
                )
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private object Fixtures {
        const val IMAGE_URL = "https://img.example.com/data/1.jpg"
    }

    /**
     * A source that hands back a complete page list — every page already carrying its resolved
     * image URL, the 1.6-extension shape — and counts the image requests it serves.
     *
     * The counting happens at [client], not at an overridden `getImage`: on this host `getImage`
     * is a concrete, final convenience that pipes [imageRequest] through the real OkHttp client,
     * so there is nothing to override. Overriding [client] with an interceptor-backed one is the
     * seam the loader actually reaches — every `getImage` call passes through the interceptor,
     * which counts the request and answers a canned 200 without touching a network.
     */
    private class CountingSource(pageCount: Int) : HttpSource() {
        override val baseUrl: String = "https://example.org"
        override val name: String = "Counting"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        var getImageCalls: Int = 0
            private set

        override val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                getImageCalls++
                Response.Builder()
                    .code(200)
                    .message("OK")
                    .protocol(Protocol.HTTP_1_1)
                    .request(chain.request())
                    .body(ByteArray(0).toResponseBody(null))
                    .build()
            }
            .build()

        private val pages = (0 until pageCount).map { index ->
            Page(index, "/data/${index + 1}.jpg", "https://img.example.com/data/${index + 1}.jpg")
        }

        override suspend fun getPageList(chapter: SChapter): List<Page> = pages

        override fun headersBuilder(): Headers.Builder = Headers.Builder()

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
     * A cache that already holds a (deliberately undecodable) entry for every image — the state a
     * chapter is in when its bytes were stored by a bad pass. The fetch boundary counts calls,
     * invokes the fetch lambda the way the real cache does, and replaces the entry with fresh
     * bytes, so a forced retry both re-downloads and recovers.
     */
    private class PreloadedImageCache(tempDir: Path, pageCount: Int) : ChapterCache {
        private val files = (0 until pageCount).map { index ->
            tempDir.resolve("${index + 1}.jpg").toFile().apply { writeBytes(BAD_BYTES) }
        }

        var fetchCalls: Int = 0
            private set

        override fun removeChapter(chapter: Chapter): Boolean = false

        override fun getPageListFromCache(chapter: Chapter): List<Page> =
            throw NoSuchElementException("no page list cached")

        override fun putPageListToCache(chapter: Chapter, pages: List<Page>) = Unit

        override fun isImageInCache(imageUrl: String): Boolean = true

        override suspend fun fetchAndCacheImage(imageUrl: String, fetchImage: suspend () -> Response) {
            fetchCalls++
            // The real cache invokes the fetch lambda to obtain the bytes it stores; the
            // fake must too, or the source behind the lambda is never exercised and the
            // forced-retry wiring goes unproven.
            fetchImage()
            // Serve the new bytes the way a real fetch would have written them.
            val index = fetchCalls - 1
            files[index.coerceAtMost(files.lastIndex)].writeBytes(GOOD_BYTES)
        }

        override fun getImageFile(imageUrl: String): File =
            files.firstOrNull { it.exists() } ?: files.first()

        override suspend fun getReadableSize(): String = "0"

        override fun clear(): Int = 0

        private companion object {
            /** Bytes a decoder would reject — the HTML error page a source cached as an image. */
            val BAD_BYTES = "<html><body>rate limited</body></html>".toByteArray()

            /** Bytes a decoder accepts, standing in for the recovered image. `0x89` needs an explicit `.toByte()`: 137 exceeds `Byte`'s signed range, so the constant conversion to `Byte` does not apply. */
            val GOOD_BYTES = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        }
    }

    private companion object {
        const val SETTLE_TIMEOUT_MS = 15_000L
        const val POLL_INTERVAL_MS = 10L
    }
}
