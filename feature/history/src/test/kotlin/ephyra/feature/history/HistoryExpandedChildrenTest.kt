package ephyra.feature.history

import ephyra.domain.history.model.HistoryWithRelations
import ephyra.domain.manga.model.MangaCover
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Date

/**
 * Tests for [withExpandedSeriesChildren] — the UI-layer pass that lifts an expanded
 * series' older reads into top-level keyed lazy items. The stacking itself is #353's and
 * stays covered by [HistoryViewModelTest]; this pass only changes how an *expanded* group
 * is laid out, so every test below feeds a grouped list through the function and asserts
 * the flattened shape.
 */
class HistoryExpandedChildrenTest {

    private fun entry(id: Long, mangaId: Long, readAt: Date) = HistoryWithRelations(
        id = id,
        chapterId = id,
        mangaId = mangaId,
        title = "Test Manga",
        chapterNumber = 1.0,
        readAt = readAt,
        readDuration = 0L,
        coverData = MangaCover(
            mangaId = mangaId,
            sourceId = 1L,
            isMangaFavorite = false,
            url = null,
            lastModified = 0L,
        ),
    )

    private fun day(offsetDays: Long) = Date(1_700_000_000_000L + offsetDays * 86_400_000L)

    /** [HistoryUiModel.Header] carries a [LocalDate], not the reads' [Date] timestamp. */
    private fun headerDay(offsetDays: Long) = LocalDate.ofEpochDay(19_000L + offsetDays)

    /** A stacked group: [ids]' first entry is the latest read, the rest are older. */
    private fun stacked(mangaId: Long, vararg ids: Long) = HistoryUiModel.Series(
        latest = entry(ids.first(), mangaId, day(3)),
        older = ids.drop(1).map { entry(it, mangaId, day(1)) },
    )

    @Test
    fun `collapsed series pass through unchanged`() {
        val list = listOf(
            HistoryUiModel.Header(headerDay(3)),
            stacked(mangaId = 7L, 3L, 2L, 1L),
        )
        assertEquals(list, list.withExpandedSeriesChildren(emptySet()))
    }

    @Test
    fun `an expanded series lifts its older reads into top-level items after the series row`() {
        val series = stacked(mangaId = 7L, 3L, 2L, 1L)

        val out = listOf(series).withExpandedSeriesChildren(setOf(7L))

        assertEquals(4, out.size)
        assertEquals(series, out[0])
        val children = out.drop(1).map { it as HistoryUiModel.Item }
        // Most-recent-first, the repository order the group holds; the children are the
        // SAME read instances, not copies, so cover loads and identities are preserved.
        assertEquals(series.older, children.map { it.item })
        assertEquals(listOf(2L, 1L), children.map { it.item.id })
    }

    @Test
    fun `only the expanded series flattens`() {
        val expanded = stacked(mangaId = 7L, 3L, 2L)
        val collapsed = stacked(mangaId = 9L, 5L, 4L)

        val out = listOf(expanded, collapsed).withExpandedSeriesChildren(setOf(7L))

        // series 7 + its two children + series 9, still stacked.
        assertEquals(4, out.size)
        assertTrue(out[0] is HistoryUiModel.Series)
        assertTrue(out[1] is HistoryUiModel.Item)
        assertTrue(out[2] is HistoryUiModel.Item)
        val second = out[3] as HistoryUiModel.Series
        assertEquals(9L, second.latest.mangaId)
        assertEquals(listOf(4L), second.older.map { it.id })
    }

    @Test
    fun `headers keep their position and expanded children file under their series`() {
        val list = listOf(
            HistoryUiModel.Header(headerDay(3)),
            stacked(mangaId = 7L, 3L, 2L),
            HistoryUiModel.Header(headerDay(1)),
            HistoryUiModel.Item(entry(9L, mangaId = 9L, readAt = day(1))),
        )

        val out = list.withExpandedSeriesChildren(setOf(7L))

        // header, series 7, its child, header, plain item — a child never crosses a
        // day boundary, because it is filed directly after its own series row.
        assertEquals(5, out.size)
        assertTrue(out[0] is HistoryUiModel.Header)
        assertTrue(out[1] is HistoryUiModel.Series)
        assertEquals(2L, (out[2] as HistoryUiModel.Item).item.id)
        assertTrue(out[3] is HistoryUiModel.Header)
        assertTrue(out[4] is HistoryUiModel.Item)
    }

    @Test
    fun `unknown expansion ids and childless series are inert`() {
        val childless = HistoryUiModel.Series(latest = entry(3L, 7L, day(3)), older = emptyList())
        val list = listOf(childless, stacked(mangaId = 9L, 5L, 4L))

        val out = list.withExpandedSeriesChildren(setOf(7L, 99L))

        // Expanding a series with no older reads changes nothing, and an expansion id
        // that names no group is ignored — the list shape is the caller's contract.
        assertEquals(2, out.size)
        assertEquals(childless, out[0])
        assertTrue(out[1] is HistoryUiModel.Series)
    }
}
