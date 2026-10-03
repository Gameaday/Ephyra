package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The MangaDex 1.6 image-address contract, pinned so the loader cannot break it again.
 *
 * **What a real MangaDex extension does** (verified against `keiyoushi/extensions-source`,
 * `MangaDex.kt` / `MangaDexHelper.kt`):
 *
 * ```kotlin
 * // getPageList:
 * Page(index, url = "$host,$atHomeRequestUrl,$now", imageUrl = "/data/$hash/$file")
 *
 * // its own imageRequest override:
 * GET(mdAtHomeServerUrl + page.imageUrl, headers)   // host comes from page.url
 * ```
 *
 * `Page.imageUrl` is a **relative path** and `Page.url` is an at-home cache key. The *source's*
 * overridden `imageRequest` is the only thing that knows how to join them. Upstream Mihon's reader
 * never writes into a populated `Page.imageUrl`; it reads it back and hands the page verbatim to
 * `source.getImage`.
 *
 * **The defect this file exists for.** The loader once resolved `page.imageUrl` against the
 * source's `baseUrl` and wrote the result back before fetching, so MangaDex's request became
 * `"<at-home-host>https://mangadex.org/data/..."` — two URLs spliced into a host that can never
 * resolve — and **every page of every chapter failed identically**, with the failure reading as a
 * DNS error about a host like `…mangadex.network,https`. `resolvePageImage` now passes a populated
 * `imageUrl` through opaquely (`ResolvedImageUrl.opaque`), and the loader no longer rewrites it.
 */
class MangaDexOpaqueImageUrlContractTest {

    /** The MangaDex shape: `imageUrl` relative, `url` an at-home cache key. */
    private fun mangadexPage() = Page(0, url = atHomeCacheKey, imageUrl = relativePageUrl)

    @Test
    fun `a populated relative imageUrl passes through resolvePageImage unchanged`() = runBlocking {
        val source = MangaDexShapedSource()
        val page = mangadexPage()

        val resolved = source.resolvePageImage(page, "reader").value

        assertEquals(
            relativePageUrl,
            resolved,
            "the host must not join a source's own imageUrl onto its baseUrl — only the " +
                "source's imageRequest knows which host the path belongs to",
        )
    }

    @Test
    fun `the loader write-back pattern yields a requestable MangaDex URL`() = runBlocking {
        val source = MangaDexShapedSource()
        val page = mangadexPage()

        // Exactly what HttpPageLoader/Downloader do: assign the owner's answer back, then let
        // the source build the request from the page.
        page.imageUrl = source.resolvePageImage(page, "reader").value
        val request = source.imageRequestFor(page)

        assertEquals(
            "$atHomeHost$relativePageUrl",
            request.url.toString(),
            "with the old baseUrl rewrite this was " +
                "\"$atHomeHost" + "https://mangadex.org$relativePageUrl\" — a host that can " +
                "never resolve, failing every page of every chapter",
        )
    }

    @Test
    fun `an absolute imageUrl also passes through byte-identical`() = runBlocking {
        val source = MangaDexShapedSource()
        val absolute = "https://uploads.mangadex.org/data/abc/1.png"
        val page = Page(0, url = atHomeCacheKey, imageUrl = absolute)

        assertEquals(absolute, source.resolvePageImage(page, "reader").value)
    }

    @Test
    fun `a page without an address still asks the source`() = runBlocking {
        val source = MangaDexShapedSource()
        val page = Page(0, url = atHomeHost, imageUrl = null)

        assertEquals(
            "$atHomeHost/data-saver/0200a52e/1.jpg",
            source.resolvePageImage(page, "reader").value,
        )
    }

    /** The cache-key/staleness gate still judges a relative address by resolving it first. */
    @Test
    fun `a MangaDex-shaped list is not judged stale for its relative addresses`() {
        val pages = listOf(Page(0, atHomeCacheKey, relativePageUrl))
        assertFalse(pages.needsFreshPageList("https://mangadex.org"))
        assertTrue(pages.none { it.imageUrl != relativePageUrl })
    }

    /**
     * A source shaped exactly like the real MangaDex extension: `imageUrl` is a relative path,
     * `url` is an at-home cache key, and `imageRequest` — which it overrides — joins them.
     *
     * Not `inner`: a nested `inner` class would hold an implicit reference to the test instance,
     * and the values it shares with the tests live in the [Companion] below, which a nested class
     * can read without any outer receiver.
     */
    private open class MangaDexShapedSource : HttpSource() {
        override val name: String = "MangaDex-shaped"
        override val lang: String = "en"
        override val baseUrl: String = "https://mangadex.org"
        override val supportsLatest: Boolean = true

        override fun headersBuilder(): Headers.Builder = Headers.Builder()

        fun imageRequestFor(page: Page): Request = imageRequest(page)

        override fun imageRequest(page: Page): Request {
            val (host, _, _) = page.url.split(",")
            return GET(host + page.imageUrl, headers)
        }

        override suspend fun getImageUrl(page: Page): String =
            "$atHomeHost/data-saver/0200a52e/1.jpg"

        override suspend fun getPageList(chapter: SChapter): List<Page> =
            listOf(Page(0, atHomeCacheKey, relativePageUrl))

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

    private companion object {
        val relativePageUrl = "/data/0200a52e6f6f4b6c8c6c8e6c6e6f4a2b/P1.png"
        val atHomeHost = "https://cmdxd98sb0x3yprd.mangadex.network"
        val atHomeCacheKey =
            "$atHomeHost,https://api.mangadex.org/at-home/server/733233d4-19fa-4cd9-9e8d-8dbcdfaa5bf4,1791046242118"
    }
}
