package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Pins that the two default `HttpSource` request builders can always build a request.
 *
 * **The failure this exists for (`DEF-027`).** The owner saw, on a manga page in the reader:
 *
 * ```
 * Expected URL scheme 'http' or 'https' but no scheme was found for …
 * ```
 *
 * OkHttp throws that from `toHttpUrl()` while a [Request] is being *constructed*. It is not a
 * network outcome, which is what makes the report's own facts consistent rather than contradictory:
 * the page was present at the source site, and nothing was rate limited, because nothing was sent.
 *
 * The string that reached it was [Page.url] verbatim. `HttpPageLoader` copies it from the source's
 * page list without touching it, and the default `imageUrlRequest` passed it to `GET(...)` as-is.
 * A source naming its page relatively — `img.attr("src")` rather than `absUrl("src")`, or the
 * protocol-relative `//cdn…` a `<base>`-tagged site emits — therefore could not be read at all.
 *
 * **Why this is a behavioural test and not a structural one.** The defect was not a missing call in
 * an unreadable branch; it was a value arriving at a constructor that rejects it. Building the
 * [Request] here reproduces the throw exactly, with no network and no Android, so the assertion can
 * be about the thing that was actually wrong: the address the request would have carried.
 */
@Suppress("DEPRECATION") // Calls the deprecated request builders on purpose; they are what ships.
class HttpSourceImageUrlRequestTest {

    /**
     * A relative page URL, which is what the reported source produced. Asserting on the built
     * request rather than on a return value means this test fails with the *same* exception the user
     * saw, so a regression reports itself in the shape of the original defect.
     */
    @ParameterizedTest
    @CsvSource(
        "//cdn.example.com/data/1.jpg, https://mangadex.org, https://cdn.example.com/data/1.jpg",
        "/data/1.jpg, https://mangadex.org, https://mangadex.org/data/1.jpg",
        "data/1.jpg, https://mangadex.org, https://mangadex.org/data/1.jpg",
    )
    fun `a relative page URL still yields a request that can be built`(
        pageUrl: String,
        baseUrl: String,
        expected: String,
    ) {
        val request = TestSource(baseUrl).imageUrlRequestFor(Page(0, pageUrl))

        assertEquals(expected, request.url.toString())
    }

    @Test
    fun `a protocol-relative page URL inherits the base scheme rather than assuming https`() {
        // Assuming https would silently change the host behaviour of a source served over http.
        val request = TestSource("http://mangadex.org").imageUrlRequestFor(Page(0, "//cdn.example.com/1.jpg"))

        assertEquals("http", request.url.scheme)
    }

    @Test
    fun `an absolute page URL is requested exactly as the source spelled it`() {
        // The signed-URL case `DEF-020` exists for. Resolution must not canonicalise on the way past.
        val signed = "https://cdn.example.com/1.jpg?token=aB3%2Fxyz&expires=1893456000"
        val request = TestSource("https://mangadex.org").imageUrlRequestFor(Page(0, signed))

        assertEquals(signed, request.url.toString())
    }

    /**
     * The same guarantee for the image request, which is the boundary the reader and the downloader
     * both reach through `getImage`. `imageUrl` is set directly by some sources' `pageListParse`
     * from a raw `src` attribute, so it can be just as relative as `Page.url`.
     */
    @Test
    fun `a relative image URL still yields a request that can be built`() {
        val request = TestSource("https://mangadex.org")
            .imageRequestFor(Page(0, "/page/1.jpg", "//cdn.example.com/1.jpg"))

        assertEquals("https://cdn.example.com/1.jpg", request.url.toString())
    }

    /**
     * `headersBuilder` is overridden rather than left alone because the base implementation reads
     * `network.defaultUserAgentProvider()`, and `network` comes from the Injekt service locator.
     * A test that had to stand up a service locator to assert a string join would be a test that
     * could fail for reasons unrelated to the rule.
     */
    private class TestSource(override val baseUrl: String) : HttpSource() {
        override val name: String = "Test"
        override val lang: String = "en"
        override val supportsLatest: Boolean = false

        override fun headersBuilder(): Headers.Builder = Headers.Builder()

        fun imageUrlRequestFor(page: Page): Request = imageUrlRequest(page)

        fun imageRequestFor(page: Page): Request = imageRequest(page)

        override suspend fun getPopularManga(page: Int): MangasPage = throw UnsupportedOperationException()

        override suspend fun getSearchManga(
            page: Int,
            query: String,
            filters: FilterList,
        ): MangasPage = throw UnsupportedOperationException()

        override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

        override suspend fun getMangaDetails(manga: SManga): SManga = throw UnsupportedOperationException()

        override suspend fun getChapterList(manga: SManga): List<SChapter> = throw UnsupportedOperationException()

        override suspend fun getPageList(chapter: SChapter): List<Page> = throw UnsupportedOperationException()

        override suspend fun getImageUrl(page: Page): String = throw UnsupportedOperationException()

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
