package ephyra.domain.library.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * Pins the closed-set contract [LibraryDisplayMode] exposes to the library pager.
 *
 * The pager's renderer `when` is total by way of a final `else` arm, so the compiler no longer
 * forces its consumers to name every variant - this test does that instead. Adding a variant
 * without touching the pager (whose fallback renders the newcomer as the comfortable grid) or
 * this test becomes a red test, not a silent render.
 */
@Execution(ExecutionMode.CONCURRENT)
class LibraryDisplayModeTest {

    @Test
    fun `values exposes exactly the four canonical modes`() {
        LibraryDisplayMode.values shouldBe setOf(
            LibraryDisplayMode.CompactGrid,
            LibraryDisplayMode.ComfortableGrid,
            LibraryDisplayMode.List,
            LibraryDisplayMode.CoverOnlyGrid,
        )
    }

    @Test
    fun `serialize round-trips every mode through deserialize`() {
        for (mode in LibraryDisplayMode.values) {
            LibraryDisplayMode.deserialize(mode.serialize()) shouldBe mode
        }
    }

    @Test
    fun `deserialize maps unknown or corrupt strings to the default`() {
        for (garbage in listOf("", "compact_grid", "null", "0", "WEBTOON", "FUTURE_MODE")) {
            LibraryDisplayMode.deserialize(garbage) shouldBe LibraryDisplayMode.default
        }
    }
}
