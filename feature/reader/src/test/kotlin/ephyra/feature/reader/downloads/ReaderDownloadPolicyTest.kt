package ephyra.feature.reader.downloads

import ephyra.domain.chapter.model.Chapter
import ephyra.domain.download.model.Download
import ephyra.domain.manga.model.Manga
import ephyra.feature.reader.model.ReaderChapter
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Pins the decision tables of the download-seam coordinator (R-A, Stage 3 seam 3 of the
 * reader modular redesign, 2026-11-08).
 *
 * The handler records call names, so the bodies under test are which effects run, in what
 * order, under which inputs - the view model's implementations are the moved production code
 * and are exercised by the app suite; what must not drift is the gate order of the
 * download-ahead path, the remove-after-read slot arithmetic and the queue-cancel. The
 * duplicate-collapsing and amount-cap shaping of the download-ahead list previously lived
 * beside the view model's queue call; the tables here keep the single copy honest.
 *
 * runBlocking rather than runTest: the coordinator launches nothing - it sequences suspend
 * effects the caller already scopes - so virtual-time control would buy nothing.
 */
class ReaderDownloadPolicyTest {

    private val calls = mutableListOf<String>()

    private var mangaResult: Manga? = mockk()

    private var currentLoadsFromDownload = true

    private var nextChapterResult: ReaderChapter? = null

    private var downloadAheadAmount = 2

    private var nextChapterDownloaded = true

    private var skipDuplicates = false

    private var chaptersAfterResult: List<Chapter> = emptyList()

    private var removeAfterReadSlots = -1

    private var chapterListResult: List<ReaderChapter> = emptyList()

    private var queuedDownloadResult: Download? = null

    private val handler = object : ReaderDownloadEffectHandler {
        override fun currentManga(): Manga? {
            calls += "manga"
            return mangaResult
        }

        override fun nextChapter(): ReaderChapter? {
            calls += "nextChapter"
            return nextChapterResult
        }

        override fun currentChapterLoadsFromDownload(): Boolean {
            calls += "fromDownload"
            return currentLoadsFromDownload
        }

        override suspend fun downloadAheadAmount(): Int {
            calls += "amount"
            return downloadAheadAmount
        }

        override suspend fun awaitDownloadCacheReady() {
            calls += "awaitCache"
        }

        override fun isChapterDownloaded(manga: Manga, chapter: Chapter): Boolean {
            calls += "isDownloaded"
            return nextChapterDownloaded
        }

        override suspend fun skipDuplicateChapters(): Boolean {
            calls += "skipDupes"
            return skipDuplicates
        }

        override suspend fun chaptersAfter(mangaId: Long, chapterId: Long): List<Chapter> {
            calls += "chaptersAfter"
            return chaptersAfterResult
        }

        override fun downloadChapters(manga: Manga, chapters: List<Chapter>) {
            calls += "download:${chapters.map { it.id }.joinToString()}"
        }

        override fun queuedDownloadFor(chapterId: Long): Download? {
            calls += "queuedFor:$chapterId"
            return queuedDownloadResult
        }

        override fun cancelQueuedDownload(download: Download) {
            calls += "cancelQueued"
        }

        override suspend fun removeAfterReadSlots(): Int {
            calls += "slots"
            return removeAfterReadSlots
        }

        override suspend fun chapterList(): List<ReaderChapter> {
            calls += "chapterList"
            return chapterListResult
        }

        override fun clearRequeueSlot() {
            calls += "clearRequeue"
        }

        override suspend fun enqueueChapterForDeletion(manga: Manga, chapter: Chapter) {
            calls += "enqueueDelete:${chapter.id}"
        }
    }

    private val coordinator = ReaderDownloadPolicy(handler)

    @BeforeEach
    fun reset() {
        calls.clear()
        mangaResult = mockk()
        currentLoadsFromDownload = true
        nextChapterResult = null
        downloadAheadAmount = 2
        nextChapterDownloaded = true
        skipDuplicates = false
        chaptersAfterResult = emptyList()
        removeAfterReadSlots = -1
        chapterListResult = emptyList()
        queuedDownloadResult = null
    }

    private fun chapter(
        id: Long,
        number: Double,
        read: Boolean = false,
    ) = Chapter(
        id = id,
        mangaId = 1L,
        read = read,
        bookmark = false,
        lastPageRead = 0L,
        dateFetch = 0L,
        sourceOrder = id,
        url = "/chapter/$id",
        name = "Chapter $number",
        dateUpload = 0L,
        chapterNumber = number,
        scanlator = null,
        lastModifiedAt = 0L,
        version = 1L,
    )

    private fun readerChapter(chapter: Chapter): ReaderChapter = ReaderChapter(chapter)

    @Test
    fun `download ahead starts only past a quarter of the chapter`() {
        // The boundary itself does not trigger: 1 of 4 is exactly a quarter.
        assertFalse(coordinator.isInDownloadAheadRange(pageNumber = 1, pageCount = 4))
        assertTrue(coordinator.isInDownloadAheadRange(pageNumber = 2, pageCount = 4))
        assertFalse(coordinator.isInDownloadAheadRange(pageNumber = 2, pageCount = 10))
        assertTrue(coordinator.isInDownloadAheadRange(pageNumber = 3, pageCount = 10))
    }

