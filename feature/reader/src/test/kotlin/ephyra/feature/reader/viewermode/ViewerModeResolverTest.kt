package ephyra.feature.reader.viewermode

import ephyra.domain.chapter.model.Chapter
import ephyra.domain.content.model.ContentType
import ephyra.domain.manga.model.Manga
import ephyra.domain.reader.model.ReaderOrientation
import ephyra.domain.reader.model.ReadingMode
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ViewerChapters
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Pins the decision tables of the viewer-mode coordinator (R-A, Stage 3 seam 5 of the reader
 * modular redesign, 2026-11-08).
 *
 * The handler records call names, so the bodies under test are which effects run, in what order,
 * under which inputs; two members also mirror their real side-effects - saveRequestedPage writes
 * the requested page on the window's chapter and installReloadedManga serves the installed
 * manga's derived flags, exactly as the view model's overrides do against real state - so the
 * set-path tests assert the coordinator routes the same instances and sees the state a real
 * install produces. What must not drift is the reading-mode precedence (stored flag; DEFAULT +
 * resolveDefault through DefaultReadingModeResolver with the raw user default when no manga is
 * open), the orientation precedence (DEFAULT or unknown falls back, any other stored value is
 * returned raw), the set-path order (persist, gate on the window, save page, reload, install,
 * emit - orientation additionally emitting the reloaded constant), the crop-borders split, and
 * the pager-type derivation the session record now consumes.
 *
 * All fixtures are real values - no mockk - so a fixture cannot answer an unstubbed read the way
 * a strict mock can; the seam-3 lesson this test class takes for granted.
 *
 * runBlocking rather than runTest: the coordinator launches nothing - it sequences suspend
 * effects the caller already scopes - so virtual-time control would buy nothing.
 */
class ViewerModeResolverTest {

    private val calls = mutableListOf<String>()

    private var defaultReadingMode = 0

    private var defaultOrientation = 0

    private var readingModeFlag: Long? = null

    private var readerOrientationFlag: Long? = null

    private var contentType: ContentType? = ContentType.MANGA

    private var genres: List<String>? = emptyList()

    private var mangaId: Long? = 1L

    private var chapterWindow: ViewerChapters? = null

    private var cropBordersPager = false

    private var cropBordersWebtoon = true

    private var reloadResult: Manga? = null

    private val handler = object : ViewerModeEffectHandler {
        override fun defaultReadingModeValue(): Int {
            calls += "defaultMode"
            return defaultReadingMode
        }

        override fun defaultOrientationValue(): Int {
            calls += "defaultOrientation"
            return defaultOrientation
        }

        override fun mangaReadingModeFlag(): Long? {
            calls += "modeFlag"
            return readingModeFlag
        }

        override fun mangaReaderOrientationFlag(): Long? {
            calls += "orientationFlag"
            return readerOrientationFlag
        }

        override fun mangaContentType(): ContentType? {
            calls += "contentType"
            return contentType
        }

        override fun mangaGenres(): List<String>? {
            calls += "genres"
            return genres
        }

        override fun currentMangaId(): Long? {
            calls += "mangaId"
            return mangaId
        }

        override suspend fun awaitSetReadingMode(mangaId: Long, flag: Long) {
            calls += "setMode:$mangaId:$flag"
        }

        override suspend fun awaitSetOrientation(mangaId: Long, flag: Long) {
            calls += "setOrientation:$mangaId:$flag"
        }

        override fun viewerChaptersSnapshot(): ViewerChapters? {
            calls += "chapters"
            return chapterWindow
        }

        override fun saveRequestedPage(viewerChapters: ViewerChapters) {
            val currChapter = viewerChapters.currChapter
            calls += "savePage:${currChapter.chapter.lastPageRead}"
            // The real effect writes the requested page the reload must restore (the view
            // model's override); the fake mirrors that contract so the set-path tests assert
            // the coordinator routes this very window through the effect.
            currChapter.requestedPage = currChapter.chapter.lastPageRead.toInt()
        }

        override suspend fun reloadManga(mangaId: Long): Manga? {
            calls += "reload:$mangaId"
            return reloadResult
        }

        override fun installReloadedManga(manga: Manga?, viewerChapters: ViewerChapters) {
            calls += "install"
            // The view model's override copies the reloaded manga into state, and the state
            // getter is what serves the flag effects; the fake mirrors that by serving the
            // installed manga's derived flags, so the coordinator's post-install orientation
            // read sees exactly what a real install produces.
            readingModeFlag = manga?.readingMode
            readerOrientationFlag = manga?.readerOrientation
        }

        override fun emitReloadViewerChapters() {
            calls += "emitReload"
        }

        override fun emitOrientationSet(orientation: Int) {
            calls += "emitOrientation:$orientation"
        }

        override fun cropBordersEnabled(isPagerType: Boolean): Boolean {
            calls += "cropEnabled:$isPagerType"
            return if (isPagerType) cropBordersPager else cropBordersWebtoon
        }

        override fun storeCropBorders(isPagerType: Boolean, value: Boolean) {
            calls += "cropStored:$isPagerType:$value"
            if (isPagerType) cropBordersPager = value else cropBordersWebtoon = value
        }
    }

