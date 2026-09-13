package ephyra.domain.content.model

/**
 * Domain representation of chapter/unit metadata within a source's manifest.
 */
data class ChapterInfo(
    val key: String,
    val title: String,
    val number: Double,
    val dateUpload: Long = 0L,
    val scanlator: String? = null,
    val url: String = key,
)
