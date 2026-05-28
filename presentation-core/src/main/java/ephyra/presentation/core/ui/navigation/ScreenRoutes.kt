package ephyra.presentation.core.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

val LocalNavController = staticCompositionLocalOf<NavHostController> {
    error("No NavController provided")
}

object NavigationEvents {
    private val _reselectEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val reselectEvent = _reselectEvent.asSharedFlow()

    fun triggerReselect(route: String) {
        _reselectEvent.tryEmit(route)
    }
}

sealed class ScreenRoutes(val route: String) {
    data object Onboarding : ScreenRoutes("onboarding")
    data object Home : ScreenRoutes("home")

    // Home Tabs
    data object Library : ScreenRoutes("library")
    data object Updates : ScreenRoutes("updates")
    data object History : ScreenRoutes("history")
    data object Browse : ScreenRoutes("browse")
    data object More : ScreenRoutes("more")

    data object DownloadQueue : ScreenRoutes("download_queue")
    data object Stats : ScreenRoutes("stats")
    data object Upcoming : ScreenRoutes("upcoming")

    data object MangaNotes : ScreenRoutes("manga_notes/{mangaId}") {
        fun createRoute(mangaId: Long) = "manga_notes/$mangaId"
    }
    data object Settings : ScreenRoutes("settings")

    object VideoPlayer : ScreenRoutes("player/{title}/{url}") {
        fun createRoute(title: String, url: String): String {
            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
            val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
            return "player/$encodedTitle/$encodedUrl"
        }
    }

    object BookReader : ScreenRoutes("book/{title}/{content}") {
        fun createRoute(title: String, content: String): String {
            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
            val encodedContent = java.net.URLEncoder.encode(content, "UTF-8")
            return "book/$encodedTitle/$encodedContent"
        }
    }

    object GlobalSearch : ScreenRoutes("global_search?query={query}") {
        fun createRoute(query: String?): String {
            return if (query != null) "global_search?query=$query" else "global_search"
        }
    }

    object BrowseSource : ScreenRoutes("browse_source/{sourceId}?query={query}") {
        fun createRoute(sourceId: Long, query: String? = null): String {
            return if (query != null) "browse_source/$sourceId?query=$query" else "browse_source/$sourceId"
        }
    }

    data object SourcesFilter : ScreenRoutes("sources_filter")
    data object ExtensionFilter : ScreenRoutes("extension_filter")

    data object ExtensionDetails : ScreenRoutes("extension_details/{pkgName}") {
        fun createRoute(pkgName: String) = "extension_details/$pkgName"
    }

    object WebView : ScreenRoutes("webview?url={url}&title={title}&sourceId={sourceId}") {
        fun createRoute(url: String, title: String? = null, sourceId: Long? = null): String {
            return "webview?url=$url" +
                (if (title != null) "&title=$title" else "") +
                (if (sourceId != null) "&sourceId=$sourceId" else "")
        }
    }

    object MigrateManga : ScreenRoutes("migrate_manga/{sourceId}") {
        fun createRoute(sourceId: Long) = "migrate_manga/$sourceId"
    }

    object MigrationConfig : ScreenRoutes("migration_config/{mangaIds}") {
        fun createRoute(mangaIds: Collection<Long>) = "migration_config/${mangaIds.joinToString(",")}"
    }

    object MigrationList : ScreenRoutes("migration_list/{mangaIds}?query={query}") {
        fun createRoute(mangaIds: Collection<Long>, query: String? = null): String {
            return "migration_list/${mangaIds.joinToString(",")}" +
                (if (query != null) "?query=$query" else "")
        }
    }

    object MigrateSearch : ScreenRoutes("migrate_search/{mangaId}") {
        fun createRoute(mangaId: Long) = "migrate_search/$mangaId"
    }

    object MigrateSourceSearch : ScreenRoutes("migrate_source_search/{mangaId}/{sourceId}") {
        fun createRoute(mangaId: Long, sourceId: Long) = "migrate_source_search/$mangaId/$sourceId"
    }

    object RestoreBackup : ScreenRoutes("restore_backup")
    object ExtensionRepos : ScreenRoutes("extension_repos")

    object SourcePreferences : ScreenRoutes("source_preferences/{sourceId}") {
        fun createRoute(sourceId: Long) = "source_preferences/$sourceId"
    }

    object MatchResults : ScreenRoutes("match_results")

    object About : ScreenRoutes("about")
    object OpenSourceLicenses : ScreenRoutes("open_source_licenses")
    object OpenSourceLibraryLicense : ScreenRoutes("open_source_library_license/{name}") {
        fun createRoute(name: String) = "open_source_library_license/$name"
    }

    object ClearDatabase : ScreenRoutes("clear_database")
    object AppLanguage : ScreenRoutes("app_language")
    object CreateBackup : ScreenRoutes("create_backup")
    object BackupSchema : ScreenRoutes("backup_schema")
    object DebugInfo : ScreenRoutes("debug_info")
    object WorkerInfo : ScreenRoutes("worker_info")

    object SettingsMain : ScreenRoutes("settings_main")
    object SettingsSearch : ScreenRoutes("settings_search")

    object SettingsAppearance : ScreenRoutes("settings_appearance")
    object SettingsLibrary : ScreenRoutes("settings_library")
    object SettingsReader : ScreenRoutes("settings_reader")
    object SettingsDownloads : ScreenRoutes("settings_downloads")
    object SettingsTracking : ScreenRoutes("settings_tracking")
    object SettingsBrowse : ScreenRoutes("settings_browse")
    object SettingsData : ScreenRoutes("settings_data")
    object SettingsSecurity : ScreenRoutes("settings_security")
    object SettingsAdvanced : ScreenRoutes("settings_advanced")
    data object ContentSourcing : ScreenRoutes("content_sourcing")
}

@kotlinx.serialization.Serializable
sealed interface Screen {
    @kotlinx.serialization.Serializable data object Onboarding : Screen
    @kotlinx.serialization.Serializable data object Home : Screen
    @kotlinx.serialization.Serializable data object Library : Screen
    @kotlinx.serialization.Serializable data object Updates : Screen
    @kotlinx.serialization.Serializable data object History : Screen
    @kotlinx.serialization.Serializable data object Browse : Screen
    @kotlinx.serialization.Serializable data object More : Screen
    @kotlinx.serialization.Serializable data object Category : Screen
    @kotlinx.serialization.Serializable data class MangaDetails(val mangaId: Long, val fromSource: Boolean) : Screen
    @kotlinx.serialization.Serializable data class VideoPlayer(val title: String, val url: String) : Screen
    @kotlinx.serialization.Serializable data class BookReader(val title: String, val content: String) : Screen
    @kotlinx.serialization.Serializable data class WebView(val url: String, val title: String? = null, val sourceId: Long? = null) : Screen
    @kotlinx.serialization.Serializable data class GlobalSearch(val query: String? = null) : Screen
    @kotlinx.serialization.Serializable data class BrowseSource(val sourceId: Long, val query: String? = null) : Screen
    @kotlinx.serialization.Serializable data class ExtensionDetails(val pkgName: String) : Screen
}