    private val coordinator = ViewerModeResolver(handler)

    @BeforeEach
    fun reset() {
        calls.clear()
        defaultReadingMode = 0
        defaultOrientation = 0
        readingModeFlag = null
        readerOrientationFlag = null
        contentType = ContentType.MANGA
        genres = emptyList()
        mangaId = 1L
        chapterWindow = null
        cropBordersPager = false
        cropBordersWebtoon = true
        reloadResult = null
    }

    private fun manga(viewerFlags: Long = 0L) = Manga.create().copy(
        id = 1L,
        viewerFlags = viewerFlags,
    )

    private fun window(lastPageRead: Long = 41L): ViewerChapters {
        val chapter = Chapter(
            id = 20L,
            mangaId = 5L,
            read = false,
            bookmark = false,
            lastPageRead = lastPageRead,
            dateFetch = 0L,
            sourceOrder = 20L,
            url = "/chapter/20",
            name = "Chapter 12.3",
            dateUpload = 0L,
            chapterNumber = 12.3,
            scanlator = null,
            lastModifiedAt = 0L,
            version = 1L,
        )
        return ViewerChapters(ReaderChapter(chapter), null, null)
    }

    @Test
    fun `a stored explicit reading mode wins and never consults the manga`() {
        readingModeFlag = ReadingMode.LEFT_TO_RIGHT.flagValue.toLong()

        assertEquals(ReadingMode.LEFT_TO_RIGHT.flagValue, coordinator.getMangaReadingMode())
        assertEquals(
            listOf("defaultMode", "modeFlag"),
            calls,
            "an explicit flag takes the else branch: no content type, no genres, no inference",
        )
    }

    @Test
    fun `a DEFAULT stored mode with no open manga falls back to the raw user default`() {
        readingModeFlag = null
        contentType = null
        defaultReadingMode = ReadingMode.RIGHT_TO_LEFT.flagValue

        assertEquals(ReadingMode.RIGHT_TO_LEFT.flagValue, coordinator.getMangaReadingMode())
        assertEquals(listOf("defaultMode", "modeFlag", "contentType"), calls)
    }

    @Test
    fun `a DEFAULT stored mode resolves through the policy with content type and genres`() {
        genres = listOf("Manhwa")

        assertEquals(
            ReadingMode.WEBTOON.flagValue,
            coordinator.getMangaReadingMode(),
            "long-strip inference is exact and normalized: Manhwa matches the strip genre set",
        )
        assertEquals(listOf("defaultMode", "modeFlag", "contentType", "genres"), calls)
    }

    @Test
    fun `long-strip inference does not apply to content that cannot render a strip`() {
        contentType = ContentType.NOVEL
        genres = listOf("manhwa")

        assertEquals(
            ReadingMode.DEFAULT.flagValue,
            coordinator.getMangaReadingMode(),
            "a strip genre on a novel must not select the webtoon reader",
        )
    }

    @Test
    fun `resolveDefault false returns the stored flag even when it is DEFAULT`() {
        readingModeFlag = ReadingMode.DEFAULT.flagValue.toLong()

        assertEquals(ReadingMode.DEFAULT.flagValue, coordinator.getMangaReadingMode(resolveDefault = false))
        assertEquals(
            listOf("defaultMode", "modeFlag"),
            calls,
            "the caller that renders the stored value must not trigger inference",
        )
    }

    @Test
    fun `an unknown stored mode resolves to the user default through the policy`() {
        readingModeFlag = 99L
        defaultReadingMode = ReadingMode.LEFT_TO_RIGHT.flagValue
        genres = null

        assertEquals(
            ReadingMode.LEFT_TO_RIGHT.flagValue,
            coordinator.getMangaReadingMode(),
            "an unknown flag falls to DEFAULT in the flag map, so the user default applies",
        )
        assertEquals(listOf("defaultMode", "modeFlag", "contentType", "genres"), calls)
    }

    @Test
    fun `a stored orientation is returned raw and a DEFAULT one falls back`() {
        readerOrientationFlag = ReaderOrientation.FREE.flagValue.toLong()
        assertEquals(ReaderOrientation.FREE.flagValue, coordinator.getMangaOrientation())
        assertEquals(listOf("defaultOrientation", "orientationFlag"), calls)

        calls.clear()
        readerOrientationFlag = null
        defaultOrientation = ReaderOrientation.LANDSCAPE.flagValue
        assertEquals(ReaderOrientation.LANDSCAPE.flagValue, coordinator.getMangaOrientation())
    }

