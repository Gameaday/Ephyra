package ephyra.domain.content.ingest

import java.util.regex.Pattern

data class ParsedMetadata(
    val title: String,
    val season: Int? = null,
    val episode: Double? = null,
    val volume: Int? = null,
    val chapter: Double? = null,
)

object MediaMatcher {
    // Regex for TV/Anime episodes e.g. "Name - S01E02 - Title", "Name - 1x02"
    private val ANIME_PATTERN = Pattern.compile(
        """(?i)^(.+?)\s*-\s*S(\d+)\s*E(\d+(?:\.\d+)?).*$""",
        Pattern.CASE_INSENSITIVE,
    )
    private val ANIME_ALT_PATTERN = Pattern.compile(
        """(?i)^(.+?)\s*-\s*(\d+)x(\d+(?:\.\d+)?).*$""",
        Pattern.CASE_INSENSITIVE,
    )

    // Regex for Manga/Books e.g. "Name - Vol. 1 Ch. 2 - Title", "Name - Chapter 3"
    private val MANGA_PATTERN = Pattern.compile(
        """(?i)^(.+?)\s*-\s*(?:Vol\.|v)\s*(\d+)\s*(?:Ch\.|c)\s*(\d+(?:\.\d+)?).*$""",
        Pattern.CASE_INSENSITIVE,
    )
    private val MANGA_ALT_PATTERN = Pattern.compile(
        """(?i)^(.+?)\s*-\s*(?:Chapter|Ch\.|c)\s*(\d+(?:\.\d+)?).*$""",
        Pattern.CASE_INSENSITIVE,
    )

    /**
     * Parses the name of a file or folder and extracts structural metadata.
     */
    fun parse(fileName: String): ParsedMetadata {
        val cleanName = fileName.substringBeforeLast(".") // Remove extension

        val animeMatcher = ANIME_PATTERN.matcher(cleanName)
        if (animeMatcher.matches()) {
            return ParsedMetadata(
                title = animeMatcher.group(1).trim(),
                season = animeMatcher.group(2).toIntOrNull(),
                episode = animeMatcher.group(3).toDoubleOrNull(),
            )
        }

        val animeAltMatcher = ANIME_ALT_PATTERN.matcher(cleanName)
        if (animeAltMatcher.matches()) {
            return ParsedMetadata(
                title = animeAltMatcher.group(1).trim(),
                season = animeAltMatcher.group(2).toIntOrNull(),
                episode = animeAltMatcher.group(3).toDoubleOrNull(),
            )
        }

        val mangaMatcher = MANGA_PATTERN.matcher(cleanName)
        if (mangaMatcher.matches()) {
            return ParsedMetadata(
                title = mangaMatcher.group(1).trim(),
                volume = mangaMatcher.group(2).toIntOrNull(),
                chapter = mangaMatcher.group(3).toDoubleOrNull(),
            )
        }

        val mangaAltMatcher = MANGA_ALT_PATTERN.matcher(cleanName)
        if (mangaAltMatcher.matches()) {
            return ParsedMetadata(
                title = mangaAltMatcher.group(1).trim(),
                chapter = mangaAltMatcher.group(2).toDoubleOrNull(),
            )
        }

        // Fallback: treat the entire string as the title
        return ParsedMetadata(title = cleanName.trim())
    }
}
