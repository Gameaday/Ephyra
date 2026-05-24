package ephyra.domain.content.model

/**
 * Publishing/release status for any content type.
 *
 * Replaces manga-specific status integers with a media-agnostic sealed type.
 * Data layer mappers convert from/to source-specific status values.
 */
sealed interface ContentStatus {
    /** Content is actively being published/released. */
    data object Ongoing : ContentStatus

    /** Content has concluded. */
    data object Completed : ContentStatus

    /** Content is on hiatus / temporarily paused. */
    data object Hiatus : ContentStatus

    /** Content was cancelled/discontinued. */
    data object Cancelled : ContentStatus

    /** Content is licensed (relevant for manga, but media-agnostic). */
    data object Licensed : ContentStatus

    /** Status is unknown or not yet determined. */
    data object Unknown : ContentStatus

    companion object {
        /** Attempts to parse a string status into the sealed type. */
        fun fromString(value: String): ContentStatus = when (value.lowercase().trim()) {
            "ongoing", "publishing", "releasing", "airing" -> Ongoing
            "completed", "finished", "ended" -> Completed
            "hiatus", "on hiatus", "paused" -> Hiatus
            "cancelled", "canceled", "discontinued" -> Cancelled
            "licensed" -> Licensed
            else -> Unknown
        }

        /** Attempts to parse a numeric status from legacy manga sources. */
        fun fromLegacyInt(value: Long): ContentStatus = when (value) {
            1L -> Ongoing
            2L -> Completed
            4L -> Licensed
            5L -> Cancelled
            6L -> Hiatus
            else -> Unknown
        }
    }
}