    @Test
    fun `download ahead queues the capped tail when every gate opens`() = runBlocking {
        val next = chapter(id = 10L, number = 10.0)
        nextChapterResult = readerChapter(next)
        downloadAheadAmount = 2
        chaptersAfterResult = listOf(chapter(11L, 11.0), chapter(12L, 12.0), chapter(13L, 13.0))

        coordinator.downloadNextChapters()

        assertEquals(
            listOf(
                "manga",
                "fromDownload",
                "nextChapter",
                "amount",
                "awaitCache",
                "isDownloaded",
                "chaptersAfter",
                "skipDupes",
                "download:11, 12",
            ),
            calls,
        )
        Unit
    }

    @Test
    fun `the gates stop the path in the old order`() = runBlocking {
        coordinator.downloadNextChapters()
        assertEquals(listOf<String>(), calls, "no manga means no gate even runs")

        mangaResult = mockk()
        currentLoadsFromDownload = false
        coordinator.downloadNextChapters()
        assertEquals(listOf("manga", "fromDownload"), calls, "an online chapter never downloads ahead")

        calls.clear()
        nextChapterResult = null
        coordinator.downloadNextChapters()
        assertEquals(listOf("manga", "fromDownload", "nextChapter"), calls)

        calls.clear()
        nextChapterResult = readerChapter(chapter(10L, 10.0))
        downloadAheadAmount = 0
        coordinator.downloadNextChapters()
        assertEquals(listOf("manga", "fromDownload", "nextChapter", "amount"), calls, "amount 0 stops before the cache")

        calls.clear()
        downloadAheadAmount = 2
        nextChapterDownloaded = false
        coordinator.downloadNextChapters()
        assertEquals(
            listOf("manga", "fromDownload", "nextChapter", "amount", "awaitCache", "isDownloaded"),
            calls,
            "an undownloaded next chapter stops before the list shaping",
        )
        Unit
    }

    @Test
    fun `skip-duplicate shaping collapses twins before the amount cap`() = runBlocking {
        nextChapterResult = readerChapter(chapter(10L, 10.0))
        skipDuplicates = true
        chaptersAfterResult = listOf(
            chapter(11L, 12.3),
            chapter(12L, 12.300000190734863),
            chapter(13L, 13.0),
        )
        downloadAheadAmount = 2

        coordinator.downloadNextChapters()

        assertEquals("download:11, 13", calls.last(), "the restored twin collapses, then the cap takes 2")
        Unit
    }

    @Test
    fun `the queue-cancel returns the held download and cancels it`() {
        val queued = mockk<Download>()
        queuedDownloadResult = queued

        val held = coordinator.cancelQueuedDownloadOf(readerChapter(chapter(7L, 7.0)))

        assertSame(queued, held)
        assertEquals(listOf("queuedFor:7", "cancelQueued"), calls)
    }

    @Test
    fun `the queue-cancel is a no-op for a chapter that was never queued`() {
        assertNull(coordinator.cancelQueuedDownloadOf(readerChapter(chapter(7L, 7.0))))
        assertEquals(listOf("queuedFor:7"), calls)
    }

    @Test
    fun `remove-after-read schedules the chapter slots behind the completed one`() = runBlocking {
        removeAfterReadSlots = 2
        val first = readerChapter(chapter(1L, 1.0, read = true))
        val middle = readerChapter(chapter(2L, 2.0))
        val last = readerChapter(chapter(3L, 3.0))
        chapterListResult = listOf(first, middle, last)

        coordinator.removeAfterRead(last)

        assertEquals(
            listOf("slots", "chapterList", "clearRequeue", "manga", "enqueueDelete:1"),
            calls,
        )
        Unit
    }

    @Test
    fun `remove-after-read is disabled at minus one slots`() = runBlocking {
        removeAfterReadSlots = -1

        coordinator.removeAfterRead(readerChapter(chapter(1L, 1.0)))

        assertEquals(listOf("slots"), calls)
        Unit
    }

    @Test
    fun `an unread deletion target is never scheduled`() = runBlocking {
        removeAfterReadSlots = 2
        val first = readerChapter(chapter(1L, 1.0, read = false))
        val last = readerChapter(chapter(3L, 3.0))
        chapterListResult = listOf(first, readerChapter(chapter(2L, 2.0)), last)

        coordinator.removeAfterRead(last)

        assertEquals(listOf("slots", "chapterList", "clearRequeue"), calls)
        Unit
    }

    @Test
    fun `the requeue slot clears even when no target exists`() = runBlocking {
        removeAfterReadSlots = 5
        val only = readerChapter(chapter(1L, 1.0))
        chapterListResult = listOf(only)

        coordinator.removeAfterRead(only)

        assertEquals(listOf("slots", "chapterList", "clearRequeue"), calls)
        Unit
    }

    @Test
    fun `remove-after-read without a manga schedules nothing`() = runBlocking {
        removeAfterReadSlots = 1
        mangaResult = null
        val first = readerChapter(chapter(1L, 1.0, read = true))
        val second = readerChapter(chapter(2L, 2.0))
        chapterListResult = listOf(first, second)

        coordinator.removeAfterRead(second)

        assertEquals(listOf("slots", "chapterList", "clearRequeue"), calls)
        Unit
    }
}
