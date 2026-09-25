package ephyra.data.backup.target

import ephyra.data.room.target.TargetCategoryEntity
import ephyra.data.room.target.TargetChapterEntity
import ephyra.data.room.target.TargetChapterStateEntity
import ephyra.data.room.target.TargetExcludedScanlatorEntity
import ephyra.data.room.target.TargetHistoryEntity
import ephyra.data.room.target.TargetLibraryEntryEntity
import ephyra.data.room.target.TargetSeriesCategoryEntity
import ephyra.data.room.target.TargetSeriesEntity
import ephyra.data.room.target.TargetSeriesSourceEntity
import ephyra.data.room.target.TargetTrackingEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/** Versioned, source-neutral target backup. It intentionally does not reuse legacy BackupManga. */
@Serializable
data class TargetBackupDocument(
    @ProtoNumber(1) val formatVersion: Int = CURRENT_FORMAT_VERSION,
    @ProtoNumber(2) val series: List<TargetBackupSeries> = emptyList(),
    @ProtoNumber(3) val categories: List<TargetBackupCategory> = emptyList(),
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

@Serializable
data class TargetBackupSeries(
    @ProtoNumber(1) val localId: String,
    @ProtoNumber(2) val contentType: String,
    @ProtoNumber(3) val title: String,
    @ProtoNumber(4) val author: String? = null,
    @ProtoNumber(5) val artist: String? = null,
    @ProtoNumber(6) val description: String? = null,
    @ProtoNumber(7) val status: String? = null,
    @ProtoNumber(8) val genres: List<String> = emptyList(),
    @ProtoNumber(9) val createdAt: Long = 0L,
    @ProtoNumber(10) val updatedAt: Long = 0L,
    @ProtoNumber(11) val sourceReferences: List<TargetBackupSourceReference> = emptyList(),
    @ProtoNumber(12) val libraryEntry: TargetBackupLibraryEntry? = null,
    @ProtoNumber(13) val chapters: List<TargetBackupChapter> = emptyList(),
    @ProtoNumber(14) val chapterStates: List<TargetBackupChapterState> = emptyList(),
    @ProtoNumber(15) val history: List<TargetBackupHistory> = emptyList(),
    @ProtoNumber(16) val categoryIds: List<String> = emptyList(),
    @ProtoNumber(17) val tracking: List<TargetBackupTracking> = emptyList(),
    @ProtoNumber(18) val excludedScanlators: List<String> = emptyList(),
)

@Serializable
data class TargetBackupSourceReference(
    @ProtoNumber(1) val sourceId: String,
    @ProtoNumber(2) val externalId: String? = null,
    @ProtoNumber(3) val url: String,
    @ProtoNumber(4) val revision: Long,
    @ProtoNumber(5) val displayTitle: String,
    @ProtoNumber(6) val thumbnailUrl: String? = null,
    @ProtoNumber(7) val sourceMetadataJson: String? = null,
    @ProtoNumber(8) val lastSeenAt: Long = 0L,
)

@Serializable
data class TargetBackupCategory(
    @ProtoNumber(1) val categoryId: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val order: Long,
    @ProtoNumber(4) val flags: Long,
    @ProtoNumber(5) val isSystem: Boolean = false,
)

@Serializable
data class TargetBackupLibraryEntry(
    @ProtoNumber(1) val addedAt: Long,
    @ProtoNumber(2) val librarySortPosition: Long? = null,
    @ProtoNumber(3) val updatePolicy: String,
    @ProtoNumber(4) val updateEnabled: Boolean,
    @ProtoNumber(5) val lastCheckedAt: Long? = null,
    @ProtoNumber(6) val lastChangedAt: Long? = null,
)

@Serializable
data class TargetBackupChapter(
    @ProtoNumber(1) val localId: String,
    @ProtoNumber(2) val sourceId: String,
    @ProtoNumber(3) val externalId: String? = null,
    @ProtoNumber(4) val url: String,
    @ProtoNumber(5) val title: String,
    @ProtoNumber(6) val unitNumber: Double? = null,
    @ProtoNumber(7) val scanlator: String? = null,
    @ProtoNumber(8) val sortKey: String,
    @ProtoNumber(9) val revision: Long,
    @ProtoNumber(10) val publishedAt: Long? = null,
    @ProtoNumber(11) val fetchedAt: Long? = null,
)

@Serializable
data class TargetBackupChapterState(
    @ProtoNumber(1) val chapterId: String,
    @ProtoNumber(2) val isRead: Boolean,
    @ProtoNumber(3) val bookmarked: Boolean,
    @ProtoNumber(4) val lastPageRead: Long,
)

@Serializable
data class TargetBackupHistory(
    @ProtoNumber(1) val targetChapterLocalId: String,
    @ProtoNumber(2) val lastReadAt: Long? = null,
    @ProtoNumber(3) val readDurationMs: Long,
)

@Serializable
data class TargetBackupTracking(
    @ProtoNumber(1) val trackerId: String,
    @ProtoNumber(2) val remoteId: String?,
    @ProtoNumber(3) val libraryId: String?,
    @ProtoNumber(4) val title: String,
    @ProtoNumber(5) val lastChapterRead: Double,
    @ProtoNumber(6) val totalChapters: Long,
    @ProtoNumber(7) val status: String,
    @ProtoNumber(8) val score: Double,
    @ProtoNumber(9) val remoteUrl: String,
    @ProtoNumber(10) val startedAt: Long,
    @ProtoNumber(11) val finishedAt: Long,
    @ProtoNumber(12) val isPrivate: Boolean,
    @ProtoNumber(13) val updatedAt: Long = 0L,
)

/** All target tables for one series, used as the lossless backup/restore boundary. */
data class TargetBackupSnapshot(
    val series: TargetSeriesEntity,
    val sourceReferences: List<TargetSeriesSourceEntity>,
    val libraryEntry: TargetLibraryEntryEntity?,
    val chapters: List<TargetChapterEntity>,
    val chapterStates: List<TargetChapterStateEntity>,
    val history: List<TargetHistoryEntity>,
    val categories: List<TargetCategoryEntity> = emptyList(),
    val seriesCategories: List<TargetSeriesCategoryEntity> = emptyList(),
    val tracking: List<TargetTrackingEntity> = emptyList(),
    val excludedScanlators: List<TargetExcludedScanlatorEntity> = emptyList(),
)
