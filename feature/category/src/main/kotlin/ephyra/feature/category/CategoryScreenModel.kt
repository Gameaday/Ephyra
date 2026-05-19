package ephyra.feature.category

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.domain.category.interactor.CreateCategoryWithName
import ephyra.domain.category.interactor.DeleteCategory
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.category.interactor.RenameCategory
import ephyra.domain.category.interactor.ReorderCategory
import ephyra.domain.category.model.Category
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryScreenModel @Inject constructor(
    private val getCategories: GetCategories,
    private val createCategoryWithName: CreateCategoryWithName,
    private val deleteCategory: DeleteCategory,
    private val reorderCategory: ReorderCategory,
    private val renameCategory: RenameCategory,
) : ViewModel() {

    private val _state = MutableStateFlow<CategoryScreenState>(CategoryScreenState.Loading)
    val state = _state.asStateFlow()

    private val _events: Channel<CategoryEvent> = Channel()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            getCategories.subscribe()
                .collectLatest { categories ->
                    _state.update {
                        CategoryScreenState.Success(
                            categories = categories
                                .filterNot(Category::isSystemCategory)
                                .toImmutableList(),
                        )
                    }
                }
        }
    }

    fun onEvent(event: CategoryScreenEvent) {
        when (event) {
            is CategoryScreenEvent.CreateCategory -> createCategory(event.name)
            is CategoryScreenEvent.DeleteCategory -> deleteCategory(event.categoryId)
            is CategoryScreenEvent.ChangeOrder -> changeOrder(event.category, event.newIndex)
            is CategoryScreenEvent.RenameCategory -> renameCategory(event.category, event.name)
            is CategoryScreenEvent.ShowDialog -> showDialog(event.dialog)
            CategoryScreenEvent.DismissDialog -> dismissDialog()
        }
    }

    private fun createCategory(name: String) {
        viewModelScope.launch {
            when (createCategoryWithName.await(name)) {
                is CreateCategoryWithName.Result.InternalError -> _events.send(CategoryEvent.InternalError)
                else -> {}
            }
        }
    }

    private fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            when (deleteCategory.await(categoryId = categoryId)) {
                is DeleteCategory.Result.InternalError -> _events.send(CategoryEvent.InternalError)
                else -> {}
            }
        }
    }

    private fun changeOrder(category: Category, newIndex: Int) {
        viewModelScope.launch {
            when (reorderCategory.await(category, newIndex)) {
                is ReorderCategory.Result.InternalError -> _events.send(CategoryEvent.InternalError)
                else -> {}
            }
        }
    }

    private fun renameCategory(category: Category, name: String) {
        viewModelScope.launch {
            when (renameCategory.await(category, name)) {
                is RenameCategory.Result.InternalError -> _events.send(CategoryEvent.InternalError)
                else -> {}
            }
        }
    }

    private fun showDialog(dialog: CategoryDialog) {
        _state.update {
            when (it) {
                CategoryScreenState.Loading -> it
                is CategoryScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    private fun dismissDialog() {
        _state.update {
            when (it) {
                CategoryScreenState.Loading -> it
                is CategoryScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed interface CategoryDialog {
    data object Create : CategoryDialog
    data class Rename(val category: Category) : CategoryDialog
    data class Delete(val category: Category) : CategoryDialog
}

sealed interface CategoryEvent {
    sealed class LocalizedMessage(val stringRes: Int) : CategoryEvent
    data object InternalError : LocalizedMessage(ephyra.app.core.common.R.string.internal_error)
}

sealed interface CategoryScreenState {

    @Immutable
    data object Loading : CategoryScreenState

    @Immutable
    data class Success(
        val categories: ImmutableList<Category>,
        val dialog: CategoryDialog? = null,
    ) : CategoryScreenState {

        val isEmpty: Boolean
            get() = categories.isEmpty()
    }
}
