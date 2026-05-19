package ephyra.feature.settings.screen

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.domain.extensionrepo.interactor.GetExtensionRepoCount
import ephyra.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class SettingsBrowseScreenModel @Inject constructor(
    val sourcePreferences: SourcePreferences,
    private val getExtensionRepoCount: GetExtensionRepoCount,
) : ViewModel() {

    fun getExtensionRepoCount(): Flow<Int> {
        return getExtensionRepoCount.subscribe()
    }
}
