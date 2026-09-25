package ephyra.domain.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadArtifactIndexContractTest {
    @Test
    fun `verified artifact requires stable series chapter and source identity`() {
        val record = artifact()
        assertEquals("artifact:1", record.artifactId.value)
        assertEquals("chapter:1", record.chapterId)
        assertEquals("series:1", record.seriesId)
        assertEquals("opds", record.sourceId)
    }

    @Test
    fun `absolute and parent traversing paths are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { artifact(relativePath = "/downloads/series/chapter") }
        assertThrows(IllegalArgumentException::class.java) { artifact(relativePath = "series/../../outside") }
        assertThrows(IllegalArgumentException::class.java) { artifact(relativePath = "series\\..\\outside") }
    }

    @Test
    fun `empty or invalid artifact metadata is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { DownloadArtifactId("") }
        assertThrows(IllegalArgumentException::class.java) { artifact(chapterId = "") }
        assertThrows(IllegalArgumentException::class.java) { artifact(seriesId = "") }
        assertThrows(IllegalArgumentException::class.java) { artifact(sourceId = "") }
        assertThrows(IllegalArgumentException::class.java) { artifact(relativePath = "") }
        assertThrows(IllegalArgumentException::class.java) { artifact(pageCount = 0) }
        assertThrows(IllegalArgumentException::class.java) { artifact(byteSize = -1L) }
    }

    @Test
    fun `index outcomes distinguish success and missing artifact`() {
        val id = DownloadArtifactId("artifact:1")
        val success: DownloadArtifactIndexResult = DownloadArtifactIndexResult.Success(
            DownloadReconciliation.Added,
        )
        assertEquals(DownloadReconciliation.Added, (success as DownloadArtifactIndexResult.Success).change)
        assertEquals(id, (DownloadArtifactIndexResult.NotFound(id) as DownloadArtifactIndexResult.NotFound).artifactId)
    }

    @Test
    fun `missing filesystem probe does not invent a record`() {
        val result = verifyDownloadArtifact(
            indexed = null,
            expected = artifact(),
            probe = DownloadArtifactProbe(exists = false),
            verifiedAt = 2_000L,
        )
        assertEquals(DownloadArtifactVerification.Missing(DownloadArtifactId("artifact:1")), result)
    }

    @Test
    fun `valid probe publishes added record with verified timestamp`() {
        val result = verifyDownloadArtifact(
            indexed = null,
            expected = artifact(),
            probe = DownloadArtifactProbe(
                exists = true,
                relativePath = "Opds/Series/Chapter",
                container = DownloadContainer.DIRECTORY,
                pageCount = 20,
                byteSize = 4_096L,
            ),
            verifiedAt = 2_000L,
        )
        val valid = result as DownloadArtifactVerification.Valid
        assertEquals(DownloadReconciliation.Added, valid.change)
        assertEquals(2_000L, valid.record.verifiedAt)
    }

    @Test
    fun `changed filesystem facts update rather than invalidate record`() {
        val result = verifyDownloadArtifact(
            indexed = artifact(),
            expected = artifact(),
            probe = DownloadArtifactProbe(
                exists = true,
                relativePath = "Opds/Series/Renamed",
                container = DownloadContainer.CBZ,
                pageCount = 21,
                byteSize = 5_000L,
            ),
            verifiedAt = 2_000L,
        )
        val valid = result as DownloadArtifactVerification.Valid
        assertEquals(DownloadReconciliation.Updated, valid.change)
        assertEquals("Opds/Series/Renamed", valid.record.relativePath)
        assertEquals(DownloadContainer.CBZ, valid.record.container)
        assertEquals(21, valid.record.pageCount)
    }

    @Test
    fun `unsafe relative path is invalid`() {
        val result = verifyDownloadArtifact(
            indexed = null,
            expected = artifact(),
            probe = DownloadArtifactProbe(
                exists = true,
                relativePath = "/outside/chapter",
                container = DownloadContainer.DIRECTORY,
                pageCount = 1,
                byteSize = 1L,
            ),
            verifiedAt = 2_000L,
        )
        assertTrue(result is DownloadArtifactVerification.Invalid)
    }

    @Test
    fun `malformed probe is invalid`() {
        val result = verifyDownloadArtifact(
            indexed = artifact(),
            expected = artifact(),
            probe = DownloadArtifactProbe(
                exists = true,
                relativePath = "Opds/Series/Chapter",
                container = DownloadContainer.CBZ,
                pageCount = 0,
                byteSize = 0L,
            ),
            verifiedAt = 2_000L,
        )
        assertTrue(result is DownloadArtifactVerification.Invalid)
    }

    private fun artifact(
        chapterId: String = "chapter:1",
        seriesId: String = "series:1",
        sourceId: String = "opds",
        relativePath: String = "Opds/Series/Chapter",
        pageCount: Int = 20,
        byteSize: Long = 4_096L,
    ) = DownloadArtifactRecord(
        artifactId = DownloadArtifactId("artifact:1"),
        chapterId = chapterId,
        seriesId = seriesId,
        sourceId = sourceId,
        relativePath = relativePath,
        container = DownloadContainer.DIRECTORY,
        pageCount = pageCount,
        byteSize = byteSize,
        verifiedAt = 1_000L,
    )
}
