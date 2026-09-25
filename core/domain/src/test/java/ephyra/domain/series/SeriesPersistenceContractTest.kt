package ephyra.domain.series

import ephyra.domain.content.model.ContentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SeriesPersistenceContractTest {
    @Test
    fun `external id takes precedence over mutable url`() {
        val a = DurableSeriesIdentity("source", "stable-id", "https://one.example/series")
        val b = DurableSeriesIdentity("source", "stable-id", "https://two.example/series")

        assertEquals(a.stableKey, b.stableKey)
    }

    @Test
    fun `url is used when source has no external id`() {
        val a = DurableSeriesIdentity("source", null, "/series/1")
        val b = DurableSeriesIdentity("source", null, "/series/2")

        assertNotEquals(a.stableKey, b.stableKey)
    }

    @Test
    fun `identity rejects blank source and url`() {
        assertThrows(IllegalArgumentException::class.java) {
            DurableSeriesIdentity("", url = "/series")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DurableSeriesIdentity("source", url = "")
        }
    }

    @Test
    fun `snapshot requires a title and positive source revision`() {
        val identity = DurableSeriesIdentity("source", url = "/series")
        assertThrows(IllegalArgumentException::class.java) {
            DurableSeriesSnapshot(identity, title = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DurableSeriesSnapshot(identity, title = "Series", sourceRevision = 0)
        }
    }

    @Test
    fun `library membership is explicit and not part of upsert result`() {
        val identity = DurableSeriesIdentity("source", url = "/series", contentType = ContentType.BOOK)
        val snapshot = DurableSeriesSnapshot(identity, title = "Series")
        val record = DurableSeriesRecord("local-1", snapshot, inLibrary = false)
        val result: SeriesUpsertResult = SeriesUpsertResult.Created(record)

        assertEquals(record, (result as SeriesUpsertResult.Created).record)
        assertEquals(false, result.record.inLibrary)
    }
}
