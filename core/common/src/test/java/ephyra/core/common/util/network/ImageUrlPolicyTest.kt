package ephyra.core.common.util.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Pins the pre-flight check on an image URL.
 *
 * **The failure this exists for.** A page resolved to
 *
 * ```
 * https://cmdxd98sb0x3yprd.mangadex.network,https
 * ```
 *
 * and the reader sent it. OkHttp accepted it — a comma is not a forbidden host character — so the
 * name reached DNS, and DNS reported the one thing it could report about a name that can never
 * exist:
 *
 * ```
 * Unable to resolve host "cmdxd98sb0x3yprd.mangadex.network,https": No address associated with hostname
 * ```
 *
 * The request was guaranteed to fail, the user saw the raw resolver string instead of anything
 * about the cause, and the reader retried the identical dead name. Checking first removes all
 * three.
 *
 * **What the counterweight tests are for.** A check that is only ever right about the one reported
 * URL is a check that will one day reject a legitimate CDN. The "usable" cases below are as much
 * part of the contract as the rejected ones, and several are deliberately awkward — ports, query
 * strings, credentials, punycode, IPv6, hyphenated labels — because those are what a real image
 * host looks like.
 */
class ImageUrlPolicyTest {

    @Test
    fun `the reported splicing artifact is rejected before it is ever sent`() {
        val reported = "https://cmdxd98sb0x3yprd.mangadex.network,https"

        val defect = ImageUrlPolicy.defectOf(reported)

        assertNotNull(defect, "the host carries a comma and can never resolve; this must not pass as usable")
        assertFalse(ImageUrlPolicy.isUsable(reported))
    }

    /**
     * The exact string from the report, asserted as the exception rather than as a boolean.
     *
     * The reader's recovery depends on the *type* — `TransientErrors.shouldReResolveUrl` matches on
     * it to decide that the URL must be dropped and the source re-asked — so a policy that
     * detected the defect but threw something generic would classify as a permanent failure and
     * re-request the same dead URL. The type is the behaviour.
     */
    @Test
    fun `requireUsable throws the type the reader's recovery is keyed on`() {
        val thrown = assertThrows(MalformedImageUrlException::class.java) {
            ImageUrlPolicy.requireUsable("https://cmdxd98sb0x3yprd.mangadex.network,https")
        }

        assertEquals(
            "https://cmdxd98sb0x3yprd.mangadex.network,https",
            thrown.url,
            "the rejected URL must survive for the reader's re-resolve comparison",
        )
        assertTrue(
            TransientErrors.shouldReResolveUrl(thrown),
            "an unusable URL must require a fresh one, exactly like a name that did not resolve",
        )
        assertTrue(
            TransientErrors.isTransient(thrown),
            "and it must stay worth retrying, or the source never gets a second chance to answer",
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://uploads.mangadex.org/data/ab/cd/1.jpg",
            // A port is normal on an image host and must not be mistaken for a defect.
            "https://cdn.example.com:8443/data/1.jpg",
            // Query strings are how most CDNs carry a signed URL, and the signed URL is the
            // case `DEF-020`/`DEF-023` exist for. Rejecting one would regress that fix.
            "https://cdn.example.com/1.jpg?token=abc&expires=1",
            // Credentials in the authority are legal and were a plausible real-world shape.
            "https://user:pass@cdn.example.com/1.jpg",
            // Punycode, because a Japanese source is exactly who serves punycode hosts.
            "https://xn--wgv71a.example.co.jp/1.jpg",
            // Hyphens at interior label positions are legal and common.
            "https://my-cdn-01.example.com/1.jpg",
            "https://a-b.example.com/1.jpg",
            // Percent-encoding belongs in the path and must not be read as a host defect.
            "https://example.com/a%20b/1.jpg",
            "HTTPS://CDN.EXAMPLE.COM/1.jpg",
            "http://plain.example.com/1.jpg",
        ],
    )
    fun `a real image URL is never rejected`(url: String) {
        assertNull(ImageUrlPolicy.defectOf(url), "$url is a legitimate image URL and must be requested")
        assertTrue(ImageUrlPolicy.isUsable(url))
        // requireUsable must be a no-op, not a throw, on the same input.
        ImageUrlPolicy.requireUsable(url)
    }

    @Test
    fun `an IPv6 literal is a host, not a defect`() {
        // `HttpUrl.host` returns IPv6 without its brackets, so the bracketed form in the URL
        // becomes `::1` by the time the policy sees it. Rejecting it would break every local
        // mirror, and the policy has no business second-guessing an address shape anyway.
        assertNull(ImageUrlPolicy.defectOf("http://[2001:db8::1]/1.jpg"))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "   ",
            // A relative or scheme-less string has no host to send a request to.
            "/data/1.jpg",
            "cdn.example.com/1.jpg",
            // Non-http schemes cannot address an image host. `data:` in particular is a
            // memory bomb with no size bound, and `file:` would let a source name a path on
            // the device.
            "file:///data/1.jpg",
            "data:image/png;base64,iVBORw0KGgo=",
            "javascript:alert(1)",
            // A host label that ends in a hyphen is not a hostname.
            "https://cdn-.example.com/1.jpg",
            // Whitespace inside the host is the other common splicing artifact.
            "https://cdn.example.com https/1.jpg",
        ],
    )
    fun `a URL that cannot address a host is rejected with a stated reason`(url: String) {
        val defect = requireNotNull(ImageUrlPolicy.defectOf(url)) { "$url cannot address a host" }
        assertFalse(defect.isBlank(), "a rejection must say why; an unexplained failure is what the user sees instead")
    }

    @Test
    fun `an absent URL is rejected rather than crashing`() {
        // `Page.imageUrl` is nullable throughout the loader, so null has to be a policy answer
        // and not an NPE from inside the policy.
        assertNotNull(ImageUrlPolicy.defectOf(null))
        assertFalse(ImageUrlPolicy.isUsable(null))
    }

    /**
     * The policy must not become a synonym for "the host is down".
     *
     * `DEF-023` is about a well-formed URL whose name does not answer. If this check had swallowed
     * that case the reader would stop sending requests for hosts that were merely unreachable,
     * which is a different bug wearing the same symptom. Reachability is not decided here, and the
     * test says so by asserting a dead-but-well-formed host is still sent.
     */
    @Test
    fun `a well formed host that happens not to resolve is still sent`() {
        val url = "https://nonexistent-host-abc123.invalid/data/1.jpg"
        assertNull(ImageUrlPolicy.defectOf(url), "reachability is a network question, not a URL question")
    }

    /**
     * A second rejected URL must not be confused with the first.
     *
     * The reader compares the re-resolved string against the one it rejected to decide whether
     * another resolution is worth anything. If the policy could not tell them apart — say it
     * normalised away the difference, or reported only "malformed" — that comparison would either
     * stop a recovery which would have worked or miss one which would not. The exact strings are
     * therefore part of the contract.
     */
    @Test
    fun `distinct rejected URLs stay distinct`() {
        val spliced = "https://cmdxd98sb0x3yprd.mangadex.network,https"
        val clean = "https://cmdxd98sb0x3yprd.mangadex.network/data/1.jpg"

        assertFalse(ImageUrlPolicy.isUsable(spliced))
        assertTrue(
            ImageUrlPolicy.isUsable(clean),
            "the same host without the splice is perfectly usable and must not be caught by the same rule",
        )
    }
}
