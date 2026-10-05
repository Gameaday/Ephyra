package ephyra.core.common.util.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Pins `ImageUrlPolicy.resolve` — turning a possibly-relative URL into an absolute one.
 *
 * **The failure this exists for (`DEF-027`).** The owner reported, viewing a manga page:
 *
 * ```
 * Expected URL scheme 'http' or 'https' but no scheme was found for …
 * ```
 *
 * That is OkHttp's own wording, thrown by `toHttpUrl()` while a request is being *built*. It is
 * worth being precise about what that rules out, because several plausible readings of the report
 * are excluded by it: nothing was sent, so the page was not rate limited, the host was never
 * contacted, and the page being present at the source site is not in tension with it. The address
 * simply could not be turned into a request.
 *
 * **Why the address was incomplete.** A source is free to name a page relatively. `img.attr("src")`
 * instead of `absUrl("src")` is ordinary source code, and a protocol-relative `//cdn…` is what a
 * `<base>`-tagged site emits. Upstream resolved both against `baseUrl`; this fork's
 * `HttpSource.getImageUrl` is the deprecated *network* variant, so that resolution existed nowhere
 * and the raw string reached OkHttp.
 *
 * **Why most of what follows asserts that nothing changes.** The tempting version of this fix is a
 * normalising helper that rebuilds every URL through `HttpUrl`. That would be a regression dressed
 * as a cleanup: a signed CDN URL is a query string whose exact spelling *is* the credential
 * (`DEF-020`), and re-encoding it can invalidate it. So the majority of cases here assert
 * byte-identical passthrough, and the resolution cases assert an exact expected string rather than
 * merely "it parses now" — a weaker assertion would pass for a resolver that mangled every URL into
 * something valid.
 */
class ImageUrlPolicyResolveTest {

    @Test
    fun `the reported protocol-relative URL takes the base scheme`() {
        // The reported shape. OkHttp cannot build a request from this at all, and the reader showed
        // its message to the user verbatim.
        assertEquals(
            "https://cdn.example.com/1.jpg",
            ImageUrlPolicy.resolve("//cdn.example.com/1.jpg", "https://mangadex.org"),
        )
    }

