package ephyra.domain.series

import ephyra.domain.content.model.ContentType

/** Minimal legacy input needed to migrate a series without exposing Room or extension types. */
data class LegacySeriesRecord(
    val legacyId: Long,
    val sourceId: Long,
    val url: String,
    val title: String,
    val author: String?,
    val artist: String?,
    val description: String?,
    val genres: List<String>,
    val status: Long,
    val thumbnailUrl: String?,
    val sourceRevision: Long,
    val inLibrary: Boolean,
    val dateAdded: Long,
    val contentType: ContentType,
)

data class LegacyChapterRecord(
    val legacyId: Long,
    val seriesLegacyId: Long,
    val url: String,
    val title: String,
    val scanlator: String?,
    val chapterNumber: Double,
    val sourceOrder: Long,
    val read: Boolean,
    val bookmark: Boolean,
    val lastPageRead: Long,
    val dateFetch: Long,
    val dateUpload: Long,
    val lastModifiedAt: Long,
    val revision: Long,
)

data class LegacyHistoryRecord(
    val chapterLegacyId: Long,
    val lastReadAtMillis: Long?,
    val readDurationMillis: Long,
)

/** Legacy category definition and ordering needed by the target library organization model. */
data class LegacyCategoryRecord(
    val legacyId: Long,
    val name: String,
    val order: Long,
    val flags: Long,
    val isSystem: Boolean = false,
)

data class LegacySeriesMigrationInput(
    val series: LegacySeriesRecord,
    val chapters: List<LegacyChapterRecord>,
    val history: List<LegacyHistoryRecord>,
    val categories: List<LegacyCategoryRecord> = emptyList(),
    val seriesCategoryIds: List<Long> = emptyList(),
)

data class MigratedChapter(
    val targetLocalId: String,
    val legacyId: Long,
    val sourceId: String,
    val url: String,
    val title: String,
    val scanlator: String?,
    val chapterNumber: Double,
    val sourceOrder: Long,
    val read: Boolean,
    val bookmark: Boolean,
    val lastPageRead: Long,
    val dateFetch: Long,
    val dateUpload: Long,
    val lastModifiedAt: Long,
    val revision: Long,
)

data class MigratedHistory(
    val targetChapterLocalId: String,
    val legacyChapterId: Long,
    val lastReadAtMillis: Long?,
    val readDurationMillis: Long,
)

data class MigratedCategory(
    val targetCategoryId: String,
    val legacyCategoryId: Long,
    val name: String,
    val order: Long,
    val flags: Long,
    val isSystem: Boolean,
)

/**
 * Converts compatibility records into a target persistence plan. The conversion is deliberately
 * pure: it performs no writes and makes no title-based or cross-source merge decisions.
 */
