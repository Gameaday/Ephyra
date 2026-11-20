package ephyra.feature.history.components

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import ephyra.domain.manga.model.MangaCover
import ephyra.feature.history.HistoryUiItem
import java.util.Date

/**
 * Preview fixtures for the history row.
 *
 * **These build [HistoryUiItem], not `HistoryWithRelations`.** That is the point of the UI model:
 * a preview must not need a database join to render a row, and it must not re-implement the
 * ViewModel's formatting. The three cases are the ones the row actually distinguishes:
 * a known read time, an unknown one, and a source that reported no usable chapter number.
 */
internal class HistoryWithRelationsProvider : PreviewParameterProvider<HistoryUiItem> {

    private val cover = MangaCover(
        mangaId = 3L,
        sourceId = 4L,
        isMangaFavorite = false,
        url = "https://example.com/cover.png",
        lastModified = 5L,
    )

    private val base = HistoryUiItem(
        id = 1L,
        mangaId = 3L,
        chapterId = 2L,
        title = "Test Title",
        chapterLabel = "10.2",
        readAtLabel = "2 minutes ago",
        coverData = cover,
        canAddToLibrary = true,
        readAt = Date(1697247357L),
    )

    private val simple = base

    private val historyWithoutReadAt = base.copy(
        chapterLabel = null,
        readAtLabel = "",
        readAt = null,
    )

    private val historyWithNegativeChapterNumber = base.copy(
        chapterLabel = null,
        readAtLabel = "3 days ago",
    )

    override val values: Sequence<HistoryUiItem>
        get() = sequenceOf(simple, historyWithoutReadAt, historyWithNegativeChapterNumber)
}
