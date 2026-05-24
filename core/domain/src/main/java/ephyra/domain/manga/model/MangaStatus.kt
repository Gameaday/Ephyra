package ephyra.domain.manga.model

/**
 * Domain-level manga publishing status.
 *
 * Replaces raw `Long` status fields that formerly required importing
 * `eu.kanade.tachiyomi.source.model.SManga` constants into the domain layer.
 * Mappers in the data layer convert from/to source-api values.
 */
sealed interface MangaStatus {
    val value: Long

    /** Manga is currently being published. */
    data object Ongoing : MangaStatus {
        override val value: Long = 1L
    }

    /** Manga has concluded. */
    data object Completed : MangaStatus {
        override val value: Long = 2L
    }

    /** Manga is on a break / hiatus. */
    data object Hiatus : MangaStatus {
        override val value: Long = 6L
    }

    /** Manga publication was cancelled. */
    data object Cancelled : MangaStatus {
        override val value: Long = 5L
    }

    /** Manga is licensed (status used by some sources). */
    data object Licensed : MangaStatus {
        override val value: Long = 4L
    }

    /** Status is unknown or not specified. */
    data object Unknown : MangaStatus {
        override val value: Long = 0L
    }

    companion object {
        /** Mapping from raw Long value to domain sealed status. */
        fun fromValue(value: Long): MangaStatus = when (value) {
            1L -> Ongoing
            2L -> Completed
            4L -> Licensed
            5L -> Cancelled
            6L -> Hiatus
            else -> Unknown
        }
    }
}
