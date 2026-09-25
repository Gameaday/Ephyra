package ephyra.feature.browse.source.globalsearch

import ephyra.domain.content.model.ContentType
import ephyra.source.api.GlobalSearchState
import ephyra.source.api.SearchFailure
import ephyra.source.api.SearchPhase
import ephyra.source.api.SourceCapability
import ephyra.source.api.SourceContentItem
import ephyra.source.api.SourceId
import ephyra.source.api.SourceSearchState

/** A search row that is safe for a target-native UI to render. */
data class TargetSearchRow(
    val key: String,
    val representative: SourceContentItem,
    val sourceIds: List<SourceId>,
) {
    val title: String get() = representative.title
    val thumbnailUrl: String? get() = representative.thumbnailUrl
    val contentType: ContentType get() = representative.contentType
}

/** Product-facing status for one source participating in the current search. */
sealed interface TargetSourceSearchStatus {
    data object Pending : TargetSourceSearchStatus
    data object Running : TargetSourceSearchStatus
    data class Succeeded(val itemCount: Int) : TargetSourceSearchStatus
    data object Empty : TargetSourceSearchStatus
    data class Unsupported(val capability: SourceCapability) : TargetSourceSearchStatus
    data class Failed(val failure: SearchFailure) : TargetSourceSearchStatus
}

data class TargetSearchPresentation(
    val query: String = "",
    val rows: List<TargetSearchRow> = emptyList(),
    val sources: List<Pair<SourceId, TargetSourceSearchStatus>> = emptyList(),
    val phase: SearchPhase = SearchPhase.IDLE,
    val hasPartialResults: Boolean = false,
)

/** Pure, deterministic mapping from target search state to product presentation state. */
object TargetSearchMapper {
    fun map(state: GlobalSearchState): TargetSearchPresentation {
        val rows = state.search.mergedItems.map { merged ->
            TargetSearchRow(
                key = "${merged.representative.sourceId.value}\u0000${merged.representative.url}",
                representative = merged.representative,
                sourceIds = merged.sourceIds,
            )
        }
        val sources = state.search.sources.map { (id, sourceState) ->
            val status = when (sourceState) {
                SourceSearchState.Pending -> TargetSourceSearchStatus.Pending
                SourceSearchState.Running -> TargetSourceSearchStatus.Running
                is SourceSearchState.Succeeded -> TargetSourceSearchStatus.Succeeded(sourceState.page.items.size)
                SourceSearchState.Empty -> TargetSourceSearchStatus.Empty
                is SourceSearchState.Unsupported -> TargetSourceSearchStatus.Unsupported(sourceState.capability)
                is SourceSearchState.Failed -> TargetSourceSearchStatus.Failed(sourceState.failure)
            }
            id to status
        }
        return TargetSearchPresentation(
            query = state.query,
            rows = rows,
            sources = sources,
            phase = state.search.phase,
            hasPartialResults = state.search.hasPartialResults,
        )
    }
}
