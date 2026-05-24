package ephyra.domain.content.model

/**
 * Categorizes the type of media content.
 */
enum class ContentType(val value: Int) {
    /** Default: content type has not been determined yet. */
    UNKNOWN(0),

    /** Standard manga (comics, manhwa, manhua, webtoon, doujinshi, one-shot). */
    MANGA(1),

    /** Light novel or web novel — text-primary content with illustrations. */
    NOVEL(2),

    /** General book (artbook, reference, etc.) — non-serial. */
    BOOK(3),

    /** Streaming video / animation. */
    ANIME(4),

    /** Audiobooks, podcasts, and other audio formats. */
    AUDIO(5),
    ;

    companion object {
        private val BY_VALUE = entries.associateBy { it.value }

        /** Resolves an integer DB value to a [ContentType], defaulting to [UNKNOWN]. */
        fun fromValue(value: Int): ContentType = BY_VALUE[value] ?: UNKNOWN

        /**
         * Infers [ContentType] from a tracker's `publishing_type` string.
         */
        fun fromPublishingType(publishingType: String): ContentType {
            return when (publishingType.lowercase().trim()) {
                "manga", "manhwa", "manhua", "webtoon", "comic",
                "oneshot", "one_shot", "one-shot", "one shot",
                "doujinshi", "doujin",
                "oel",
                -> MANGA

                "novel", "light novel", "light_novel",
                "web novel", "web_novel",
                -> NOVEL

                "artbook", "art book", "art_book",
                -> BOOK

                "anime", "tv", "movie", "ova", "ona", "special",
                -> ANIME

                "audio", "audiobook", "podcast",
                -> AUDIO

                else -> UNKNOWN
            }
        }

        /**
         * Genre keywords that indicate webtoon-style long-strip content.
         */
        val WEBTOON_GENRE_KEYWORDS = setOf(
            "webtoon",
            "long strip",
            "long-strip",
            "longstrip",
            "manhwa",
            "manhua",
        )

        /**
         * Checks whether [genres] contain any keyword that suggests webtoon-style content.
         */
        fun isLikelyWebtoon(genres: List<String>?): Boolean {
            if (genres.isNullOrEmpty()) return false
            return genres.any { genre ->
                WEBTOON_GENRE_KEYWORDS.any { keyword ->
                    genre.lowercase().contains(keyword)
                }
            }
        }

        /**
         * Checks whether a [publishingType] string indicates webtoon format.
         */
        fun isWebtoonPublishingType(publishingType: String): Boolean {
            return when (publishingType.lowercase().trim()) {
                "webtoon", "manhwa", "manhua" -> true
                else -> false
            }
        }
    }
}
