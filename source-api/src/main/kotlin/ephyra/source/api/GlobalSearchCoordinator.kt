package ephyra.source.api

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Product-facing global search coordinator for target-native sources.
 *
 * It owns query policy and delegates execution/cancellation to one [SearchSession]. It never
 * converts source results into legacy `Manga`/`CatalogueSource` models; a later UI adapter owns
 * that explicit boundary.
 */
class GlobalSearchCoordinator(
    private val scope: CoroutineScope,
    private val registry: NativeSourceRegistry,
    private val dispatcher: CoroutineDispatcher? = null,
    private val config: SearchSessionConfig = SearchSessionConfig(),
) {
    private val mutableState = MutableStateFlow(GlobalSearchState())
    val state: StateFlow<GlobalSearchState> = mutableState.asStateFlow()
    private val session = SearchSession(scope, dispatcher ?: kotlinx.coroutines.Dispatchers.IO)
    private var job: Job? = null

    init {
        scope.launch {
            session.state.collect { searchState ->
                mutableState.update { current -> current.copy(search = searchState) }
            }
        }
    }

    fun start(query: String, sourceIds: Set<SourceId> = emptySet()): Job {
        require(query.isNotBlank()) { "Search query must not be blank" }
        val gateways = registry.gateways.filter { sourceIds.isEmpty() || it.descriptor.id in sourceIds }
        job = session.start(SourceSearchRequest(query), gateways, config)
        mutableState.value = GlobalSearchState(query = query, sourceIds = sourceIds, search = session.state.value)
        return requireNotNull(job)
    }

    fun cancel() {
        session.cancel()
        job = null
    }

    /** The session's state is the authoritative execution state; this is a read-only bridge. */
    fun currentState(): GlobalSearchState = GlobalSearchState(
        query = mutableState.value.query,
        sourceIds = mutableState.value.sourceIds,
        search = session.state.value,
    )
}

data class GlobalSearchState(
    val query: String = "",
    val sourceIds: Set<SourceId> = emptySet(),
    val search: SearchState = SearchState(),
)
