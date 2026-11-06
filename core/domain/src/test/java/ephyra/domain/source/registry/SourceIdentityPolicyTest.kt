package ephyra.domain.source.registry

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/**
 * Pins the one identity rule (`SRC-011` / `ADR-0012`), including its migration property: a clean
 * spelling keeps the id every pre-registry component computed, and an unclean one becomes
 * detectable instead of silently orphaning every entry written against it.
 */
class SourceIdentityPolicyTest {

    private fun legacyHash(url: String): Long = url.hashCode().toLong()

    @Test
    fun `one identity per origin, across spellings`() {
        SourceIdentityPolicy.identityOf("https://mangadex.org/") shouldBe
            SourceIdentityPolicy.identityOf("HTTPS://MangaDex.org:443")
        SourceIdentityPolicy.identityOf("https://mangadex.org") shouldNotBe
            SourceIdentityPolicy.identityOf("https://manganato.com")
    }

    @Test
    fun `a clean spelling keeps the id the pre-SRC-011 rule minted`() {
        // Compat property, deliberate: the stored spelling is already canonical, so the
        // registry's id and the legacy raw-hash id agree and the upgrade orphans nothing.
        SourceIdentityPolicy.identityOf("https://mangadex.org") shouldBe legacyHash("https://mangadex.org")
    }

    @Test
    fun `a spelling change is detectable, not silent`() {
        val storedSpelling = "https://mangadex.org/"
        val newIdentity = SourceIdentityPolicy.identityOf(storedSpelling)
        val legacyId = SourceIdentityPolicy.legacyIdentityOf(storedSpelling)

        // The old rule and the new rule disagree about this spelling, and that disagreement is
        // exactly the pair `SourceRegistry.resolveEntry` matches to report `SpellingChanged`.
        newIdentity shouldNotBe legacyId
        newIdentity shouldBe SourceIdentityPolicy.identityOf("https://mangadex.org")
    }

    @Test
    fun `an uncanonicalizable origin keeps its legacy identity`() {
        // Fallback, not a new rule: a stored value the origin policy cannot name must stay
        // reachable by the id it already has rather than be orphaned by this change.
        SourceIdentityPolicy.identityOf("mangadex.org") shouldBe legacyHash("mangadex.org")
    }

    @Test
    fun `legacyIdentityOf is exactly the raw hash`() {
        SourceIdentityPolicy.legacyIdentityOf("https://mangadex.org/") shouldBe
            legacyHash("https://mangadex.org/")
    }
}
