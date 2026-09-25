package ephyra.data.room.target

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Target source-neutral series metadata. Not part of the legacy Room v3 database yet. */
@Entity(tableName = "target_series")
data class TargetSeriesEntity(
    @PrimaryKey
    @ColumnInfo(name = "local_id")
    val localId: String,
    @ColumnInfo(name = "content_type")
    val contentType: String,
    val title: String,
    val author: String?,
    val artist: String?,
    val description: String?,
    val status: String?,
    @ColumnInfo(name = "genres_json")
    val genresJson: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

/** Source-specific identity and presentation reference for a target series. */
@Entity(
    tableName = "target_series_sources",
    foreignKeys = [
        ForeignKey(
            entity = TargetSeriesEntity::class,
            parentColumns = ["local_id"],
            childColumns = ["series_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["source_id", "external_id"]), Index(value = ["source_id", "url"])],
    primaryKeys = ["series_id", "source_id"],
)
data class TargetSeriesSourceEntity(
    @ColumnInfo(name = "series_id")
    val seriesId: String,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "external_id")
    val externalId: String?,
    val url: String,
    val revision: Long,
    @ColumnInfo(name = "display_title")
    val displayTitle: String,
    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String?,
    @ColumnInfo(name = "source_metadata_json")
    val sourceMetadataJson: String?,
    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: Long,
)

/** Explicit user-owned library relationship. */
@Entity(
    tableName = "target_library_entries",
    foreignKeys = [
        ForeignKey(
            entity = TargetSeriesEntity::class,
            parentColumns = ["local_id"],
            childColumns = ["series_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TargetLibraryEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "series_id")
    val seriesId: String,
    @ColumnInfo(name = "added_at")
    val addedAt: Long,
    @ColumnInfo(name = "library_sort_position")
    val librarySortPosition: Long?,
    @ColumnInfo(name = "update_policy")
    val updatePolicy: String,
    @ColumnInfo(name = "update_enabled")
    val updateEnabled: Boolean,
    @ColumnInfo(name = "last_checked_at")
    val lastCheckedAt: Long?,
    @ColumnInfo(name = "last_changed_at")
    val lastChangedAt: Long?,
)

/** Canonical target chapter/unit metadata. Reading state is intentionally separate. */
@Entity(
    tableName = "target_chapters",
    foreignKeys = [
        ForeignKey(
            entity = TargetSeriesEntity::class,
            parentColumns = ["local_id"],
            childColumns = ["series_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["series_id", "sort_key"]), Index(value = ["source_id", "external_id"])],
)
data class TargetChapterEntity(
    @PrimaryKey
    @ColumnInfo(name = "local_id")
    val localId: String,
    @ColumnInfo(name = "series_id")
    val seriesId: String,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "external_id")
    val externalId: String?,
    val url: String,
    val title: String,
    @ColumnInfo(name = "unit_number")
    val unitNumber: Double?,
    val scanlator: String?,
    @ColumnInfo(name = "sort_key")
    val sortKey: String,
    val revision: Long,
    @ColumnInfo(name = "published_at")
    val publishedAt: Long?,
    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long?,
)

/** User-owned reading state kept separate from source-owned chapter metadata. */
@Entity(
    tableName = "target_chapter_states",
    foreignKeys = [
        ForeignKey(
            entity = TargetChapterEntity::class,
            parentColumns = ["local_id"],
            childColumns = ["chapter_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TargetChapterStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "chapter_id")
    val chapterId: String,
    @ColumnInfo(name = "is_read")
    val isRead: Boolean,
    val bookmarked: Boolean,
    @ColumnInfo(name = "last_page_read")
    val lastPageRead: Long,
)

/** Durable user history for a target chapter. */
@Entity(
    tableName = "target_history",
    foreignKeys = [
        ForeignKey(
            entity = TargetChapterEntity::class,
            parentColumns = ["local_id"],
            childColumns = ["target_chapter_local_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TargetHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "target_chapter_local_id")
    val targetChapterLocalId: String,
    @ColumnInfo(name = "last_read_at")
    val lastReadAt: Long?,
    @ColumnInfo(name = "read_duration_ms")
    val readDurationMs: Long,
)
