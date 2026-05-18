package ephyra.presentation.core.i18n

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource as composePluralStringResource
import androidx.compose.ui.res.stringResource as composeStringResource

@Composable
@ReadOnlyComposable
fun stringResource(@StringRes resource: Int): String {
    return composeStringResource(resource)
}

@Composable
@ReadOnlyComposable
fun stringResource(@StringRes resource: Int, vararg args: Any): String {
    return composeStringResource(resource, *args)
}

@Composable
@ReadOnlyComposable
fun pluralStringResource(@PluralsRes resource: Int, count: Int): String {
    return composePluralStringResource(resource, count, count)
}

@Composable
@ReadOnlyComposable
fun pluralStringResource(@PluralsRes resource: Int, count: Int, vararg args: Any): String {
    return composePluralStringResource(resource, count, *args)
}
