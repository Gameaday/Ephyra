package ephyra.feature.updates

import ephyra.core.common.util.lang.toLocalDate
import ephyra.domain.download.model.Download
import ephyra.domain.manga.model.MangaCover
import ephyra.domain.updates.model.UpdatesWithRelations
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.collections.immutable.toPersistentList

/**
 * Tests for the Updates screen's series stacking (the History screen's #353 shape applied to
 * updates): several chapter updates of one series stack behind a single row — the latest
 * update visible, the older ones collapsed behind an expander — instead of one row per
 * chapter. [withExpandedSeriesChildren]'s flattening is covered by
 * [UpdatesExpandedChildrenTest]; these tests pin the grouping itself.
 */
class UpdatesSeriesStackingTest {

    private fun updateItem(mangaId: Long, chapterId: Long, dateFetch: Long) = UpdatesItem(
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
            dateFetch = dateFetch,
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

    private fun day(offsetDays: Long) = 1_700_000_000_000L + offsetDays * 86_400_000L

    private fun stateOf(vararg items: UpdatesItem) = UpdatesViewModel.State(
        isLoading = false,
        items = items.toList().toPersistentList(),
    )

    @Test
    fun `several updates of one series stack into a single series row, latest first`() {
        val state = stateOf(
            updateItem(mangaId = 7L, chapterId = 3L, dateFetch = day(3)),
            updateItem(mangaId = 7L, chapterId = 2L, dateFetch = day(2)),
            updateItem(mangaId = 7L, chapterId = 1L, dateFetch = day(1)),
        )

        val ui = state.getUiModel()

        // A day header precedes the group (the pre-existing header behavior), then exactly
        // ONE row for the series: the latest update, with the two older ones stacked behind.
        assertEquals(2, ui.size)
        assertTrue(ui[0] is UpdatesUiModel.Header)
        val series = ui[1] as UpdatesUiModel.Series
        assertEquals(3L, series.latest.update.chapterId)
        assertEquals(listOf(2L, 1L), series.older.map { it.update.chapterId })
    }

    @Test
    fun `a single-update series stays a plain item`() {
        val state = stateOf(updateItem(mangaId = 7L, chapterId = 1L, dateFetch = day(1)))

        val ui = state.getUiModel()

        val item = ui.filterIsInstance<UpdatesUiModel.Item>().single()
        assertEquals(1L, item.item.update.chapterId)
    }

    @Test
    fun `interleaved series keep most-recent-activity group order`() {
        val state = stateOf(
            updateItem(mangaId = 7L, chapterId = 2L, dateFetch = day(4)),
            updateItem(mangaId = 9L, chapterId = 5L, dateFetch = day(3)),
            updateItem(mangaId = 7L, chapterId = 1L, dateFetch = day(1)),
            updateItem(mangaId = 9L, chapterId = 4L, dateFetch = day(2)),
        )

        val ui = state.getUiModel()

        // Series 7's latest update is the newest overall, so its group leads; series 9's
        // latest is next. No update appears twice and none is dropped.
        val groups = ui.filterIsInstance<UpdatesUiModel.Series>()
        assertEquals(listOf(7L, 9L), groups.map { it.latest.update.mangaId })
        assertEquals(listOf(2L), groups[0].older.map { it.update.chapterId })
        assertEquals(listOf(4L), groups[1].older.map { it.update.chapterId })
        val totalRows = ui.sumOf {
            when (it) {
                is UpdatesUiModel.Series -> 1 + it.older.size
                else -> 1
            }
        }
        assertEquals(6, totalRows) // 2 headers + 4 updates
    }

    @Test
    fun `a stacked series is dated by its latest update for day headers`() {
        // Series 7's latest update is on day 3; its older update is on day 1. With stacking,
        // the group lives under day 3's header only — the older chapter does not pull a day-1
        // header out of it.
        val state = stateOf(
            updateItem(mangaId = 7L, chapterId = 2L, dateFetch = day(3)),
            updateItem(mangaId = 7L, chapterId = 1L, dateFetch = day(1)),
        )

        val ui = state.getUiModel()

        val headers = ui.filterIsInstance<UpdatesUiModel.Header>()
        assertEquals(1, headers.size)
        // Dated exactly as the latest update's own day (same conversion the header logic uses).
        assertEquals(day(3).toLocalDate(), headers[0].date)
    }
}
