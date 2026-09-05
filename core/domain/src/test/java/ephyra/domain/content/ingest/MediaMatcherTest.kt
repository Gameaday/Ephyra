package ephyra.domain.content.ingest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit tests for [MediaMatcher], the filename/folder parser that turns raw
 * file-system entries into structured metadata (title, season, episode, volume, chapter).
 *
 * This is the first link in the "index content" chain: if parsing regresses,
 * ingestion produces garbage titles and deduplication keys.
 */
class MediaMatcherTest {

    // ── Anime / TV (SxxExx) ──────────────────────────────────────────────

    @Test
    fun `parses anime SxxExx pattern`() {
        val parsed = MediaMatcher.parse("Frieren - S01E02 - Title")
        assertEquals("Frieren", parsed.title)
        assertEquals(1, parsed.season)
        assertEquals(2.0, parsed.episode)
        assertNull(parsed.volume)
        assertNull(parsed.chapter)
    }

    @Test
    fun `parses anime SxxExx with decimals`() {
        val parsed = MediaMatcher.parse("Show - S01E1.5")
        assertEquals("Show", parsed.title)
        assertEquals(1, parsed.season)
        assertEquals(1.5, parsed.episode)
    }

    @Test
    fun `parses anime alt pattern NxN`() {
        val parsed = MediaMatcher.parse("One Piece - 1x025")
        assertEquals("One Piece", parsed.title)
        assertEquals(1, parsed.season)
        assertEquals(25.0, parsed.episode)
    }

    @Test
    fun `parses anime alt pattern with decimals`() {
        val parsed = MediaMatcher.parse("Anime - 2x03.5 Special")
        assertEquals("Anime", parsed.title)
        assertEquals(2, parsed.season)
        assertEquals(3.5, parsed.episode)
    }

    @Test
    fun `anime pattern is case insensitive`() {
        val parsed = MediaMatcher.parse("show - s02e03")
        assertEquals("show", parsed.title)
        assertEquals(2, parsed.season)
        assertEquals(3.0, parsed.episode)
    }

    // ── Manga (Vol x Ch y) ───────────────────────────────────────────────

    @Test
    fun `parses manga volume and chapter pattern`() {
        val parsed = MediaMatcher.parse("Berserk - Vol. 5 Ch. 12")
        assertEquals("Berserk", parsed.title)
        assertEquals(5, parsed.volume)
        assertEquals(12.0, parsed.chapter)
        assertNull(parsed.season)
    }

    @Test
    fun `parses manga volume and chapter shorthand`() {
        val parsed = MediaMatcher.parse("One Piece - v3 c7 - Title")
        assertEquals("One Piece", parsed.title)
        assertEquals(3, parsed.volume)
        assertEquals(7.0, parsed.chapter)
    }

    @Test
    fun `parses manga chapter only pattern`() {
        val parsed = MediaMatcher.parse("Naruto - Chapter 450")
        assertEquals("Naruto", parsed.title)
        assertNull(parsed.volume)
        assertEquals(450.0, parsed.chapter)
    }

    @Test
    fun `parses manga chapter alt shorthand`() {
        val parsed = MediaMatcher.parse("Chainsaw Man - Ch. 120.5")
        assertEquals("Chainsaw Man", parsed.title)
        assertEquals(120.5, parsed.chapter)
    }

    @Test
    fun `parses manga chapter with decimals`() {
        val parsed = MediaMatcher.parse("Jujutsu - Chapter 1.5")
        assertEquals("Jujutsu", parsed.title)
        assertEquals(1.5, parsed.chapter)
    }

    // ── Extension stripping ──────────────────────────────────────────────

    @Test
    fun `strips file extension before parsing`() {
        val parsed = MediaMatcher.parse("One Piece - Chapter 3.cbz")
        assertEquals("One Piece", parsed.title)
        assertEquals(3.0, parsed.chapter)
    }

    @Test
    fun `strips extension for epub`() {
        val parsed = MediaMatcher.parse("Frieren - S01E01.epub")
        assertEquals("Frieren", parsed.title)
        assertEquals(1, parsed.season)
        assertEquals(1.0, parsed.episode)
    }

    // ── Fallback ─────────────────────────────────────────────────────────

    @Test
    fun `falls back to title when no pattern matches`() {
        val parsed = MediaMatcher.parse("Mystery Series")
        assertEquals("Mystery Series", parsed.title)
        assertNull(parsed.season)
        assertNull(parsed.episode)
        assertNull(parsed.volume)
        assertNull(parsed.chapter)
    }

    @Test
    fun `falls back for plain directory names`() {
        val parsed = MediaMatcher.parse("My Manga Series")
        assertEquals("My Manga Series", parsed.title)
    }

    @Test
    fun `strip extension leaves title intact for plain names`() {
        val parsed = MediaMatcher.parse("My Manga Series.cbz")
        assertEquals("My Manga Series", parsed.title)
    }
}
