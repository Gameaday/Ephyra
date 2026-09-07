package ephyra.feature.library

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.TriState
import ephyra.core.common.preference.getAndSet
import ephyra.domain.base.BasePreferences
import ephyra.domain.category.interactor.SetDisplayMode
import ephyra.domain.category.interactor.SetSortModeForCategory
import ephyra.domain.category.model.Category
import ephyra.domain.library.model.LibraryDisplayMode
import ephyra.domain.library.model.LibrarySort
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.track.service.Tracker
import ephyra.domain.track.service.TrackerManager
import ephyra.presentation.core.udf.BaseUdfViewModel
import ephyra.presentation.core.ui.AppInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class LibrarySettingsViewModel @Inject constructor(
    private val basePreferences: BasePreferences,
    private val libPreferences: LibraryPreferences,
    private val setDisplayMode: SetDisplayMode,
    private val setSortModeForCategory: SetSortModeForCategory,
    trackerManager: TrackerManager,
    val appInfo: AppInfo,
) : BaseUdfViewModel<LibrarySettingsViewModel.State, LibrarySettingsScreenEvent, Nothing>(State()) {

    val preferences: BasePreferences get() = basePreferences
    val libraryPreferences: LibraryPreferences get() = libPreferences

    val trackersFlow: StateFlow<List<Tracker>> = trackerManager.loggedInTrackersFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            initialValue = emptyList(),
        )

    init {
        viewModelScope.launch {
            trackerManager.loggedInTrackersFlow()
                .distinctUntilChanged()
                .collect { trackers ->
                    updateState { it.copy(loggedInTrackers = trackers.toPersistentList()) }
                }
        }
    }

    override fun onEvent(event: LibrarySettingsScreenEvent) {
        when (event) {
            is LibrarySettingsScreenEvent.ToggleFilter -> toggleFilter(event.preference)
            is LibrarySettingsScreenEvent.ToggleTracker -> toggleTracker(event.id)
            is LibrarySettingsScreenEvent.SetDisplayMode -> setDisplayMode(event.mode)
            is LibrarySettingsScreenEvent.SetSort -> setSort(event.category, event.mode, event.direction)
        }
    }

    private fun toggleFilter(preference: (LibraryPreferences) -> Preference<TriState>) {
        viewModelScope.launch {
            preference(libPreferences).getAndSet {
                it.next()
            }
        }
    }

    private fun toggleTracker(id: Int) {
        toggleFilter { libPreferences.filterTracking(id) }
    }

    private fun setDisplayMode(mode: LibraryDisplayMode) {
        setDisplayMode.await(mode)
    }

    private fun setSort(category: Category?, mode: LibrarySort.Type, direction: LibrarySort.Direction) {
        viewModelScope.launch {
            setSortModeForCategory.await(category, mode, direction)
        }
    }

    @Immutable
    data class State(
        val loggedInTrackers: ImmutableList<Tracker> = persistentListOf(),
    )
}
