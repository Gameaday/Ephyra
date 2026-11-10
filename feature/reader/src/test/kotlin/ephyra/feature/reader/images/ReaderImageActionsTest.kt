package ephyra.feature.reader.images

import android.net.Uri
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.manga.model.Manga
import ephyra.feature.reader.ReaderViewModel
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import eu.kanade.tachiyomi.source.model.Page
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Pins the decision tables of the image-action coordinator (R-A, Stage 3 seam 2 of the
 * reader modular redesign, 2026-11-08).
 *
 * The handler records call names, so the bodies under test are which effects run, in what
 * order, under which inputs - the view model's implementations are the moved production code
 * and are exercised by the app suite; what must not drift is the guard set (ready page; manga
 * only for save/share/set-cover), the filename and relative-path rules, the cover-result
 * mapping and the blocked-hash matching table. The one deliberate behaviour note - a share
 * failure now visibly propagates instead of vanishing into dead code - is pinned here too.
 *
 * runBlocking rather than runTest: the coordinator launches nothing - it sequences suspend
 * effects the caller already scopes - so virtual-time control would buy nothing. Same
 * choice as the seam-1 coordinator test.
 */
class ReaderImageActionsTest {

    private val calls = mutableListOf<String>()

    private val picturesUri = mockk<Uri>()
    private val cacheUri = mockk<Uri>()

    private var selectedPageValue: ReaderPage? = null
    private var currentMangaValue: Manga? = null
    private var folderPerMangaValue = false
    private var pageDHashValue: Long? = 0L
    private var blockedHashesValue = emptySet<String>()
    private var saveToPicturesError: Throwable? = null
    private var saveToCacheError: Throwable? = null
    private var applyCoverError: Throwable? = null
    private var addBlockedHashError: Throwable? = null
    private var lastExportedUri: Uri? = null

    private val effects = object : ReaderImageEffectHandler {
        override fun selectedPage(): ReaderPage? = selectedPageValue

        override fun currentManga(): Manga? = currentMangaValue

        override fun folderPerManga(): Boolean = folderPerMangaValue

        override fun resetSaveNotification() {
            calls += "resetSaveNotification"
        }

        override fun showSaveNotificationError(message: String?) {
            calls += "showSaveNotificationError:$message"
        }

        override fun saveToPictures(page: ReaderPage, name: String, relativePath: String): Uri {
            calls += "saveToPictures:name=$name,relPath=$relativePath"
            saveToPicturesError?.let { throw it }
            return picturesUri
        }

        override fun saveToCache(page: ReaderPage, name: String): Uri {
            calls += "saveToCache:name=$name"
            saveToCacheError?.let { throw it }
            return cacheUri
        }

        override fun clearShareCache() {
            calls += "clearShareCache"
        }

        override fun reportSavedImage(result: ReaderViewModel.SaveImageResult) {
            calls += when (result) {
                is ReaderViewModel.SaveImageResult.Success -> "reportSavedImage:Success"
                is ReaderViewModel.SaveImageResult.Error -> "reportSavedImage:Error"
            }
        }

        override fun reportImageExported(uri: Uri, page: ReaderPage, copyToClipboard: Boolean) {
            calls += "reportImageExported:copy=$copyToClipboard"
            lastExportedUri = uri
        }

        override suspend fun applyCover(manga: Manga, stream: () -> InputStream) {
            calls += "applyCover"
            applyCoverError?.let { throw it }
        }

        override fun reportSetCoverResult(result: ReaderViewModel.SetAsCoverResult) {
            calls += "reportSetCoverResult:$result"
        }

        override suspend fun pageDHash(page: ReaderPage): Long? {
            calls += "pageDHash"
            return pageDHashValue
        }

        override suspend fun blockedHashes(): Set<String> {
            calls += "blockedHashes"
            return blockedHashesValue
        }

        override suspend fun addBlockedHash(hex: String) {
            calls += "addBlockedHash:$hex"
            addBlockedHashError?.let { throw it }
        }

        override suspend fun removeBlockedHash(hex: String): Boolean {
            calls += "removeBlockedHash:$hex"
            return false
        }

        override fun reportBlockResult(result: ReaderViewModel.BlockPageResult) {
            calls += when (result) {
                is ReaderViewModel.BlockPageResult.Success -> "reportBlockResult:${result.hex}"
                ReaderViewModel.BlockPageResult.Error -> "reportBlockResult:Error"
            }
        }

        override fun logError(message: String, error: Throwable) {
            calls += "logError:$message"
        }
    }

    private val actions = ReaderImageActions(effects)

    @BeforeEach
    fun reset() {
        calls.clear()
        selectedPageValue = null
        currentMangaValue = null
        folderPerMangaValue = false
        pageDHashValue = 0L
        blockedHashesValue = emptySet()
        saveToPicturesError = null
        saveToCacheError = null
        applyCoverError = null
        addBlockedHashError = null
        lastExportedUri = null
    }