    @Test
    fun `a protocol-relative URL keeps an http base on http`() {
        // The base's scheme is the only thing that decides this, so a source served over http must
        // not have its images upgraded to https. That is a different host behaviour and it can break
        // a mixed-content page.
        assertEquals(
            "http://cdn.example.com/1.jpg",
            ImageUrlPolicy.resolve("//cdn.example.com/1.jpg", "http://mangadex.org"),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["/data/1.jpg", "data/1.jpg"])
    fun `a relative path is completed against the base`(path: String) {
        assertEquals(
            "https://mangadex.org/data/1.jpg",
            ImageUrlPolicy.resolve(path, "https://mangadex.org"),
        )
    }

    /**
     * The path is joined, not canonicalised.
     *
     * `./data/1.jpg` and `data/../1.jpg` come out spelled exactly as the source spelled them. That
     * is deliberate: normalising here would mean parsing and re-encoding every URL, which is the
     * change that would break a signed CDN URL, and OkHttp canonicalises the path when it parses the
     * address anyway. The property that matters is that the result is a buildable request, not that
     * it is a tidy string.
     */
    @ParameterizedTest
    @ValueSource(strings = ["./data/1.jpg", "data/../1.jpg"])
    fun `a dotted relative path is joined verbatim rather than canonicalised`(path: String) {
        val resolved = ImageUrlPolicy.resolve(path, "https://mangadex.org")

        assertEquals("https://mangadex.org/$path", resolved)
        assertNull(
            ImageUrlPolicy.defectOf(resolved),
            "however it is spelled, the joined address must still be one a request can be built from",
        )
    }

    /**
     * The separator is collapsed rather than concatenated, so neither a doubled nor a missing slash
     * can be produced by however the two sides happen to be spelled.
     */
    @ParameterizedTest
    @CsvSource(
        "https://mangadex.org, /1.jpg",
        "https://mangadex.org/, /1.jpg",
        "https://mangadex.org, 1.jpg",
        "https://mangadex.org/, 1.jpg",
    )
    fun `exactly one separator survives whatever the two sides are spelled`(base: String, path: String) {
        assertEquals("https://mangadex.org/1.jpg", ImageUrlPolicy.resolve(path, base))
    }

    /**
     * A base with a path is a real shape — a source rooted at a subdirectory — and it is the case
     * that decides root-relative against RFC 3986 resolution. Here `/1.jpg` resolves to
     * `https://example.com/manga/1.jpg`, **not** to `https://example.com/1.jpg`.
     *
     * That is the codebase's own convention, not a local preference: `HttpSource.getUrlWithoutDomain`
     * stores a manga or chapter URL as `uri.path`, so a stored URL is a leading-`/` path, and
     * `pageListRequest` rebuilds it as `baseUrl + chapter.url`. A leading slash therefore already
     * means "relative to `baseUrl`" everywhere else in the source layer. Resolving it against the
     * origin instead would make page resolution disagree with chapter resolution for every source
     * whose `baseUrl` carries a path, which is precisely the set of sources for which the two
     * answers differ.
     */
    @Test
    fun `a leading slash is relative to the base, matching how chapter URLs are already stored`() {
        assertEquals(
            "https://example.com/manga/1.jpg",
            ImageUrlPolicy.resolve("/1.jpg", "https://example.com/manga/"),
        )
        assertEquals(
            "https://example.com/manga/1.jpg",
            ImageUrlPolicy.resolve("1.jpg", "https://example.com/manga/"),
        )
    }

    /**
     * The counterweight that matters most: resolution must be a no-op on an address that already
     * works, because the alternative is a fix that breaks the signed-URL case while repairing the
     * relative one.
     */
    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://uploads.mangadex.org/data/ab/cd/1.jpg",
            "http://plain.example.com/1.jpg",
            "https://cdn.example.com:8443/data/1.jpg",
            "https://user:pass@cdn.example.com/1.jpg",
            "https://xn--wgv71a.example.co.jp/1.jpg",
            "HTTPS://CDN.EXAMPLE.COM/1.jpg",
            "https://example.com/a%20b/1.jpg",
        ],
    )
    fun `an absolute URL is returned byte-identical`(url: String) {
        assertEquals(
            url,
            ImageUrlPolicy.resolve(url, "https://mangadex.org"),
            "a URL that already addresses a host must not be rewritten",
        )
    }

    @Test
    fun `a signed CDN URL survives resolution exactly`() {
        // `DEF-020` exists because these expire and are re-issued. Canonicalising the encoding or
        // reordering the query would invalidate a credential that was valid a moment ago, so this
        // asserts the whole string rather than a normalised equivalent of it.
        val signed = "https://cdn.example.com/data/1.jpg?token=aB3%2Fxyz&expires=1893456000&sig=h%3D"
        assertEquals(signed, ImageUrlPolicy.resolve(signed, "https://mangadex.org"))
    }

    /**
     * A non-http scheme is a decision by the source, not a missing prefix, and prefixing a host onto
     * it would manufacture a request nobody asked for. `defectOf` is what rejects these, so `resolve`
     * must leave them recognisable enough for it to.
     */
    @ParameterizedTest
    @ValueSource(
        strings = [
            "data:image/png;base64,iVBORw0KGgo=",
            "file:///data/1.jpg",
            "javascript:alert(1)",
        ],
    )
    fun `a non-http scheme is left exactly as the source produced it`(url: String) {
        assertEquals(url, ImageUrlPolicy.resolve(url, "https://mangadex.org"))
        assertNotNull(
            ImageUrlPolicy.defectOf(ImageUrlPolicy.resolve(url, "https://mangadex.org")),
            "a scheme that cannot address an image host must still be rejected downstream",
        )
    }

    /**
     * A colon inside a path is not a scheme delimiter: RFC 3986 requires the colon to follow the
     * scheme immediately, with nothing that could be a path segment before it. Reading
     * `chapter/1:2.jpg` as a scheme would prefix a base onto a perfectly good relative path.
     */
    @Test
    fun `a colon inside a path segment is not mistaken for a scheme`() {
        assertEquals(
            "https://mangadex.org/chapter/1:2.jpg",
            ImageUrlPolicy.resolve("chapter/1:2.jpg", "https://mangadex.org"),
        )
    }

    /**
     * With no base able to *lend* a scheme there is no absolute address to form, so the input is
     * returned unchanged and the caller's own verdict reports the original string. A resolver that
     * concatenated anyway would report `mangadex.org/data/1.jpg` — a different string that still
     * cannot address a host, so the diagnosis would be no more actionable than the fault.
     */
    @ParameterizedTest
    @CsvSource(
        "'', https://mangadex.org",
        "'   ', https://mangadex.org",
        "//cdn.example.com/1.jpg, ''",
        "//cdn.example.com/1.jpg, '   '",
        "/data/1.jpg, null",
        "/data/1.jpg, mangadex.org",
    )
    fun `with nothing to resolve against the input is returned unchanged`(url: String?, base: String?) {
        assertEquals(url.orEmpty(), ImageUrlPolicy.resolve(url, base))
    }

    @Test
    fun `null is returned as empty rather than as the string null`() {
        // `Page.imageUrl` is nullable throughout the loader, so null has to be a value this function
        // answers for and not an NPE from inside it.
        assertEquals("", ImageUrlPolicy.resolve(null, "https://mangadex.org"))
    }

    /**
     * The property the whole fix rests on: whatever a source hands back, a request built from the
     * result is buildable. Asserted against `defectOf` rather than a hard-coded list so it keeps
     * holding if the policy's notion of a usable URL is ever widened.
     */
    @ParameterizedTest
    @ValueSource(strings = ["//cdn.example.com/data/1.jpg", "/data/1.jpg", "data/1.jpg"])
    fun `a relative URL becomes one this policy considers usable`(relative: String) {
        assertNull(
            ImageUrlPolicy.defectOf(ImageUrlPolicy.resolve(relative, "https://mangadex.org")),
            "the resolved address must pass the same pre-flight check as any other",
        )
    }

    /**
     * Resolution is idempotent, which is what makes it safe to apply at more than one boundary: the
     * loader and `HttpSource` both run it, and the loader re-runs it on a URL restored from the
     * chapter cache. Were a second pass able to change the string, the disk-cache key would depend on
     * how many times a URL had been through — the same bytes stored twice, and a reader that
     * re-downloads what a download already saved.
     */
    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://cdn.example.com/1.jpg",
            "//cdn.example.com/1.jpg",
            "/data/1.jpg",
            "data/1.jpg",
            "data:image/png;base64,iVBORw0KGgo=",
        ],
    )
    fun `resolving twice is the same as resolving once`(url: String) {
        val base = "https://mangadex.org"
        val once = ImageUrlPolicy.resolve(url, base)
        assertEquals(once, ImageUrlPolicy.resolve(once, base))
    }
}
