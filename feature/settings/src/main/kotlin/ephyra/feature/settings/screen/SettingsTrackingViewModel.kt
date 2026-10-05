package ephyra.feature.settings.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.di.IoDispatcher
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.interactor.TrackerListImporter
import ephyra.domain.track.service.TrackPreferences
import ephyra.domain.track.service.TrackerManager
import ephyra.presentation.core.ui.MatchUnlinkedJobRunner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsTrackingViewModel @Inject constructor(
    val trackPreferences: TrackPreferences,
    val trackerManager: TrackerManager,
    val sourceManager: SourceManager,
    val libraryPreferences: LibraryPreferences,
    val trackerListImporter: TrackerListImporter,
    val matchUnlinkedJobRunner: MatchUnlinkedJobRunner,
    /**
     * Dispatcher for tracker network work, injectable so a test can substitute its own scheduler.
     *
     * This was hardcoded to `Dispatchers.IO`, which no test can control: `Dispatchers.setMain` and
     * `advanceUntilIdle()` do not reach a real thread pool, so an assertion about a launched
     * operation races the coroutine. `InjectableViewModelDispatcherTest` enforces that shape.
     *
     * Production behaviour is unchanged: this binds `@IoDispatcher`, which is `Dispatchers.IO`.
     * Follows `AboutViewModel.updateCheckDispatcher`.
     */
    @IoDispatcher
    private val trackerDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    /**
     * Runs a tracker operation that must outlive the composition that started it.
     *
     * Tracker imports, logins and logout are network calls that can take seconds. Launching them in
     * `rememberCoroutineScope()` tied them to the composition, so a rotation or a trip through the
     * background cancelled them mid-flight and the work was silently lost. `viewModelScope` survives
     * both, so the operation completes even when the screen that requested it is gone; the UI state
     * that reported progress is recreated by the next composition.
     */
    fun launchPersistent(block: suspend CoroutineScope.() -> Unit): Job =
        viewModelScope.launch(trackerDispatcher) { block() }
}
