package ephyra.feature.history

import ephyra.domain.history.model.HistoryWithRelations
import ephyra.domain.manga.model.Manga

/**
 * All user intents originating from the History screen.
 *
 * The [HistoryScreenModel] is the single consumer of these events via [HistoryScreenModel.onEvent].
 * No other public methods should be called on the ScreenModel from UI code.
 */
sealed interface HistoryScreenEvent {

    /** User typed into (or cleared) the search bar. */
    data class UpdateSearchQuery(val query: String?) : HistoryScreenEvent

    /** User tapped "Resume" on a history item — navigate to the next chapter for [mangaId]/[chapterId]. */
    data class GetNextChapterForManga(val mangaId: Long, val chapterId: Long) : HistoryScreenEvent

    /** User tapped the heart/favorite icon on a history item identified by [mangaId]. */
    data class AddFavoriteById(val mangaId: Long) : HistoryScreenEvent

    /**
     * User confirmed adding [manga] to the library when the duplicate-manga dialog is already
     * dismissed (i.e. the user chose to add anyway with the existing [manga] object in hand).
     */
    data class AddFavorite(val manga: Manga) : HistoryScreenEvent

    /** User confirmed moving [manga] to [categories] and adding to the library. */
    data class MoveMangaToCategoriesAndAddToLibrary(
        val manga: Manga,
        val categories: List<Long>,
    ) : HistoryScreenEvent

    /** Delete a single [history] item from the read log. */
    data class RemoveFromHistory(val history: HistoryWithRelations) : HistoryScreenEvent

    /** Delete all history items for a given manga. */
    data class RemoveAllForManga(val mangaId: Long) : HistoryScreenEvent

    /** Delete the entire read history. */
    data object RemoveAllHistory : HistoryScreenEvent

    /** Show the migrate-manga dialog to move reading progress from [current] to [target]. */
    data class ShowMigrateDialog(val target: Manga, val current: Manga) : HistoryScreenEvent

    /** Open the change-category dialog for [manga]. */
    data class ShowChangeCategoryDialog(val manga: Manga) : HistoryScreenEvent

    /** Change (or dismiss) the active dialog. */
    data class SetDialog(val dialog: HistoryScreenModel.Dialog?) : HistoryScreenEvent
}
