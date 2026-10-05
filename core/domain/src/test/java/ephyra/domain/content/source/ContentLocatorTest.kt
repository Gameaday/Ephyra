package ephyra.domain.content.source

import ephyra.core.common.util.network.MalformedImageUrlException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The contract [ContentLocator] exists to hold: a value that can address a host, wrapped so callers
 * cannot read it back and branch on its shape.
 *
 * Every case below is written so that *removing* the judgement makes it fail. A test that passes
 * whether or not the check runs is worse than no test, because it reports the property as proven.
 */
class ContentLocatorTest {

    private val base = "https://source.example.com"

    // --- construction: an address that can be requested is representable ---

    @Test
    fun `an absolute address is accepted`() {
        val locator = ContentLocator.of("https://cdn.example.com/item/1", null, EngineId.EXTENSION_APK)
        assertEquals(EngineId.EXTENSION_APK.value, locator.engineId.value)
    }

    @Test
    fun `a relative address is resolved against the base before being judged`() {
        // Judged *after* resolution, because a relative value is only meaningful once it has a base.
        // Judging the raw value first would reject shapes that resolve perfectly well.
        val locator = ContentLocator.of("/item/1", base, EngineId.LOCAL_ARCHIVE)
        assertEquals(EngineId.LOCAL_ARCHIVE.value, locator.engineId.value)
    }

    // --- construction: the reported defect is unrepresentable ---

    @Test
    fun `a composite of two URLs is refused at construction`() {
        // The reported value, verbatim in shape: a three-part cache key that reached DNS and came
        // back as `Unable to resolve host`. Nothing downstream refused it because nothing owned the
        // decision - the reader cannot address a host, and it should not have been asked to.
        val reported = "https://cmdxd98sb0x3yprd.mangadex.network" +
            ",https://api.mangadex.org/at-home/server/505ebe29,1790988508107"
        val failure = expectRefusal { ContentLocator.of(reported, base, EngineId.EXTENSION_APK) }
        assertTrue(failure.message!!, failure.message!!.contains("scheme separator"))
    }

    @Test
    fun `a host with a scheme spliced onto it is refused`() {
        // The second reported shape. `networkhttps` is a syntactically valid RFC-1123 host, so it
        // passes any check that looks only at the host and fails at DNS. Only the scheme-separator
        // rule catches it, which is why that rule has to exist.
        val spliced = "https://cmdxd98sb0x3yprd.mangadex.networkhttps://api.mangadex.org/x"
        expectRefusal { ContentLocator.of(spliced, base, EngineId.EXTENSION_APK) }
    }

    @Test
    fun `an empty address is refused rather than becoming an empty locator`() {
        // A locator that exists but cannot address a host is precisely the defect this type exists
        // to prevent, so it must be unrepresentable rather than merely discouraged.
        expectRefusal { ContentLocator.of("", base, EngineId.EXTENSION_APK) }
        expectRefusal { ContentLocator.of(null, base, EngineId.EXTENSION_APK) }
    }

    @Test
    fun `a scheme that is not http is refused`() {
        expectRefusal { ContentLocator.of("ftp://files.example.com/1", base, EngineId.EXTENSION_APK) }
    }
// --- persistence ---

    @Test
    fun `a locator survives being written and read back`() {
        // A locator that cannot survive a restart cannot address anything on the next launch, so the
        // round trip is part of the contract rather than an implementation detail.
        val original = ContentLocator.of("https://cdn.example.com/a?b=c&d=e#f", base, EngineId.OPDS)
        val restored = ContentLocator.fromPersisted(original.persisted())
        assertEquals(original.engineId.value, restored.engineId.value)
        assertEquals(original.persisted(), restored.persisted())
    }

    @Test
    fun `an engine id containing a space is not confused for the token boundary`() {
        // The separator is U+001F because no URL can contain it, so no legitimate value can be
        // mistaken for a boundary. If this fails, `engineId` is reading the wrong substring.
        val locator = ContentLocator.of("https://cdn.example.com/1", base, EngineId("odd id"))
        assertEquals("odd id", ContentLocator.fromPersisted(locator.persisted()).engineId.value)
    }

    @Test
    fun `a token with no engine id is not a locator`() {
        // Guards the migration path: a column value failing this predates locators, so it has to be
        // converted rather than rehydrated - rehydrating would smuggle an unjudged address back in.
        assertTrue(!ContentLocator.isLocator("https://cdn.example.com/1"))
        expectArgumentFailure { ContentLocator.fromPersisted("https://cdn.example.com/1") }
    }

    // --- EngineId ---

    @Test
    fun `engine ids are value-based, so a duplicate registration collides rather than shadowing`() {
        // A registry keyed on EngineId must see these as one engine. That is what makes an
        // accidental double-registration visible instead of silently first-wins.
        assertEquals(EngineId("jellyfin"), EngineId("jellyfin"))
        assertEquals(EngineId("jellyfin").hashCode(), EngineId("jellyfin").hashCode())
        assertNotEquals(EngineId("jellyfin"), EngineId("local-archive"))
    }

    @Test
    fun `a blank engine id is rejected at construction`() {
        // An engine whose id is blank would produce a locator whose leading segment is nothing, and
        // `substringBefore` would then hand back the whole token - a URL presented as an engine id.
        expectArgumentFailure { EngineId("") }
        expectArgumentFailure { EngineId("   ") }
    }

    @Test
    fun `the toString of a locator does not leak the address`() {
        // The address is the part a caller must not start matching on. If it reaches a log line by
        // accident, someone will eventually parse it back out.
        val locator = ContentLocator.of("https://cdn.example.com/secret/1", base, EngineId.EXTENSION_APK)
        assertTrue(locator.toString(), !locator.toString().contains("secret"))
    }

    // --- helpers ---

    private inline fun expectRefusal(block: () -> Unit): MalformedImageUrlException =
        try {
            block()
            fail("Expected the address to be refused, but it was accepted")
            throw AssertionError("unreachable")
        } catch (e: MalformedImageUrlException) {
            e
        }

    private inline fun expectArgumentFailure(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException, but none was thrown")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
