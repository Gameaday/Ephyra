package ephyra.feature.browse.source.authority

import ephyra.domain.manga.model.ContentType
import ephyra.domain.manga.model.MangaWithChapterCount
import ephyra.domain.track.model.TrackSearch
import ephyra.domain.track.service.Tracker

/**
 * All user intents for the Authority (Discover) screen.
 * [AuthoritySearchScreenModel.onEvent] is the sole external mutation entry-point.
 *
 * Value-returning accessors remain public:
 *   • `trackersForFilter()` — pure query, returns an [ImmutableList] used inline by the composable
 */
sealed interface AuthoritySearchScreenEvent {
    data class SelectTracker(val tracker: Tracker) : AuthoritySearchScreenEvent
    data class SetContentTypeFilter(val contentType: ContentType) : AuthoritySearchScreenEvent
    data class Search(val query: String) : AuthoritySearchScreenEvent
    data class AddToLibrary(val result: TrackSearch) : AuthoritySearchScreenEvent
    data class MergeWithExisting(val candidate: MangaWithChapterCount) : AuthoritySearchScreenEvent
    data object SkipMerge : AuthoritySearchScreenEvent
    data object DismissMergePrompt : AuthoritySearchScreenEvent
    data object DismissSourcePrompt : AuthoritySearchScreenEvent
    data class SelectResult(val result: TrackSearch) : AuthoritySearchScreenEvent
    data object DismissDetail : AuthoritySearchScreenEvent
    data object RetrySearch : AuthoritySearchScreenEvent
}
