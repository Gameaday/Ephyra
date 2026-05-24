package ephyra.domain.content.model

/**
 * Generic domain model representing any chronological or sequential sub-division of a [ContentItem].
 *
 * This media-agnostic entity acts as the single model for:
 * - Chapters in Manga and Books
 * - Episodes in Anime and TV shows
 * - Tracks or Sections in Audiobooks and Podcasts
 *
 * It bridges the gap between different content types, allowing a unified presentation
 * and progress tracking sub-system.
 */
data class ContentUnit(
    val id: Long,
    val contentItemId: Long,
    val url: String,
    val title: String,
    val number: Double,
    val dateUpload: Long,
    val progress: Long, // Playback progress (seconds), read position (characters), or page number
    val totalLength: Long, // Total duration (seconds), book length (characters), or total page count
    val lastRead: Long, // Timestamp of last interaction
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val scanlator: String? = null, // Optional manga-specific attribute
) {
    /**
     * Progress ratio (0.0 to 1.0) of completion for the unit.
     */
    val progressRatio: Float
        get() = if (totalLength > 0L) progress.toFloat() / totalLength else 0.0f

    /**
     * Whether the user has started interacting with this unit but not completed it yet.
     */
    val hasStarted: Boolean
        get() = progress > 0L && !read
}
