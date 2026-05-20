package ephyra.presentation.core.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController

val LocalNavController = staticCompositionLocalOf<NavHostController> {
    error("No NavController provided")
}

sealed class ScreenRoutes(val route: String) {
    object Onboarding : ScreenRoutes("onboarding")
    object Home : ScreenRoutes("home")
    
    object MangaDetails : ScreenRoutes("manga/{mangaId}/{fromSource}") {
        fun createRoute(mangaId: Long, fromSource: Boolean): String {
            return "manga/$mangaId/$fromSource"
        }
    }
    
    object GlobalSearch : ScreenRoutes("global_search?query={query}") {
        fun createRoute(query: String?): String {
            return if (query != null) "global_search?query=$query" else "global_search"
        }
    }
    
    object BrowseSource : ScreenRoutes("browse_source/{sourceId}") {
        fun createRoute(sourceId: Long): String {
            return "browse_source/$sourceId"
        }
    }
    
    object RestoreBackup : ScreenRoutes("restore_backup")
    object ExtensionRepos : ScreenRoutes("extension_repos")
}
