package ephyra.feature.settings.screen

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.interactor.TrackerListImporter
import ephyra.domain.track.service.TrackPreferences
import ephyra.domain.track.service.TrackerManager
import ephyra.presentation.core.ui.MatchUnlinkedJobRunner
import javax.inject.Inject

@HiltViewModel
class SettingsTrackingScreenModel @Inject constructor(
    val trackPreferences: TrackPreferences,
    val trackerManager: TrackerManager,
    val sourceManager: SourceManager,
    val libraryPreferences: LibraryPreferences,
    val trackerListImporter: TrackerListImporter,
    val matchUnlinkedJobRunner: MatchUnlinkedJobRunner,
) : ViewModel()
