package ephyra.domain.series

import ephyra.domain.content.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CanonicalSeriesAggregateTest {
    @Test
    fun `aggregate preserves explicit source representations without selecting one`() {
        val aggregate = CanonicalSeriesAggregate(
            seriesId = "series:1",
            contentType = ContentType.MANGA,
            title = "Series",
            representations = listOf(
                representation("series:1", "opds", "opds-id", "OPDS title"),
                representation("series:2", "other", "other-id", "Other title"),
            ),
            inLibrary = true,
        )

        assertEquals(2, aggregate.representations.size)
        assertEquals("opds", aggregate.representations.first().identity.sourceId)
        assertEquals("Other title", aggregate.representations.last().displayTitle)
    }

    @Test
    fun `aggregate rejects empty and duplicate source representations`() {
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalSeriesAggregate("series:1", ContentType.MANGA, "Series", emptyList(), true)
        }
        val identity = DurableSeriesIdentity("opds", "external", "https://opds.test/series")
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalSeriesAggregate(
                "series:1",
                ContentType.MANGA,
                "Series",
                listOf(
                    SeriesSourceRepresentation("series:1", identity, "One", null, 1L),
                    SeriesSourceRepresentation("series:2", identity, "Two", null, 1L),
                ),
                true,
            )
        }
    }

    @Test
    fun `issues are explicit and non fatal`() {
        val aggregate = CanonicalSeriesAggregate(
            seriesId = "series:1",
            contentType = ContentType.MANGA,
            title = "Series",
            representations = listOf(representation("series:1", "opds", "id", "Series")),
            inLibrary = false,
            issues = listOf(CanonicalAggregateIssue.MissingLinkedSource("link:1", "missing source")),
        )

        assertEquals(1, aggregate.issues.size)
        assertEquals(false, aggregate.inLibrary)
    }

    private fun representation(
        seriesId: String,
        sourceId: String,
        externalId: String,
        title: String,
    ) = SeriesSourceRepresentation(
        sourceSeriesId = seriesId,
        identity = DurableSeriesIdentity(sourceId, externalId, "https://$sourceId.test/series"),
        displayTitle = title,
        thumbnailUrl = null,
        revision = 1L,
    )
}
