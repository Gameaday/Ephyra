package cafe.adriel.voyager.koin

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import ephyra.core.common.di.CoreContainer

/**
 * A backward-compatible shim for Voyager's koinScreenModel.
 * Resolves ScreenModels deterministically at compile-time via CoreContainer.
 */
@Composable
inline fun <reified T : ScreenModel> Screen.koinScreenModel(
    noinline parameters: (() -> Any)? = null
): T {
    return rememberScreenModel {
        CoreContainer.get<T>()
    }
}
