package eu.kanade.tachiyomi.source.online

import ephyra.core.common.util.network.MalformedImageUrlException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Pins that the default `HttpSource` image request builder can always build a request.
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
 * The string that reached it was [Page.imageUrl] verbatim — a source naming its page relatively
 * (`img.attr("src")` rather than `absUrl("src")`, or the protocol-relative `//cdn…` a `<base>`-tagged
 * site emits) therefore could not be read at all.
 *
 * **Why this is a behavioural test and not a structural one.** The defect was not a missing call in
 * an unreadable branch; it was a value arriving at a constructor that rejects it. Building the
 * [Request] here reproduces the throw exactly, with no network and no Android, so the assertion can
 * be about the thing that was actually wrong: the address the request would have carried.
 *
 * **One builder now, not two.** `imageRequest` is the only image request builder left — the
 * deprecated `imageUrlRequest` that read `Page.url` was removed with the 1.4/1.5 ABI — so this file
 * asserts the one field the contract gives an image address, [Page.imageUrl], and that `Page.url` is
 * never consulted as a substitute for it.
 */
@Suppress("DEPRECATION") // Calls the deprecated request/parse helpers on purpose; they are what ships.
class HttpSourceImageUrlRequestTest {

    /**
     * A relative image URL, which is what the reported source produced. Asserting on the built
     * request rather than on a return value means this test fails with the *same* exception the user
     * saw, so a regression reports itself in the shape of the original defect.
     */
    @ParameterizedTest
    @CsvSource(
        "//cdn.example.com/data/1.jpg, https://mangadex.org, https://cdn.example.com/data/1.jpg",
        "/data/1.jpg, https://mangadex.org, https://mangadex.org/data/1.jpg",
        "data/1.jpg, https://mangadex.org, https://mangadex.org/data/1.jpg",
    )
    fun `a relative image URL still yields a request that can be built`(
        imageUrl: String,
        baseUrl: String,
        expected: String,
    ) {
        val request = TestSource(baseUrl).imageRequestFor(Page(0, imageUrl = imageUrl))

        assertEquals(expected, request.url.toString())
    }

    @Test
    fun `a protocol-relative image URL inherits the base scheme rather than assuming https`() {
        // Assuming https would silently change the host behaviour of a source served over http.
        val request = TestSource("http://mangadex.org")
            .imageRequestFor(Page(0, imageUrl = "//cdn.example.com/1.jpg"))

        assertEquals("http", request.url.scheme)
    }

    @Test
    fun `an absolute image URL is requested exactly as the source spelled it`() {
        // The signed-URL case `DEF-020` exists for. Resolution must not canonicalise on the way past.
        val signed = "https://cdn.example.com/1.jpg?token=aB3%2Fxyz&expires=1893456000"
        val request = TestSource("https://mangadex.org").imageRequestFor(Page(0, imageUrl = signed))

        assertEquals(signed, request.url.toString())
    }

