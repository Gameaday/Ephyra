package ephyra.domain.manga.interactor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FormatMangaRecommendationTest {

    private val formatter = FormatMangaRecommendation()

    @Test
    fun `formats minimal recommendation with title only`() {
        val result = formatter(
            title = "Berserk",
        )
        assertEquals("📖 Berserk", result)
    }

    @Test
    fun `formats full recommendation with all metadata`() {
        val result = formatter(
            title = "Berserk",
            author = "Kentaro Miura",
            url = "https://example.com/manga/berserk",
            notes = "Masterpiece dark fantasy.",
            readChapters = 364,
            totalChapters = 364,
            score = 10.0,
            genres = listOf("Action", "Dark Fantasy", "Horror"),
            options = RecommendationShareOptions(),
        )

        assertTrue(result.contains("📖 Berserk by Kentaro Miura"))
        assertTrue(result.contains("⭐ Score: 10/10"))
        assertTrue(result.contains("📊 Progress: 364 / 364 chapters (Completed)"))
        assertTrue(result.contains("🏷️ Action, Dark Fantasy, Horror"))
        assertTrue(result.contains("💬 \"Masterpiece dark fantasy.\""))
        assertTrue(result.contains("🔗 https://example.com/manga/berserk"))
    }

    @Test
    fun `custom note overrides default notes`() {
        val result = formatter(
            title = "Chainsaw Man",
            notes = "Original note",
            options = RecommendationShareOptions(
                customNote = "You have to read this!",
            ),
        )

        assertTrue(result.contains("💬 \"You have to read this!\""))
        assertFalse(result.contains("Original note"))
    }

    @Test
    fun `disabled options omit specific metadata`() {
        val result = formatter(
            title = "One Piece",
            author = "Eiichiro Oda",
            url = "https://example.com/manga/onepiece",
            notes = "Peak fiction",
            readChapters = 1100,
            totalChapters = 1120,
            score = 9.5,
            genres = listOf("Adventure", "Shounen"),
            options = RecommendationShareOptions(
                includeUrl = false,
                includeNotes = false,
                includeProgress = false,
                includeScore = false,
                includeGenres = false,
            ),
        )

        assertEquals("📖 One Piece by Eiichiro Oda", result)
        assertFalse(result.contains("⭐"))
        assertFalse(result.contains("📊"))
        assertFalse(result.contains("🏷️"))
        assertFalse(result.contains("💬"))
        assertFalse(result.contains("🔗"))
    }

    @Test
    fun `formats in-progress reading status without completion tag`() {
        val result = formatter(
            title = "Vinland Saga",
            readChapters = 50,
            totalChapters = 200,
        )

        assertTrue(result.contains("📊 Progress: 50 / 200 chapters"))
        assertFalse(result.contains("(Completed)"))
    }
}
