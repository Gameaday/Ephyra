package ephyra.domain.manga.interactor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [TitleNormalizer] — the single source of truth for title normalization
 * and fuzzy matching. Every feature that matches titles delegates here, so these tests
 * guard the shared contract for search dedup, tracker matching, and merge.
 */
class TitleNormalizerTest {

    @Test
    fun `forEquality strips all non-alphanumeric including spaces`() {
        assertEquals("attackontitan", TitleNormalizer.forEquality("  Attack on Titan! "))
        assertEquals("onepiece", TitleNormalizer.forEquality("One_Piece"))
        assertEquals("berserk", TitleNormalizer.forEquality("Berserk"))
    }

    @Test
    fun `forEquality preserves unicode letters`() {
        assertEquals("進撃の巨人", TitleNormalizer.forEquality("進撃の巨人"))
    }

    @Test
    fun `forMatching preserves word boundaries`() {
        assertEquals("re zero", TitleNormalizer.forMatching("Re:Zero"))
        assertEquals("one piece", TitleNormalizer.forMatching("One   Piece"))
        assertEquals("jujutsu kaisen tv", TitleNormalizer.forMatching("Jujutsu Kaisen (TV)"))
    }

    @Test
    fun `forMatching preserves unicode letters`() {
        assertEquals("進撃の巨人", TitleNormalizer.forMatching("進撃の巨人"))
    }

    @Test
    fun `similarity is one for identical strings`() {
        assertEquals(1.0, TitleNormalizer.similarity("Berserk", "Berserk"))
    }

    @Test
    fun `similarity is low for unrelated strings`() {
        assertTrue(TitleNormalizer.similarity("berserk", "xqzvwj") < 0.5)
    }

    @Test
    fun `isFuzzyMatch merges near-identical titles above threshold`() {
        assertTrue(TitleNormalizer.isFuzzyMatch("martial peak", "martial peak", 0.97))
    }

    @Test
    fun `isFuzzyMatch rejects short titles below length guard`() {
        assertFalse(TitleNormalizer.isFuzzyMatch("Alice", "Alicia", 0.97))
    }
}
