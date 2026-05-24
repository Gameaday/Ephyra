package ephyra.feature.reader

import android.content.Context
import android.content.Intent
import androidx.navigation.NavController
import dagger.hilt.android.qualifiers.ApplicationContext
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.ContentUnit
import ephyra.presentation.core.ui.viewer.MediaViewerLauncher
import javax.inject.Inject

/**
 * Handles launching the high-performance [ReaderActivity] for sequential Manga reading.
 */
class MangaViewerLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaViewerLauncher {

    override fun canHandle(type: ContentType): Boolean {
        return type == ContentType.MANGA
    }

    override fun launch(navController: NavController, item: ContentItem, unit: ContentUnit) {
        val intent = ReaderActivity.newIntent(context, item.id, unit.id).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