    /**
     * The address the owner actually saw:
     *
     * ```
     * Unable to resolve host "cmdxd98sb0x3yprd.mangadex.network,https"
     * ```
     *
     * A host and a scheme joined by a comma. Nothing in this codebase concatenates two addresses
     * with `","`, so the string is produced by whatever builds the page list — but the *pipeline*
     * is what decides whether it is ever requested, and until now only the reader checked. The
     * downloader reaches `getImage` without consulting that check, so a spliced address was built
     * into a real [Request], canonicalised by OkHttp into a syntactically valid host, and handed to
     * DNS, which reported a name-resolution failure about a host that could never exist.
     *
     * Asserted at the constructor because that is the seam: the point is that the request is never
     * built, not that some later stage notices.
     */
    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://cmdxd98sb0x3yprd.mangadex.network,https/data/1.jpg",
            "https://mangadex.org,https/data/1.jpg",
        ],
    )
    fun `a spliced image URL is refused before a request can be built`(spliced: String) {
        assertThrows(MalformedImageUrlException::class.java) {
            TestSource("https://mangadex.org").imageRequestFor(Page(0, url = "", imageUrl = spliced))
        }
    }

    /**
     * An API source that hands back `Page(index, imageUrl = absolute)` and leaves `url` at its `""`
     * default — the shape `SRC-012` describes, and the only common source whose image host is a
     * fresh per-request address. Reading `url` would yield `""`, which no request can carry.
     */
    @Test
    fun `an API-shaped page is requested by its image URL when its url is empty`() {
        val atHome = "https://cmdxd98sb0x3yprd.mangadex.network/data/hash/1.jpg"

        val request = TestSource("https://mangadex.org").imageRequestFor(Page(0, "", atHome))

        assertEquals(atHome, request.url.toString())
    }

    /**
     * The at-home address shape `SRC-012` describes: host, then an explicit port, then the quality,
     * hash and filename segments. MangaDex is the one common source whose image host is a fresh
     * per-request address rather than a stable CDN, so a resolution rule that mangles a host, drops a
     * path or re-roots it against `baseUrl` shows up here and nowhere else.
     *
     * The port is deliberately non-default: on `https` OkHttp canonicalises `:443` away when the
     * request is built, so a default port would not prove the value survived.
     */
    @Test
    fun `an at-home image address keeps its host, port and path`() {
        val atHome = "https://cmdxd98sb0x3yprd.mangadex.network:8443/data-saver/0200a52e/1.jpg"

        val request = TestSource("https://mangadex.org").imageRequestFor(Page(0, "", atHome))

        assertEquals(atHome, request.url.toString())
    }

    /**
     * The reported page, characterised: an `imageUrl` that is a three-part composite of
     * `(at-home server, at-home API URL, fetch timestamp)` while `url` is empty.
     *
     * The composite is a cache-key shape, not an address, so it must be refused with the offending
     * value attached — and `url` must still be empty, because if it ever became populated the
     * "`imageUrl` is the only address field" premise would no longer be the whole story.
     */
    @Test
    fun `the reported composite imageUrl fails and names the value`() {
        val source = TestSource("https://mangadex.org")
        val composite = "https://cmdxd98sb0x3yprd.mangadex.network," +
            "https://api.mangadex.org/at-home/server/267e6e0f-c03f-4c15-9608-9a94aebc4ffd," +
            "1790973083449"
        val page = Page(0, url = "", imageUrl = composite)

        val thrown = assertThrows(MalformedImageUrlException::class.java) {
            source.imageRequestFor(page)
        }

        assertEquals(composite, thrown.url)
        assertEquals("", page.url)
    }

    /**
     * A blank `imageUrl` fails on its own terms, as it does in Mihon, rather than borrowing `url`.
     *
     * The fallback this replaces was well-meant — a source putting a non-address in one field should
     * not hide a good value in the other — but it applies a rule the extension author never agreed
     * to. `imageUrl` is the contract's image-address field; `url` is not a substitute.
     */
    @Test
    fun `imageRequest fails on a blank imageUrl rather than borrowing url`() {
        val source = TestSource("https://mangadex.org")
        val page = Page(0, url = "https://cdn.example.com/data/1.jpg", imageUrl = null)

        assertThrows(MalformedImageUrlException::class.java) { source.imageRequestFor(page) }
    }

    /**
     * `imageRequest` reads [Page.imageUrl] and never [Page.url].
     *
     * The other field is populated with a poison value, so if the builder ever consults it the
     * request carries `MUST-NOT-BE-USED` and this fails. A future change that reintroduces a
     * cross-field fallback fails here instead of shipping.
     *
     * Relative values still resolve, which is ours rather than Mihon's and is what makes
     * `img.attr("src")` requestable (`DEF-027`).
     */
    @ParameterizedTest
    @CsvSource(
        "https://cdn.example.com/i.jpg, https://cdn.example.com/i.jpg",
        "/relative.jpg, https://mangadex.org/relative.jpg",
        "//cdn.example.com/i.jpg, https://cdn.example.com/i.jpg",
        "i.jpg, https://mangadex.org/i.jpg",
    )
    fun `imageRequest reads page imageUrl and ignores url`(imageUrl: String, expected: String) {
        val source = TestSource("https://mangadex.org")
        val page = Page(
            0,
            url = "https://cdn.example.com/MUST-NOT-BE-USED.jpg",
            imageUrl = imageUrl,
        )

        assertEquals(expected, source.imageRequestFor(page).url.toString())
    }

    /**
     * A null `imageUrl` is a contract violation and is named as one, rather than surfacing as a bare
     * `NullPointerException`. Mihon throws NPE here. Ours names the field, attributes a layer, and
     * is classified as "ask the source again" by `TransientErrors`, so the failure can recover.
     */
    @Test
    fun `imageRequest on a null imageUrl fails as a named contract violation not an NPE`() {
        val source = TestSource("https://mangadex.org")
        val page = Page(0, url = "https://img-r2.2xstorage.com/1.jpg", imageUrl = null)

        val thrown = assertThrows(MalformedImageUrlException::class.java) {
            source.imageRequestFor(page)
        }

        assertEquals(true, thrown.reason.isNotBlank())
    }

    /**
     * `headersBuilder` is overridden rather than left alone because the base implementation reads
     * `network.defaultUserAgentProvider()`, and `network` comes from the Injekt service locator.
     * A test that had to stand up a service locator to assert a string join would be a test that
     * could fail for reasons unrelated to the rule.
     */
    private open class TestSource(override val baseUrl: String) : HttpSource() {
        override val name: String = "Test"
        override val lang: String = "en"
        override val supportsLatest: Boolean = false

        override fun headersBuilder(): Headers.Builder = Headers.Builder()

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
    }
}
