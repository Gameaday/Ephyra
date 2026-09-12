package ephyra.feature.browse.extension.details

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.ui.navigation.LocalNavController

@Composable
fun SourcePreferencesScreen(
    sourceId: Long,
    navController: NavController = LocalNavController.current,
    viewModel: SourcePreferencesViewModel = hiltViewModel(),
) {
    LaunchedEffect(sourceId) {
        viewModel.init(sourceId)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.isLoading) {
        LoadingScreen()
        return
    }

    Scaffold(
        topBar = {
            AppBar(
                title = state.sourceTitle,
                navigateUp = { navController.popBackStack() },
                scrollBehavior = it,
            )
        },
    ) { contentPadding ->
        val configurableSource = viewModel.getConfigurableSource(sourceId)
        if (configurableSource != null) {
            SourcePreferenceContent(
                source = configurableSource,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
        } else {
            LoadingScreen()
        }
    }
}
