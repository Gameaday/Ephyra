package ephyra.presentation.core.ui

import androidx.compose.runtime.Composable

fun interface MigrationConfigScreenFactory {
    @Composable
    operator fun invoke(mangaIds: Collection<Long>)
}
