package ephyra.feature.browse.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.domain.manga.model.Manga
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric-backed Compose UI tests for the Global Search suggestions row, the
 * Smart Merge banner, the no-results empty state, and the as-you-type Library section.
 * These run on the JVM (no emulator) so CI gates them on every PR.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
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

    @Test
    fun `empty state shown when a query yields no results`() {
        composeRule.setContent {
            GlobalSearchContent(
                items = emptyMap(),
                contentPadding = PaddingValues(0.dp),
                getManga = { mutableStateOf(it) },
                onClickSource = {},
                onClickItem = {},
                onLongClickItem = {},
                searchQuery = "Naruto",
            )
        }
        composeRule.onNodeWithText("No results found").assertExists()
    }

    @Test
    fun `empty state is suppressed when library suggestions exist`() {
        composeRule.setContent {
            GlobalSearchContent(
                items = emptyMap(),
                contentPadding = PaddingValues(0.dp),
                getManga = { mutableStateOf(it) },
                onClickSource = {},
                onClickItem = {},
                onLongClickItem = {},
                searchQuery = "Naruto",
                suggestions = listOf("Naruto"),
            )
        }
        // Recents/library suggestions for the query are valid matches, so the
        // "no results" empty state must not appear alongside them.
        composeRule.onNodeWithText("No results found").assertDoesNotExist()
    }

    @Test
    fun `library section renders above results for matching library titles`() {
        val berserk = Manga.create().copy(id = 1, title = "Berserk")
        composeRule.setContent {
            GlobalSearchContent(
                items = emptyMap(),
                contentPadding = PaddingValues(0.dp),
                getManga = { mutableStateOf(it) },
                onClickSource = {},
                onClickItem = {},
                onLongClickItem = {},
                searchQuery = "Berserk",
                libraryResults = listOf(berserk),
            )
        }
        composeRule.onNodeWithText("From your library").assertExists()
        composeRule.onNodeWithText("No results found").assertDoesNotExist()
    }
}
