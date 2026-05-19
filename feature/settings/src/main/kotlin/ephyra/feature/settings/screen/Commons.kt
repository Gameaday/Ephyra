package ephyra.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import ephyra.domain.category.model.Category
import ephyra.feature.category.presentation.visualName
import ephyra.presentation.core.i18n.stringResource

/**
 * Returns a string of categories name for settings subtitle
 */
@ReadOnlyComposable
@Composable
fun getCategoriesLabel(
    allCategories: List<Category>,
    included: Set<String>,
    excluded: Set<String>,
): String {
    val context = LocalContext.current

    val categoryById = allCategories.associateBy { it.id }
    val includedCategories = included
        .mapNotNull { id -> categoryById[id.toLong()] }
        .sortedBy { it.order }
    val excludedCategories = excluded
        .mapNotNull { id -> categoryById[id.toLong()] }
        .sortedBy { it.order }
    val allExcluded = excludedCategories.size == allCategories.size

    val includedItemsText = when {
        // Some selected, but not all
        includedCategories.isNotEmpty() && includedCategories.size != allCategories.size ->
            includedCategories.joinToString { it.visualName(context) }
        // All explicitly selected
        includedCategories.size == allCategories.size -> stringResource(ephyra.app.core.common.R.string.all)
        allExcluded -> stringResource(ephyra.app.core.common.R.string.none)
        else -> stringResource(ephyra.app.core.common.R.string.all)
    }
    val excludedItemsText = when {
        excludedCategories.isEmpty() -> stringResource(ephyra.app.core.common.R.string.none)
        allExcluded -> stringResource(ephyra.app.core.common.R.string.all)
        else -> excludedCategories.joinToString { it.visualName(context) }
    }
    return stringResource(ephyra.app.core.common.R.string.include, includedItemsText) + "\n" +
        stringResource(ephyra.app.core.common.R.string.exclude, excludedItemsText)
}
