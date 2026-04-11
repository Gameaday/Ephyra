package ephyra.presentation.core.ui

/**
 * Implemented by screens that can receive search queries from child screens.
 *
 * [HomeScreen] (library tab) and [BrowseSourceScreen] implement this interface so that
 * [MangaScreen] can trigger searches without importing app-level or cross-feature screens.
 *
 * Feature modules use this interface instead of importing concrete screen classes, keeping
 * the dependency graph acyclic.
 */
interface SearchableScreen {
    /** Perform a text search using [query]. */
    suspend fun search(query: String)

    /** Perform a genre search using [name] (optional; default falls back to [search]). */
    suspend fun searchGenre(name: String) = search(name)
}
