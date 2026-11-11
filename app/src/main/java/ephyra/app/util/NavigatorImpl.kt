package ephyra.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import ephyra.app.ui.main.MainActivity
import ephyra.core.common.Constants
import ephyra.feature.reader.ReaderActivity
import ephyra.feature.webview.WebViewActivity
import ephyra.presentation.core.util.AppNavigator

/**
 * Concrete implementation of the Navigator interface that bridges feature modules with
 * application-level activity navigation. Since these activities reside in the app
 * module, this implementation must also live here to resolve the target classes.
 */
class NavigatorImpl : AppNavigator {
    override fun openMangaScreen(context: Context, mangaId: Long) {
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                action = Constants.SHORTCUT_MANGA
                putExtra(Constants.MANGA_EXTRA, mangaId)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        // The reader -> manga open path (the #341 residue): the destination is MainActivity,
        // whose theme is not the boundary theme, so this open used to play the platform's
        // default while every other boundary path crossfaded. This plays the same boundary fade
        // pair the theme installs — the open half of the app-owned crossfade (owner motion
        // directive). The close half needs nothing here: ReaderActivity's own boundary theme
        // renders it, and the predictive gesture is the host's. Non-activity callers (app
        // shortcuts, widgets) skip the override — the platform default is correct for a cold
        // start into the app.
        (context as? Activity)?.overridePendingTransition(
            ephyra.presentation.core.R.anim.activity_boundary_fade_in,
            ephyra.presentation.core.R.anim.activity_boundary_fade_out,
        )
    }

    override fun openWebView(context: Context, url: String, sourceId: Long, title: String) {
        val intent = WebViewActivity.newIntent(context, url, sourceId, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    override fun openReader(context: Context, mangaId: Long, chapterId: Long) {
        val intent = ReaderActivity.newIntent(context, mangaId, chapterId).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
