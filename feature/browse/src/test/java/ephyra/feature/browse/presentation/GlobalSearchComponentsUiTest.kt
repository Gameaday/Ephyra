package ephyra.feature.browse.presentation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric-backed Compose UI tests for the Global Search suggestions row and the
 * Smart Merge banner. These run on the JVM (no emulator) so CI gates them on every PR.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GlobalSearchComponentsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `suggestion chips render and report clicks`() {
        val clicked = mutableListOf<String>()
        composeRule.setContent {
            GlobalSearchSuggestions(
                suggestions = listOf("Berserk", "One Piece"),
                onSuggestionClick = { clicked.add(it) },
            )
        }

        composeRule.onNodeWithContentDescription("Search suggestion: Berserk")
            .assertExists()
            .performClick()
        composeRule.onNodeWithContentDescription("Search suggestion: One Piece").assertExists()
        composeRule.onNodeWithText("Berserk").assertExists()

        composeRule.runOnIdle {
            assert(clicked.single() == "Berserk") { "unexpected clicks: $clicked" }
        }
    }

    @Test
    fun `suggestion row renders nothing when empty`() {
        composeRule.setContent {
            GlobalSearchSuggestions(
                suggestions = emptyList(),
                onSuggestionClick = {},
            )
        }
        composeRule.onNodeWithText("Berserk").assertDoesNotExist()
    }

    @Test
    fun `merged banner is hidden for zero count`() {
        composeRule.setContent { GlobalSearchMergedBanner(mergedCount = 0) }
        composeRule.onNodeWithText("Smart Merge").assertDoesNotExist()
    }

    @Test
    fun `merged banner shows the deduplicated count`() {
        composeRule.setContent { GlobalSearchMergedBanner(mergedCount = 3) }
        composeRule.onNodeWithText("Smart Merge removed 3 duplicate result(s) from other sources")
            .assertExists()
    }
}
