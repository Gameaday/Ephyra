package ephyra.feature.browse.source.globalsearch

import ephyra.domain.content.model.ContentType
import ephyra.source.api.GlobalSearchState
import ephyra.source.api.MergedSourceItem
import ephyra.source.api.SearchPhase
import ephyra.source.api.SearchState
import ephyra.source.api.SourceContentItem
import ephyra.source.api.SourceId
import ephyra.source.api.SourcePage
import ephyra.source.api.SourceSearchState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TargetSearchMapperTest {
    @Test
    fun `maps target rows and preserves source provenance`() {
        val first = item("source-a", "The Title", "/a")
        val second = item("source-b", "the title", "/b")
        val state = GlobalSearchState(
            query = "title",
            search = SearchState(
                phase = SearchPhase.COMPLETED,
                mergedItems = listOf(MergedSourceItem(first, listOf(SourceId("source-a"), SourceId("source-b")))),
            ),
        )

        val result = TargetSearchMapper.map(state)

        assertEquals("title", result.query)
        assertEquals("The Title", result.rows.single().title)
        assertEquals(listOf(SourceId("source-a"), SourceId("source-b")), result.rows.single().sourceIds)
        assertTrue(result.hasPartialResults)
    }

    @Test
    fun `maps typed source status without collapsing failure kinds`() {
        val id = SourceId("source-a")
        val state = GlobalSearchState(
            search = SearchState(
                phase = SearchPhase.RUNNING,
                sources = mapOf(id to SourceSearchState.Succeeded(SourcePage(listOf(item("source-a", "A", "/a"))))),
            ),
        )

        val result = TargetSearchMapper.map(state)

        assertEquals(TargetSourceSearchStatus.Succeeded(1), result.sources.single().second)
    }

    private fun item(source: String, title: String, url: String) = SourceContentItem(
        sourceId = SourceId(source),
        url = url,
        title = title,
        contentType = ContentType.BOOK,
    )
}
