package ephyra.feature.webview

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.presentation.core.util.AssistContentScreen
import ephyra.presentation.core.util.Screen
import ephyra.presentation.core.util.system.openInBrowser
import ephyra.presentation.core.util.system.toShareIntent
import ephyra.presentation.core.util.system.toast

class WebViewScreen(
    private val url: String,
    private val initialTitle: String? = null,
    private val sourceId: Long? = null,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = hiltViewModel<WebViewScreenModel>()

        remember(screenModel, sourceId) {
            screenModel.initialize(sourceId)
        }

        LaunchedEffect(screenModel) {
            screenModel.effectFlow.collect { effect ->
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
            onNavigateUp = { navigator.pop() },
            initialTitle = initialTitle,
            url = url,
            headers = screenModel.headers,
            onUrlChange = { assistUrl = it },
            onShare = { screenModel.onEvent(WebViewScreenEvent.ShareWebpage(it)) },
            onOpenInBrowser = { screenModel.onEvent(WebViewScreenEvent.OpenInBrowser(it)) },
            onClearCookies = { screenModel.onEvent(WebViewScreenEvent.ClearCookies(it)) },
        )
    }
}
