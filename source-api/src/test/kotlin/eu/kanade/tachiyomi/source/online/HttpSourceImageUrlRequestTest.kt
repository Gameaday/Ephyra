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
            TestSource("https://mangadex.org").imageRequestFor(Page(0, "", spliced))
        }
    }

    /** The same rule on the deprecated builder, which is the chain a legacy source actually reaches. */
    @Test
    fun `a spliced page URL is refused before a request can be built`() {
        assertThrows(MalformedImageUrlException::class.java) {
            TestSource("https://mangadex.org").imageUrlRequestFor(
                Page(0, "https://cmdxd98sb0x3yprd.mangadex.network,https/data/1.jpg"),
            )
        }
    }

    /**
     * An API source that hands back `Page(index, imageUrl = absolute)` and leaves `url` at its `""`
     * default — the shape `SRC-012` describes, and the only common source whose image host is a
     * fresh per-request address. Resolving `url` alone yields `""`, which no request can carry.
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
     * request is built, which is correct but would make the assertion about OkHttp's normalisation
     * rather than about this pipeline passing the string through untouched.
     */
    @Test
    fun `an at-home address keeps its host, port and path`() {
        val atHome = "https://cmdxd98sb0x3yprd.mangadex.network:8443/data/hash/1.jpg"

        val request = TestSource("https://mangadex.org").imageRequestFor(Page(0, "", atHome))

        assertEquals(atHome, request.url.toString())
    }

    /**
     * The deprecated builder on a page that only populates `imageUrl` fails, as it does in Mihon.
     *
     * This previously asserted the opposite — that `imageUrl` "wins" — and that assertion is what
     * allowed the two builders to be collapsed onto one rule. The comment claimed `Page.imageUrl` is
     * "documented as the resolved address", but `imageUrlRequest` is not what reads it:
     * `imageRequest` is. Under the reference contract a source that reaches this path with an empty
     * `url` gets `GET("")`, and the app's job is to say so clearly rather than quietly substitute a
     * different field.
     */
    @Test
    fun `the deprecated builder rejects an empty page url instead of substituting imageUrl`() {
        val atHome = "https://cmdxd98sb0x3yprd.mangadex.network/data/hash/1.jpg"

        assertThrows(MalformedImageUrlException::class.java) {
            TestSource("https://mangadex.org").imageUrlRequestFor(Page(0, "", atHome))
        }
    }

    /**
     * The exact string the owner's device produced, verbatim from logcat.
     *
     * It is a three-part comma-join, not a URL: the at-home server, the API URL that obtained it,
     * and a timestamp (`1790648354548` = 2026-09-29T02:19:14Z). No URL is ever shaped like that, so
     * whatever produced it was building a cache key of its own and returning it where an address
     * belongs. Reproducing it exactly means this test fails on the real defect rather than on a
     * convenient paraphrase of it.
     *
     * Asserted through [imageRequestFor], because `imageUrl` is the field this value actually lands
     * in, and the field that builder reads.
     */
    @Test
    fun `the reported three-part composite is refused before a request exists`() {
        val composite = "https://cmdxd98sb0x3yprd.mangadex.network" +
            ",https://api.mangadex.org/at-home/server/605c371d-904f-4dda-96a0-24ffdd65e642" +
            ",1790648354548"
        val source = TestSource("https://mangadex.org")

        val thrown = assertThrows(MalformedImageUrlException::class.java) {
            source.imageRequestFor(Page(0, url = "", imageUrl = composite))
        }

        assertEquals(composite, thrown.url)
        // The reason moved from "the host ... is not a hostname" to the scheme-separator count once
        // that check existed. The new wording is the better one: it names the defect (two URLs were
        // joined) rather than a symptom of it (a comma in a hostname), which is why this is a
        // deliberate update rather than a test bent to fit the code.
        assertEquals(true, thrown.reason.contains("two URLs have been joined"))
    }

    /**
     * A populated `imageUrl` must not be able to hide a usable `url` behind it.
     *
     * **Why this is not speculative.** Preferring `imageUrl` is correct for the API sources that
     * leave `url` empty — but "preferred" had been implemented as "trusted", so a source putting
     * something that is not an address into `imageUrl` suppressed a perfectly good `url` and failed
     * without ever attempting the one value that could have worked. Which field the private
     * extension populated is not observable from here, so resolution tries the other one rather
     * than assuming.
     */
    @Test
    fun `a good url is still used when imageUrl holds something unusable`() {
        val source = TestSource("https://mangadex.org")
        val page = Page(
            0,
            url = "https://img-r2.2xstorage.com/data/1.jpg",
            imageUrl = "https://cmdxd98sb0x3yprd.mangadex.network,https://api.mangadex.org/x,1790648354548",
        )

        val request = source.imageUrlRequestFor(page)

        assertEquals("https://img-r2.2xstorage.com/data/1.jpg", request.url.toString())
    }

    /**
     * The two builders read **different fields**, and that is the extension contract rather than an
     * oversight.
     *
     * Mihon's `HttpSource`:
     * ```
     * imageUrlRequest -> GET(page.url)
     * imageRequest    -> GET(page.imageUrl!!)
     * ```
     *
     * An earlier version of this file asserted the opposite — that `imageUrl` won in both — on the
     * reasoning that "preferred beats wrong". That produced a real divergence: `imageUrlRequest`
     * drives the deprecated chain, where the request below it is a **live fetch of `page.url`** whose
     * response is handed to `imageUrlParse`. Reading `imageUrl` there fetches a different URL, so the
     * source parses a different body and returns a different value than it would in any other host.
     *
     * These two assertions exist so the builders cannot quietly collapse into one again. The
     * consolidation that merged them was well-intentioned, changed no test that existed at the time,
     * and was wrong.
     */
    @Test
    fun `imageUrlRequest reads the url field and never substitutes imageUrl`() {
        val source = TestSource("https://mangadex.org")
        val page = Page(
            0,
            url = "https://cdn.example.com/data/from-url-field.jpg",
            imageUrl = "https://cdn.example.com/data/from-image-url-field.jpg",
        )

        assertEquals(
            "https://cdn.example.com/data/from-url-field.jpg",
            source.imageUrlRequestFor(page).url.toString(),
        )
    }

    @Test
    fun `imageRequest reads the imageUrl field and never substitutes url`() {
        val source = TestSource("https://mangadex.org")
        val page = Page(
            0,
            url = "https://cdn.example.com/data/from-url-field.jpg",
            imageUrl = "https://cdn.example.com/data/from-image-url-field.jpg",
        )

        assertEquals(
            "https://cdn.example.com/data/from-image-url-field.jpg",
            source.imageRequestFor(page).url.toString(),
        )
    }

    /**
     * A blank field fails on its own terms, as it does in Mihon, rather than borrowing the other.
     *
     * The fallback this replaces was well-meant — a source putting a non-address in one field should
     * not hide a good value in the other — but it applies a rule the extension author never agreed to,
     * and it is invisible in exactly the deprecated path that fetches and parses a response.
     */
    @Test
    fun `imageUrlRequest fails on a blank url rather than borrowing imageUrl`() {
        val source = TestSource("https://mangadex.org")
        val page = Page(0, url = "", imageUrl = "https://cdn.example.com/data/1.jpg")

        assertThrows(MalformedImageUrlException::class.java) { source.imageUrlRequestFor(page) }
    }

    @Test
    fun `imageRequest fails on a blank imageUrl rather than borrowing url`() {
        val source = TestSource("https://mangadex.org")
        val page = Page(0, url = "https://cdn.example.com/data/1.jpg", imageUrl = null)

        assertThrows(MalformedImageUrlException::class.java) { source.imageRequestFor(page) }
    }

    // Superseded: a usable `url` no longer rescues a page whose `imageUrl` is unusable. The cross-field
    // fallback was a divergence from Mihon — `imageUrlRequest` reads `page.url` only and
    // `imageRequest` reads `page.imageUrl` only — and it applied a rule the extension author never
    // agreed to. It is kept in this comment rather than deleted because the reasoning behind it
    // ("preferred must not mean trusted") sounds right, and recording where it was wrong is more
    // useful than pretending the question never came up. The replacement tests assert that each
    // builder fails on its own field instead.

    /**
     * The reported MangaDex page, characterised.
     *
     * **What this settles.** The device reports a page whose `imageUrl` is a three-part composite:
     * `(at-home server, at-home API URL, fetch timestamp)`. Full-clear, uninstall, reinstall and a new
     * series all reproduce it, so it is not persisted state — it is generated on each load.
     *
     * The composite is a cache-key shape. Upstream MangaDex builds an image address from four DTO
     * fields (`baseUrl`, `chapter.hash`, `data`/`dataSaver`, filename) and contains no cache, no
     * timestamp and no comma-join anywhere in its page path, so it cannot be the producer. Our
     * `ImagePipelineCannotInventAUrlTest` proves `resolve()` cannot build it either, which leaves the
     * extension running against *our* `source-api` as the only place the two facts can both hold.
     *
     * These tests pin the contract that makes that hunt possible: each builder reads exactly the field
     * the reference implementation reads, so a divergence cannot hide behind a fallback.
     */
    @Test
    fun `the reported page fails because url offers nothing usable either`() {
        val source = TestSource("https://mangadex.org")
        val composite = "https://cmdxd98sb0x3yprd.mangadex.network," +
            "https://api.mangadex.org/at-home/server/267e6e0f-c03f-4c15-9608-9a94aebc4ffd," +
            "1790973083449"
        val page = Page(0, url = "", imageUrl = composite)

        val thrown = assertThrows(MalformedImageUrlException::class.java) {
            source.imageRequestFor(page)
        }

        assertEquals(composite, thrown.url)
        // If this ever becomes false, `url` is being populated too — which would mean the source does
        // offer a second address and the builder reading `imageUrl` alone is no longer the whole story.
        assertEquals("", page.url)
    }

    // Superseded: "the two request builders must agree on which field to use". They must not. That test
    // existed because `imageUrlRequest` had learned to try `imageUrl` then `url` while `imageRequest`
    // still read `page.imageUrl!!`, and asserting agreement was the wrong remedy — it encoded the
    // divergence instead of correcting it. The reference implementation reads `page.url` in one and
    // `page.imageUrl` in the other, and the tests below now assert exactly that.

    /**
     * The reference implementation's field split, stated as two independent rules.
     *
     * **What this replaced.** A previous test asserted that both builders return the *same* value for
     * any page, on the reasoning that two copies of one rule must not drift. That reasoning was the
     * defect: Mihon's two builders deliberately read different fields, and asserting they agreed
     * encoded the divergence rather than catching it.
     *
     * Each case below populates the **other** field with a poison value. That is what makes these
     * assertions load-bearing rather than decorative: if either builder ever consults the field it
     * should not, it builds a request for `MUST-NOT-BE-USED` and fails. A future consolidation that
     * merges the two builders again fails here instead of shipping.
     *
     * Relative values still resolve, which is ours rather than Mihon's and is what makes
     * `img.attr("src")` requestable (`DEF-027`).
     */
    @ParameterizedTest
    @CsvSource(
        "https://cdn.example.com/u.jpg, https://cdn.example.com/u.jpg",
        "/relative.jpg, https://mangadex.org/relative.jpg",
        "//cdn.example.com/u.jpg, https://cdn.example.com/u.jpg",
        "u.jpg, https://mangadex.org/u.jpg",
    )
    fun `imageUrlRequest reads page url and ignores imageUrl`(url: String, expected: String) {
        val source = TestSource("https://mangadex.org")
        val page = Page(
            0,
            url = url,
            imageUrl = "https://cdn.example.com/MUST-NOT-BE-USED.jpg",
        )

        assertEquals(expected, source.imageUrlRequestFor(page).url.toString())
    }

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
     * A source populating only `url` gets a **classified failure**, not a bare NPE.
     *
     * **What is kept and what changed.** This previously asserted that `imageRequest` falls back to
     * `url`; the fallback is gone, because Mihon's does not have it and a source reaching this path with
     * a null `imageUrl` is a contract violation the app should name rather than paper over.
     *
     * What is deliberately retained is the *shape* of the failure: `MalformedImageUrlException` rather
     * than `NullPointerException`. Mihon throws NPE here. Ours names the field, attributes a layer, and
     * is classified as "ask the source again" by `TransientErrors`, so the failure can recover. That is
     * a genuine improvement over the reference rather than a divergence from it.
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
