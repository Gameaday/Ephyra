package ephyra.domain.migration.models

import ephyra.domain.content.source.SourceProfile
import ephyra.domain.manga.model.Manga

/**
 * Represents a high-confidence migration candidate discovered from a healthy content source.
 */
data class MigrationCandidate(
    val manga: Manga,
    val sourceProfile: SourceProfile? = null,
    val sourceName: String,
    val sourceId: Long,
    val confidence: Double,
    val chapterCount: Int = -1,
)
