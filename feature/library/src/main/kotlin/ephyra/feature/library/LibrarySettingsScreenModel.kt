package ephyra.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.TriState
import ephyra.core.common.preference.getAndSet
import ephyra.core.common.util.lang.launchIO
import ephyra.domain.base.BasePreferences
import ephyra.domain.category.interactor.SetDisplayMode
import ephyra.domain.category.interactor.SetSortModeForCategory
import ephyra.domain.category.model.Category
import ephyra.domain.library.model.LibraryDisplayMode
import ephyra.domain.library.model.LibrarySort
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.track.service.TrackerManager
import ephyra.source.local.isLocal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class LibrarySettingsScreenModel @Inject constructor(
    val preferences: BasePreferences,
    val libraryPreferences: LibraryPreferences,
    private val setDisplayMode: SetDisplayMode,
    private val setSortModeForCategory: SetSortModeForCategory,
    trackerManager: TrackerManager,
) : ViewModel() {

    val trackersFlow = trackerManager.loggedInTrackersFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            initialValue = emptyList(),
        )

    fun onEvent(event: LibrarySettingsScreenEvent) {
        when (event) {
            is LibrarySettingsScreenEvent.ToggleFilter -> toggleFilter(event.preference)
            is LibrarySettingsScreenEvent.ToggleTracker -> toggleTracker(event.id)
            is LibrarySettingsScreenEvent.SetDisplayMode -> setDisplayMode(event.mode)
            is LibrarySettingsScreenEvent.SetSort -> setSort(event.category, event.mode, event.direction)
        }
    }

    private fun toggleFilter(preference: (LibraryPreferences) -> Preference<TriState>) {
        viewModelScope.launchIO {
            preference(libraryPreferences).getAndSet {
                it.next()
            }
        }
    }

    private fun toggleTracker(id: Int) {
        toggleFilter { libraryPreferences.filterTracking(id) }
    }

    private fun setDisplayMode(mode: LibraryDisplayMode) {
        setDisplayMode.await(mode)
    }

    private fun setSort(category: Category?, mode: LibrarySort.Type, direction: LibrarySort.Direction) {
        viewModelScope.launchIO {
            setSortModeForCategory.await(category, mode, direction)
        }
    }
}
