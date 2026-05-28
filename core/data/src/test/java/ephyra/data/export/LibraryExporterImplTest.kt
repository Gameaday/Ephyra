package ephyra.data.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.domain.export.LibraryExporter
import ephyra.domain.manga.model.Manga
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LibraryExporterImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun testExportToCsv_allFields() = runTest(testDispatcher) {
        val exporter = LibraryExporterImpl(context, testDispatcher)
        val tempFile = tempFolder.newFile("export.csv")
        val uriString = tempFile.toURI().toString()

        val manga1 = Manga.create().copy(title = "Manga One", author = "Author One", artist = "Artist One")
        val manga2 = Manga.create().copy(title = "Manga Two", author = "Author Two", artist = "Artist Two")

        val options = LibraryExporter.ExportOptions(
            includeTitle = true,
            includeAuthor = true,
            includeArtist = true,
        )

        var callbackInvoked = false
        exporter.exportToCsv(uriString, listOf(manga1, manga2), options) {
            callbackInvoked = true
        }

        assertTrue(callbackInvoked)
        val fileContent = tempFile.readText()
        val expectedLines = listOf(
            "Manga One,Author One,Artist One",
            "Manga Two,Author Two,Artist Two",
        )
        val actualLines = fileContent.split("\r\n")
        assertEquals(expectedLines, actualLines)
    }

    @Test
    fun testExportToCsv_partialFields() = runTest(testDispatcher) {
        val exporter = LibraryExporterImpl(context, testDispatcher)
        val tempFile = tempFolder.newFile("export_partial.csv")
        val uriString = tempFile.toURI().toString()

        val manga1 = Manga.create().copy(title = "Manga One", author = "Author One", artist = "Artist One")
        val manga2 = Manga.create().copy(title = "Manga Two", author = "Author Two", artist = "Artist Two")

        val options = LibraryExporter.ExportOptions(
            includeTitle = true,
            includeAuthor = false,
            includeArtist = true,
        )

        var callbackInvoked = false
        exporter.exportToCsv(uriString, listOf(manga1, manga2), options) {
            callbackInvoked = true
        }

        assertTrue(callbackInvoked)
        val fileContent = tempFile.readText()
        val expectedLines = listOf(
            "Manga One,Artist One",
            "Manga Two,Artist Two",
        )
        val actualLines = fileContent.split("\r\n")
        assertEquals(expectedLines, actualLines)
    }
}
