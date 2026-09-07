package ephyra.feature.webview

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.system.logcat
import ephyra.domain.source.service.SourceManager
import ephyra.presentation.core.udf.BaseUdfViewModel
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

@HiltViewModel
class WebViewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sourceManager: SourceManager,
    private val network: NetworkHelper,
) : BaseUdfViewModel<WebViewViewModel.State, WebViewScreenEvent, WebViewEffect>(State()) {

    val headers: Map<String, String>
        get() = state.value.headers

    val effectFlow: Flow<WebViewEffect>
        get() = effects

    init {
        val initialSourceId = savedStateHandle.get<Long>("source_key")
        if (initialSourceId != null) {
            initialize(initialSourceId)
        }
    }

    fun initialize(sourceId: Long?) {
        if (sourceId == null || state.value.sourceId == sourceId) return
        val headers = loadHeaders(sourceId)
        updateState { it.copy(sourceId = sourceId, headers = headers) }
    }

    private fun loadHeaders(id: Long): Map<String, String> {
        return (sourceManager.get(id) as? HttpSource)?.let { source ->
            try {
                source.headers.toMultimap().mapValues { it.value.getOrNull(0) ?: "" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to build headers" }
                emptyMap()
            }
        } ?: emptyMap()
    }

    override fun onEvent(event: WebViewScreenEvent) {
        when (event) {
            is WebViewScreenEvent.ShareWebpage -> shareWebpage(event.url)
            is WebViewScreenEvent.OpenInBrowser -> openInBrowser(event.url)
            is WebViewScreenEvent.ClearCookies -> clearCookies(event.url)
        }
    }

    private fun shareWebpage(url: String) {
        emitEffect(WebViewEffect.ShareWebpage(url))
    }

    private fun openInBrowser(url: String) {
        emitEffect(WebViewEffect.OpenInBrowser(url))
    }

    private fun clearCookies(url: String) {
        url.toHttpUrlOrNull()?.let {
            val cleared = network.cookieJar.remove(it)
            logcat { "Cleared $cleared cookies for: $url" }
        }
    }

    @Immutable
    data class State(
        val sourceId: Long? = null,
        val headers: Map<String, String> = emptyMap(),
    )
}

