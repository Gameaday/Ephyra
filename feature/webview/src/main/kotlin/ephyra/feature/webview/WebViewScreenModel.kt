package ephyra.feature.webview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.system.logcat
import ephyra.domain.source.service.SourceManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

@HiltViewModel
class WebViewScreenModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sourceManager: SourceManager,
    private val network: NetworkHelper,
) : ViewModel() {

    private var sourceId: Long? = savedStateHandle.get<Long>("source_key")

    var headers = emptyMap<String, String>()

    private val effectChannel = Channel<WebViewEffect>(Channel.BUFFERED)

    /** One-shot UI side-effects to be collected by the composable. */
    val effectFlow = effectChannel.receiveAsFlow()

    init {
        sourceId?.let { initHeaders(it) }
    }

    fun initialize(sourceId: Long?) {
        if (this.sourceId == null && sourceId != null) {
            this.sourceId = sourceId
            initHeaders(sourceId)
        }
    }

    private fun initHeaders(id: Long) {
        (sourceManager.get(id) as? HttpSource)?.let { source ->
            try {
                headers = source.headers.toMultimap().mapValues { it.value.getOrNull(0) ?: "" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to build headers" }
            }
        }
    }

    fun onEvent(event: WebViewScreenEvent) {
        when (event) {
            is WebViewScreenEvent.ShareWebpage -> shareWebpage(event.url)
            is WebViewScreenEvent.OpenInBrowser -> openInBrowser(event.url)
            is WebViewScreenEvent.ClearCookies -> clearCookies(event.url)
        }
    }

    private fun shareWebpage(url: String) {
        effectChannel.trySend(WebViewEffect.ShareWebpage(url))
    }

    private fun openInBrowser(url: String) {
        effectChannel.trySend(WebViewEffect.OpenInBrowser(url))
    }

    private fun clearCookies(url: String) {
        url.toHttpUrlOrNull()?.let {
            val cleared = network.cookieJar.remove(it)
            logcat { "Cleared $cleared cookies for: $url" }
        }
    }
}
