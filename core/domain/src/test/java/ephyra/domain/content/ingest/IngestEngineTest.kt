package ephyra.domain.content.ingest

import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentStatus
import ephyra.domain.content.model.ContentType
import ephyra.source.api.LocalFsSource
import ephyra.source.api.NetworkSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [IngestEngine], the orchestrator that scans local/network
 * sources, parses raw entries into [ContentItem]s, and deduplicates them.
 *
 * This is the front door of the "index content" pipeline.
 */
class IngestEngineTest {

    private val engine = IngestEngine()

    private fun rawItem(
        title: String,
        author: String? = null,
        genres: List<String> = emptyList(),
        url: String = "/$title",
    ) = ContentItem(
        id = -1L,
        sourceId = 7L,
        url = url,
        title = title,
        author = author,
        artist = null,
        description = null,
        genres = genres,
        status = ContentStatus.Unknown,
        thumbnailUrl = null,
        contentType = ContentType.MANGA,
    )

    @Test
    fun `scans local source directory and returns parsed items`() = runBlocking {
        val source = mockk<LocalFsSource>(relaxed = true)
        coEvery { source.scanDirectory("/manga") } returns listOf(
            rawItem(title = "One Piece - Chapter 1"),
            rawItem(title = "Frieren - S01E01"),
        )

        val result = engine.ingestSource(source, "/manga")

        assertEquals(2, result.size)
        val onePiece = result.first { it.title == "One Piece" }
        assertEquals(1.0, onePiece.metadata["chapter"]?.toDoubleOrNull())
        val frieren = result.first { it.title == "Frieren" }
        assertEquals(1, frieren.metadata["season"]?.toIntOrNull())
        assertEquals(1.0, frieren.metadata["episode"]?.toDoubleOrNull())
    }

    @Test
    fun `scans network source directory`() = runBlocking {
        val source = mockk<NetworkSource>(relaxed = true)
        coEvery { source.fetchRemoteDirectory("/smb-share") } returns listOf(
            rawItem(title = "Remote Series - Chapter 4"),
        )

        val result = engine.ingestSource(source, "/smb-share")

        assertEquals(1, result.size)
        assertEquals("Remote Series", result.first().title)
        assertEquals(4.0, result.first().metadata["chapter"]?.toDoubleOrNull())
    }

    @Test
    fun `deduplicates duplicates found in the same scan`() = runBlocking {
        val source = mockk<LocalFsSource>(relaxed = true)
        coEvery { source.scanDirectory(any()) } returns listOf(
            rawItem(title = "One Piece - Chapter 1", author = "Oda"),
            rawItem(title = "One Piece - Chapter 1", author = "Oda"),
        )

        val result = engine.ingestSource(source, "/manga")

        assertEquals(1, result.size)
    }

    @Test
    fun `records canonical hash metadata on each parsed item`() = runBlocking {
        val source = mockk<LocalFsSource>(relaxed = true)
        coEvery { source.scanDirectory(any()) } returns listOf(
            rawItem(title = "One Piece - Chapter 1", author = "Oda"),
        )

        val result = engine.ingestSource(source, "/manga")

        val hash = result.first().metadata["canonical_hash"].orEmpty()
        assertTrue(hash.isNotBlank())
        assertEquals(64, hash.length)
    }

    @Test
    fun `propagates source failures as errors`() = runBlocking {
        val source = mockk<LocalFsSource>(relaxed = true)
        val exception = IllegalStateException("disk error")
        coEvery { source.scanDirectory(any()) } throws exception

        // IngestEngine emits ScanFailed and rethrows the underlying error.
        var caught: Throwable? = null
        try {
            engine.ingestSource(source, "/manga")
        } catch (e: IllegalStateException) {
            caught = e
        }
        assertEquals(exception.message, caught?.message)
    }
}
