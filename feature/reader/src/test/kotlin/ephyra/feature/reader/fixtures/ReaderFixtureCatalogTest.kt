package ephyra.feature.reader.fixtures

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderFixtureCatalogTest {
    @Test
    fun `catalog covers all required page behaviors`() {
        val behaviors = ReaderFixtureCatalog.pages.map { it.behavior }.toSet()
        assertTrue(FixtureBehavior.STATIC in behaviors)
        assertTrue(FixtureBehavior.LONG_STRIP in behaviors)
        assertTrue(FixtureBehavior.BORDERED in behaviors)
        assertTrue(FixtureBehavior.NOISY_BORDER in behaviors)
        assertTrue(FixtureBehavior.TALL_PAGED in behaviors)
        assertTrue(FixtureBehavior.ANIMATED in behaviors)
        assertTrue(FixtureBehavior.CORRUPT in behaviors)
        assertTrue(FixtureBehavior.MISSING in behaviors)
        assertTrue(FixtureBehavior.REVISION_CHANGE in behaviors)
    }

    @Test
    fun `fixture identities are unique and paths are explicit`() {
        assertEquals(ReaderFixtureCatalog.pages.size, ReaderFixtureCatalog.pages.map { it.id }.toSet().size)
        assertTrue(ReaderFixtureCatalog.pages.all { it.testPath.endsWith("ReaderFixtureCatalogTest.kt") })
        assertTrue(ReaderFixtureCatalog.interactions.all { it.id.isNotBlank() && it.description.isNotBlank() })
    }

    @Test
    fun `geometry expectations describe a real content rectangle`() {
        val fixture = ReaderFixtureCatalog.pages.first { it.id == "uniform-bordered-page-v1" }
        val rect = requireNotNull(fixture.expectedContentRect)
        assertTrue(rect[0] > 0 && rect[1] > 0)
        assertTrue(rect[2] < fixture.width && rect[3] < fixture.height)
        assertTrue(fixture.cropExpected)
        assertTrue(fixture.requiresSlicing.not())
    }

    @Test
    fun `long strips are continuous fixtures and failure fixtures are not crop candidates`() {
        val long = ReaderFixtureCatalog.pages.first { it.id == "static-long-webtoon-v1" }
        assertTrue(long.requiresSlicing)
        assertTrue(long.supportsContinuous)
        assertFalse(long.supportsPaged)

        val failures = ReaderFixtureCatalog.pages.filter {
            it.behavior in setOf(FixtureBehavior.CORRUPT, FixtureBehavior.MISSING)
        }
        assertEquals(2, failures.size)
        assertTrue(failures.none { it.cropExpected })
    }

    @Test
    fun `required interaction fixtures include pinch, tap, and predictive back`() {
        val required = ReaderFixtureCatalog.interactions.filter { it.required }.map { it.id }.toSet()
        assertTrue("multi-pointer-pinch-v1" in required)
        assertTrue("pinch-after-parent-scroll-v1" in required)
        assertTrue("double-tap-versus-single-v1" in required)
        assertTrue("predictive-back-v1" in required)
    }
}
