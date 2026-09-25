package ephyra.domain.reader.policy

import ephyra.domain.content.model.ContentType
import ephyra.domain.reader.model.ReadingMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultReadingModeResolverTest {

    @Test
    fun `an explicit per-series mode always wins over inference`() {
        val mode = DefaultReadingModeResolver.resolve(
            explicitMode = ReadingMode.RIGHT_TO_LEFT,
            userDefaultMode = ReadingMode.DEFAULT,
            contentType = ContentType.MANGA,
            genres = listOf("Webtoon"),
        )
        assertEquals(ReadingMode.RIGHT_TO_LEFT, mode)
    }

    @Test
    fun `an explicit webtoon choice survives a non-webtoon series`() {
        val mode = DefaultReadingModeResolver.resolve(
            explicitMode = ReadingMode.WEBTOON,
            userDefaultMode = ReadingMode.LEFT_TO_RIGHT,
            contentType = ContentType.MANGA,
            genres = listOf("Action"),
        )
        assertEquals(ReadingMode.WEBTOON, mode)
    }

    @Test
    fun `an explicit user default wins over inference`() {
        val mode = DefaultReadingModeResolver.resolve(
            explicitMode = ReadingMode.DEFAULT,
            userDefaultMode = ReadingMode.RIGHT_TO_LEFT,
            contentType = ContentType.MANGA,
            genres = listOf("Webtoon"),
        )
        assertEquals(ReadingMode.RIGHT_TO_LEFT, mode)
    }

    @Test
    fun `long strip genres select the webtoon reader`() {
        listOf("Webtoon", "webtoon", "WEBTOON", " Long Strip ", "long-strip", "longstrip", "Manhwa", "Manhua")
            .forEach { genre ->
                val mode = DefaultReadingModeResolver.resolve(
                    explicitMode = ReadingMode.DEFAULT,
                    userDefaultMode = ReadingMode.DEFAULT,
                    contentType = ContentType.MANGA,
                    genres = listOf(genre),
                )
                assertEquals(ReadingMode.WEBTOON, mode, "genre=$genre")
            }
    }

    @Test
    fun `unrelated genres never select the webtoon reader`() {
        listOf("Action", "Romance", "Full Color", "Fantasy", "Slice of life", "Long story", "Webtoon Comedy")
            .forEach { genre ->
                val mode = DefaultReadingModeResolver.resolve(
                    explicitMode = ReadingMode.DEFAULT,
                    userDefaultMode = ReadingMode.DEFAULT,
                    contentType = ContentType.MANGA,
                    genres = listOf(genre),
                )
                assertEquals(ReadingMode.DEFAULT, mode, "genre=$genre")
            }
    }

    @Test
    fun `non strip renderable content never infers webtoon`() {
        listOf(ContentType.NOVEL, ContentType.BOOK, ContentType.ANIME, ContentType.AUDIO).forEach { type ->
            val mode = DefaultReadingModeResolver.resolve(
                explicitMode = ReadingMode.DEFAULT,
                userDefaultMode = ReadingMode.DEFAULT,
                contentType = type,
                genres = listOf("Webtoon"),
            )
            assertEquals(ReadingMode.DEFAULT, mode, "type=$type")
        }
    }

    @Test
    fun `missing or empty genres fall back safely`() {
        val genreCases: List<List<String>?> = listOf(null, emptyList())
        genreCases.forEach { genres ->
            val mode = DefaultReadingModeResolver.resolve(
                explicitMode = ReadingMode.DEFAULT,
                userDefaultMode = ReadingMode.LEFT_TO_RIGHT,
                contentType = ContentType.MANGA,
                genres = genres,
            )
            assertEquals(ReadingMode.LEFT_TO_RIGHT, mode)
        }
    }

    @Test
    fun `inference matches normalized exact genres rather than substrings`() {
        assertTrue(DefaultReadingModeResolver.isLongStrip(ContentType.MANGA, listOf("Webtoon")))
        assertTrue(DefaultReadingModeResolver.isLongStrip(ContentType.MANGA, listOf("LONG_STRIP")))
        assertFalse(DefaultReadingModeResolver.isLongStrip(ContentType.MANGA, listOf("Long Strip Comedy")))
        assertFalse(DefaultReadingModeResolver.isLongStrip(ContentType.UNKNOWN, emptyList()))
        assertFalse(DefaultReadingModeResolver.isLongStrip(ContentType.NOVEL, listOf("Webtoon")))
    }
}
