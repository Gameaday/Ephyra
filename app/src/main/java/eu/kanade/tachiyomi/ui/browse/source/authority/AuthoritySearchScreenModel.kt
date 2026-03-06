package eu.kanade.tachiyomi.ui.browse.source.authority

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.interactor.TrackerListImporter
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GenerateAuthorityChapters
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AuthoritySearchScreenModel(
    private val trackerManager: TrackerManager = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    private val generateAuthorityChapters: GenerateAuthorityChapters = Injekt.get(),
) : StateScreenModel<AuthoritySearchState>(AuthoritySearchState()) {

    /** Logged-in trackers that support authority search (canonical ID assignment). */
    val availableTrackers: ImmutableList<Tracker> = trackerManager.trackers
        .filter { it.isLoggedIn && AddTracks.TRACKER_CANONICAL_PREFIXES.containsKey(it.id) }
        .toImmutableList()

    init {
        if (availableTrackers.isNotEmpty()) {
            mutableState.value = mutableState.value.copy(selectedTracker = availableTrackers.first())
        }
    }

    fun selectTracker(tracker: Tracker) {
        mutableState.value = mutableState.value.copy(
            selectedTracker = tracker,
            results = persistentListOf(),
            query = "",
        )
    }

    fun search(query: String) {
        val tracker = mutableState.value.selectedTracker ?: return
        mutableState.value = mutableState.value.copy(
            query = query,
            isSearching = true,
            results = persistentListOf(),
        )
        screenModelScope.launch {
            try {
                val results = withIOContext { tracker.search(query) }
                mutableState.value = mutableState.value.copy(
                    results = results.toImmutableList(),
                    isSearching = false,
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Authority search failed: query=$query" }
                mutableState.value = mutableState.value.copy(
                    isSearching = false,
                    results = persistentListOf(),
                )
            }
        }
    }

    /**
     * Adds a search result to the library as an authority-only manga entry with tracker binding.
     */
    fun addToLibrary(result: TrackSearch) {
        val tracker = mutableState.value.selectedTracker ?: return
        val prefix = AddTracks.TRACKER_CANONICAL_PREFIXES[tracker.id] ?: return
        val canonicalId = "$prefix:${result.remote_id}"

        screenModelScope.launch {
            try {
                withIOContext {
                    // Reuse the same dedup logic as TrackerListImporter
                    val existingByCanonical = mangaRepository.getFavoritesByCanonicalId(canonicalId, -1L)
                    if (existingByCanonical.isNotEmpty()) {
                        // Already in library — just mark as added in UI
                        mutableState.value = mutableState.value.copy(
                            addedCanonicalIds = mutableState.value.addedCanonicalIds + canonicalId,
                        )
                        return@withIOContext
                    }

                    val existingByUrl = mangaRepository.getMangaByUrlAndSourceId(
                        canonicalId,
                        TrackerListImporter.AUTHORITY_SOURCE_ID,
                    )

                    val manga = if (existingByUrl != null) {
                        existingByUrl
                    } else {
                        val newManga = Manga.create().copy(
                            url = canonicalId,
                            title = result.title,
                            source = TrackerListImporter.AUTHORITY_SOURCE_ID,
                            thumbnailUrl = result.cover_url.ifBlank { null },
                            artist = result.artists.joinToString(", ").ifBlank { null },
                            author = result.authors.joinToString(", ").ifBlank { null },
                            description = result.summary.ifBlank { null },
                            initialized = true,
                        )
                        val inserted = mangaRepository.insertNetworkManga(listOf(newManga))
                        inserted.firstOrNull() ?: return@withIOContext
                    }

                    // Mark as favourite
                    mangaRepository.update(
                        MangaUpdate(
                            id = manga.id,
                            favorite = true,
                            dateAdded = System.currentTimeMillis(),
                            canonicalId = canonicalId,
                        ),
                    )

                    // Bind the tracker
                    val track = Track(
                        id = 0L,
                        mangaId = manga.id,
                        trackerId = tracker.id,
                        remoteId = result.remote_id,
                        libraryId = null,
                        title = result.title,
                        lastChapterRead = result.last_chapter_read,
                        totalChapters = result.total_chapters,
                        status = result.status,
                        score = result.score,
                        remoteUrl = result.tracking_url,
                        startDate = result.started_reading_date,
                        finishDate = result.finished_reading_date,
                        private = result.private,
                    )
                    insertTrack.await(track)

                    // Generate authority chapters so the user can mark progress
                    if (result.total_chapters > 0) {
                        generateAuthorityChapters.await(
                            mangaId = manga.id,
                            totalChapters = result.total_chapters.toInt(),
                            lastChapterRead = result.last_chapter_read.toInt(),
                        )
                    }

                    mutableState.value = mutableState.value.copy(
                        addedCanonicalIds = mutableState.value.addedCanonicalIds + canonicalId,
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to add authority manga: canonical_id=$canonicalId" }
            }
        }
    }
}

data class AuthoritySearchState(
    val selectedTracker: Tracker? = null,
    val query: String = "",
    val isSearching: Boolean = false,
    val results: ImmutableList<TrackSearch> = persistentListOf(),
    /** Canonical IDs of manga already added to the library in this session. */
    val addedCanonicalIds: Set<String> = emptySet(),
)
