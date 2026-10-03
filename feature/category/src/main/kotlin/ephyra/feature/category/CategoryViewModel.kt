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
import ephyra.presentation.core.udf.BaseUdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val getCategories: GetCategories,
    private val createCategoryWithName: CreateCategoryWithName,
    private val deleteCategory: DeleteCategory,
    private val reorderCategory: ReorderCategory,
    private val renameCategory: RenameCategory,
) : BaseUdfViewModel<CategoryScreenState, CategoryScreenEvent, CategoryEvent>(CategoryScreenState.Loading) {

    val events: Flow<CategoryEvent> get() = effects

    init {
        viewModelScope.launch {
            getCategories.subscribe()
                .collectLatest { categories ->
                    updateState {
                        CategoryScreenState.Success(
                            categories = categories
                                .filterNot(Category::isSystemCategory)
                                .toImmutableList(),
                        )
                    }
                }
        }
    }

    override fun onEvent(event: CategoryScreenEvent) {
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
                is CreateCategoryWithName.Result.InternalError -> emitEffect(CategoryEvent.InternalError)
                else -> {}
            }
        }
    }

    private fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            when (deleteCategory.await(categoryId = categoryId)) {
                is DeleteCategory.Result.InternalError -> emitEffect(CategoryEvent.InternalError)
                else -> {}
            }
        }
    }

    private var reorderJob: Job? = null

    /**
     * Scope that outlives `viewModelScope` by one deferred write. Only ever launched from
     * [onCleared], so at most one job exists at a time; the scope itself is never cancelled, which
     * is the point — the whole reason for it is that `viewModelScope` is being cancelled at that
     * moment.
     */
    private val reorderWriteScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    /**
     * The most recent (category, newIndex) whose write has not yet landed.
     *
     * Debouncing means the write is deferred, which is correct while the screen is alive, but if the
     * ViewModel is cleared before the delay elapses the last move would be dropped and the database
     * would keep a stale order. [onCleared] flushes it.
     */
    private var pendingReorder: Pair<Category, Int>? = null

    private fun changeOrder(category: Category, newIndex: Int) {
        // Dragging emits a move event per drag frame; debounce the DB write so the order is
        // persisted only once dragging settles instead of on every frame.
        pendingReorder = category to newIndex
        reorderJob?.cancel()
        reorderJob = viewModelScope.launch {
            delay(REORDER_DEBOUNCE_MILLIS)
            flushPendingReorder()
        }
    }

    private suspend fun flushPendingReorder() {
        val pending = pendingReorder ?: return
        pendingReorder = null
        when (reorderCategory.await(pending.first, pending.second)) {
            is ReorderCategory.Result.InternalError -> emitEffect(CategoryEvent.InternalError)
            else -> {}
        }
    }

    override fun onCleared() {
        // viewModelScope is about to be cancelled, which would drop any deferred write. The write
        // is a single idempotent DB call, so it is moved to a scope that outlives the ViewModel for
        // exactly that one write rather than being dropped with it.
        val pending = pendingReorder ?: return super.onCleared()
        pendingReorder = null
        reorderWriteScope.launch {
            runCatching { reorderCategory.await(pending.first, pending.second) }
        }
        super.onCleared()
    }

    private fun renameCategory(category: Category, name: String) {
        viewModelScope.launch {
            when (renameCategory.await(category, name)) {
                is RenameCategory.Result.InternalError -> emitEffect(CategoryEvent.InternalError)
                else -> {}
            }
        }
    }

    private fun showDialog(dialog: CategoryDialog) {
        updateState {
            when (it) {
                CategoryScreenState.Loading -> it
                is CategoryScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    private fun dismissDialog() {
        updateState {
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

private const val REORDER_DEBOUNCE_MILLIS = 500L

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
