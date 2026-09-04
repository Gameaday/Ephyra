package ephyra.feature.browse.source.globalsearch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SearchResultMerger] — the search-side Smart Merge: identical works
 * returned by different catalogues must collapse into a single merged row.
 */
class SearchResultMergerTest {

    @Test
    fun `empty input produces empty output`() {
        assertTrue(SearchResultMerger.merge(emptyList()).isEmpty())
    }

    @Test
    fun `single result passes through untouched`() {
        val merged = SearchResultMerger.merge(listOf(manga(id = 1, source = 10, title = "Berserk")))
        assertEquals(1, merged.size)
        assertEquals(listOf(10L), merged.single().sourceIds)
    }

    @Test
    fun `same work from different sources merges`() {
        val results = listOf(
            manga(id = 1, source = 10, title = "Berserk"),
            manga(id = 2, source = 20, title = "Berserk"),
        )
        val merged = SearchResultMerger.merge(results)
        assertEquals(1, merged.size)
        assertEquals(setOf(10L, 20L), merged.single().sourceIds.toSet())
    }

    @Test
    fun `titles differing in punctuation and case merge`() {
        val results = listOf(
            manga(id = 1, source = 10, title = "Attack on Titan!"),
            manga(id = 2, source = 20, title = "attack_on_titan"),
        )
        val merged = SearchResultMerger.merge(results)
        assertEquals(1, merged.size, "punctuation/case variants should merge: $merged")
    }

    @Test
    fun `distinct works never merge`() {
        val results = listOf(
            manga(id = 1, source = 10, title = "Berserk"),
            manga(id = 2, source = 20, title = "Vagabond"),
        )
        val merged = SearchResultMerger.merge(results)
        assertEquals(2, merged.size)
        assertTrue(merged.all { it.sourceIds.size == 1 })
    }

    @Test
    fun `same source duplicates do not merge`() {
        // Cross-source merger must not collapse two entries from the SAME source
        // (they are distinct URLs, e.g. a special chapter listing).
        val results = listOf(
            manga(id = 1, source = 10, title = "One Piece"),
            manga(id = 2, source = 10, title = "One Piece"),
        )
        val merged = SearchResultMerger.merge(results)
        assertEquals(2, merged.size)
    }

    @Test
    fun `entries merged from more sources sort first`() {
        val results = listOf(
            manga(id = 1, source = 10, title = "Solo Leveling"),
            manga(id = 2, source = 20, title = "Berserk"),
            manga(id = 3, source = 30, title = "Solo Leveling"),
            manga(id = 4, source = 40, title = "Berserk"),
            manga(id = 5, source = 50, title = "Berserk"),
        )
        val merged = SearchResultMerger.merge(results)
        assertEquals("Berserk", merged.first().manga.title)
        assertEquals(3, merged.first().sourceIds.size)
    }

    @Test
    fun `very short titles only merge on exact normalized match`() {
        // Fuzzy threshold is length-guarded; short titles must not fuzzy-merge.
        val results = listOf(
            manga(id = 1, source = 10, title = "Alice"),
            manga(id = 2, source = 20, title = "Alicia"),
        )
        val merged = SearchResultMerger.merge(results)
        assertEquals(2, merged.size)
    }
}
