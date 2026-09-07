package ephyra.feature.browse.source.authority

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.domain.chapter.interactor.GenerateAuthorityChapters
import ephyra.domain.content.model.ContentType
import ephyra.domain.manga.interactor.FindContentSource
import ephyra.domain.manga.interactor.GetDuplicateLibraryManga
import ephyra.domain.manga.interactor.GetFavoritesByCanonicalId
import ephyra.domain.manga.interactor.GetMangaByUrlAndSourceId
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.model.MangaWithChapterCount
import ephyra.domain.manga.model.mergedAlternativeTitles
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.interactor.InsertTrack
import ephyra.domain.track.interactor.TrackerListImporter
import ephyra.domain.track.model.Track
import ephyra.domain.track.model.TrackSearch
import ephyra.domain.track.service.Tracker
import ephyra.domain.track.service.TrackerManager
import ephyra.presentation.core.udf.BaseUdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import logcat.LogPriority
import javax.inject.Inject

@HiltViewModel
class AuthoritySearchViewModel @Inject constructor(
    private val trackerManager: TrackerManager,
    private val getFavoritesByCanonicalId: GetFavoritesByCanonicalId,
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga,
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId,
    private val networkToLocalManga: NetworkToLocalManga,
    private val updateManga: UpdateManga,
    private val insertTrack: InsertTrack,
    private val generateAuthorityChapters: GenerateAuthorityChapters,
    private val findContentSource: FindContentSource,
) : BaseUdfViewModel<AuthoritySearchState, AuthoritySearchScreenEvent, Nothing>(AuthoritySearchState()) {

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val trackers = withIOContext {
                trackerManager.getAll(AddTracks.TRACKER_CANONICAL_PREFIXES.keys)
                    .filter {
                        AddTracks.TRACKER_CANONICAL_PREFIXES.containsKey(it.id) &&
                            (it.isLoggedIn() || it.id in AddTracks.TRACKERS_WITH_PUBLIC_SEARCH)
                    }
                    .toImmutableList()
            }
            updateState { state ->
                state.copy(
                    availableTrackers = trackers,
                    selectedTracker = state.selectedTracker ?: trackers.firstOrNull(),
                )
            }
        }
    }

    fun trackersForFilter(contentType: ContentType): ImmutableList<Tracker> {
        val available = currentState.availableTrackers
        if (contentType == ContentType.UNKNOWN) return available
        val validIds = AddTracks.trackersForContentType(contentType)
        return available.filter { it.id in validIds }.toImmutableList()
    }

    override fun onEvent(event: AuthoritySearchScreenEvent) {
        when (event) {
            is AuthoritySearchScreenEvent.SelectTracker -> selectTracker(event.tracker)
            is AuthoritySearchScreenEvent.SetContentTypeFilter -> setContentTypeFilter(event.contentType)
            is AuthoritySearchScreenEvent.Search -> search(event.query)
            is AuthoritySearchScreenEvent.AddToLibrary -> addToLibrary(event.result)
            is AuthoritySearchScreenEvent.MergeWithExisting -> mergeWithExisting(event.candidate)
            is AuthoritySearchScreenEvent.SkipMerge -> skipMerge()
            is AuthoritySearchScreenEvent.DismissMergePrompt -> dismissMergePrompt()
            is AuthoritySearchScreenEvent.DismissSourcePrompt -> dismissSourcePrompt()
            is AuthoritySearchScreenEvent.SelectResult -> selectResult(event.result)
            is AuthoritySearchScreenEvent.DismissDetail -> dismissDetail()
            is AuthoritySearchScreenEvent.RetrySearch -> retrySearch()
        }
    }

    private fun selectTracker(tracker: Tracker) {
        updateState {
            it.copy(
                selectedTracker = tracker,
                results = persistentListOf(),
                query = "",
            )
        }
    }

    private fun setContentTypeFilter(contentType: ContentType) {
        val filteredTrackers = trackersForFilter(contentType)
        val currentTracker = currentState.selectedTracker

        val newTracker = if (currentTracker != null && currentTracker in filteredTrackers) {
            currentTracker
        } else {
            filteredTrackers.firstOrNull()
        }

        updateState {
            it.copy(
                contentTypeFilter = contentType,
                selectedTracker = newTracker,
                results = persistentListOf(),
            )
        }
    }

    private fun search(query: String) {
        val tracker = currentState.selectedTracker ?: return
        searchJob?.cancel()
        updateState {
            it.copy(
                query = query,
                isSearching = true,
                results = persistentListOf(),
                searchError = null,
            )
        }
        searchJob = viewModelScope.launch {
            try {
                val results = withIOContext { tracker.search(query) }
                updateState {
                    it.copy(
                        results = results.toImmutableList(),
                        isSearching = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Authority search failed: query=$query" }
                updateState {
                    it.copy(
                        isSearching = false,
                        results = persistentListOf(),
                        searchError = e.message ?: e.javaClass.simpleName,
                    )
                }
            }
        }
    }

    private fun addToLibrary(result: TrackSearch) {
        val tracker = currentState.selectedTracker ?: return
        val prefix = AddTracks.TRACKER_CANONICAL_PREFIXES[tracker.id] ?: return
        val canonicalId = "$prefix:${result.remote_id}"

        updateState { it.copy(addingCanonicalIds = it.addingCanonicalIds + canonicalId) }

        viewModelScope.launch {
            try {
                withIOContext {
                    val existingByCanonical = getFavoritesByCanonicalId.await(canonicalId, -1L)
                    if (existingByCanonical.isNotEmpty()) {
                        updateState { state ->
                            state.copy(
                                addedCanonicalIds = state.addedCanonicalIds + canonicalId,
                                addingCanonicalIds = state.addingCanonicalIds - canonicalId,
                            )
                        }
                        return@withIOContext
                    }

                    val unpairedMatches = getDuplicateLibraryManga.invoke(result.title)
                        .filter { it.manga.canonicalId == null }
                    if (unpairedMatches.isNotEmpty()) {
                        updateState { state ->
                            state.copy(
                                mergePrompt = MergePromptInfo(
                                    result = result,
                                    canonicalId = canonicalId,
                                    tracker = tracker,
                                    candidates = unpairedMatches.toImmutableList(),
                                ),
                                addingCanonicalIds = state.addingCanonicalIds - canonicalId,
                            )
                        }
                        return@withIOContext
                    }

                    createAuthorityEntry(result, tracker, canonicalId)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to add authority manga: canonical_id=$canonicalId" }
            } finally {
                updateState { it.copy(addingCanonicalIds = it.addingCanonicalIds - canonicalId) }
            }
        }
    }

    private fun mergeWithExisting(candidate: MangaWithChapterCount) {
        val prompt = currentState.mergePrompt ?: return
        viewModelScope.launch {
            try {
                withIOContext {
                    val result = prompt.result
                    val manga = candidate.manga
                    updateManga.await(
                        MangaUpdate(
                            id = manga.id,
                            canonicalId = prompt.canonicalId,
                            description = result.summary.takeIf {
                                it.isNotBlank() && manga.description.isNullOrBlank()
                            },
                            author = result.authors.joinToString(", ").takeIf {
                                it.isNotBlank() && manga.author.isNullOrBlank()
                            },
                            artist = result.artists.joinToString(", ").takeIf {
                                it.isNotBlank() && manga.artist.isNullOrBlank()
                            },
                            thumbnailUrl = result.cover_url.takeIf {
                                it.isNotBlank() && manga.thumbnailUrl.isNullOrBlank()
                            },
                            contentType = ContentType.fromPublishingType(result.publishing_type).takeIf {
                                it != ContentType.UNKNOWN && manga.contentType == ContentType.UNKNOWN
                            },
                        ),
                    )
                    logcat(LogPriority.INFO) {
                        "Merged '${manga.title}' with canonical_id=${prompt.canonicalId}"
                    }

                    mergeAlternativeTitles(manga, result)

                    if (prompt.tracker.isLoggedIn()) {
                        val track = Track(
                            id = 0L,
                            mangaId = manga.id,
                            trackerId = prompt.tracker.id,
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
                            isPrivate = result.isPrivate,
                        )
                        insertTrack.await(track)
                    }

                    updateState { state ->
                        state.copy(
                            addedCanonicalIds = state.addedCanonicalIds + prompt.canonicalId,
                            mergePrompt = null,
                        )
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to merge with existing manga" }
                updateState { it.copy(mergePrompt = null) }
            }
        }
    }

    private fun skipMerge() {
        val prompt = currentState.mergePrompt ?: return
        updateState { it.copy(mergePrompt = null) }
        viewModelScope.launch {
            try {
                withIOContext {
                    createAuthorityEntry(prompt.result, prompt.tracker, prompt.canonicalId)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to create authority entry after skip merge" }
            }
        }
    }

    private fun dismissMergePrompt() {
        updateState { it.copy(mergePrompt = null) }
    }

    private suspend fun createAuthorityEntry(
        result: TrackSearch,
        tracker: Tracker,
        canonicalId: String,
    ) {
        val existingByUrl = getMangaByUrlAndSourceId.await(
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
            val inserted = networkToLocalManga(listOf(newManga))
            inserted.firstOrNull() ?: return
        }

        val inferredType = ContentType.fromPublishingType(result.publishing_type)
        updateManga.await(
            MangaUpdate(
                id = manga.id,
                favorite = true,
                dateAdded = System.currentTimeMillis(),
                canonicalId = canonicalId,
                contentType = inferredType.takeIf { it != ContentType.UNKNOWN },
            ),
        )

        if (tracker.isLoggedIn()) {
            val track = Track(
                id = 0L,
                mangaId = manga.id,
                trackerId = tracker.id,
                remoteId = result.remote_id,
                libraryId = null,
                title = result.title,
                lastChapterRead = 0.0,
                totalChapters = result.total_chapters,
                status = 0L,
                score = 0.0,
                remoteUrl = result.tracking_url,
                startDate = 0L,
                finishDate = 0L,
                isPrivate = result.isPrivate,
            )
            insertTrack.await(track)
        }

        if (result.total_chapters > 0) {
            generateAuthorityChapters.await(
                mangaId = manga.id,
                totalChapters = result.total_chapters.toInt(),
                lastChapterRead = 0,
            )
        }

        updateState { state ->
            state.copy(
                addedCanonicalIds = state.addedCanonicalIds + canonicalId,
                sourcePromptManga = SourcePromptInfo(
                    title = result.title,
                    mangaId = manga.id,
                    isSearching = true,
                ),
            )
        }

        autoSearchForSources(manga)
    }

    private fun autoSearchForSources(manga: Manga) {
        viewModelScope.launch {
            try {
                val matches = withIOContext {
                    findContentSource.findSources(manga, maxResults = 5, deepSearch = false)
                }
                val currentPrompt = currentState.sourcePromptManga ?: return@launch
                if (currentPrompt.mangaId == manga.id) {
                    updateState {
                        it.copy(
                            sourcePromptManga = currentPrompt.copy(
                                sourceMatches = matches.toImmutableList(),
                                isSearching = false,
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Auto-search for sources failed" }
                val currentPrompt = currentState.sourcePromptManga ?: return@launch
                if (currentPrompt.mangaId == manga.id) {
                    updateState {
                        it.copy(sourcePromptManga = currentPrompt.copy(isSearching = false))
                    }
                }
            }
        }
    }

    private fun dismissSourcePrompt() {
        updateState { it.copy(sourcePromptManga = null) }
    }

    private fun selectResult(result: TrackSearch) {
        updateState { it.copy(selectedResult = result) }
    }

    private fun dismissDetail() {
        updateState { it.copy(selectedResult = null) }
    }

    private fun retrySearch() {
        val query = currentState.query
        if (query.isNotBlank()) search(query)
    }

    private suspend fun mergeAlternativeTitles(manga: Manga, result: TrackSearch) {
        val newTitles = buildList {
            if (result.title.isNotBlank()) add(result.title)
            addAll(result.alternative_titles)
        }
        val merged = manga.mergedAlternativeTitles(newTitles) ?: return
        updateManga.await(
            MangaUpdate(id = manga.id, alternativeTitles = merged),
        )
    }
}

@Immutable
data class AuthoritySearchState(
    val availableTrackers: ImmutableList<Tracker> = persistentListOf(),
    val selectedTracker: Tracker? = null,
    val contentTypeFilter: ContentType = ContentType.UNKNOWN,
    val query: String = "",
    val isSearching: Boolean = false,
    val results: ImmutableList<TrackSearch> = persistentListOf(),
    val searchError: String? = null,
    val addingCanonicalIds: Set<String> = emptySet(),
    val addedCanonicalIds: Set<String> = emptySet(),
    val mergePrompt: MergePromptInfo? = null,
    val sourcePromptManga: SourcePromptInfo? = null,
    val selectedResult: TrackSearch? = null,
) {
    val filteredResults: ImmutableList<TrackSearch>
        get() = if (contentTypeFilter == ContentType.UNKNOWN) {
            results
        } else {
            results.filter {
                ContentType.fromPublishingType(it.publishing_type) == contentTypeFilter
            }.toImmutableList()
        }
}

data class MergePromptInfo(
    val result: TrackSearch,
    val canonicalId: String,
    val tracker: Tracker,
    val candidates: ImmutableList<MangaWithChapterCount>,
)

data class SourcePromptInfo(
    val title: String,
    val mangaId: Long,
    val isSearching: Boolean,
    val sourceMatches: ImmutableList<FindContentSource.SourceMatch> = persistentListOf(),
)
