package ephyra.feature.reader.loader

import ephyra.core.common.util.network.FailureLayer
import ephyra.core.common.util.network.LayeredFailure
import ephyra.core.common.util.network.MalformedImageUrlException
import ephyra.core.common.util.network.PageLoadRecovery
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.service.ChapterCache
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import eu.kanade.tachiyomi.network.HttpException
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import java.nio.file.Path

/**
 * Pins the behaviour a source with rotating image hosts depends on: the first resolution names a CDN
 * that fails, the second names a different one, and the page loads.
 *
 * **Why this needed its own file.** `HttpPageLoaderUrlResolutionTest` covers the address that gets
 * requested; this covers what happens after a request *fails*. They need different fixtures — one
 * needs a cache that can refuse a fetch, the other does not — and merging them would have produced a
 * single class whose two halves share a setup neither half fully uses.
 *
 * **Why it existed as a gap at all.** `PageRetryUrlPolicyStructuralTest` asserts, textually, that
 * the loader *asks* the shared classifier rather than consulting a counter, and its own docstring
 * says it does not prove a page recovers. So the behaviour was asserted nowhere: the code read as
 * though the CDN swap worked, and nothing had ever watched it do so. `DEF-028` found the same gap
 * from the other side — the downloader could not do the swap at all.
 *
 * **What is asserted.** The sequence of URLs the loader actually requested, in order. That is the
 * whole claim: if a second, different host was requested, the source got a second chance; if the
 * first host was requested twice, the retry was theatre and the page would be unrecoverable.
 */
class HttpPageLoaderCdnSwapTest {

    @TempDir
    lateinit var tempDir: Path

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    /**
     * The reported scenario. A `403` on the first CDN — a refused or expired signed URL — must drop
     * that URL and ask the source again, and the second host must be the one requested.
     */
    @Test
    fun `a refused CDN is dropped and the source is asked for a different one`() = runBlocking {
        val fixture = Fixture(
            urlsFromSource = listOf("https://cdn-a.example/1.jpg", "https://cdn-b.example/1.jpg"),
            failWith = HttpException(403),
        )

        val page = fixture.loadFirstPage()

        assertEquals(Page.State.Ready, page.status, "the page gave up instead of trying the second host")
        assertEquals(
            listOf("https://cdn-a.example/1.jpg", "https://cdn-b.example/1.jpg"),
            fixture.cache.requested,
            "the retry must request the second host, not repeat the first",
        )
    }

    /**
     * The source must be asked exactly once per attempt, not repeatedly. Every extra call is a
     * round-trip spent learning nothing, and against a source that counts resolution calls it is
     * the difference between recovering and being rate-limited.
     */
    @Test
    fun `the source is asked once per attempt, never twice for one retry`() = runBlocking {
        val fixture = Fixture(
            urlsFromSource = listOf("https://cdn-a.example/1.jpg", "https://cdn-b.example/1.jpg"),
            failWith = HttpException(403),
        )

        fixture.loadFirstPage()

        assertEquals(2, fixture.source.resolutions, "one resolution per attempt, and no more")
    }

    /**
     * A host that does not resolve is the same shape of problem as a refused one: the URL's host is
     * the suspect, and a fresh resolution is the only thing that can replace it.
     */
    @Test
    fun `a CDN whose host does not resolve is replaced rather than retried`() = runBlocking {
        val fixture = Fixture(
            urlsFromSource = listOf("https://cdn-dead.example/1.jpg", "https://cdn-b.example/1.jpg"),
            failWith = UnknownHostException("cdn-dead.example"),
        )

        val page = fixture.loadFirstPage()

        assertEquals(Page.State.Ready, page.status)
        assertEquals(
            listOf("https://cdn-dead.example/1.jpg", "https://cdn-b.example/1.jpg"),
            fixture.cache.requested,
        )
    }

