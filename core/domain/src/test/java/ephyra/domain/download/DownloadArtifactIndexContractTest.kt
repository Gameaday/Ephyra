package ephyra.domain.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
