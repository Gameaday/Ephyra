package ephyra.domain.source.registry

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/**
 * Pins the one canonicalisation rule (`SRC-011` / `ADR-0012`): same origin, same spelling rules,
 * forever, on both the JVM and the device — no network, no clock, no platform URI behavior.
 */
class SourceOriginPolicyTest {

    @Test
    fun `case, default port, trailing slash and query are one origin`() {
        val spellings = listOf(
            "https://mangadex.org",
            "HTTPS://MangaDex.org",
            "https://mangadex.org:443",
            "https://mangadex.org/",
            "https://mangadex.org?tab=up",
            "https://mangadex.org/#fragment",
        )
        val origins = spellings.map { SourceOriginPolicy.canonicalize(it) }
        origins.forEach { it shouldBe SourceOrigin("https://mangadex.org") }
    }

    @Test
    fun `the scheme is part of the origin`() {
        SourceOriginPolicy.canonicalize("http://mangadex.org") shouldNotBe
            SourceOriginPolicy.canonicalize("https://mangadex.org")
    }

    @Test
    fun `an explicit port is kept and is part of the origin`() {
        SourceOriginPolicy.canonicalize("https://mangadex.org:8443") shouldBe
            SourceOrigin("https://mangadex.org:8443")
        SourceOriginPolicy.canonicalize("https://mangadex.org:8443") shouldNotBe
            SourceOriginPolicy.canonicalize("https://mangadex.org")
    }

    @Test
    fun `the path is kept because two sources may share a host`() {
        SourceOriginPolicy.canonicalize("https://example.org/manga") shouldBe
            SourceOrigin("https://example.org/manga")
        SourceOriginPolicy.canonicalize("https://example.org/manga") shouldNotBe
            SourceOriginPolicy.canonicalize("https://example.org/anime")
    }

    @Test
    fun `ipv6 hosts keep their brackets and may carry a port`() {
        SourceOriginPolicy.canonicalize("https://[2001:db8::1]:8443/x") shouldBe
            SourceOrigin("https://[2001:db8::1]:8443/x")
        SourceOriginPolicy.canonicalize("https://[2001:db8::1]:443") shouldBe
            SourceOrigin("https://[2001:db8::1]")
    }

    @Test
    fun `what is not an http(s) origin does not canonicalize`() {
        val rejected = listOf(
            "",                            // nothing at all
            "mangadex.org",                // no scheme
            "ftp://mangadex.org",          // non-http scheme
            "https://",                    // no host
            "https://user@mangadex.org",   // userinfo is a credential, not identity
            "https://mangadex.org:notaport", // a port that is not a number
            "https://mangadex.org:443x",
        )
        rejected.forEach { SourceOriginPolicy.canonicalize(it) shouldBe null }
    }

    @Test
    fun `spellings that cannot canonicalize compare by their raw value`() {
        // Neither side canonicalizes: the fallback is trimmed raw equality, so an unregister can
        // still erase a malformed stored spelling instead of helplessly mismatching it.
        SourceOriginPolicy.isSameOrigin("mangadex.org", " mangadex.org ") shouldBe true
        SourceOriginPolicy.isSameOrigin("mangadex.org", "https://mangadex.org") shouldBe false
    }

    @Test
    fun `same-origin matching is spelling-insensitive in both directions`() {
        SourceOriginPolicy.isSameOrigin("https://mangadex.org/", "HTTPS://MANGADEX.ORG") shouldBe true
        SourceOriginPolicy.isSameOrigin("https://mangadex.org", "https://mangadex.org:443/") shouldBe true
        SourceOriginPolicy.isSameOrigin("https://mangadex.org", "https://example.org") shouldBe false
    }
}