    /**
     * The counterweight, and the reason the re-resolve is not unconditional. A dropped connection
     * says nothing about the URL, so spending a source round-trip to be handed the same host is
     * waste — and on a source that rotates hosts, it is worse than waste: it can hand back the same
     * dead host that just failed.
     */
    @Test
    fun `a dropped connection retries the same URL without asking the source again`() = runBlocking {
        val fixture = Fixture(
            urlsFromSource = listOf("https://cdn-a.example/1.jpg", "https://cdn-b.example/1.jpg"),
            failWith = IOException("connection reset"),
        )

        val page = fixture.loadFirstPage()

        assertEquals(Page.State.Ready, page.status)
        assertEquals(
            listOf("https://cdn-a.example/1.jpg", "https://cdn-a.example/1.jpg"),
            fixture.cache.requested,
            "a transient connection failure must not spend a resolution on a URL that is still good",
        )
        assertEquals(1, fixture.source.resolutions, "and must not ask the source a second time")
    }

    /**
     * A `404` will be a `404` on a different host, so re-resolving is pure waste — and it is the
     * case where a source that is merely misbehaving can be made to look busy by a client that
     * cannot tell the difference.
     */
    @Test
    fun `a permanent status is not retried and does not ask the source again`() = runBlocking {
        val fixture = Fixture(
            urlsFromSource = listOf("https://cdn-a.example/1.jpg", "https://cdn-b.example/1.jpg"),
            failWith = HttpException(404),
        )

        val page = fixture.loadFirstPage()

        assertTrue(page.status is Page.State.Error, "a 404 must not be retried")
        assertEquals(listOf("https://cdn-a.example/1.jpg"), fixture.cache.requested)
        assertEquals(1, fixture.source.resolutions)
    }

    /**
     * When every host is refused, the page fails — and it must fail *without* a URL on it. A page
     * holding an address known to be bad would have that address written to the persisted page list,
     * so the next open of this chapter would begin by requesting it again.
     */
    @Test
    fun `a page that exhausts its hosts fails holding no URL`() = runBlocking {
        val fixture = Fixture(
            urlsFromSource = List(8) { "https://cdn-$it.example/1.jpg" },
            failWith = HttpException(403),
            failTimes = Int.MAX_VALUE,
        )

        val page = fixture.loadFirstPage()

        assertTrue(page.status is Page.State.Error, "every host was refused, so this must fail")
        assertNull(page.imageUrl, "a URL known to be refused must not survive to be persisted")
    }

    /**
     * A source that hands back the *same* unusable string cannot be rescued by asking again, and
     * each ask is a round-trip spent learning nothing. The second attempt must fail without a second
     * image request going out.
     */
    @Test
    fun `a source repeating one refused host is not asked to try again indefinitely`() = runBlocking {
        val fixture = Fixture(
            urlsFromSource = List(8) { "https://cdn-dead.example/1.jpg" },
            failWith = HttpException(403),
            failTimes = Int.MAX_VALUE,
        )

        fixture.loadFirstPage()

        assertTrue(
            fixture.source.resolutions <= MAX_ATTEMPTS,
            "asked the source ${fixture.source.resolutions} times for a string it had already given; " +
                "the retry budget is supposed to bound this",
        )
    }

    /**
     * The case this whole investigation started from, pinned as behaviour rather than as prose.
     *
     * A source can hand back an address that parses but cannot possibly address a host — the reported
     * one was a host and a scheme joined by a comma, `cmdxd98sb0x3yprd.mangadex.network,https`. Two
     * properties matter and both are asserted here:
     *
     * 1. **No request is spent on it.** A comma is not a forbidden host character, so without a
     *    pre-flight check OkHttp canonicalises it, hands it to DNS, and the user is shown
     *    `UnknownHostException` — a network verdict about a host that never existed.
     * 2. **The failure is attributed to the adapter, not the network.** The address was already
     *    impossible when the source produced it; the resolver did its job correctly on a string it
     *    should never have been handed. Getting this wrong is what made the original report expensive.
     */
    @Test
    fun `a spliced address is refused before a request is sent, and blamed on the adapter`() = runBlocking {
        val spliced = "https://cmdxd98sb0x3yprd.mangadex.network,https"
        val fixture = Fixture(
            urlsFromSource = List(8) { spliced },
            failWith = IOException("the fetch should never be reached"),
            failTimes = Int.MAX_VALUE,
        )

        val page = fixture.loadFirstPage()

        assertTrue(page.status is Page.State.Error, "an unusable address must not resolve to Ready")
        assertTrue(
            fixture.cache.requested.isEmpty(),
            "no request may be spent on an address that cannot address a host, but these were sent: " +
                fixture.cache.requested,
        )

        val error = (page.status as Page.State.Error).error
        assertTrue(
            error is MalformedImageUrlException,
            "expected the address to be refused by the owner of that judgement, got: $error",
        )
        assertEquals(
            FailureLayer.ADAPTER,
            LayeredFailure.classify("image request", spliced, error).layer,
            "the source produced this string, so the fault is the adapter's — not the network's",
        )
    }

