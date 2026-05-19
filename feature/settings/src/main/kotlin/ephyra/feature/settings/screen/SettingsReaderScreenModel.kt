package ephyra.feature.settings.screen

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.domain.reader.service.ReaderPreferences
import javax.inject.Inject

@HiltViewModel
class SettingsReaderScreenModel @Inject constructor(
    val readerPreferences: ReaderPreferences,
) : ViewModel()
