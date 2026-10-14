package ephyra.app.architecture

import ephyra.app.security.TrackedFileNames
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Structural gate for **WS7** of the Mihon 0.21.1 adoption plan: when a chapter's page list shifts
 * under the pager — the next chapter's pages are prepended on resume, a page is absorbed, a page is
 * split, a page list is reset — the pager must re-seek to the item the viewer is still on instead of
 * staying at the old *index*, which is now a different page (the RTL "resumed into the next chapter"
 * symptom upstream fixed in `51b61a36`).
 *
 * **Why a structural gate and not a behavioural test.** The realignment is a `LaunchedEffect(items)`
 * inside `ComposePagerReader`, a `@Composable` bound to a `PagerState`, a `PagerViewer` and a live
 * reader activity. A JVM behavioural test would need a Compose test host plus all three faked before
 * it reached an assertion, and a test that never reaches its assertion is the blind-gate failure this
 * programme has hit repeatedly. What can be asserted cheaply and structurally is the property that
 * matters: **the pager observes its item list and re-seeks by item identity.** That is a property of
 * the source's shape, so it survives a refactor of the surrounding composable.
 *
 * **What it does not claim.** It does not prove the visible page and saved progress are identical
 * across a resume; that needs the device. It proves the realignment path exists and is keyed on the
 * item list, which is the half that silently regressed.
 */
class PagerRealignmentStructuralTest {

    private val pagerReader = "feature/reader/src/main/kotlin/ephyra/feature/reader/viewer/pager/ComposePagerReader.kt"

    @Test
    fun `the pager re-seeks by item identity when its item list shifts`() {
        val root = TrackedFileNames.repositoryRoot()
        val file = File(root, pagerReader)
        assertTrue(file.exists(), "ComposePagerReader.kt not found at the expected path")

        val text = file.readText()
        assertTrue(
            REALIGN_ON_ITEMS_CHANGE.containsMatchIn(text),
            "the pager must observe its item list and re-seek to the current item's new index, so a " +
                "prepended/absorbed/split page cannot leave the reader on a different page",
        )
    }

    @Test
    fun `the matcher recognises a realignment and rejects a plain index reset`() {
        val realignment = """
            LaunchedEffect(items) {
                val cur = viewer.currentPage
                if (cur != null) {
                    val newIdx = items.indexOf(cur)
                    if (newIdx != -1 && newIdx != pagerState.currentPage) {
                        pagerState.scrollToPage(newIdx)
                    }
                }
            }
        """.trimIndent()
        assertTrue(REALIGN_ON_ITEMS_CHANGE.containsMatchIn(realignment))

        // A block keyed on something else, or one that jumps without resolving the current item,
        // must not read as a realignment.
        assertTrue(
            !REALIGN_ON_ITEMS_CHANGE.containsMatchIn(
                """
                LaunchedEffect(pagerState) {
                    pagerState.scrollToPage(0)
                }
                """.trimIndent(),
            ),
        )
        assertTrue(
            !REALIGN_ON_ITEMS_CHANGE.containsMatchIn(
                """
                LaunchedEffect(items) {
                    val cur = viewer.currentPage
                }
                """.trimIndent(),
            ),
        )
    }

    private companion object {
        /**
         * A `LaunchedEffect(items)` block that resolves the current item by identity and scrolls to
         * its new index. `DOT_MATCHES_ALL` lets `.*?` cross the nested `if`/`try` braces between the
         * two anchors; the non-greedy quantifier keeps it from matching across an unrelated effect.
         */
        val REALIGN_ON_ITEMS_CHANGE = Regex(
            """LaunchedEffect\(items\)\s*\{.*?indexOf\(.*?scrollToPage\(""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
    }
}