    private fun manga(title: String = "T", favorite: Boolean = false, source: Long = 1L): Manga {
        val mock = mockk<Manga>()
        every { mock.title } returns title
        every { mock.favorite } returns favorite
        every { mock.source } returns source
        return mock
    }

    private fun page(
        status: Page.State = Page.State.Ready,
        stream: (() -> InputStream)? = { ByteArrayInputStream(ByteArray(0)) },
    ): ReaderPage =
        ReaderPage(0, "/a.jpg").apply {
            this.status = status
            this.resource.stream = stream
            this.chapter = ReaderChapter(Chapter.create().copy(name = "C"))
        }

    @Test
    fun `guards do nothing when no page is selected`() = runBlocking {
        actions.saveImage()
        actions.shareImage(copyToClipboard = false)
        actions.setAsCover()
        actions.blockPage()
        assertNull(actions.findMatchingBlockedHash())
        assertTrue(calls.isEmpty())
        Unit
    }

    @Test
    fun `guards reject a page that is not ready`() = runBlocking {
        selectedPageValue = page(status = Page.State.Queue)
        currentMangaValue = manga()
        actions.saveImage()
        actions.shareImage(copyToClipboard = false)
        actions.setAsCover()
        actions.blockPage()
        assertNull(actions.findMatchingBlockedHash())
        assertTrue(calls.isEmpty())
        Unit
    }

    @Test
    fun `save, share and set-cover require an open manga`() = runBlocking {
        selectedPageValue = page()
        currentMangaValue = null
        actions.saveImage()
        actions.shareImage(copyToClipboard = false)
        actions.setAsCover()
        assertTrue(calls.isEmpty())
        Unit
    }

    @Test
    fun `block and hash-match never required an open manga and still do not`() = runBlocking {
        selectedPageValue = page()
        currentMangaValue = null
        blockedHashesValue = setOf("0000000000000000")
        actions.blockPage()
        assertEquals("0000000000000000", actions.findMatchingBlockedHash())
        assertEquals(
            listOf(
                "pageDHash",
                "addBlockedHash:0000000000000000",
                "reportBlockResult:0000000000000000",
                "pageDHash",
                "blockedHashes",
            ),
            calls,
        )
        Unit
    }

    @Test
    fun `save image resets the notification, saves under the built name, reports success`() = runBlocking {
        selectedPageValue = page()
        currentMangaValue = manga()
        actions.saveImage()
        assertEquals(
            listOf(
                "resetSaveNotification",
                "saveToPictures:name=T - C - 1,relPath=",
                "reportSavedImage:Success",
            ),
            calls,
        )
        Unit
    }

    @Test
    fun `save image uses the manga folder as relative path when the preference is on`() = runBlocking {
        selectedPageValue = page()
        currentMangaValue = manga()
        folderPerMangaValue = true
        actions.saveImage()
        assertEquals(
            listOf(
                "resetSaveNotification",
                "saveToPictures:name=T - C - 1,relPath=T",
                "reportSavedImage:Success",
            ),
            calls,
        )
        Unit
    }

    @Test
    fun `save image failure reports the notification error and error result`() = runBlocking {
        selectedPageValue = page()
        currentMangaValue = manga()
        saveToPicturesError = RuntimeException("boom")
        actions.saveImage()
        assertEquals(
            listOf(
                "resetSaveNotification",
                "saveToPictures:name=T - C - 1,relPath=",
                "showSaveNotificationError:boom",
                "reportSavedImage:Error",
            ),
            calls,
        )
        Unit
    }

    @Test
    fun `share image clears the cache, saves and reports the export`() = runBlocking {
        selectedPageValue = page()
        currentMangaValue = manga()
        actions.shareImage(copyToClipboard = false)
        assertEquals(
            listOf(
                "clearShareCache",
                "saveToCache:name=T - C - 1",
                "reportImageExported:copy=false",
            ),
            calls,
        )
        assertEquals(cacheUri, lastExportedUri)
        Unit
    }

    @Test
    fun `share image reports copy when the clipboard is requested`() = runBlocking {
        selectedPageValue = page()
        currentMangaValue = manga()
        actions.shareImage(copyToClipboard = true)
        assertEquals(
            listOf(
                "clearShareCache",
                "saveToCache:name=T - C - 1",
                "reportImageExported:copy=true",
            ),
            calls,
        )
        Unit
    }

    @Test
    fun `share image failure propagates instead of vanishing`() {
        selectedPageValue = page()
        currentMangaValue = manga()
        saveToCacheError = RuntimeException("cache")
        assertThrows(RuntimeException::class.java) {
            runBlocking { actions.shareImage(copyToClipboard = false) }
        }
        assertFalse(calls.contains("reportImageExported:copy=false"))
    }

    @Test
    fun `set as cover stops silently without a stream`() = runBlocking {
        selectedPageValue = page(stream = null)
        currentMangaValue = manga()
        actions.setAsCover()
        assertTrue(calls.isEmpty())
        Unit
    }

    @Test
    fun `set as cover succeeds for a favorited manga`() = runBlocking {
        selectedPageValue = page()
        currentMangaValue = manga(favorite = true)
        actions.setAsCover()
        assertEquals(listOf("applyCover", "reportSetCoverResult:Success"), calls)
        Unit
    }

