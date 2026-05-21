package ephyra.app.ui.deeplink

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.feature.reader.ReaderActivity
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes

@Composable
fun DeepLinkScreen(
    query: String = "",
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current

    val screenModel = hiltViewModel<DeepLinkScreenModel>()
    LaunchedEffect(query) {
        screenModel.init(query)
    }
    val state by screenModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(ephyra.app.core.common.R.string.action_search_hint),
                navigateUp = { navController.popBackStack() },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        when (state) {
            is DeepLinkScreenModel.State.Loading -> {
                LoadingScreen(Modifier.padding(contentPadding))
            }
            is DeepLinkScreenModel.State.NoResults -> {
                navController.navigate(ScreenRoutes.GlobalSearch.createRoute(query)) {
                    popUpTo(ScreenRoutes.Home.route) { inclusive = false }
                }
            }
            is DeepLinkScreenModel.State.Result -> {
                val resultState = state as DeepLinkScreenModel.State.Result
                if (resultState.chapterId == null) {
                    navController.navigate(ScreenRoutes.MangaDetails.createRoute(resultState.manga.id, true)) {
                        popUpTo(ScreenRoutes.Home.route) { inclusive = false }
                    }
                } else {
                    navController.popBackStack()
                    ReaderActivity.newIntent(
                        context,
                        resultState.manga.id,
                        resultState.chapterId,
                    ).also(context::startActivity)
                }
            }
        }
    }
}
