package ephyra.presentation.core.ui

import androidx.compose.runtime.Composable

fun interface ExtensionReposScreenFactory {
    @Composable
    operator fun invoke(url: String?)
}
