package ephyra.domain.content.service

import com.hippo.unifile.UniFile
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentType
import ephyra.domain.storage.service.StorageManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalContentScannerTest {

    private val storageManager = mockk<StorageManager>()
    private val scanner = LocalContentScanner(storageManager)

    @Test
    fun `testNetworkConnection with valid network protocols`() {
        assertTrue(scanner.testNetworkConnection("smb://user:pass@192.168.1.100/share"))
        assertTrue(scanner.testNetworkConnection("nfs://192.168.1.100/share"))
        assertTrue(scanner.testNetworkConnection("user:pass@192.168.1.100/share"))
        assertFalse(scanner.testNetworkConnection(""))
        assertFalse(scanner.testNetworkConnection("/storage/emulated/0"))
    }

    @Test
    fun `scanNetworkDirectory returns item if network connection matches`() {
        val result = scanner.scanNetworkDirectory("smb://192.168.1.100/share", "Anime/Frieren Anime")
        assertEquals(1, result.size)
        val item = result.first()
        assertEquals("Frieren Anime", item.title)
        assertEquals(ContentType.ANIME, item.contentType)
        assertEquals("smb://192.168.1.100/share/Anime/Frieren Anime", item.url)
    }

    @Test
    fun `scanNetworkDirectory returns empty list if network connection is invalid`() {
        val result = scanner.scanNetworkDirectory("/local/path", "Anime/Frieren")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `scanDefaultLocalDirectory emits empty when base directory is null`() = runBlocking {
        every { storageManager.getLocalSourceDirectory() } returns null
        val result = scanner.scanDefaultLocalDirectory().first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `scanDirectory parses directories as ContentItems`() {
        val mockBaseDir = mockk<UniFile>()
        val mockSeriesDir1 = mockk<UniFile>()
        val mockSeriesDir2 = mockk<UniFile>()

        every { mockBaseDir.listFiles() } returns arrayOf(mockSeriesDir1, mockSeriesDir2)

        every { mockSeriesDir1.isDirectory } returns true
        every { mockSeriesDir1.name } returns "One Piece"
        every { mockSeriesDir1.listFiles() } returns emptyArray()

        every { mockSeriesDir2.isDirectory } returns true
        every { mockSeriesDir2.name } returns "Frieren Anime"
        every { mockSeriesDir2.listFiles() } returns emptyArray()

        val results = scanner.scanDirectory(mockBaseDir)
        assertEquals(2, results.size)

        val onePiece = results.first { it.title == "One Piece" }
        assertEquals(ContentType.MANGA, onePiece.contentType)

        val frieren = results.first { it.title == "Frieren Anime" }
        assertEquals(ContentType.ANIME, frieren.contentType)
    }

    @Test
    fun `scanContentUnits extracts chapters from directory`() {
        val mockSeriesDir = mockk<UniFile>()
        val mockChapterFile1 = mockk<UniFile>()
        val mockChapterFile2 = mockk<UniFile>()

        every { mockSeriesDir.listFiles() } returns arrayOf(mockChapterFile1, mockChapterFile2)

        every { mockChapterFile1.isDirectory } returns false
        every { mockChapterFile1.name } returns "One Piece Ch. 001.cbz"
        every { mockChapterFile1.lastModified() } returns 1000L

        every { mockChapterFile2.isDirectory } returns false
        every { mockChapterFile2.name } returns "One Piece Ch. 002 - Red.cbz"
        every { mockChapterFile2.lastModified() } returns 2000L

        val seriesItem = ContentItem(
            id = 42L,
            sourceId = 0L,
            url = "One Piece",
            title = "One Piece",
            author = null,
            artist = null,
            description = null,
            genres = emptyList(),
            status = ephyra.domain.content.model.ContentStatus.Unknown,
            thumbnailUrl = null,
            contentType = ContentType.MANGA,
            initialized = true,
        )

        val units = scanner.scanContentUnits(mockSeriesDir, seriesItem)
        assertEquals(2, units.size)

        val unit1 = units.first { it.number == 1.0 }
        assertEquals("One Piece Ch. 001.cbz", unit1.title)
        assertEquals(1000L, unit1.dateUpload)

        val unit2 = units.first { it.number == 2.0 }
        assertEquals("Ch. 2.0 - Red", unit2.title)
        assertEquals(2000L, unit2.dateUpload)
    }
}
