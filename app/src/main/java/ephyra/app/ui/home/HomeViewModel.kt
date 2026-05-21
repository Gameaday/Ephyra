package ephyra.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.download.DownloadCache
import ephyra.domain.base.BasePreferences
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.source.interactor.GetIncognitoState
import ephyra.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getIncognitoState: GetIncognitoState,
    private val basePreferences: BasePreferences,
    private val downloadCache: DownloadCache,
    val libraryPreferences: LibraryPreferences,
    val sourcePreferences: SourcePreferences,
) : ViewModel() {

    val incognito: StateFlow<Boolean> = getIncognitoState.subscribe(null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val downloadOnly: StateFlow<Boolean> = basePreferences.downloadedOnly().asState(viewModelScope)

    val indexing: StateFlow<Boolean> = downloadCache.isInitializing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val updatesBadgeCount: StateFlow<Int> = combine(
        libraryPreferences.newShowUpdatesCount().changes(),
        libraryPreferences.newUpdatesCount().changes(),
    ) { show, count -> if (show) count else 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val extensionsBadgeCount: StateFlow<Int> = sourcePreferences.extensionUpdatesCount().changes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private fun <T> ephyra.core.common.preference.Preference<T>.asState(scope: kotlinx.coroutines.CoroutineScope): StateFlow<T> {
        return changes().stateIn(scope, SharingStarted.WhileSubscribed(5000), getSync())
    }
}
