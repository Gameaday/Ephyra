package ephyra.app.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.download.DownloadCache
import ephyra.domain.base.BasePreferences
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.source.interactor.GetIncognitoState
import ephyra.domain.source.service.SourcePreferences
import ephyra.presentation.core.udf.BaseUdfViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getIncognitoState: GetIncognitoState,
    private val basePreferences: BasePreferences,
    private val downloadCache: DownloadCache,
    val libraryPreferences: LibraryPreferences,
    val sourcePreferences: SourcePreferences,
) : BaseUdfViewModel<HomeViewModel.State, Nothing, Nothing>(State()) {

    init {
        getIncognitoState.subscribe(null)
            .onEach { incognito -> updateState { it.copy(incognito = incognito) } }
            .launchIn(viewModelScope)

        basePreferences.downloadedOnly().changes()
            .onEach { downloadOnly -> updateState { it.copy(downloadOnly = downloadOnly) } }
            .launchIn(viewModelScope)

        downloadCache.isInitializing
            .onEach { indexing -> updateState { it.copy(indexing = indexing) } }
            .launchIn(viewModelScope)

        combine(
            libraryPreferences.newShowUpdatesCount().changes(),
            libraryPreferences.newUpdatesCount().changes(),
        ) { show, count -> if (show) count else 0 }
            .onEach { count -> updateState { it.copy(updatesBadgeCount = count) } }
            .launchIn(viewModelScope)

        sourcePreferences.extensionUpdatesCount().changes()
            .onEach { count -> updateState { it.copy(extensionsBadgeCount = count) } }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: Nothing) {}

    @Immutable
    data class State(
        val incognito: Boolean = false,
        val downloadOnly: Boolean = false,
        val indexing: Boolean = false,
        val updatesBadgeCount: Int = 0,
        val extensionsBadgeCount: Int = 0,
    )
}
