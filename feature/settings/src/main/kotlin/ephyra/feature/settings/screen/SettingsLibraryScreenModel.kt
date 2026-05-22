package ephyra.feature.settings.screen

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.category.interactor.ResetCategoryFlags
import ephyra.domain.category.model.Category
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.library.service.LibraryUpdateScheduler
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class SettingsLibraryScreenModel @Inject constructor(
    val libraryPreferences: LibraryPreferences,
    val libraryUpdateScheduler: LibraryUpdateScheduler,
    private val getCategories: GetCategories,
    val resetCategoryFlags: ResetCategoryFlags,
) : ViewModel() {

    fun getCategories(): Flow<List<Category>> {
        return getCategories.subscribe()
    }
}
