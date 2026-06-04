package ephyra.feature.category

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.fastMap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.feature.category.presentation.components.CategoryCreateDialog
import ephyra.feature.category.presentation.components.CategoryDeleteDialog
import ephyra.feature.category.presentation.components.CategoryRenameDialog
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.util.system.toast
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CategoryScreen(
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current
    val ViewModel = hiltViewModel<CategoryViewModel>()

    val state by ViewModel.state.collectAsStateWithLifecycle()

    if (state is CategoryScreenState.Loading) {
        LoadingScreen()
        return
    }

    val successState = state as CategoryScreenState.Success

    ephyra.feature.category.presentation.CategoryScreen(
        state = successState,
        onClickCreate = { ViewModel.onEvent(CategoryScreenEvent.ShowDialog(CategoryDialog.Create)) },
        onClickRename = { ViewModel.onEvent(CategoryScreenEvent.ShowDialog(CategoryDialog.Rename(it))) },
        onClickDelete = { ViewModel.onEvent(CategoryScreenEvent.ShowDialog(CategoryDialog.Delete(it))) },
        onChangeOrder = { category, newIndex ->
            ViewModel.onEvent(CategoryScreenEvent.ChangeOrder(category, newIndex))
        },
        navigateUp = { navController.popBackStack() },
    )

    when (val dialog = successState.dialog) {
        null -> {}
        CategoryDialog.Create -> {
            CategoryCreateDialog(
                onDismissRequest = { ViewModel.onEvent(CategoryScreenEvent.DismissDialog) },
                onCreate = { ViewModel.onEvent(CategoryScreenEvent.CreateCategory(it)) },
                categories = successState.categories.fastMap { it.name }.toImmutableList(),
            )
        }

        is CategoryDialog.Rename -> {
            CategoryRenameDialog(
                onDismissRequest = { ViewModel.onEvent(CategoryScreenEvent.DismissDialog) },
                onRename = { ViewModel.onEvent(CategoryScreenEvent.RenameCategory(dialog.category, it)) },
                categories = successState.categories.fastMap { it.name }.toImmutableList(),
                category = dialog.category.name,
            )
        }

        is CategoryDialog.Delete -> {
            CategoryDeleteDialog(
                onDismissRequest = { ViewModel.onEvent(CategoryScreenEvent.DismissDialog) },
                onDelete = { ViewModel.onEvent(CategoryScreenEvent.DeleteCategory(dialog.category.id)) },
                category = dialog.category.name,
            )
        }
    }

    LaunchedEffect(Unit) {
        ViewModel.events.collectLatest { event ->
            (event as? CategoryEvent.LocalizedMessage)?.let {
                context.toast(it.stringRes)
            }
        }
    }
}