    @Test
    fun `set as cover on an untracked manga asks to add it to the library first`() = runBlocking {
        selectedPageValue = page()
        currentMangaValue = manga(favorite = false, source = 1L)
        actions.setAsCover()
        assertEquals(listOf("applyCover", "reportSetCoverResult:AddToLibraryFirst"), calls)
        Unit
    }

    @Test
    fun `set as cover succeeds for a local-source manga`() = runBlocking {
        selectedPageValue = page()
        currentMangaValue = manga(favorite = false, source = 0L)
        actions.setAsCover()
        assertEquals(listOf("applyCover", "reportSetCoverResult:Success"), calls)
        Unit
    }

    @Test
    fun `set as cover failure reports error`() = runBlocking {
        selectedPageValue = page()
        currentMangaValue = manga()
        applyCoverError = RuntimeException("cover")
        actions.setAsCover()
        assertEquals(listOf("applyCover", "reportSetCoverResult:Error"), calls)
        Unit
    }

    @Test
    fun `block page stores the page hash and reports its hex`() = runBlocking {
        selectedPageValue = page()
        currentMangaValue = manga()
        actions.blockPage()
        assertEquals(
            listOf(
                "pageDHash",
                "addBlockedHash:0000000000000000",
                "reportBlockResult:0000000000000000",
            ),
            calls,
        )
        Unit
    }

    @Test
    fun `block page reports error when the hash cannot be computed`() = runBlocking {
        selectedPageValue = page()
        currentMangaValue = manga()
        pageDHashValue = null
        actions.blockPage()
        assertEquals(
            listOf("pageDHash", "logError:Failed to block page", "reportBlockResult:Error"),
            calls,
        )
        Unit
    }

    @Test
    fun `block page reports error when storing the hash fails`() = runBlocking {
        selectedPageValue = page()
        currentMangaValue = manga()
        addBlockedHashError = RuntimeException("pref")
        actions.blockPage()
        assertEquals(
            listOf(
                "pageDHash",
                "addBlockedHash:0000000000000000",
                "logError:Failed to block page",
                "reportBlockResult:Error",
            ),
            calls,
        )
        Unit
    }

    @Test
    fun `unblock delegates removal to the effect`() = runBlocking {
        actions.unblockPage("ffffffffffffffff")
        assertEquals(listOf("removeBlockedHash:ffffffffffffffff"), calls)
        Unit
    }

    @Test
    fun `hash match returns the first entry within the threshold`() = runBlocking {
        selectedPageValue = page()
        currentMangaValue = manga()
        blockedHashesValue = setOf("ffffffffffffffff", "0000000000000000")
        assertEquals("0000000000000000", actions.findMatchingBlockedHash())
        Unit
    }

    @Test
    fun `hash match skips malformed entries and matches the next valid one`() = runBlocking {
        selectedPageValue = page()
        currentMangaValue = manga()
        blockedHashesValue = setOf("xyz", "0000000000000000")
        assertEquals("0000000000000000", actions.findMatchingBlockedHash())
        Unit
    }

    @Test
    fun `hash match returns null when nothing matches or the hash is unavailable`() = runBlocking {
        selectedPageValue = page()
        currentMangaValue = manga()
        blockedHashesValue = setOf("ffffffffffffffff")
        assertNull(actions.findMatchingBlockedHash())
        blockedHashesValue = emptySet()
        assertNull(actions.findMatchingBlockedHash())
        pageDHashValue = null
        blockedHashesValue = setOf("0000000000000000")
        assertNull(actions.findMatchingBlockedHash())
        assertEquals(
            listOf("pageDHash", "blockedHashes", "pageDHash", "blockedHashes", "pageDHash"),
            calls,
        )
        Unit
    }

    @Test
    fun `filename combines manga title, chapter name and page number`() {
        assertEquals("T - C - 1", actions.generateFilename(manga(), page()))
    }

    @Test
    fun `filename respects the byte budget for long titles`() {
        val name = actions.generateFilename(manga(title = "X".repeat(300)), page())
        assertTrue(name.length <= 240, "expected the built name to respect the byte budget: $name")
        assertTrue(name.endsWith(" - 1"))
    }

    @Test
    fun `save relative path is the manga folder only when enabled`() {
        folderPerMangaValue = false
        assertEquals("", actions.saveRelativePath(manga()))
        folderPerMangaValue = true
        assertEquals("T", actions.saveRelativePath(manga()))
    }

    @Test
    fun `cover result maps local-or-favorite to success`() {
        assertEquals(ReaderViewModel.SetAsCoverResult.Success, actions.coverResult(manga(favorite = true)))
        assertEquals(ReaderViewModel.SetAsCoverResult.Success, actions.coverResult(manga(source = 0L)))
        assertEquals(
            ReaderViewModel.SetAsCoverResult.AddToLibraryFirst,
            actions.coverResult(manga(favorite = false, source = 1L)),
        )
    }
}
