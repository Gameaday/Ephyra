package ephyra.domain.track.interactor

import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.domain.chapter.interactor.GenerateAuthorityChapters
import ephyra.domain.content.model.ContentType
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.track.model.Track
import ephyra.domain.track.model.TrackSearch
import ephyra.domain.track.service.ReadingListTracker
import ephyra.domain.track.service.Tracker
import ephyra.domain.track.service.TrackerManager
import kotlinx.coroutines.yield
import logcat.LogPriority

/**
 * Imports a user's reading list from a tracker (AniList, MyAnimeList) into the local library.
 */
class TrackerListImporter(
    private val mangaRepository: MangaRepository,
    private val insertTrack: InsertTrack,
    private val trackerManager: TrackerManager,
    private val generateAuthorityChapters: GenerateAuthorityChapters,
) {

    /**
     * Imports manga from the user's MyAnimeList reading list.
     */
    suspend fun importFromMal(): ImportResult = importFromTracker(TrackerManager.MYANIMELIST)

    /**
     * Imports manga from the user's AniList reading list.
     */
    suspend fun importFromAnilist(): ImportResult = importFromTracker(TrackerManager.ANILIST)

    /**
     * Imports manga from any tracker supporting [ReadingListTracker].
     */
    suspend fun importFromTracker(trackerId: Long): ImportResult = withIOContext {
        val tracker = trackerManager.get(trackerId)
            ?: return@withIOContext ImportResult(error = "Tracker not found")
        if (!tracker.isLoggedIn()) {
            return@withIOContext ImportResult(error = "Not logged in to ${tracker.name}")
        }
        if (tracker !is ReadingListTracker) {
            return@withIOContext ImportResult(error = "${tracker.name} does not support reading list import")
        }

        val prefix = AddTracks.TRACKER_CANONICAL_PREFIXES[tracker.id]
            ?: return@withIOContext ImportResult(error = "No canonical prefix configured for ${tracker.name}")

        val listItems = try {
            tracker.getUserReadingList()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to fetch reading list from ${tracker.name}" }
            return@withIOContext ImportResult(error = e.message ?: "Failed to fetch reading list")
        }

        logcat(LogPriority.INFO) { "${tracker.name} import: fetched ${listItems.size} items from reading list" }

        var imported = 0
        var skipped = 0
        var failed = 0

        for (item in listItems) {
            try {
                val success = importSingleEntry(tracker, prefix, item)
                if (success) imported++ else skipped++
            } catch (e: Exception) {
                failed++
                logcat(LogPriority.WARN, e) { "${tracker.name} import: failed to import '${item.title}'" }
            }
            yield()
        }

        ImportResult(imported = imported, skipped = skipped, failed = failed)
    }

    private suspend fun importSingleEntry(
        tracker: Tracker,
        prefix: String,
        item: TrackSearch,
    ): Boolean {
        if (item.remote_id <= 0) return false
        val canonicalId = "$prefix:${item.remote_id}"

        val existingManga = mangaRepository.getMangaByUrlAndSourceId(
            canonicalId,
            AUTHORITY_SOURCE_ID,
        )

        val manga = if (existingManga != null) {
            if (!existingManga.favorite) {
                mangaRepository.update(
                    MangaUpdate(
                        id = existingManga.id,
                        favorite = true,
                        dateAdded = System.currentTimeMillis(),
                    ),
                )
            }
            existingManga
        } else {
            val inferredType = ContentType.fromPublishingType(item.publishing_type)
            val newManga = Manga.create().copy(
                url = canonicalId,
                title = item.title,
                source = AUTHORITY_SOURCE_ID,
                thumbnailUrl = item.cover_url.ifBlank { null },
                artist = item.artists.joinToString(", ").ifBlank { null },
                author = item.authors.joinToString(", ").ifBlank { null },
                description = item.summary.ifBlank { null },
                favorite = true,
                initialized = true,
                dateAdded = System.currentTimeMillis(),
                canonicalId = canonicalId,
                contentType = inferredType.takeIf { it != ContentType.UNKNOWN } ?: ContentType.MANGA,
            )
            val inserted = mangaRepository.insertNetworkManga(listOf(newManga))
            inserted.firstOrNull() ?: return false
        }

        // Insert or link track record
        val track = Track(
            id = 0L,
            mangaId = manga.id,
            trackerId = tracker.id,
            remoteId = item.remote_id,
            libraryId = null,
            title = item.title,
            lastChapterRead = item.last_chapter_read,
            totalChapters = item.total_chapters,
            status = item.status,
            score = item.score,
            remoteUrl = item.tracking_url,
            startDate = item.started_reading_date,
            finishDate = item.finished_reading_date,
            isPrivate = item.isPrivate,
        )
        insertTrack.await(track)

        // Generate placeholder authority chapters if total chapters known
        if (item.total_chapters > 0) {
            generateAuthorityChapters.await(
                mangaId = manga.id,
                totalChapters = item.total_chapters.toInt(),
                lastChapterRead = item.last_chapter_read.toInt(),
            )
        }

        return true
    }

    data class ImportResult(
        val imported: Int = 0,
        val skipped: Int = 0,
        val failed: Int = 0,
        val error: String? = null,
    ) {
        val isSuccess get() = error == null
        val total get() = imported + skipped + failed
    }

    companion object {
        const val AUTHORITY_SOURCE_ID = -1L
    }
}