object LegacySeriesMigrationMapper {
    fun migrate(input: LegacySeriesMigrationInput): LegacySeriesMigrationResult {
        val series = input.series
        if (series.legacyId <= 0L) {
            return LegacySeriesMigrationResult.Unresolved(series.legacyId, "legacy series id must be positive")
        }
        if (series.sourceId <= 0L) {
            return LegacySeriesMigrationResult.Unresolved(series.legacyId, "legacy source id must be positive")
        }
        if (series.url.isBlank() || series.title.isBlank()) {
            return LegacySeriesMigrationResult.Unresolved(series.legacyId, "legacy series url and title are required")
        }

        val targetLocalId = targetSeriesLocalId(series.legacyId)
        val sourceId = targetSourceId(series.sourceId)
        val identity = DurableSeriesIdentity(sourceId, url = series.url, contentType = series.contentType)
        val snapshot = DurableSeriesSnapshot(
            identity = identity,
            title = series.title,
            author = series.author,
            artist = series.artist,
            description = series.description,
            genres = series.genres,
            status = series.status.toString(),
            thumbnailUrl = series.thumbnailUrl,
            sourceRevision = series.sourceRevision.coerceAtLeast(1L),
            metadata = mapOf("legacyId" to series.legacyId.toString()),
        )

        val targetChapterIds = input.chapters.associate { chapter ->
            chapter.legacyId to targetChapterLocalId(chapter.legacyId)
        }
        val chapters = input.chapters.map { chapter ->
            require(chapter.seriesLegacyId == series.legacyId) {
                "Chapter ${chapter.legacyId} belongs to legacy series ${chapter.seriesLegacyId}"
            }
            require(chapter.url.isNotBlank() && chapter.title.isNotBlank()) {
                "Chapter ${chapter.legacyId} has incomplete identity"
            }
            MigratedChapter(
                targetLocalId = targetChapterIds.getValue(chapter.legacyId),
                legacyId = chapter.legacyId,
                sourceId = sourceId,
                url = chapter.url,
                title = chapter.title,
                scanlator = chapter.scanlator,
                chapterNumber = chapter.chapterNumber,
                sourceOrder = chapter.sourceOrder,
                read = chapter.read,
                bookmark = chapter.bookmark,
                lastPageRead = chapter.lastPageRead.coerceAtLeast(0L),
                dateFetch = chapter.dateFetch,
                dateUpload = chapter.dateUpload,
                lastModifiedAt = chapter.lastModifiedAt,
                revision = chapter.revision.coerceAtLeast(1L),
            )
        }
        val history = input.history.mapNotNull { record ->
            targetChapterIds[record.chapterLegacyId]?.let { targetId ->
                MigratedHistory(
                    targetChapterLocalId = targetId,
                    legacyChapterId = record.chapterLegacyId,
                    lastReadAtMillis = record.lastReadAtMillis,
                    readDurationMillis = record.readDurationMillis.coerceAtLeast(0L),
                )
            }
        }

        val categories = input.categories.map { category ->
            require(category.legacyId >= 0L) { "Category id must not be negative" }
            require(category.name.isNotBlank()) { "Category name must not be blank" }
            MigratedCategory(
                targetCategoryId = targetCategoryId(category.legacyId),
                legacyCategoryId = category.legacyId,
                name = category.name,
                order = category.order,
                flags = category.flags,
                isSystem = category.isSystem,
            )
        }
        val categoryIds = input.seriesCategoryIds.map { categoryId ->
            categories.firstOrNull { it.legacyCategoryId == categoryId }?.targetCategoryId
                ?: throw IllegalArgumentException("Series references unknown category $categoryId")
        }

        return LegacySeriesMigrationResult.Migrated(
            LegacySeriesMigrationPlan(
                targetLocalId = targetLocalId,
                sourceId = sourceId,
                snapshot = snapshot,
                inLibrary = series.inLibrary,
                libraryAddedAt = series.dateAdded.coerceAtLeast(0L),
                chapters = chapters,
                history = history,
                categories = categories,
                seriesCategories = categoryIds,
            ),
        )
    }

    fun targetSourceId(legacySourceId: Long): String = "legacy:$legacySourceId"
    fun targetSeriesLocalId(legacySeriesId: Long): String = "legacy-series:$legacySeriesId"
    fun targetChapterLocalId(legacyChapterId: Long): String = "legacy-chapter:$legacyChapterId"
    fun targetCategoryId(legacyCategoryId: Long): String = "legacy-category:$legacyCategoryId"
}

data class LegacySeriesMigrationPlan(
    val targetLocalId: String,
    val sourceId: String,
    val snapshot: DurableSeriesSnapshot,
    val inLibrary: Boolean,
    val libraryAddedAt: Long = 0L,
    val chapters: List<MigratedChapter>,
    val history: List<MigratedHistory>,
    val categories: List<MigratedCategory> = emptyList(),
    val seriesCategories: List<String> = emptyList(),
)

sealed interface LegacySeriesMigrationResult {
    data class Migrated(val plan: LegacySeriesMigrationPlan) : LegacySeriesMigrationResult
    data class Unresolved(
        val legacyId: Long,
        val reason: String,
    ) : LegacySeriesMigrationResult
}
