package ephyra.feature.webview

import android.app.assist.AssistContent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint
import ephyra.core.common.util.system.WebViewUtil
import ephyra.core.common.util.system.logcat
import ephyra.core.common.util.system.openInBrowser
import ephyra.domain.source.service.SourceManager
import ephyra.domain.ui.UiPreferences
import ephyra.presentation.core.ui.activity.BaseActivity
import ephyra.presentation.core.ui.navigation.ActivityBoundaryCrossfadeHost
import ephyra.presentation.core.ui.navigation.LocalMotionPreference
import ephyra.presentation.core.ui.navigation.effectiveReducedMotion
import ephyra.presentation.core.ui.navigation.rememberSystemReducedMotion
import ephyra.presentation.core.util.collectAsState
import ephyra.presentation.core.util.system.toShareIntent
import ephyra.presentation.core.util.system.toast
import ephyra.presentation.core.util.view.setComposeContent
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.online.HttpSource
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject

@AndroidEntryPoint
class WebViewActivity : BaseActivity() {

    @Inject lateinit var sourceManager: SourceManager

    @Inject lateinit var network: NetworkHelper

    @Inject lateinit var uiPreferences: UiPreferences

    private var assistUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // This activity owns its boundary motion as one minimal crossfade with no movement
        // (owner directive 2026-11-09): the theme (Theme.Tachiyomi.ActivityBoundary) renders
        // every open and non-gesture close, and ActivityBoundaryCrossfadeHost routes a committed
        // predictive back gesture through finish() so the same close crossfade plays instead of
        // the system's scale reveal (`ReaderActivity` documents the full rationale, and
        // `MotionConsistencyTest` gates it).
        super.onCreate(savedInstanceState)

        if (!WebViewUtil.supportsWebView(this)) {
            toast(ephyra.app.core.common.R.string.information_webview_required, Toast.LENGTH_LONG)
            finish()
            return
        }

        val url = intent.extras?.getString(URL_KEY) ?: return
        assistUrl = url

        var headers = emptyMap<String, String>()
        (sourceManager.get(intent.extras!!.getLong(SOURCE_KEY)) as? HttpSource)?.let { source ->
            try {
                headers = source.headers.toMultimap().mapValues { it.value.getOrNull(0) ?: "" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to build headers" }
            }
        }

        setComposeContent {
            val systemReducedMotion = rememberSystemReducedMotion()
            val appReduceMotion by uiPreferences.reduceMotion().collectAsState()
            CompositionLocalProvider(
                LocalMotionPreference provides effectiveReducedMotion(systemReducedMotion, appReduceMotion),
            ) {
                ActivityBoundaryCrossfadeHost(onExit = ::finish) {
                    WebViewScreenContent(
                        onNavigateUp = { finish() },
                        initialTitle = intent.extras?.getString(TITLE_KEY),
                        url = url,
                        headers = headers,
                        onUrlChange = { assistUrl = it },
                        onShare = this::shareWebpage,
                        onOpenInBrowser = this::openInBrowser,
                        onClearCookies = this::clearCookies,
                    )
                }
            }
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        assistUrl?.let { outContent.webUri = it.toUri() }
    }

    private fun shareWebpage(url: String) {
        try {
            startActivity(url.toUri().toShareIntent(this, type = "text/plain"))
        } catch (e: Exception) {
            toast(e.message)
        }
    }

    private fun openInBrowser(url: String) {
        openInBrowser(url, forceDefaultBrowser = true)
    }

    private fun clearCookies(url: String) {
        val cleared = network.cookieJar.remove(url.toHttpUrl())
        logcat { "Cleared $cleared cookies for: $url" }
    }

    companion object {
        private const val URL_KEY = "url_key"
        private const val SOURCE_KEY = "source_key"
        private const val TITLE_KEY = "title_key"

        fun newIntent(context: Context, url: String, sourceId: Long? = null, title: String? = null): Intent {
            return Intent(context, WebViewActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(URL_KEY, url)
                putExtra(SOURCE_KEY, sourceId)
                putExtra(TITLE_KEY, title)
            }
        }
    }
}
