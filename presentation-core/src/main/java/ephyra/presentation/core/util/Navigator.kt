package ephyra.presentation.core.util

import android.content.Context
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * A generic navigator interface to decouple feature modules from the main application
 * navigation logic.
 * * Your Feature modules (Manga, Reader) will call these methods, and your :app module
 * will provide the actual implementation.
 */
interface AppNavigator {
    fun openMangaScreen(context: Context, mangaId: Long)
    fun openWebView(context: Context, url: String, sourceId: Long, title: String)
}

/**
 * For invoking back press to the parent activity
 */
val LocalBackPress: ProvidableCompositionLocal<(() -> Unit)?> = staticCompositionLocalOf { null }

interface AssistContentScreen {
    fun onProvideAssistUrl(): String?
}

interface Tab {
    suspend fun onReselect() {}
}
