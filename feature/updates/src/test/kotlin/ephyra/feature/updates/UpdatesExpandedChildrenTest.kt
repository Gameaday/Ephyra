package ephyra.feature.updates

import ephyra.domain.download.model.Download
import ephyra.domain.manga.model.MangaCover
import ephyra.domain.updates.model.UpdatesWithRelations
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [withExpandedSeriesChildren] — the UI-layer pass that lifts an expanded series'
 * older updates into top-level keyed lazy items (the History screen's #358 pass applied to
 * the Updates stack). The stacking itself is covered by [UpdatesSeriesStackingTest]; this
 * pass only changes how an *expanded* group is laid out, so every test below feeds a grouped
 * list through the function and asserts the flattened shape.
 */
class UpdatesExpandedChildrenTest {

    private fun updateItem(mangaId: Long, chapterId: Long) = UpdatesItem(
        update = UpdatesWithRelations(
            mangaId = mangaId,
            mangaTitle = "Manga $mangaId",
            chapterId = chapterId,
            chapterName = "Chapter $chapterId",
            scanlator = null,
            chapterUrl = "url-$chapterId",
            read = false,
            bookmark = false,
            lastPageRead = 0L,
            sourceId = 1L,
            dateFetch = 0L,
            coverData = MangaCover(
                mangaId = mangaId,
                sourceId = 1L,
                isMangaFavorite = false,
                url = null,
                lastModified = 0L,
            ),
        ),
        downloadStateProvider = { Download.State.NOT_DOWNLOADED },
        downloadProgressProvider = { 0 },
    )

    /** A stacked group: [ids]' first update is the latest, the rest are older. */
    private fun stacked(mangaId: Long, vararg ids: Long) = UpdatesUiModel.Series(
        latest = updateItem(mangaId, ids.first()),
        older = ids.drop(1).map { updateItem(mangaId, it) },
    )

    @Test
    fun `collapsed series pass through unchanged`() {
        val list = listOf(
            UpdatesUiModel.Header(java.time.LocalDate.ofEpochDay(19_000L)),
            stacked(mangaId = 7L, 3L, 2L, 1L),
        )
        assertEquals(list, list.withExpandedSeriesChildren(emptySet()))
    }

    @Test
    fun `an expanded series lifts its older updates into top-level items after the series row`() {
        val series = stacked(mangaId = 7L, 3L, 2L, 1L)

        val out = listOf(series).withExpandedSeriesChildren(setOf(7L))

        assertEquals(3, out.size)
        assertEquals(series, out[0])
        val children = out.drop(1).map { it as UpdatesUiModel.Item }
        // Most-recent-first, the order the group holds; the children are the SAME update
        // instances, not copies, so cover loads and identities are preserved.
        assertEquals(series.older, children.map { it.item })
        assertEquals(listOf(2L, 1L), children.map { it.item.update.chapterId })
    }

    @Test
    fun `only the expanded series flattens`() {
        val expanded = stacked(mangaId = 7L, 3L, 2L)
        val collapsed = stacked(mangaId = 9L, 5L, 4L)

        val out = listOf(expanded, collapsed).withExpandedSeriesChildren(setOf(7L))

        // series 7 + its one child + series 9, still stacked.
        assertEquals(3, out.size)
        assertTrue(out[0] is UpdatesUiModel.Series)
        assertEquals(2L, (out[1] as UpdatesUiModel.Item).item.update.chapterId)
        val second = out[2] as UpdatesUiModel.Series
        assertEquals(9L, second.latest.update.mangaId)
        assertEquals(listOf(4L), second.older.map { it.update.chapterId })
    }

    @Test
    fun `headers keep their position and expanded children file under their series`() {
        val list = listOf(
            UpdatesUiModel.Header(java.time.LocalDate.ofEpochDay(19_003L)),
            stacked(mangaId = 7L, 3L, 2L),
            UpdatesUiModel.Header(java.time.LocalDate.ofEpochDay(19_001L)),
            UpdatesUiModel.Item(updateItem(mangaId = 9L, chapterId = 9L)),
        )

        val out = list.withExpandedSeriesChildren(setOf(7L))

        // header, series 7, its child, header, plain item — a child never crosses a
        // day boundary, because it is filed directly after its own series row.
        assertEquals(5, out.size)
        assertTrue(out[0] is UpdatesUiModel.Header)
        assertTrue(out[1] is UpdatesUiModel.Series)
        assertEquals(2L, (out[2] as UpdatesUiModel.Item).item.update.chapterId)
        assertTrue(out[3] is UpdatesUiModel.Header)
        assertTrue(out[4] is UpdatesUiModel.Item)
    }

    @Test
    fun `unknown expansion ids and childless series are inert`() {
        val childless = UpdatesUiModel.Series(latest = updateItem(7L, 3L), older = emptyList())
        val list = listOf(childless, stacked(mangaId = 9L, 5L, 4L))

        val out = list.withExpandedSeriesChildren(setOf(7L, 99L))

        // Expanding a series with no older updates changes nothing, and an expansion id
        // that names no group is ignored — the list shape is the caller's contract.
        assertEquals(2, out.size)
        assertEquals(childless, out[0])
        assertTrue(out[1] is UpdatesUiModel.Series)
    }
}
