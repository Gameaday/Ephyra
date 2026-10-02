package ephyra.core.common.util.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The shapes real extensions actually emit, asserted rather than assumed.
 *
 * **Why this file exists.** These cases were found by probing the shipped [ImageUrlPolicy] with a
 * list of shapes taken from real source code (`attr("src")` instead of `absUrl("src")`, a `src` read
 * across a line break, a URL pasted out of raw HTML) and reading what came back. Three were wrong,
 * and the worst was wrong *silently*:
 *
 * ```
 * "  https://cdn.example.com/i.jpg  "  ->  "https://example.com/  https://cdn.example.com/i.jpg"
 * ```
 *
 * The leading whitespace meant the string no longer looked absolute, so it was treated as a relative
 * path and joined onto the base URL. The result parses, its host is a valid hostname, and
 * [ImageUrlPolicy.isUsable] approves it — so it would have been requested, cached under that name,
 * and failed as a 404 that reads like a missing page rather than a malformed URL. No check
 * downstream can catch it, which is exactly why it needed to be caught here.
 *
 * A probe that only ever printed its findings would rot; these are assertions.
 */
class ImageExtensionOutputCompatibilityTest {

    private val base = "https://example.com"

    private fun resolved(raw: String) = ImageUrlPolicy.resolve(raw, base)

    @Test
    fun `surrounding whitespace does not turn an absolute URL into a relative one`() {
        // The silent corruption. Before the repair this resolved to a base-joined path that passed
        // every usability check while pointing at nothing.
        assertEquals("https://cdn.example.com/i.jpg", resolved("  https://cdn.example.com/i.jpg  "))
        assertTrue(ImageUrlPolicy.isUsable(resolved("  https://cdn.example.com/i.jpg  ")))
    }

    @Test
    fun `a tab after a URL does not fork the cache key`() {
        // Same image, two spellings, two disk-cache entries, stored twice — which is the exact
        // duplication the downloader resolves URLs specifically to avoid.
        assertEquals("https://cdn.example.com/i.jpg", resolved("https://cdn.example.com/i.jpg\t"))
        assertEquals(
            resolved("https://cdn.example.com/i.jpg"),
            resolved("https://cdn.example.com/i.jpg\t"),
        )
    }

    @Test
    fun `a line break inside a URL is removed`() {
        assertEquals("https://cdn.example.com/i.jpg", resolved("https://cdn.example.com/i\n.jpg"))
    }

    @Test
    fun `an escaped query separator is decoded`() {
        // `attr("src")` returns the escaped spelling; `absUrl("src")` does not. Left alone the server
        // receives a parameter named `amp;b`.
        assertEquals("https://cdn.example.com/i.jpg?a=1&b=2", resolved("https://cdn.example.com/i.jpg?a=1&amp;b=2"))
    }

    @Test
    fun `an already clean URL is untouched`() {
        val clean = "https://cdn.example.com/data/1.jpg?token=abc&expires=123"
        assertEquals(clean, resolved(clean))
    }

    @Test
    fun `relative forms still resolve against the base after repair`() {
        assertEquals("https://example.com/i.jpg", resolved(" /i.jpg "))
        assertEquals("https://example.com/i.jpg", resolved("i.jpg\n"))
        assertEquals("https://cdn.example.com/i.jpg", resolved(" //cdn.example.com/i.jpg "))
    }

    @Test
    fun `a space inside a path is not silently deleted`() {
        // Deleting it would join two path segments into a different file, which is worse than
        // leaving a URL that fails visibly.
        val spaced = "https://cdn.example.com/my folder/i.jpg"
        assertEquals(spaced, resolved(spaced))
    }

    @Test
    fun `a non-http scheme is still left alone`() {
        val data = "data:image/png;base64,iVBOR"
        assertEquals(data, resolved(data))
    }

    // ---------------------------------------------------------------------------------------
    // The reported failure: a source handing over two URLs joined together.
    //
    // Both shapes below are the same defect — the at-home image server and the API URL that
    // produced it, concatenated — differing only in what sat between them. The comma survives to
    // `defectOf`, which has always rejected it. The newline does not: it is a control character,
    // so `repair` deleted it and manufactured a string that *looks* like a valid URL.
    // ---------------------------------------------------------------------------------------

    private val atHome = "https://cmdxd98sb0x3yprd.mangadex.network"
    private val atHomeApi =
        "https://api.mangadex.org/at-home/server/605c371d-904f-4dda-96a0-24ffdd65e642"

    @Test
    fun `the reported host with a comma is rejected`() {
        val joined = "$atHome,$atHomeApi"
        assertNotNull(ImageUrlPolicy.defectOf(joined), "a comma in the host must be rejected")
    }

    @Test
    fun `the reported host with the scheme glued on is rejected`() {
        // Exactly what the device reported: "...mangadex.networkhttps". This is the string the
        // repair *manufactured* from a newline-joined pair, so it had to get past the repair to be
        // caught here. `networkhttps` is a legal hostname, so nothing else rejects it.
        val reported = "$atHome" + "https://api.mangadex.org/at-home/server/" +
            "605c371d-904f-4dda-96a0-24ffdd65e642"
        assertNotNull(
            ImageUrlPolicy.defectOf(reported),
            "a host carrying a second scheme must be rejected before DNS",
        )
    }

    @Test
    fun `control characters joining two URLs are not repaired into one`() {
        // The falsification. Before this guard, repair deleted the newline and produced a URL that
        // parsed, named a valid-looking host, and was requested — failing at DNS with the cause two
        // steps removed. If someone weakens repair again, this goes red.
        val joined = "$atHome\n$atHomeApi"
        val result = resolved(joined)
        // Asserted on what `resolve` *returns*, not on whether the result is eventually rejected.
        // Asserting the outcome instead lets the downstream check mask a missing guard here — which
        // is exactly how this test passed while the guard it was written for was deleted.
        assertTrue(
            result.contains('\n'),
            "a newline between two URLs must not be silently deleted; got \"$result\"",
        )
    }

    @Test
    fun `a genuinely single URL with a control character is still repaired`() {
        // The other half of the guard: refusing every repair would break the `attr("src")` shapes
        // the repair exists for. Only a *second* scheme separator disqualifies it.
        assertEquals("https://cdn.example.com/i.jpg", resolved("https://cdn.example.com/i\n.jpg"))
    }

    @Test
    fun `a hostname that merely ends in https is not rejected`() {
        // Why this is a separator count and not a suffix test.
        assertTrue(ImageUrlPolicy.isUsable("https://myhttpserver.com/i.jpg"))
        assertTrue(ImageUrlPolicy.isUsable("https://cdn.https/i.jpg"))
    }

    @Test
    fun `a proxy URL with an embedded target is not rejected`() {
        // A legitimate single URL can *contain* another URL in a query parameter. Counting
        // separators on the whole string would break it, which is why the count is only ever
        // applied where it is unambiguous.
        val proxied = "https://proxy.example.com/fetch?target=https://cdn.example.com/i.jpg"
        assertTrue(ImageUrlPolicy.isUsable(proxied))
    }
}
