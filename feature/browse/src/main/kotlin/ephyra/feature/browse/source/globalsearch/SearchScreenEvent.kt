package ephyra.feature.browse.source.globalsearch

import ephyra.domain.manga.model.Manga

/**
 * All user intents for screens backed by [SearchScreenModel].
 * [SearchScreenModel.onEvent] is the sole external mutation entry-point.
 *
 * Value-returning / reactive accessors remain public:
 *   • `getManga()` — `@Composable` Compose State subscription
 *   • `getEnabledSources()` — override-able list accessor
 */
sealed interface SearchScreenEvent {
    data class UpdateSearchQuery(val query: String?) : SearchScreenEvent
    data class SetSourceFilter(val filter: SourceFilter) : SearchScreenEvent
    data object ToggleFilterResults : SearchScreenEvent
    data object Search : SearchScreenEvent
    data class SetMigrateDialog(val currentId: Long, val target: Manga) : SearchScreenEvent
    data object ClearDialog : SearchScreenEvent
}
