package ephyra.feature.reader.loader

import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.service.ChapterCache
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.PageImageAddress
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import rx.Observable
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
 * `HttpSourceImageUrlRequestTest` pins the default request builders, but neither proves the whole
 * path applies it — and three routes reach a request without passing through either: a source that
 * overrides `getImageUrl` and returns a relative string, a source that sets `Page.imageUrl` itself in
 * `pageListParse`, and a page whose URL was restored from the chapter cache by a previous session.
 * Each is one `imageUrl` away from the reported crash, so each is a case here.
 *
 * **Where resolution happens — and where it must not.** The loader never rewrites a populated
 * `Page.imageUrl` (it is opaque to the host; see `MangaDexOpaqueImageUrlContractTest` for the source
 * whose own `imageRequest` depends on the raw value), so the recorded address in these tests is the
 * *raw* spelling, and the resolved request is asserted separately through the boundary
 * `HttpSource.imageRequest` applies. That split is the contract: the page, the disk-cache key, and
 * the persisted page list all carry the source's exact string, and the request — and only the
 * request — is resolved.
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
    fun `a protocol-relative URL from the source is resolved before the image is requested`() = runBlocking {
        val fixture = Fixture(resolvingTo = "//cdn.example.com/data/1.jpg")

        assertEquals("https://cdn.example.com/data/1.jpg", fixture.loadAndRecordRequestedUrl())
    }

    @Test
    fun `a root-relative URL from the source is resolved before the image is requested`() = runBlocking {
        val fixture = Fixture(resolvingTo = "/data/1.jpg")

        assertEquals("https://mangadex.org/data/1.jpg", fixture.loadAndRecordRequestedUrl())
    }

    /**
     * A source may hand back the image URL directly from `pageListParse` — `img.attr("src")` rather
     * than `absUrl("src")` is ordinary source code — in which case `getImageUrl` is never called.
     * The loader must not resolve it itself (a populated `Page.imageUrl` is opaque to the host —
     * MangaDex's is a relative path its own `imageRequest` joins onto an at-home host), but the
     * request must still end up absolute, so both halves are asserted: the loader carries the value
     * to the cache exactly as the source spelled it, and the boundary `HttpSource.imageRequest` —
     * what `source.getImage` actually runs for a source that does not override it — resolves it.
     */
    @Test
    fun `a relative image URL supplied with the page list is resolved by the request builder`() =
        runBlocking {
            val fixture = Fixture(pageListImageUrl = "//cdn.example.com/data/1.jpg")

            // The cache key is the raw spelling the source produced — never rewritten by the loader.
            assertEquals("//cdn.example.com/data/1.jpg", fixture.loadAndRecordRequestedUrl())
            // And the request the base imageRequest will build from that page is the resolved one.
            assertEquals(
                "https://cdn.example.com/data/1.jpg",
                PageImageAddress.of(
                    fixture.pageForTheResolvedAssertion(),
                    BASE,
                    PageImageAddress.Field.IMAGE_URL,
                ).url.value,
            )
        }

    /**
     * The page list is cached across sessions, so a URL stored by an earlier build is read back by
     * this one. The loader does not rewrite a restored value either — for the same reason as above,
     * it cannot know which of the two fields the source's own `imageRequest` will interpret — but
     * the boundary resolves it, so the request is absolute and the page still reaches `Ready`.
     *
     * The key spelling being the raw string also means the same bytes are keyed consistently within
     * a build: whatever `page.imageUrl` holds is what `isImageInCache` and `getImageFile` are both
     * asked. The one cost is historical — a cache written by the build that resolved in the loader
     * holds the absolute spelling — and the chapter cache is a transient store, so it pays once.
     */
    @Test
    fun `a relative URL restored from the page list cache is resolved by the request builder`() =
        runBlocking {
            val fixture = Fixture(cachedPageImageUrl = "/data/1.jpg")

            assertEquals("/data/1.jpg", fixture.loadAndRecordRequestedUrl())
            assertEquals(
                "https://mangadex.org/data/1.jpg",
                PageImageAddress.of(
                    fixture.pageForTheResolvedAssertion(),
                    BASE,
                    PageImageAddress.Field.IMAGE_URL,
                ).url.value,
            )
        }

    /**
     * The counterweight: an address that already works is passed through untouched. A fix that
     * canonicalised URLs would satisfy every test above while quietly invalidating the signed CDN
     * URLs `DEF-020` exists for, so the exact string is asserted rather than a normalised form.
     */
    @Test
    fun `an absolute URL reaches the cache exactly as the source spelled it`() = runBlocking {
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
    fun `the page reaches Ready rather than an error state`() = runBlocking {
        val fixture = Fixture(resolvingTo = "//cdn.example.com/data/1.jpg")

        val page = fixture.loadFirstPage()

        assertEquals(Page.State.Ready, page.status, "the reported failure ended in Page.State.Error")
        assertEquals("https://cdn.example.com/data/1.jpg", page.imageUrl)
    }

    /**
     * The reported MangaDex failure, end to end.
     *
     * The owner sent this verbatim from their device's logcat:
     *
     * ```
     * https://cmdxd98sb0x3yprd.mangadex.network
     * ,https://api.mangadex.org/at-home/server/605c371d-904f-4dda-96a0-24ffdd65e642
     * ,1790648354548
     * ```
     *
     * Three parts joined by commas — a host, an API URL and a timestamp (`1790648354548` is
     * 2026-09-29T02:19:14Z). No URL has that shape, so whatever wrote it was returning a cache key
     * of its own where an address belongs.
     *
     * **Why the fix had to be at the cache read, not at the URL policy.** Every earlier fix checked
     * the URL *after* the page list was restored, and this value passes those checks long enough to
     * be rejected rather than repaired. The decisive detail is that the poisoned `imageUrl` is
     * non-empty, so the loader's "the cache gave us an unresolved page, ask the source" branch never
     * runs and `source.getImageUrl` is never called. The source could be fixed and this chapter
     * would still fail, on every open, from a file on disk.
     *
     * So the cache is given the same contract as every other provider: a hit that fails it is a
     * miss. The test asserts the address the loader ends up requesting is the source's, which is
     * only possible if the cached list was discarded and the source consulted.
     */
    @Test
    fun `a cached page list whose URL is not an address is discarded and refetched`() = runBlocking {
        val composite = "https://cmdxd98sb0x3yprd.mangadex.network" +
            ",https://api.mangadex.org/at-home/server/605c371d-904f-4dda-96a0-24ffdd65e642" +
            ",1790648354548"
        val good = "https://uploads.mangadex.org/data/ab/cd/1.jpg"
        val fixture = Fixture(resolvingTo = good, cachedPageImageUrl = composite)

        assertEquals(good, fixture.loadAndRecordRequestedUrl())
    }

    /**
     * The counterweight, and the reason the check is a contract rather than a rejection rule: an
     * ordinary cached list must still be a *hit*. If this fails, every chapter is refetching on
     * every open and the cache has been made useless rather than made safe.
     *
     * An empty `imageUrl` is the normal state of a list fetched from the network and not yet
     * resolved, so it is explicitly not a failure.
     */
    @Test
    fun `an ordinary cached page list is still used without asking the source`() = runBlocking {
        val cached = "https://uploads.mangadex.org/data/ab/cd/1.jpg"
        val fixture = Fixture(cachedPageImageUrl = cached)

        assertEquals(cached, fixture.loadAndRecordRequestedUrl())
    }

    /**
     * A blank `imageUrl` is only a defect when `Page.url` could not be fetched either.
     *
     * **Why this is the reported MangaDex failure, and why the rule changed.** The rule used to be "does
     * the source populate addresses?", read from a class probe and then a recorded flag. Both are gone:
     * a list is stale exactly when asking the source cannot help, which is visible on the page itself.
     *
     * MangaDex overrides none of `getImageUrl` / `fetchImageUrl` / `imageUrlRequest` / `imageUrlParse`
     * — it cannot, because upstream removed that chain from the extension API — so it puts the address
     * in `Page.imageUrl` and keeps an at-home token cache key in `Page.url`. A list whose `imageUrl` is
     * empty and whose `url` is that key cannot be repaired by asking: the fix is to refetch the list,
     * not to refuse it or to spend a round-trip on a value that is not an address.
     */
    @Test
    fun `a list whose blank addresses sit beside unfetchable urls is discarded`() {
        val cacheKey = "https://cmdxd98sb0x3yprd.mangadex.network," +
            "https://api.mangadex.org/at-home/server/605c371d-904f-4dda-96a0-24ffdd65e642,1790648354548"
        val stale = listOf(Page(0, cacheKey, null), Page(1, cacheKey, ""))
        assertFalse(HttpPageLoader.cachedPagesAreUsable(stale, BASE))
    }

    /** The counterweight: a lazy source's `url` is fetchable, so asking it is the right move. */
    @Test
    fun `a list whose blank addresses sit beside fetchable urls is kept`() {
        val cached = listOf(Page(0, "/page/1.jpg", null))
        assertTrue(HttpPageLoader.cachedPagesAreUsable(cached, BASE))
    }

    /** A populated address is judged on its own merits. */
    @Test
    fun `a populated address is judged the same either way`() {
        val good = listOf(Page(0, "/page/1.jpg", "https://cdn.example.com/1.jpg"))
        assertTrue(HttpPageLoader.cachedPagesAreUsable(good, BASE))

        val bad = listOf(Page(0, "/page/1.jpg", "https://a.example.com,https://b.example.com"))
        assertFalse(HttpPageLoader.cachedPagesAreUsable(bad, BASE))
    }

    /**
     * A cached *relative* URL is a supported state, not a defect, and this is the assertion that
     * keeps it one.
     *
     * The first version of the check judged the raw string, which rejected this — and the
     * pre-existing test for a relative URL restored from the cache failed as a result. That failure
     * was the check being wrong, not the test: `img.attr("src")` is ordinary source code, and
     * sending such a page back to the source on every open makes the cache useless rather than
     * safe. So the rule is "can this become an address", not "is this already one".
     */
    @Test
    fun `a cached relative URL is kept because it can be resolved, not refetched`() {
        assertTrue(
            HttpPageLoader.cachedPagesAreUsable(listOf(Page(0, "/page/1.jpg", "/data/1.jpg")), BASE),
        )
    }

    /** The reported value, as a pure rule, so the contract itself is pinned and not just a path. */
    @Test
    fun `the URL contract rejects the reported composite`() {
        val composite = "https://cmxd98sb0x3yprd.mangadex.network" +
            ",https://api.mangadex.org/at-home/server/605c371d-904f-4dda-96a0-24ffdd65e642" +
            ",1790648354548"

        assertFalse(
            HttpPageLoader.cachedPagesAreUsable(listOf(Page(0, "/page/1.jpg", composite)), BASE),
        )
    }

    /**
     * The reported MangaDex page, end to end — and the one case where `Page.url` is *not* an address.
     *
     * MangaDex keeps an at-home **token cache key** in `Page.url`, by design: its own
     * `MangaDexHelper` does
     * ```
     * val (host, tokenRequestUrl, time) = page.url.split(",")
     * ```
     * to recover the three parts, because MangaDex@Home tokens expire after five minutes and the
     * chapter is re-fetched when the cached one is stale. The reported value is exactly that shape —
     * `(at-home server, at-home API URL, fetch timestamp)`.
     *
     * **Why this is our bug and not the extension's.** The app treats `Page.url` as an image address
     * in one place only: the deprecated chain, where `imageUrlRequest` *fetches* it and hands the
     * response to `imageUrlParse`. That is correct for a legacy extension and wrong for this one, and
     * the difference is invisible until a source uses the field for something else. So the loader now
     * resolves `page.url` before asking, and reports the contract violation instead of spending a
     * request on a cache key.
     *
     * Asserted in both directions, because either half alone would pass while the app is wrong:
     *  - a source that **overrides** `getImageUrl` is still asked, whatever `url` holds, because the
     *    extension knows its own field's meaning;
     *  - a source that does **not** override it gets a named failure rather than a fetch.
     */
    @Test
    fun `a token cache key in url is reported as a contract violation not fetched`() = runBlocking {
        val tokenKey = "https://cmdxd98sb0x3yprd.mangadex.network" +
            ",https://api.mangadex.org/at-home/server/605c371d-904f-4dda-96a0-24ffdd65e642" +
            ",1790648354548"
        val fixture = Fixture(
            resolvingTo = tokenKey,
            pageListImageUrl = null,
            pageListUrl = tokenKey,
            sourceOverridesGetImageUrl = false,
        )

        val page = fixture.loadFirstPage()

        // The loader records the failure on the page rather than throwing, so the assertion is on the
        // state the reader would actually render: not Ready, and no image request issued.
        assertTrue(
            page.status is Page.State.Error,
            "expected a contract-violation error state, got ${page.status}",
        )
        assertEquals(0, fixture.imageRequestCount, "no request should be spent on a cache key")
    }

    /**
     * The counterweight: an extension that provides its own `getImageUrl` is always asked, even when
     * `url` holds something that is not an address.
     *
     * This is the case that keeps the guard from being a blunt "reject `url`" rule. MangaDex-shaped
     * sources resolve their own `url`; refusing to ask them would be refusing to ask the only party
     * that knows what the value means.
     */
    @Test
    fun `a source that overrides getImageUrl is still asked`() = runBlocking {
        val good = "https://uploads.mangadex.org/data/ab/cd/1.jpg"
        // `imageUrl` left null so the loader actually asks the source, which is the whole point:
        // a source that overrides `getImageUrl` is the only party that knows what `url` means.
        val fixture = Fixture(
            resolvingTo = good,
            pageListImageUrl = null,
            sourceOverridesGetImageUrl = true,
        )

        assertEquals(good, fixture.loadAndRecordRequestedUrl())
    }

    /** One loader, one page, and the recording cache they share. */
    private inner class Fixture(
        resolvingTo: String? = null,
        pageListImageUrl: String? = null,
        cachedPageImageUrl: String? = null,
        sourceOverridesGetImageUrl: Boolean = true,
        pageListUrl: String = "/page/1.jpg",
    ) {
        private val cache = RecordingChapterCache(tempDir, cachedPageImageUrl)
        private val source: HttpSource = if (sourceOverridesGetImageUrl) {
            TestSource(
                resolvedImageUrl = resolvingTo,
                pageListImageUrl = pageListImageUrl,
                pageListUrl = pageListUrl,
            )
        } else {
            PlainImageUrlSource(
                resolvedImageUrl = resolvingTo,
                pageListImageUrl = pageListImageUrl,
                pageListUrl = pageListUrl,
            )
        }
        private val chapter = ReaderChapter(Chapter.create().copy(id = 1, name = "Ch 1"))
        private val loader = HttpPageLoader(chapter, source, cache)

        /**
         * Loads the page and returns the address the loader asked for. The status is checked first:
         * a page that ended in `Error` never issued a request, so a bare URL assertion could pass
         * against a stale recording or report a null for a reason that has nothing to do with the
         * rule under test.
         */
        suspend fun loadAndRecordRequestedUrl(): String {
            val page = loadFirstPage()
            assertEquals(
                Page.State.Ready,
                page.status,
                "the page ended in ${page.status} rather than Ready, so the URL was never requested",
            )
            return requireNotNull(cache.requested) { "the image was never requested" }
        }

        /**
         * The page as the loader left it, for asserting what the boundary `HttpSource.imageRequest`
         * would build from it. The loader must have run first; the value the loader left in
         * `imageUrl` is the whole subject of the split these tests pin.
         */
        fun pageForTheResolvedAssertion(): Page =
            requireNotNull(lastPage) { "loadAndRecordRequestedUrl must run before the page is read" }

        /** Image requests issued by this fixture, for asserting that no request was spent on a bad value. */
        val imageRequestCount: Int get() = cache.imageRequestCount

        /** The page the last `loadFirstPage` settled, held for `pageForTheResolvedAssertion`. */
        private var lastPage: ReaderPage? = null

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
                    "the page never settled; last status was ${page.status}",
                )
                delay(POLL_INTERVAL_MS)
            }
            loader.recycle()
            lastPage = page
            return page
        }
    }

    private companion object {
        const val SETTLE_TIMEOUT_MS = 15_000L
        const val POLL_INTERVAL_MS = 10L

        /** The fixture source's `baseUrl`, so the pure contract checks resolve the same way. */
        const val BASE = "https://mangadex.org"
    }

    /**
     * Records the address the loader asks for and hands back a real file so the page can reach
     * `Ready`. The image lambda is never invoked, which is what keeps this test off the network: it
     * asserts the address, and the address is the whole defect.
     */
    private class RecordingChapterCache(
        tempDir: Path,
        private val cachedPageImageUrl: String?,
    ) : ChapterCache {
        override fun removeChapter(chapter: ephyra.domain.chapter.model.Chapter): Boolean = false

        private val image = tempDir.resolve("1.jpg").toFile().apply { writeBytes(byteArrayOf(1)) }

        var requested: String? = null
            private set

        /** How many image requests were issued, so a test can assert that none was spent on a bad value. */
        val imageRequestCount: Int get() = if (requested == null) 0 else 1

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
    private open class PlainImageUrlSource(
        /**
         * `protected`, not `private`: the base class never reads it, but the subclass's `getImageUrl`
         * does, and a plain constructor parameter would not be visible there.
         */
        @Suppress("unused") protected val resolvedImageUrl: String?,
        private val pageListImageUrl: String?,
        private val pageListUrl: String = "/page/1.jpg",
    ) : HttpSource() {
        override val baseUrl: String = "https://mangadex.org"
        override val name: String = "Test"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override fun headersBuilder(): Headers.Builder = Headers.Builder()

        override suspend fun getPageList(chapter: SChapter): List<Page> =
            listOf(Page(0, pageListUrl, pageListImageUrl))

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
     * The three ways to customise the image-URL chain, and each one alone must count.
     *
     * **Why all three are tested separately.** The gate in `HttpPageLoader` refuses to spend a request
     * on a `Page.url` that cannot address a host, but only for a source that customises *nothing*.
     * An earlier version of the capability probe checked `getImageUrl` alone, so a source that
     * customised the chain through `imageUrlRequest` or `imageUrlParse` — both of which are `open` on
     * `HttpSource` and both of which MangaDex uses — was mistaken for a plain one and blocked. The
     * symptom was our own error text appearing for a source that was doing everything correctly.
     *
     * Each case below customises exactly one entry point and must be recognised, because a source that
     * overrides only `imageUrlParse` still needs the app to fetch the page and hand it over.
     */
    @Test
    fun `a source that overrides only imageUrlParse is recognised`() {
        assertTrue(ParseOnlySource("https://cdn.example.com/1.jpg").capabilities.customisesImageUrlChain)
    }

    @Test
    fun `a source that overrides only imageUrlRequest is recognised`() {
        assertTrue(RequestOnlySource("https://cdn.example.com/1.jpg").capabilities.customisesImageUrlChain)
    }

    @Test
    fun `a source that overrides getImageUrl is recognised`() {
        assertTrue(ModernSource("https://cdn.example.com/1.jpg").capabilities.customisesImageUrlChain)
    }

    /**
     * The fourth entry point, and the one the reported failure turned on.
     *
     * `fetchImageUrl` replaces the entire chain — no request is built, no response is parsed — so a
     * source resolving its own addressing reaches for it first. MangaDex does, to read the at-home
     * cache key it keeps in `Page.url`.
     *
     * The probe omitted it, so MangaDex was reported as `overrides=none` and the reader refused pages
     * that were perfectly readable. A probe that answers "no" for a source that answers "yes" is
     * worse than no probe: it breaks a working source and does so while sounding confident.
     */
    @Test
    fun `a source that overrides only fetchImageUrl is recognised`() {
        assertTrue(FetchOnlySource("https://cdn.example.com/1.jpg").capabilities.customisesImageUrlChain)
    }

    /** The counterweight: a source that customises nothing is still reported as not customising. */
    @Test
    fun `a source that overrides nothing in the chain is not reported as customising`() {
        assertFalse(
            PlainImageUrlSource("https://cdn.example.com/1.jpg", null)
                .capabilities.customisesImageUrlChain,
        )
    }

    /** The probe and the diagnostic read the same list, so they cannot disagree. */
    @Test
    fun `the reported entry points include fetchImageUrl`() {
        val reported = FetchOnlySource("https://cdn.example.com/1.jpg")
            .capabilities.overriddenChainMethods()
        assertTrue(reported.contains("fetchImageUrl"), "reported entry points were: $reported")
    }

    /**
     * The counterweight: a source that customises nothing is not recognised, because the app's own
     * chain is what will run, and that is the case the gate exists for.
     */
    @Test
    fun `a source that customises nothing is not recognised`() {
        assertFalse(PlainImageUrlSource("https://cdn.example.com/1.jpg", null).capabilities.customisesImageUrlChain)
    }

    private class ModernSource(
        private val url: String,
    ) : PlainImageUrlSource(url, null) {
        override suspend fun getImageUrl(page: Page): String = url
    }

    private class ParseOnlySource(
        private val url: String,
    ) : PlainImageUrlSource(url, null) {
        override fun imageUrlParse(response: Response): String = url
    }

    private class RequestOnlySource(
        private val url: String,
    ) : PlainImageUrlSource(url, null) {
        override fun imageUrlRequest(page: Page): Request = GET(url, headers)
    }

    /**
     * A source that replaces the whole chain by overriding `fetchImageUrl`.
     *
     * **This is the shape that was missed, and the reason the reported MangaDex failure happened.**
     * The capability probe originally listed three entry points and omitted this one, so a source
     * resolving its own addressing this way was reported as `overrides=none`. The reader then refused
     * its pages as unreadable — the probe did not merely fail to help, it converted a working source
     * into a failure and attached a confident diagnostic saying no customisation existed.
     *
     * It is the most direct override available: no request is built and no response is parsed, so it
     * is what a source reaches for when it already knows the address from its own state. MangaDex
     * does exactly this to read the at-home cache key it keeps in `Page.url`.
     */
    private class FetchOnlySource(
        private val url: String,
    ) : PlainImageUrlSource(url, null) {
        @Suppress("DEPRECATION")
        override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(url)
    }

    /**
     * The same source, but declaring its own `getImageUrl` — the shape `HttpSource.capabilities.customisesImageUrlChain`
     * is meant to detect.
     *
     * **Why this is a subclass rather than a flag on one class.** The capability probe reads the
     * declaring class of the `getImageUrl` method at runtime, so a source that must *not* appear to
     * override it cannot be one that declares the method and then lies about it. Expressing the
     * distinction the way the compiler expresses it — override, or do not — is the only version of
     * this fixture that tests the real mechanism rather than a stand-in for it.
     */
    private class TestSource(
        resolvedImageUrl: String?,
        pageListImageUrl: String?,
        pageListUrl: String = "/page/1.jpg",
    ) : PlainImageUrlSource(resolvedImageUrl, pageListImageUrl, pageListUrl) {

        override suspend fun getImageUrl(page: Page): String =
            requireNotNull(resolvedImageUrl) { "this fixture was not given a URL to resolve" }
    }
}
