package ephyra.source.local

import android.content.Context
import com.hippo.unifile.UniFile
import ephyra.domain.content.model.ContentPage
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.FilterSet
import ephyra.source.local.io.LocalSourceFileSystem
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LocalArchiveContentSourceTest {

    @TempDir
    lateinit var tempFolder: File

    private val context = mockk<Context>(relaxed = true)
    private val fileSystem = mockk<LocalSourceFileSystem>()
    private lateinit var source: LocalArchiveContentSource

    @BeforeEach
    fun setUp() {
        io.mockk.mockkStatic(android.text.TextUtils::class)
        every { android.text.TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>().isNullOrEmpty() }
        io.mockk.mockkStatic(android.content.res.Resources::class)
        val mockResources = mockk<android.content.res.Resources>(relaxed = true)
        val mockMetrics = android.util.DisplayMetrics()
        mockMetrics.heightPixels = 1920
        mockMetrics.widthPixels = 1080
        every { mockResources.displayMetrics } returns mockMetrics
        every { android.content.res.Resources.getSystem() } returns mockResources
        source = LocalArchiveContentSource(context, fileSystem)
    }

    @Test
    fun `source metadata conforms to contract`() {
        assertEquals("local-archive", source.id)
        assertEquals("Local Archives (SAF)", source.name)
        assertTrue(source.supportedTypes.contains(ContentType.MANGA))
        assertTrue(source.supportedTypes.contains(ContentType.BOOK))
    }

    @Test
    fun `getCatalog filters and sorts manga directories`() = runTest {
        val dir1 = mockk<UniFile> {
            every { isDirectory } returns true
            every { name } returns "Berserk"
            every { lastModified() } returns 1000L
        }
        val dir2 = mockk<UniFile> {
            every { isDirectory } returns true
            every { name } returns "Attack on Titan"
            every { lastModified() } returns 2000L
        }
        val hiddenDir = mockk<UniFile> {
            every { isDirectory } returns true
            every { name } returns ".nomedia"
            every { lastModified() } returns 500L
        }

        every { fileSystem.getFilesInBaseDirectory() } returns listOf(dir1, dir2, hiddenDir)

        // Query filter
        val result = source.getCatalog(1, FilterSet(query = "titan"))
        assertEquals(1, result.size)
        assertEquals("Attack on Titan", result[0].title)

        // Alphabetical sort
        val allAlphabetical = source.getCatalog(1, FilterSet(sortOrder = FilterSet.SortOrder.ALPHABETICAL))
        assertEquals(2, allAlphabetical.size)
        assertEquals("Attack on Titan", allAlphabetical[0].title)
        assertEquals("Berserk", allAlphabetical[1].title)

        // Latest sort
        val allLatest = source.getCatalog(1, FilterSet(sortOrder = FilterSet.SortOrder.LATEST))
        assertEquals(2, allLatest.size)
        assertEquals("Attack on Titan", allLatest[0].title)
        assertEquals("Berserk", allLatest[1].title)
    }

    @Test
    fun `getChapterManifest extracts chapters and parses numbers`() = runTest {
        val baseDir = mockk<UniFile>()
        val mangaDir = mockk<UniFile>()
        val ch1 = mockk<UniFile> {
            every { isDirectory } returns false
            every { name } returns "Chapter 001.cbz"
            every { lastModified() } returns 1000L
        }
        val ch2 = mockk<UniFile> {
            every { isDirectory } returns false
            every { name } returns "Chapter 002.cbz"
            every { lastModified() } returns 2000L
        }

        every { fileSystem.getBaseDirectory() } returns baseDir
        every { baseDir.findFile("Berserk") } returns mangaDir
        every { mangaDir.listFiles() } returns arrayOf(ch1, ch2)

        val manifest = source.getChapterManifest("Berserk")
        assertEquals(2, manifest.size)
        assertEquals("Chapter 001.cbz", manifest[0].title)
        assertEquals(1.0, manifest[0].number)
        assertEquals("Chapter 002.cbz", manifest[1].title)
        assertEquals(2.0, manifest[1].number)
    }

    @Test
    fun `loadPages from Directory format returns image bytes without disk extraction`() = runTest {
        val mangaFolder = File(tempFolder, "Berserk").apply { mkdirs() }
        val chapterFolder = File(mangaFolder, "Ch1").apply { mkdirs() }
        File(chapterFolder, "001.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        val baseDir = UniFile.fromFile(tempFolder)
        every { fileSystem.getBaseDirectory() } returns baseDir

        val pages = source.loadPages("Berserk/Ch1")
        assertEquals(1, pages.size)
        val page = pages[0] as ContentPage.ImagePage
        assertEquals(0, page.index)
        assertEquals(4, page.imageBytes?.size)
    }
}
