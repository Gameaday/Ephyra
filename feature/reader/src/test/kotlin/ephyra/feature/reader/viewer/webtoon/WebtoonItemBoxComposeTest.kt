package ephyra.feature.reader.viewer.webtoon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric-backed composition tests for the webtoon reserved-box contract: the item box
 * must reserve the same size regardless of which branch (spinner vs slice column) renders
 * inside it — content swaps can never resize the LazyColumn item or shift siblings.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class WebtoonItemBoxComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `reserved box honors the aspect ratio`() {
        composeRule.setContent {
            // 2.0 (landscape) aspect so the reserved height fits the Robolectric window;
            // the mechanism under test is identical for tall strip aspects.
            Box(Modifier.testTag("item").webtoonItemBox(2.0f))
        }
        val bounds = composeRule.onNodeWithTag("item").getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        assertEquals(2.0f, width.value / height.value, 0.01f)
    }

    @Test
    fun `reserved box is identical regardless of branch child`() {
        composeRule.setContent {
            Column(Modifier.fillMaxWidth()) {
                // Branch A: spinner-like child (tiny content).
                Box(Modifier.testTag("branchA").webtoonItemBox(2.0f)) {
                    Box(Modifier.size(24.dp))
                }
                // Branch B: slice-column-like child (content taller than the reserved box).
                Box(Modifier.testTag("branchB").webtoonItemBox(2.0f)) {
                    Column {
                        repeat(4) { Spacer(Modifier.fillMaxWidth().height(200.dp)) }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val a = composeRule.onNodeWithTag("branchA").getUnclippedBoundsInRoot()
        val b = composeRule.onNodeWithTag("branchB").getUnclippedBoundsInRoot()
        assertEquals(a.right - a.left, b.right - b.left)
        assertEquals(a.bottom - a.top, b.bottom - b.top)
        assertEquals(
            2.0f,
            (b.right - b.left).value / (b.bottom - b.top).value,
            0.01f,
        )
    }
}