    private inner class Fixture(
        private val urlsFromSource: List<String>,
        private val failWith: Throwable,
        /**
         * How many fetches refuse before one succeeds. `1` models the reported case — a bad first
         * host, a good second — and a value past the retry budget models a chapter where every host
         * is bad, which is the only way to reach a page that gives up.
         */
        private val failTimes: Int = 1,
    ) {
        val cache = SwappingChapterCache(tempDir, failWith, failTimes)
        val source = RotatingSource(urlsFromSource)
        private val chapter = ReaderChapter(Chapter.create().copy(id = 1, name = "Ch 1"))
        private val loader = HttpPageLoader(chapter, source, cache)

        suspend fun loadFirstPage(): ReaderPage {
            val pages = runBlocking { loader.getPages() }
            chapter.state = ReaderChapter.State.Loaded(pages)
            chapter.pageLoader = loader
            val page = pages.first().also { it.chapter = chapter }

            scope.launch { loader.loadPage(page) }

            val deadline = System.currentTimeMillis() + SETTLE_TIMEOUT_MS
            while (page.status !is Page.State.Ready && page.status !is Page.State.Error) {
                assertTrue(
                    System.currentTimeMillis() < deadline,
                    "the page never settled; last status was ${page.status}, " +
                        "requested so far: ${cache.requested}",
                )
                delay(POLL_INTERVAL_MS)
            }
            loader.recycle()
            return page
        }
    }

    private companion object {
        /** First attempt plus the retry budget, from the owner rather than restated here. */
        const val MAX_ATTEMPTS = PageLoadRecovery.DEFAULT_MAX_RETRIES + 1
        const val SETTLE_TIMEOUT_MS = 20_000L
        const val POLL_INTERVAL_MS = 10L
    }

    /**
     * Fails the first fetch with the configured error, then succeeds. The image lambda is never
     * invoked, so the test stays off the network: it asserts which URL was *asked for*, which is the
     * entire behaviour under test.
     */
    private class SwappingChapterCache(
        tempDir: Path,
        private val failWith: Throwable,
        private val failTimes: Int,
    ) : ChapterCache {
        override fun removeChapter(chapter: ephyra.domain.chapter.model.Chapter): Boolean = false

        private val image = tempDir.resolve("1.jpg").toFile().apply { writeBytes(byteArrayOf(1)) }

        val requested = mutableListOf<String>()

        override fun getPageListFromCache(chapter: Chapter): List<Page> =
            throw NoSuchElementException("no page list cached; the test drives the source path")

        override fun putPageListToCache(chapter: Chapter, pages: List<Page>) = Unit

        override fun isImageInCache(imageUrl: String): Boolean = false

        override suspend fun fetchAndCacheImage(imageUrl: String, fetchImage: suspend () -> Response) {
            requested += imageUrl
            if (requested.size <= failTimes) throw failWith
        }

        override fun getImageFile(imageUrl: String): File = image

        override suspend fun getReadableSize(): String = "0"

        override fun clear(): Int = 0
    }

    /** Hands out [urlsFromSource] in order, then keeps returning the last one. */
    private class RotatingSource(private val urlsFromSource: List<String>) : HttpSource() {
        override val baseUrl: String = "https://mangadex.org"
        override val name: String = "Test"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        var resolutions = 0
            private set

        override fun headersBuilder(): Headers.Builder = Headers.Builder()

        override suspend fun getPageList(chapter: SChapter): List<Page> =
            listOf(Page(0, "/page/1.jpg", null))

        override suspend fun getImageUrl(page: Page): String {
            val index = resolutions.coerceAtMost(urlsFromSource.lastIndex)
            resolutions++
            return urlsFromSource[index]
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

        override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
    }
}
