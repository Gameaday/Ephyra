package ephyra.feature.history

import ephyra.domain.manga.model.MangaCover
import java.util.Date

/**
 * What a history row renders.
 *
 * The row used to take a `HistoryWithRelations` — a domain model with 8 fields of persisted
 * state — and format `readAt` and `chapterNumber` inline, then reach through
 * `coverData.isMangaFavorite` to decide whether to offer "add to library". That coupled the
 * composable to the schema of a database join: renaming a `HistoryView` column was a UI change,
 * and the formatting rules could not be tested without composing.
 *
 * [HistoryViewModel] maps domain to this; the composable only renders. Every field is already in
 * display form, so the UI holds no domain knowledge — notably it never learns that
 * `chapterNumber < 0` is the source's "unknown" sentinel.
 */
data class HistoryUiItem(
    val id: Long,
    val mangaId: Long,
    val chapterId: Long,
    val title: String,
    /** Formatted chapter number, or null when the source reported no usable number. */
    val chapterLabel: String?,
    /** Formatted read timestamp, or empty when the read time is unknown. */
    val readAtLabel: String,
    val coverData: MangaCover,
    /** Whether the "add to library" affordance should be offered for this row. */
    val canAddToLibrary: Boolean,
    /**
     * The raw read instant, used to date the day header this row files under.
     *
     * Not rendered — [readAtLabel] is. It is carried because grouping by calendar day is a
     * ViewModel decision that needs the instant, not its formatted form.
     */
    val readAt: Date?,
)
