package ephyra.feature.updates

import ephyra.feature.manga.presentation.components.ChapterDownloadAction

/**
 * All user intents originating from the Updates screen.
 *
 * The [UpdatesScreenModel] is the single consumer of these events via [UpdatesScreenModel.onEvent].
 * No other public methods should be called on the ScreenModel from UI code.
 */
sealed interface UpdatesScreenEvent {

    /** User tapped "Update library" in the toolbar. */
    data object UpdateLibrary : UpdatesScreenEvent

    /** User chose a download action for the given [items]. */
    data class DownloadChapters(
        val items: List<UpdatesItem>,
        val action: ChapterDownloadAction,
    ) : UpdatesScreenEvent

    /** User toggled read/unread status for the given [updates]. */
    data class MarkUpdatesRead(val updates: List<UpdatesItem>, val read: Boolean) : UpdatesScreenEvent

    /** User toggled bookmarked status for the given [updates]. */
    data class BookmarkUpdates(val updates: List<UpdatesItem>, val bookmark: Boolean) : UpdatesScreenEvent

    /** User confirmed deletion of the given [items]. */
    data class DeleteChapters(val items: List<UpdatesItem>) : UpdatesScreenEvent

    /** User requested to show the delete-confirmation dialog for [items]. */
    data class ShowConfirmDeleteChapters(val items: List<UpdatesItem>) : UpdatesScreenEvent

    /** User tapped/long-pressed a list item to toggle its selection. */
    data class ToggleSelection(
        val item: UpdatesItem,
        val selected: Boolean,
        val fromLongPress: Boolean = false,
    ) : UpdatesScreenEvent

    /** User chose "Select all" or "Deselect all". */
    data class ToggleAllSelection(val selected: Boolean) : UpdatesScreenEvent

    /** User tapped "Invert selection". */
    data object InvertSelection : UpdatesScreenEvent

    /** User dismissed the active dialog (or code closes it programmatically). */
    data class SetDialog(val dialog: UpdatesScreenModel.Dialog?) : UpdatesScreenEvent

    /** App lifecycle / tab re-entry — reset the new-update badge counter. */
    data object ResetNewUpdatesCount : UpdatesScreenEvent

    /** User tapped the filter icon to open the filter sheet. */
    data object ShowFilterDialog : UpdatesScreenEvent
}
