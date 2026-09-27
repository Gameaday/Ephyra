package ephyra.domain.download

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the page-file rule that `core:download`'s probe previously held inline with **no coverage
 * whatsoever** — that module has no Robolectric and no `isIncludeAndroidResources`, so a test of the
 * adapter would have required new test infrastructure before a single assertion could run. The rule
 * is the part that can actually be wrong, so it is here instead.
 */
class DownloadPageFilesTest {

    @Test
    fun `every reader supported image extension is a page`() {
        // Each of these is decoded somewhere in the app; the JXL case is covered on device by
        // JxlCoilBridgeDeviceTest, which is why omitting it here would be a real coverage hole.
        listOf("jpg", "jpeg", "png", "webp", "gif", "avif", "jxl").forEach { ext ->
            assertTrue(DownloadPageFiles.isPageFile("001.$ext"), "expected .$ext to be a page")
        }
    }

    @Test
    fun `the extension match is case insensitive`() {
        assertTrue(DownloadPageFiles.isPageFile("001.JPG"))
        assertTrue(DownloadPageFiles.isPageFile("001.Jpeg"))
        assertTrue(DownloadPageFiles.isPageFile("001.WEBP"))
    }

    @Test
    fun `a non image file is not a page`() {
        // comicinfo.xml and .nomedia are written into every chapter directory, so counting them
        // would inflate pageCount against the source's own page list.
        assertFalse(DownloadPageFiles.isPageFile("comicinfo.xml"))
        assertFalse(DownloadPageFiles.isPageFile("info.txt"))
        assertFalse(DownloadPageFiles.isPageFile("chapter.cbz"))
        assertFalse(DownloadPageFiles.isPageFile("video.mp4"))
    }

    @Test
    fun `a leading dot file is never a page`() {
        assertFalse(DownloadPageFiles.isPageFile(".nomedia"))
        assertFalse(DownloadPageFiles.isPageFile(".thumbnails"))
        assertFalse(DownloadPageFiles.isPageFile(".hidden.jpg"))
    }

    @Test
    fun `a file with no extension is not a page`() {
        assertFalse(DownloadPageFiles.isPageFile("README"))
        assertFalse(DownloadPageFiles.isPageFile("001."))
    }

    @Test
    fun `a null or blank name is not a page`() {
        assertFalse(DownloadPageFiles.isPageFile(null))
        assertFalse(DownloadPageFiles.isPageFile(""))
        assertFalse(DownloadPageFiles.isPageFile("   "))
    }

    @Test
    fun `only the final extension is considered`() {
        // "001.jpg.txt" is a text file, not a jpeg. Taking the first extension would count it.
        assertFalse(DownloadPageFiles.isPageFile("001.jpg.txt"))
        assertTrue(DownloadPageFiles.isPageFile("001.txt.jpg"))
    }

    @Test
    fun `a multi dot page name resolves on its real extension`() {
        assertTrue(DownloadPageFiles.isPageFile("chapter.001.png"))
    }
}
