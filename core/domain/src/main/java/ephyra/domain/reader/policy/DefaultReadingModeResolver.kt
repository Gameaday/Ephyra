package ephyra.domain.reader.policy

import ephyra.domain.content.model.ContentType
import ephyra.domain.reader.model.ReadingMode

/**
 * Resolves the reading mode a reader session should start in.
 *
 * Ownership: this is reader policy, not view logic. The reader ViewModel delegates here so the
 * precedence rules are stated once and testable without Compose.
 *
 * Precedence, highest first:
 *  1. An explicit per-series mode. A user choice is never overridden by inference.
 *  2. The user's global default reading mode.
 *  3. Long-strip inference, only for content that can actually render as a continuous strip.
 *
 * Why inference exists at all: legacy Tachiyomi/Mihon extensions expose no declared rendering
 * format, so genre strings are the only available signal for those sources. It is a fallback, not
 * a model, and it is deliberately conservative: it fires only on an exact normalized genre match
 * rather than a substring, so unrelated genres cannot select a webtoon reader.
 */
object DefaultReadingModeResolver {
    private val LONG_STRIP_GENRES = setOf(
        "webtoon",
        "webtoons",
        "long strip",
        "longstrip",
        "long-strip",
        "manhwa",
        "manhua",
    )

    fun resolve(
        explicitMode: ReadingMode,
        userDefaultMode: ReadingMode,
        contentType: ContentType,
        genres: List<String>?,
    ): ReadingMode = when {
        explicitMode != ReadingMode.DEFAULT -> explicitMode
        userDefaultMode != ReadingMode.DEFAULT -> userDefaultMode
        isLongStrip(contentType, genres) -> ReadingMode.WEBTOON
        else -> userDefaultMode
    }

    /** True only when content can render as a continuous strip and a genre declares it. */
    fun isLongStrip(contentType: ContentType, genres: List<String>?): Boolean {
        if (contentType !in STRIP_RENDERABLE_TYPES) return false
        if (genres.isNullOrEmpty()) return false
        return genres.any { genre ->
            normalize(genre) in LONG_STRIP_GENRES
        }
    }

    private val STRIP_RENDERABLE_TYPES = setOf(
        ContentType.MANGA,
        ContentType.UNKNOWN,
    )

    private val WHITESPACE = Regex("\\s+")

    private fun normalize(value: String): String =
        value.trim().lowercase()
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(WHITESPACE, " ")
}
