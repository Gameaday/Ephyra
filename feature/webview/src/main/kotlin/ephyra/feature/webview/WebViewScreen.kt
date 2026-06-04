package ephyra.feature.webview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.util.system.openInBrowser
import ephyra.presentation.core.util.system.toShareIntent
import ephyra.presentation.core.util.system.toast

@Composable
fun WebViewScreen(
    url: String,
    initialTitle: String? = null,
    sourceId: Long? = null,
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current
    val viewModel = hiltViewModel<WebViewViewModel>()

    var assistUrl by remember { mutableStateOf<String?>(null) }
    // TODO: handle assistUrl if needed for parent activity

    LaunchedEffect(viewModel, sourceId) {
        viewModel.initialize(sourceId)
    }

    LaunchedEffect(viewModel) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is WebViewEffect.ShareWebpage -> {
                    try {
                        context.startActivity(effect.url.toUri().toShareIntent(context, type = "text/plain"))
                    } catch (e: Exception) {
                        context.toast(e.message)
                    }
                }
                is WebViewEffect.OpenInBrowser -> {
                    context.openInBrowser(effect.url, forceDefaultBrowser = true)
                }
            }
        }
    }

    WebViewScreenContent(
        onNavigateUp = { navController.popBackStack() },
        initialTitle = initialTitle,
        url = url,
        headers = ViewModel.headers,
        onUrlChange = { assistUrl = it },
        onShare = { ViewModel.onEvent(WebViewScreenEvent.ShareWebpage(it)) },
        onOpenInBrowser = { ViewModel.onEvent(WebViewScreenEvent.OpenInBrowser(it)) },
        onClearCookies = { ViewModel.onEvent(WebViewScreenEvent.ClearCookies(it)) },
    )
}
