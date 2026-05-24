package ephyra.domain.content.model

/**
 * Generic domain model representing any type of content item in the library.
 *
 * This is the central entity for the content-agnostic core. It replaces the
 * manga-specific [Manga] domain model for all cross-cutting features
 * (Library, History, Tracking, Downloads).
 *
 * Content-type specific fields (e.g. chapter count for manga, duration for video)
 * are stored in the extensible [metadata] map rather than as typed fields,
 * keeping the core model stable across media types.
 *
 * The existing [Manga] type is an alias/superset of this model with additional
 * manga-specific convenience accessors.
 */
data class ContentItem(
    val id: Long,
    val sourceId: Long,
    val url: String,
    val title: String,
    val author: String?,
    val artist: String?,
    val description: String?,
    val genres: List<String>,
    val status: ContentStatus,
    val thumbnailUrl: String?,
    val contentType: ContentType,
    val metadata: Map<String, String> = emptyMap(),
    val favorite: Boolean = false,
    val dateAdded: Long = 0L,
    val lastUpdate: Long = 0L,
    val initialized: Boolean = true,
) {
    companion object {
        /** Known metadata key for manga chapter count. */
        const val META_CHAPTER_COUNT = "chapter_count"

        /** Known metadata key for episode count (video/audio). */
        const val META_EPISODE_COUNT = "episode_count"

        /** Known metadata key for duration in minutes (video/audio/books). */
        const val META_DURATION_MINUTES = "duration_minutes"

        /** Known metadata key for volume count (books/novels). */
        const val META_VOLUME_COUNT = "volume_count"

        /** Known metadata key for word count (novels/text). */
        const val META_WORD_COUNT = "word_count"

        /** Known metadata key for season/episode tracking (video). */
        const val META_SEASON = "season"

        /**
         * Create a minimal placeholder item (e.g. for search results before details are fetched).
         */
        fun placeholder(
            url: String,
            title: String,
            sourceId: Long,
            contentType: ContentType,
        ) = ContentItem(
            id = -1L,
            sourceId = sourceId,
            url = url,
            title = title,
            author = null,
            artist = null,
            description = null,
            genres = emptyList(),
            status = ContentStatus.Unknown,
            thumbnailUrl = null,
            contentType = contentType,
        )
    }
}
