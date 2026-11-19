package ephyra.feature.updates

import ephyra.core.common.util.lang.toLocalDate
import ephyra.domain.download.model.Download
import ephyra.domain.manga.model.MangaCover
import ephyra.domain.updates.model.UpdatesWithRelations
import kotlinx.collections.immutable.toPersistentList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Tests for the updates list's stacking pass: [UpdatesViewModel.State.getUiModel] groups by series and
 * [withExpandedSeriesItems] lifts an expanded group's older updates into top-level keyed items.
 *
 * **Why these are tested directly rather than through the ViewModel.** The state flow is a `combine`
 * of four sources, one being `downloadCache.changes` — a `SharedFlow` no unit test can make emit — so
 * `combine` never produces a value and the ViewModel's `state` stays at its initial empty list however
 * `getUpdates` is stubbed. `getUiModel()` is a pure function of `State`, so constructing the `State`
 * directly tests the real logic without the flow. That is a property of the design, not a workaround.
 */
class UpdatesSeriesStackingTest {

    @Test
    fun `several updates of one series stack behind the newest`() {
        val state = stateOf(
            row(id = 3L, mangaId = 7L, dayOffset = 1),
            row(id = 2L, mangaId = 7L, dayOffset = 2),
            row(id = 1L, mangaId = 7L, dayOffset = 3),
        )

        val model = state.getUiModel()
        val series = model.filterIsInstance<UpdatesUiModel.Series>().single()

        assertEquals(3L, series.latest.update.chapterId, "the newest update must be the visible row")
        assertEquals(
            listOf(2L, 1L),
            series.older.map { it.update.chapterId },
            "older updates keep newest-first order",
        )
    }

    @Test
    fun `a series with one update does not get an expand affordance`() {
        // A single-row group rendered as Series would show a "0 more chapters" toggle, which is noise.
        val state = stateOf(
            row(id = 2L, mangaId = 8L, dayOffset = 1),
            row(id = 1L, mangaId = 7L, dayOffset = 2),
        )

        val model = state.getUiModel()

        assertEquals(2, model.filterIsInstance<UpdatesUiModel.Item>().size)
        assertTrue(
            model.none { it is UpdatesUiModel.Series },
            "two different series must produce two plain rows, not a stack",
        )
    }

    @Test
    fun `a collapsed series stays a single row`() {
        val state = stateOf(
            row(id = 3L, mangaId = 7L, dayOffset = 1),
            row(id = 2L, mangaId = 7L, dayOffset = 2),
        )

        // Not expanded: the flatMap must pass the group through untouched.
        val flattened = state.getUiModel().withExpandedSeriesItems(expandedSeriesIds = emptySet())

        // The list also carries a day header, so assert on the row kind rather than the raw size.
        assertEquals(1, flattened.filterIsInstance<UpdatesUiModel.Series>().size)
        assertTrue(
            flattened.none { it is UpdatesUiModel.Item },
            "a collapsed series must not contribute any top-level items",
        )
    }

    @Test
    fun `an expanded series lifts its older updates into top-level items`() {
        val state = stateOf(
            row(id = 3L, mangaId = 7L, dayOffset = 1),
            row(id = 2L, mangaId = 7L, dayOffset = 2),
            row(id = 1L, mangaId = 7L, dayOffset = 3),
        )

        val flattened = state.getUiModel()
            .withExpandedSeriesItems(expandedSeriesIds = setOf(7L))

        // The series row, then one top-level item per older update.
        assertEquals(1, flattened.filterIsInstance<UpdatesUiModel.Series>().size)
        assertEquals(
            listOf(2L, 1L),
            flattened.filterIsInstance<UpdatesUiModel.Item>().map { it.item.update.chapterId },
            "older updates become their own keyed lazy items, in order",
        )
        assertTrue(
            flattened.indexOfFirst { it is UpdatesUiModel.Series } <
                flattened.indexOfFirst { it is UpdatesUiModel.Item },
            "the series row must precede the children it expands",
        )
    }

    @Test
    fun `expanding one series leaves the others collapsed`() {
        val state = stateOf(
            row(id = 2L, mangaId = 7L, dayOffset = 1),
            row(id = 1L, mangaId = 7L, dayOffset = 2),
            row(id = 2L, mangaId = 8L, dayOffset = 3),
            row(id = 1L, mangaId = 8L, dayOffset = 4),
        )

        val flattened = state.getUiModel().withExpandedSeriesItems(expandedSeriesIds = setOf(7L))

        assertEquals(2, flattened.filterIsInstance<UpdatesUiModel.Series>().size, "both stay stacked")
        // Series 7 contributes one extra item (its single older update); series 8 contributes none.
        assertEquals(
            listOf(7L),
            flattened.filterIsInstance<UpdatesUiModel.Item>().map { it.item.update.mangaId }.distinct(),
            "only the expanded series contributes extra items",
        )
    }

    @Test
    fun `headers are inserted after grouping so a stack is filed under its newest day`() {
        // The series spans two days; the group must land on the day of its newest update rather than
        // being split across days by its older chapter. This is why grouping precedes separators.
        val state = stateOf(
            row(id = 2L, mangaId = 7L, dayOffset = 1),
            row(id = 1L, mangaId = 7L, dayOffset = 3),
        )

        val model = state.getUiModel()

        assertEquals(1, model.filterIsInstance<UpdatesUiModel.Series>().size)
        val seriesIndex = model.indexOfFirst { it is UpdatesUiModel.Series }
        val headerBefore = model.take(seriesIndex)
            .filterIsInstance<UpdatesUiModel.Header>()
            .lastOrNull()
        assertEquals(
            dateFor(1),
            headerBefore?.date,
            "the group is filed under its newest update's day",
        )
    }

    @Test
    fun `an empty list produces no rows`() {
        assertTrue(stateOf().getUiModel().isEmpty())
    }

    private fun stateOf(vararg items: UpdatesItem) = UpdatesViewModel.State(
        isLoading = false,
        items = items.toPersistentList(),
    )

    private fun dateFor(dayOffset: Int): LocalDate =
        epochMilli(dayOffset).toLocalDate()

    private fun epochMilli(dayOffset: Int): Long =
        (LocalDate.of(2026, 11, 1).toEpochDay() + dayOffset) * 86_400_000L

    /** A row whose `dateFetch` is [dayOffset] days after 2026-11-01, so ordering is explicit. */
    private fun row(id: Long, mangaId: Long, dayOffset: Int): UpdatesItem =
        UpdatesItem(
            update = UpdatesWithRelations(
                mangaId = mangaId,
                mangaTitle = "Manga $mangaId",
                chapterId = id,
                chapterName = "Chapter $id",
                scanlator = null,
                chapterUrl = "https://example.com/$mangaId/$id",
                read = false,
                bookmark = false,
                lastPageRead = 0L,
                sourceId = 1L,
                dateFetch = epochMilli(dayOffset),
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
}