    @Test
    fun `an unknown stored orientation resolves to the user default, not to itself`() {
        readerOrientationFlag = 99L
        defaultOrientation = ReaderOrientation.LANDSCAPE.flagValue

        assertEquals(
            ReaderOrientation.LANDSCAPE.flagValue,
            coordinator.getMangaOrientation(),
            "fromPreference maps unknown values to DEFAULT, so they resolve rather than echo",
        )
    }

    @Test
    fun `setMangaReadingMode persists the flag then rebuilds the window in the old order`() {
        mangaId = 5L
        chapterWindow = window(lastPageRead = 41L)
        reloadResult = manga(viewerFlags = ReadingMode.LEFT_TO_RIGHT.flagValue.toLong())

        runBlocking { coordinator.setMangaReadingMode(ReadingMode.LEFT_TO_RIGHT) }

        assertEquals(
            listOf(
                "mangaId",
                "setMode:5:1",
                "chapters",
                "savePage:41",
                "reload:5",
                "install",
                "emitReload",
            ),
            calls,
        )
        assertEquals(41, chapterWindow!!.currChapter.requestedPage, "the saved page rides the window into the reload")
    }

    @Test
    fun `setMangaReadingMode stops at the manga gate`() {
        mangaId = null
        chapterWindow = window()

        runBlocking { coordinator.setMangaReadingMode(ReadingMode.WEBTOON) }

        assertEquals(listOf("mangaId"), calls, "no manga means no flag write and no rebuild")
    }

    @Test
    fun `setMangaReadingMode persists the flag but skips the rebuild without a window`() {
        mangaId = 5L
        chapterWindow = null

        runBlocking { coordinator.setMangaReadingMode(ReadingMode.WEBTOON) }

        assertEquals(
            listOf("mangaId", "setMode:5:4", "chapters"),
            calls,
            "the flag write is not undone by a missing window - exactly the old null-check shape",
        )
    }

    @Test
    fun `setMangaOrientationType applies the reloaded orientation to the window after install`() {
        mangaId = 5L
        chapterWindow = window(lastPageRead = 41L)
        reloadResult = manga(viewerFlags = ReaderOrientation.LANDSCAPE.flagValue.toLong())

        runBlocking { coordinator.setMangaOrientationType(ReaderOrientation.LANDSCAPE) }

        assertEquals(
            listOf(
                "mangaId",
                "setOrientation:5:24",
                "chapters",
                "savePage:41",
                "reload:5",
                "install",
                "defaultOrientation",
                "orientationFlag",
                "emitOrientation:24",
                "emitReload",
            ),
            calls,
            "the emitted constant is read back from the installed state, between install and reload",
        )
        assertEquals(41, chapterWindow!!.currChapter.requestedPage)
    }

    @Test
    fun `toggleCropBorders inverts and stores the pager preference`() {
        readingModeFlag = ReadingMode.LEFT_TO_RIGHT.flagValue.toLong()
        cropBordersPager = false

        assertTrue(coordinator.toggleCropBorders())
        assertTrue(cropBordersPager)
        assertEquals(
            listOf("defaultMode", "modeFlag", "cropEnabled:true", "cropStored:true:true"),
            calls,
        )
    }

    @Test
    fun `toggleCropBorders targets the webtoon preference for a strip mode`() {
        readingModeFlag = ReadingMode.WEBTOON.flagValue.toLong()
        cropBordersWebtoon = true

        assertFalse(coordinator.toggleCropBorders())
        assertFalse(cropBordersWebtoon)
        assertEquals(
            listOf("defaultMode", "modeFlag", "cropEnabled:false", "cropStored:false:false"),
            calls,
            "a stored non-DEFAULT mode needs no content type: the split is decided by the flag alone",
        )
    }

    @Test
    fun `isPagerType follows the resolved reading mode`() {
        readingModeFlag = ReadingMode.LEFT_TO_RIGHT.flagValue.toLong()
        assertTrue(coordinator.isPagerType())

        calls.clear()
        readingModeFlag = ReadingMode.WEBTOON.flagValue.toLong()
        assertFalse(coordinator.isPagerType())

        calls.clear()
        readingModeFlag = null
        contentType = ContentType.MANGA
        genres = listOf("webtoon")
        assertFalse(
            coordinator.isPagerType(),
            "the derivation consumes the RESOLVED mode - the single home the session record uses",
        )
        assertEquals(listOf("defaultMode", "modeFlag", "contentType", "genres"), calls)
    }
}
