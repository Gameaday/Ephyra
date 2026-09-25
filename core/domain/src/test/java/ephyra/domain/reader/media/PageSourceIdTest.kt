package ephyra.domain.reader.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PageSourceIdTest {

    @Test
    fun `the same identity always produces the same cache component`() {
        val a = PageSourceId("mangadex", "page/1.png", "r1")
        val b = PageSourceId("mangadex", "page/1.png", "r1")
        assertEquals(a.cacheComponent(), b.cacheComponent())
    }

    @Test
    fun `a different revision is a different identity`() {
        // The whole reason revision exists: the same URL can serve different bytes.
        val before = PageSourceId("src", "p1", "r1")
        val after = PageSourceId("src", "p1", "r2")
        assertNotEquals(before.cacheComponent(), after.cacheComponent())
    }

    @Test
    fun `an unknown revision is distinct from any real revision`() {
        val unknown = PageSourceId("src", "p1")
        val real = PageSourceId("src", "p1", "r1")
        assertNotEquals(unknown.cacheComponent(), real.cacheComponent())
        assertEquals(PageSourceId.UNKNOWN_REVISION, unknown.revision)
    }

    @Test
    fun `field boundaries cannot be repartitioned into a collision`() {
        // Without length prefixes both of these would render as the same string.
        val first = PageSourceId("src", "a_b", "r")
        val second = PageSourceId("src", "a", "b_r")
        assertNotEquals(first.cacheComponent(), second.cacheComponent())
    }

    @Test
    fun `an empty separator cannot merge two identities`() {
        val first = PageSourceId("ab", "c", "r")
        val second = PageSourceId("a", "bc", "r")
        assertNotEquals(first.cacheComponent(), second.cacheComponent())
    }
}
