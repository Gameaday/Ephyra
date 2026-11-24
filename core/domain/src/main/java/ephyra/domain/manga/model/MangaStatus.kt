package ephyra.domain.manga.model

/**
 * Domain-level manga publishing status.
 *
 * Values match `eu.kanade.tachiyomi.source.model.SManga` exactly so the domain
 * layer never depends on the extension ABI. Mappers in the data layer convert
 * from/to source-api values.
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

    /** Manga is licensed (status used by some sources). */
    data object Licensed : MangaStatus {
        override val value: Long = 3L
    }

    /** Manga publication has finished (distinct from licensed). */
    data object PublishingFinished : MangaStatus {
        override val value: Long = 4L
    }

    /** Manga publication was cancelled. */
    data object Cancelled : MangaStatus {
        override val value: Long = 5L
    }

    /** Manga is on a break / hiatus. */
    data object Hiatus : MangaStatus {
        override val value: Long = 6L
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
            3L -> Licensed
            4L -> PublishingFinished
            5L -> Cancelled
            6L -> Hiatus
            else -> Unknown
        }
    }
}
