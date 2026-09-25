package ephyra.source.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

/** Configuration for one source-search execution. */
data class SearchSessionConfig(
    val maxConcurrency: Int = 5,
    val sourceTimeoutMillis: Long = 10_000L,
) {
    init {
        require(maxConcurrency > 0) { "maxConcurrency must be positive" }
        require(sourceTimeoutMillis > 0) { "sourceTimeoutMillis must be positive" }
    }
}

enum class SearchFailureKind { TRANSIENT, PERMANENT, RATE_LIMITED }

data class SearchFailure(
    val kind: SearchFailureKind,
    val message: String,
    val retryAfterMillis: Long? = null,
)

sealed interface SourceSearchState {
    data object Pending : SourceSearchState
    data object Running : SourceSearchState
    data class Succeeded(val page: SourcePage<SourceContentItem>) : SourceSearchState
    data object Empty : SourceSearchState
    data class Unsupported(val capability: SourceCapability) : SourceSearchState
    data class Failed(val failure: SearchFailure) : SourceSearchState
}

enum class SearchPhase { IDLE, RUNNING, COMPLETED }

data class SearchState(
    val generation: Long = 0L,
    val query: String = "",
    val phase: SearchPhase = SearchPhase.IDLE,
    val sourceOrder: Map<SourceId, Int> = emptyMap(),
    val sources: Map<SourceId, SourceSearchState> = emptyMap(),
    val mergedItems: List<MergedSourceItem> = emptyList(),
) {
    val completedCount: Int
        get() = sources.values.count {
            it is SourceSearchState.Succeeded || it is SourceSearchState.Empty ||
                it is SourceSearchState.Unsupported || it is SourceSearchState.Failed
        }

    val hasPartialResults: Boolean get() = mergedItems.isNotEmpty()
}

data class SourceSearchCandidate(
    val item: SourceContentItem,
    val sourceOrder: Int,
)

data class MergedSourceItem(
    val representative: SourceContentItem,
    val sourceIds: List<SourceId>,
)

/** Exact-title aggregation. Fuzzy matching and ranking belong to a later discovery task. */
object SearchResultAggregator {
    fun aggregate(candidates: List<SourceSearchCandidate>): List<MergedSourceItem> {
        val groups = linkedMapOf<String, MergedSourceItem>()
        val seenWithinSource = HashSet<Pair<SourceId, String>>()
        candidates.sortedWith(compareBy<SourceSearchCandidate> { it.sourceOrder }.thenBy { it.item.title })
            .forEach { candidate ->
                val sourceKey = candidate.item.sourceId to (candidate.item.externalId ?: candidate.item.url)
                if (!seenWithinSource.add(sourceKey)) return@forEach
                val key = normalizedKey(candidate.item)
                if (key.isEmpty()) {
                    groups["${candidate.item.sourceId.value}\u0000${candidate.item.url}"] =
                        MergedSourceItem(candidate.item, listOf(candidate.item.sourceId))
                } else {
                    val existing = groups[key]
                    if (existing == null) {
                        groups[key] = MergedSourceItem(candidate.item, listOf(candidate.item.sourceId))
                    } else if (existing.sourceIds.none { it == candidate.item.sourceId }) {
                        groups[key] = existing.copy(sourceIds = existing.sourceIds + candidate.item.sourceId)
                    }
                }
            }
        return groups.values.toList()
    }

    private fun normalizedKey(item: SourceContentItem): String =
        item.title.trim().lowercase()
            .filter { it.isLetterOrDigit() }
            .let { "${item.contentType.name}:$it" }
}

/** Owns one progressive search execution. It never writes persistence or UI state directly. */
class SearchSession(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val generation = AtomicLong(0L)
    private var job: Job? = null
    private val mutableState = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = mutableState.asStateFlow()

    fun start(
        request: SourceSearchRequest,
        gateways: List<SourceGateway>,
        config: SearchSessionConfig = SearchSessionConfig(),
    ): Job {
        require(request.query.isNotBlank()) { "Search query must not be blank" }
        require(gateways.map { it.descriptor.id }.distinct().size == gateways.size) {
            "Search gateways must have unique source identities"
        }
        job?.cancel()
        val currentGeneration = generation.incrementAndGet()
        mutableState.value = SearchState(
            generation = currentGeneration,
            query = request.query,
            phase = SearchPhase.RUNNING,
            sourceOrder = gateways.mapIndexed { index, gateway -> gateway.descriptor.id to index }.toMap(),
            sources = gateways.associate { it.descriptor.id to SourceSearchState.Pending },
        )
        val started = scope.launch(dispatcher) {
            val semaphore = Semaphore(config.maxConcurrency)
            gateways.map { gateway ->
                launch {
                    val id = gateway.descriptor.id
                    if (!gateway.descriptor.supports(SourceCapability.SEARCH)) {
                        update(currentGeneration) {
                            it.withSource(id, SourceSearchState.Unsupported(SourceCapability.SEARCH))
                        }
                        return@launch
                    }
                    update(currentGeneration) { it.withSource(id, SourceSearchState.Running) }
                    semaphore.withPermit {
                        val result: SourceResult<SourcePage<SourceContentItem>>? = try {
                            withTimeoutOrNull(config.sourceTimeoutMillis) { gateway.search(request) }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Throwable) {
                            SourceResult.PermanentFailure(failure.message ?: "Source failed", failure)
                        }
                        val sourceState = when (result) {
                            null -> SourceSearchState.Failed(
                                SearchFailure(SearchFailureKind.TRANSIENT, "Source timed out"),
                            )
                            is SourceResult.Success -> SourceSearchState.Succeeded(result.value)
                            SourceResult.Empty -> SourceSearchState.Empty
                            is SourceResult.Unsupported -> SourceSearchState.Unsupported(result.capability)
                            is SourceResult.RateLimited -> SourceSearchState.Failed(
                                SearchFailure(
                                    SearchFailureKind.RATE_LIMITED,
                                    "Source rate limited",
                                    result.retryAfterMillis,
                                ),
                            )
                            is SourceResult.TransientFailure -> SourceSearchState.Failed(
                                SearchFailure(SearchFailureKind.TRANSIENT, result.message),
                            )
                            is SourceResult.PermanentFailure -> SourceSearchState.Failed(
                                SearchFailure(SearchFailureKind.PERMANENT, result.message),
                            )
                        }
                        update(currentGeneration) { it.withSource(id, sourceState) }
                    }
                }
            }.forEach { it.join() }
            update(currentGeneration) { it.copy(phase = SearchPhase.COMPLETED) }
        }
        job = started
        return started
    }

    fun cancel() {
        job?.cancel()
    }

    private fun update(generation: Long, transform: (SearchState) -> SearchState) {
        mutableState.update { current ->
            if (current.generation == generation) transform(current).withMergedItems() else current
        }
    }

    private fun SearchState.withSource(id: SourceId, value: SourceSearchState): SearchState =
        copy(sources = sources + (id to value))

    private fun SearchState.withMergedItems(): SearchState {
        val candidates = sources.flatMap { (id, sourceState) ->
            (sourceState as? SourceSearchState.Succeeded)?.page?.items.orEmpty().map {
                SourceSearchCandidate(it, sourceOrder[id] ?: Int.MAX_VALUE)
            }
        }
        return copy(mergedItems = SearchResultAggregator.aggregate(candidates))
    }
}
