package ephyra.data.track

import ephyra.core.common.util.Result
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentStatus
import ephyra.domain.content.model.ContentType
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.model.Track
import ephyra.domain.track.repository.TrackRepository
import ephyra.domain.track.service.TrackerManager
import ephyra.domain.track.service.TrackingService

class TrackingServiceImpl(
    private val trackerManager: TrackerManager,
    private val addTracks: AddTracks,
    private val trackRepository: TrackRepository,
) : TrackingService {

    override suspend fun search(query: String): Result<List<ContentItem>> {
        return try {
            val results = mutableListOf<ContentItem>()
            trackerManager.loggedInTrackers().forEach { tracker ->
                val searchResults = tracker.search(query)
                searchResults.forEach { searchResult ->
                    results.add(
                        ContentItem(
                            id = -1L,
                            sourceId = tracker.id,
                            url = searchResult.tracking_url,
                            title = searchResult.title,
                            author = searchResult.authors.firstOrNull(),
                            artist = searchResult.artists.firstOrNull(),
                            description = searchResult.summary,
                            genres = searchResult.genres,
                            status = ContentStatus.Unknown,
                            thumbnailUrl = searchResult.cover_url,
                            contentType = ContentType.MANGA,
                            metadata = mapOf(
                                "remote_id" to searchResult.remote_id.toString(),
                                "tracker_id" to tracker.id.toString(),
                            ),
                        ),
                    )
                }
            }
            Result.Success(results)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun linkTracker(contentItem: ContentItem, trackerId: Long): Result<Boolean> {
        return try {
            val tracker =
                trackerManager.get(trackerId) ?: return Result.Error(Exception("Tracker not found: $trackerId"))
            val remoteId = contentItem.metadata["remote_id"]?.toLongOrNull() ?: -1L
            val track = Track(
                id = -1L,
                mangaId = contentItem.id,
                trackerId = trackerId,
                remoteId = remoteId,
                libraryId = null,
                title = contentItem.title,
                lastChapterRead = 0.0,
                totalChapters = 0,
                status = 0,
                score = -1.0,
                remoteUrl = contentItem.url,
                startDate = 0,
                finishDate = 0,
                isPrivate = false,
            )
            addTracks.bind(tracker, track, contentItem.id)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun syncProgress(contentItem: ContentItem, progress: Long): Result<Boolean> {
        return try {
            val tracks = trackRepository.getTracksByMangaId(contentItem.id)
            if (tracks.isEmpty()) return Result.Success(false)

            tracks.forEach { track ->
                val service = trackerManager.get(track.trackerId)
                if (service != null && service.isLoggedIn() && progress > track.lastChapterRead) {
                    val updatedTrack = service.refresh(track)
                        .copy(lastChapterRead = progress.toDouble())
                    service.update(updatedTrack, true)
                    trackRepository.insert(updatedTrack)
                }
            }
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun fetchStatus(contentItem: ContentItem): Result<ContentItem> {
        return try {
            val tracks = trackRepository.getTracksByMangaId(contentItem.id)
            if (tracks.isEmpty()) return Result.Success(contentItem)

            var updatedItem = contentItem
            tracks.forEach { track ->
                val service = trackerManager.get(track.trackerId)
                if (service != null && service.isLoggedIn()) {
                    val refreshed = service.refresh(track)
                    trackRepository.insert(refreshed)
                    // Map remote tracking status back if desired
                    if (refreshed.trackerId == TrackerManager.MYANIMELIST ||
                        refreshed.trackerId == TrackerManager.ANILIST
                    ) {
                        updatedItem = updatedItem.copy(
                            status = when (refreshed.status) {
                                1L -> ContentStatus.Ongoing // Reading
                                2L -> ContentStatus.Completed // Completed
                                3L -> ContentStatus.Hiatus // On Hold
                                4L -> ContentStatus.Cancelled // Dropped
                                else -> updatedItem.status
                            },
                        )
                    }
                }
            }
            Result.Success(updatedItem)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
