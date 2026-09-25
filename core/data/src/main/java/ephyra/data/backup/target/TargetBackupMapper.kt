package ephyra.data.backup.target

import ephyra.data.room.target.TargetCategoryEntity
import ephyra.data.room.target.TargetChapterEntity
import ephyra.data.room.target.TargetChapterStateEntity
import ephyra.data.room.target.TargetExcludedScanlatorEntity
import ephyra.data.room.target.TargetHistoryEntity
import ephyra.data.room.target.TargetLibraryEntryEntity
import ephyra.data.room.target.TargetSeriesEntity
import ephyra.data.room.target.TargetSeriesSourceEntity
import ephyra.data.room.target.TargetTrackingEntity
import kotlinx.serialization.json.Json

class TargetBackupMapper(
    private val json: Json = Json,
) {
    fun toBackupSeries(snapshot: TargetBackupSnapshot): TargetBackupSeries {
        return TargetBackupSeries(
            localId = snapshot.series.localId,
            contentType = snapshot.series.contentType,
            title = snapshot.series.title,
            author = snapshot.series.author,
            artist = snapshot.series.artist,
            description = snapshot.series.description,
            status = snapshot.series.status,
            genres = runCatching {
                json.decodeFromString<List<String>>(snapshot.series.genresJson)
            }.getOrDefault(emptyList()),
            createdAt = snapshot.series.createdAt,
            updatedAt = snapshot.series.updatedAt,
            sourceReferences = snapshot.sourceReferences.map { it.toBackup() },
            libraryEntry = snapshot.libraryEntry?.toBackup(),
            chapters = snapshot.chapters.map { it.toBackup() },
            chapterStates = snapshot.chapterStates.map { it.toBackup() },
            history = snapshot.history.map { it.toBackup() },
            categoryIds = snapshot.seriesCategories.map { it.categoryId },
            tracking = snapshot.tracking.map { it.toBackup() },
            excludedScanlators = snapshot.excludedScanlators.map { it.scanlator },
        )
    }

    fun toBackupDocument(snapshots: List<TargetBackupSnapshot>): TargetBackupDocument {
        val categoryGroups = snapshots.flatMap { it.categories }.groupBy { it.categoryId }
        val categories = categoryGroups.map { (categoryId, definitions) ->
            require(definitions.distinct().size == 1) {
                "Target backup contains conflicting definitions for category $categoryId"
            }
            definitions.first()
        }
        snapshots.forEach { snapshot ->
            require(snapshot.seriesCategories.all { membership -> categoryGroups.containsKey(membership.categoryId) }) {
                "Target backup series references a category absent from the document"
            }
        }
        return TargetBackupDocument(
            series = snapshots.map(::toBackupSeries),
            categories = categories
                .sortedWith(compareBy<TargetCategoryEntity> { it.sortOrder }.thenBy { it.categoryId })
                .map { it.toBackup() },
        )
    }

    fun fromBackupDocument(document: TargetBackupDocument): List<TargetBackupSnapshot> {
        require(document.formatVersion in 1..TargetBackupDocument.CURRENT_FORMAT_VERSION) {
            "Unsupported target backup format version: ${document.formatVersion}"
        }
        val categories = document.categories.map { it.toEntity() }
        val categoryIds = categories.map { it.categoryId }
        require(categoryIds.size == categoryIds.toSet().size) {
            "Target backup contains duplicate category ids"
        }
        return document.series.map { fromBackup(it, categories) }
    }

    fun fromBackup(
        series: TargetBackupSeries,
        categories: List<TargetCategoryEntity> = emptyList(),
    ): TargetBackupSnapshot {
        require(series.localId.isNotBlank()) { "Target series local id must not be blank" }
        require(series.title.isNotBlank()) { "Target series title must not be blank" }
        val categoryIds = series.categoryIds
        val knownCategoryIds = categories.map { it.categoryId }.toSet()
        require(categoryIds.size == categoryIds.toSet().size) {
            "Target backup series contains duplicate category ids"
        }
        require(categoryIds.all { it in knownCategoryIds }) {
            "Target backup series references an unknown category"
        }
        val chapterIds = series.chapters.map { it.localId }.toSet()
        require(chapterIds.size == series.chapters.size) { "Target backup contains duplicate chapter local ids" }
        require(series.chapterStates.all { it.chapterId in chapterIds }) {
            "Target backup contains chapter state for an unknown chapter"
        }
        require(series.history.all { it.targetChapterLocalId in chapterIds }) {
            "Target backup contains history for an unknown chapter"
        }
        val excludedScanlators = series.excludedScanlators.map { it.trim() }
        require(excludedScanlators.none { it.isEmpty() }) {
            "Target backup contains a blank excluded scanlator"
        }
        require(excludedScanlators.size == excludedScanlators.toSet().size) {
            "Target backup contains duplicate excluded scanlators"
        }
        return TargetBackupSnapshot(
            series = TargetSeriesEntity(
                localId = series.localId,
                contentType = series.contentType,
                title = series.title,
                author = series.author,
                artist = series.artist,
                description = series.description,
                status = series.status,
                genresJson = json.encodeToString(series.genres),
                createdAt = series.createdAt,
                updatedAt = series.updatedAt,
            ),
            sourceReferences = series.sourceReferences.map { it.toEntity(series.localId) },
            libraryEntry = series.libraryEntry?.toEntity(series.localId),
            chapters = series.chapters.map { it.toEntity(series.localId) },
            chapterStates = series.chapterStates.map { it.toEntity() },
            history = series.history.map { it.toEntity() },
            categories = categories,
            seriesCategories = categoryIds.map {
                ephyra.data.room.target.TargetSeriesCategoryEntity(series.localId, it)
            },
            tracking = series.tracking.map { it.toEntity(series.localId) },
            excludedScanlators = excludedScanlators.map {
                TargetExcludedScanlatorEntity(seriesId = series.localId, scanlator = it)
            },
        )
    }

    private fun TargetTrackingEntity.toBackup() = TargetBackupTracking(
        trackerId = trackerId,
        remoteId = remoteId,
        libraryId = libraryId,
        title = title,
        lastChapterRead = lastChapterRead,
        totalChapters = totalChapters,
        status = status,
        score = score,
        remoteUrl = remoteUrl,
        startedAt = startedAt,
        finishedAt = finishedAt,
        isPrivate = isPrivate,
        updatedAt = updatedAt,
    )

    private fun TargetBackupTracking.toEntity(seriesId: String) = TargetTrackingEntity(
        seriesId = seriesId,
        trackerId = trackerId,
        remoteId = remoteId,
        libraryId = libraryId,
        title = title,
        lastChapterRead = lastChapterRead,
        totalChapters = totalChapters,
        status = status,
        score = score,
        remoteUrl = remoteUrl,
        startedAt = startedAt,
        finishedAt = finishedAt,
        isPrivate = isPrivate,
        updatedAt = updatedAt,
    )

    private fun TargetSeriesSourceEntity.toBackup() = TargetBackupSourceReference(
        sourceId,
        externalId,
        url,
        revision,
        displayTitle,
        thumbnailUrl,
        sourceMetadataJson,
        lastSeenAt,
    )

    private fun TargetCategoryEntity.toBackup() = TargetBackupCategory(
        categoryId = categoryId,
        name = name,
        order = sortOrder,
        flags = flags,
        isSystem = isSystem,
    )

    private fun TargetBackupCategory.toEntity() = TargetCategoryEntity(
        categoryId = categoryId,
        name = name,
        sortOrder = order,
        flags = flags,
        isSystem = isSystem,
    )

    private fun TargetBackupSourceReference.toEntity(seriesId: String) = TargetSeriesSourceEntity(
        seriesId, sourceId, externalId, url, revision, displayTitle, thumbnailUrl, sourceMetadataJson, lastSeenAt,
    )

    private fun TargetLibraryEntryEntity.toBackup() = TargetBackupLibraryEntry(
        addedAt,
        librarySortPosition,
        updatePolicy,
        updateEnabled,
        lastCheckedAt,
        lastChangedAt,
    )

    private fun TargetBackupLibraryEntry.toEntity(seriesId: String) = TargetLibraryEntryEntity(
        seriesId,
        addedAt,
        librarySortPosition,
        updatePolicy,
        updateEnabled,
        lastCheckedAt,
        lastChangedAt,
    )

    private fun TargetChapterEntity.toBackup() = TargetBackupChapter(
        localId, sourceId, externalId, url, title, unitNumber, scanlator, sortKey, revision, publishedAt, fetchedAt,
    )

    private fun TargetBackupChapter.toEntity(seriesId: String) = TargetChapterEntity(
        localId = localId,
        seriesId = seriesId,
        sourceId = sourceId,
        externalId = externalId,
        url = url,
        title = title,
        unitNumber = unitNumber,
        scanlator = scanlator,
        sortKey = sortKey,
        revision = revision,
        publishedAt = publishedAt,
        fetchedAt = fetchedAt,
    )

    private fun TargetChapterStateEntity.toBackup() = TargetBackupChapterState(
        chapterId = chapterId,
        isRead = isRead,
        bookmarked = bookmarked,
        lastPageRead = lastPageRead,
    )

    private fun TargetBackupChapterState.toEntity() = TargetChapterStateEntity(
        chapterId = chapterId,
        isRead = isRead,
        bookmarked = bookmarked,
        lastPageRead = lastPageRead,
    )

    private fun TargetHistoryEntity.toBackup() = TargetBackupHistory(
        targetChapterLocalId = targetChapterLocalId,
        lastReadAt = lastReadAt,
        readDurationMs = readDurationMs,
    )

    private fun TargetBackupHistory.toEntity() = TargetHistoryEntity(
        targetChapterLocalId = targetChapterLocalId,
        lastReadAt = lastReadAt,
        readDurationMs = readDurationMs,
    )
}
