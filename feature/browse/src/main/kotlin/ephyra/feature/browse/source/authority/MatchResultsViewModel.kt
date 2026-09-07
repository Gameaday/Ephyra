package ephyra.feature.browse.source.authority

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.domain.content.model.ContentType
import ephyra.domain.manga.interactor.GetFavorites
import ephyra.domain.manga.model.CanonicalId
import ephyra.domain.manga.model.Manga
import ephyra.domain.track.interactor.MatchUnlinkedManga
import ephyra.presentation.core.udf.BaseUdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import logcat.LogPriority
import javax.inject.Inject

sealed interface MatchResultsEvent {
    data class RetrySingle(val manga: Manga) : MatchResultsEvent
    data object RetryAll : MatchResultsEvent
}

@HiltViewModel
class MatchResultsViewModel @Inject constructor(
    private val getFavorites: GetFavorites,
    private val matchUnlinkedManga: MatchUnlinkedManga,
) : BaseUdfViewModel<MatchResultsState, MatchResultsEvent, Nothing>(MatchResultsState()) {

    init {
        loadManga()
    }

    override fun onEvent(event: MatchResultsEvent) {
        when (event) {
            is MatchResultsEvent.RetrySingle -> retrySingle(event.manga)
            MatchResultsEvent.RetryAll -> retryAll()
        }
    }

    private fun loadManga() {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                val favorites = withIOContext { getFavorites.await() }
                val unlinked = favorites
                    .filter { it.canonicalId == null }
                    .sortedBy { it.title }
                    .toImmutableList()
                val linked = favorites
                    .filter { it.canonicalId != null }
                    .sortedByDescending { it.lastModifiedAt }
                    .take(MAX_RECENTLY_LINKED)
                    .toImmutableList()
                val mangaCount = favorites.count { it.contentType == ContentType.MANGA }
                val novelCount = favorites.count { it.contentType == ContentType.NOVEL }
                updateState {
                    it.copy(
                        isLoading = false,
                        unlinkedManga = unlinked,
                        recentlyLinked = linked,
                        totalFavorites = favorites.size,
                        mangaCount = mangaCount,
                        novelCount = novelCount,
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load manga for match results" }
                updateState { it.copy(isLoading = false) }
            }
        }
    }

    fun retrySingle(manga: Manga) {
        if (manga.id in currentState.matchingIds) return
        if (currentState.isRetryingAll) return
        viewModelScope.launch {
            updateState { state ->
                state.copy(
                    matchingIds = state.matchingIds + manga.id,
                    failedIds = state.failedIds - manga.id,
                )
            }
            try {
                val canonicalId = withIOContext { matchUnlinkedManga.awaitSingle(manga) }
                if (canonicalId != null) {
                    loadManga()
                } else {
                    updateState { state ->
                        state.copy(
                            matchingIds = state.matchingIds - manga.id,
                            failedIds = state.failedIds + manga.id,
                        )
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to match '${manga.title}'" }
                updateState { state ->
                    state.copy(
                        matchingIds = state.matchingIds - manga.id,
                        failedIds = state.failedIds + manga.id,
                    )
                }
            }
        }
    }

    fun retryAll() {
        if (currentState.isRetryingAll) return
        viewModelScope.launch {
            updateState { it.copy(isRetryingAll = true) }
            try {
                withIOContext {
                    matchUnlinkedManga.await { current, total ->
                        updateState { it.copy(retryAllProgress = current to total) }
                    }
                }
                loadManga()
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Retry all failed" }
            } finally {
                updateState {
                    it.copy(
                        isRetryingAll = false,
                        retryAllProgress = null,
                        matchingIds = emptySet(),
                        failedIds = emptySet(),
                    )
                }
            }
        }
    }

    companion object {
        private const val MAX_RECENTLY_LINKED = 50
    }
}

@Immutable
data class MatchResultsState(
    val isLoading: Boolean = false,
    val unlinkedManga: ImmutableList<Manga> = persistentListOf(),
    val recentlyLinked: ImmutableList<Manga> = persistentListOf(),
    val totalFavorites: Int = 0,
    val mangaCount: Int = 0,
    val novelCount: Int = 0,
    val matchingIds: Set<Long> = emptySet(),
    val failedIds: Set<Long> = emptySet(),
    val isRetryingAll: Boolean = false,
    val retryAllProgress: Pair<Int, Int>? = null,
) {
    val totalLinked: Int get() = totalFavorites - unlinkedManga.size
}

data class AuthorityInfo(
    val label: String,
    val url: String?,
) {
    companion object {
        fun from(canonicalId: String?): AuthorityInfo? {
            if (canonicalId == null) return null
            val label = CanonicalId.toLabel(canonicalId) ?: return null
            val url = CanonicalId.toUrl(canonicalId)
            return AuthorityInfo(label = label, url = url)
        }
    }
}
