package ephyra.feature.browse.migration.sources

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.feature.browse.presentation.MigrateSourceScreen
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.AppBarActions
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import kotlinx.collections.immutable.persistentListOf

/**
 * Source migration as a standalone destination under Settings.
 *
 * Formerly a pager page in the Discover tab; moved because migration is an occasional
 * management task, not a discovery surface (RFC-0001 D8/D9). Content is unchanged from
 * the old `migrateSourceTab` — only the host (route instead of pager page) differs.
 */
@Composable
fun MigrateSourceRouteScreen(
    navController: NavController = LocalNavController.current,
) {
    val uriHandler = LocalUriHandler.current
    val viewModel = hiltViewModel<MigrateSourceViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(ephyra.app.core.common.R.string.label_migration),
                navigateUp = { navController.popBackStack() },
                actions = {
                    AppBarActions(
                        actions = persistentListOf(
                            AppBar.Action(
                                title = stringResource(ephyra.app.core.common.R.string.migration_help_guide),
                                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                                onClick = {
                                    uriHandler.openUri("https://ephyra.app/docs/guides/source-migration")
                                },
                            ),
                        ),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        MigrateSourceScreen(
            state = state,
            contentPadding = contentPadding,
            onClickItem = { source ->
                navController.navigate(ScreenRoutes.MigrateManga.createRoute(source.id))
            },
            onToggleSortingDirection = { viewModel.onEvent(MigrateSourceScreenEvent.ToggleSortingDirection) },
            onToggleSortingMode = { viewModel.onEvent(MigrateSourceScreenEvent.ToggleSortingMode) },
        )
    }
}
