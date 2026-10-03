package eu.kanade.tachiyomi.source.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The 1.7 model fields exist so an extension can assign them.
 *
 * The failure these prevent is `NoSuchFieldError`, which arrives *after* the source has loaded and
 * while the user is browsing it. Asserting the property is assignable is the whole test — an
 * interface property that compiles here is one the JVM signature exists for at runtime.
 */
class ExtensionModelCompatibilityTest {

    @Test
    fun `a 1_7-era extension can assign the manga fields upstream declares`() {
        val manga = SManga.create().apply {
            genres = listOf("Action", "Fantasy")
            banner = "https://cdn.example.com/banner.jpg"
            altTitles = listOf("Official Name")
            language = "ja"
            score = 87
            readingMode = SManga.ReadingMode.RIGHT_TO_LEFT
            contentRating = SManga.ContentRating.ADULT
        }

        assertEquals(listOf("Action", "Fantasy"), manga.genres)
        assertEquals("https://cdn.example.com/banner.jpg", manga.banner)
        assertEquals(listOf("Official Name"), manga.altTitles)
        assertEquals("ja", manga.language)
        assertEquals(87, manga.score)
        assertEquals(SManga.ReadingMode.RIGHT_TO_LEFT, manga.readingMode)
        assertEquals(SManga.ContentRating.ADULT, manga.contentRating)
    }

    @Test
    fun `a 1_7-era extension can assign the chapter fields upstream declares`() {
        val chapter = SChapter.create().apply {
            number = "12.5a"
            volume = "3"
            scanlators = listOf("Group A")
            language = "en"
            locked = true
            note = "Available Friday"
        }

        assertEquals("12.5a", chapter.number)
        assertEquals("3", chapter.volume)
        assertEquals(listOf("Group A"), chapter.scanlators)
        assertEquals("en", chapter.language)
        assertTrue(chapter.locked)
        assertEquals("Available Friday", chapter.note)
    }

    /**
     * Upstream does not mirror the deprecated field into the current one, so a reader must consult
     * both. A source compiled against 1.4 fills only `genre`; one compiled against 1.7 fills only
     * `genres`. Reading just one makes the other's genres silently vanish.
     */
    @Test
    fun `genres are read from whichever field the source filled`() {
        val modern = SManga.create().apply { genres = listOf("Action", "Fantasy") }
        assertEquals(listOf("Action", "Fantasy"), modern.effectiveGenres())

        @Suppress("DEPRECATION")
        val legacy = SManga.create().apply { genre = "Action, Fantasy" }
        assertEquals(listOf("Action", "Fantasy"), legacy.effectiveGenres())
    }

    /** `number` is a String upstream precisely so `"12.5a"` survives; a Float cannot hold it. */
    @Test
    fun `a chapter number with a suffix survives the string field`() {
        val chapter = SChapter.create().apply { number = "12.5a" }
        assertEquals("12.5a", chapter.effectiveNumber())

        @Suppress("DEPRECATION")
        val legacy = SChapter.create().apply { chapter_number = 12.5f }
        assertEquals("12.5", legacy.effectiveNumber())
    }

    /** Unset must not read as a number, or sorting and filtering would treat it as one. */
    @Test
    fun `an unset number reads as absent rather than as zero`() {
        val chapter = SChapter.create()
        assertNull(chapter.effectiveNumber())
        assertNull(chapter.number)
        assertFalse(chapter.locked)
    }

    /** Content rating defaults to SAFE, because a blur filter must not default to "unknown". */
    @Test
    fun `content rating defaults to SAFE`() {
        assertEquals(SManga.ContentRating.SAFE, SManga.create().contentRating)
    }

    /** Copying must carry the new fields, or a round trip silently drops them. */
    @Test
    fun `copy carries every field including the new ones`() {
        val original = SManga.create().apply {
            url = "/manga/1"
            title = "Title"
            genres = listOf("Action")
            altTitles = listOf("Alt")
            banner = "https://cdn.example.com/b.jpg"
            score = 42
            contentRating = SManga.ContentRating.SUGGESTIVE
            readingMode = SManga.ReadingMode.LONG_STRIP
        }
        val copy = original.copy()
        assertEquals(original.genres, copy.genres)
        assertEquals(original.altTitles, copy.altTitles)
        assertEquals(original.banner, copy.banner)
        assertEquals(original.score, copy.score)
        assertEquals(original.contentRating, copy.contentRating)
        assertEquals(original.readingMode, copy.readingMode)
    }
}
