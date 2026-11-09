package ephyra.feature.reader.progress

import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.model.ChapterUpdate
import ephyra.feature.reader.model.ReaderChapter
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Date

/**
 * Pins the decision tables of the progress-seam coordinator (R-A, Stage 3 seam 4 of the reader
 * modular redesign, 2026-11-08).
 *
 * The handler records call names, so the bodies under test are which effects run, in what
 * order, under which inputs; one member also mirrors its real side-effect - markChapterRead
 * replaces the chapter reference with a read = true copy, exactly as the view model's
 * override does, so the completion test can assert the coordinator routes the same instance
 * through it. The view model's implementations are the moved production code
 * and are exercised by the app suite; what must not drift is the read-timer arithmetic (the
 * timer survives a skipped or failed write and is consumed only by a successful one), the
 * history-write gate order, the track-chapter-read gate order, and the completion path's
 * ordering of read flag / tracker / remove-after-read / duplicate sweep.
 *
 * All fixtures are real values - no mockk - so a fixture cannot answer an unstubbed read the
 * way a strict mock can; the one seam-3 lesson this test class takes for granted.
 *
 * runBlocking rather than runTest: the coordinator launches nothing - it sequences suspend
 * effects the caller already scopes - so virtual-time control would buy nothing.
 */
class ReaderProgressRecorderTest {

    private val calls = mutableListOf<String>()

    private var nowResult = 1_000L

    private var incognito = false

    private var currentChapterIdResult: Long? = 1L

    private var mangaIdResult: Long? = 5L

    private var autoUpdateTrack = true

    private var markDuplicates = false

    private var unfilteredResult: List<Chapter> = emptyList()

    private var upsertThrows = false

    private val handler = object : ReaderProgressEffectHandler {
        override fun nowMillis(): Long {
            calls += "now"
            return nowResult
        }

        override fun isIncognito(): Boolean {
            calls += "incognito"
            return incognito
        }

        override fun currentChapterId(): Long? {
            calls += "chapterId"
            return currentChapterIdResult
        }

        override suspend fun upsertHistory(chapterId: Long, readAt: Date, sessionReadDuration: Long) {
            calls += "upsert:" + chapterId + ":" + readAt.time + ":" + sessionReadDuration
            if (upsertThrows) throw IllegalStateException("simulated history write failure")
        }

        override fun markChapterRead(readerChapter: ReaderChapter) {
            calls += "markRead:" + readerChapter.chapter.id
            // The real effect replaces the chapter reference with a read = true copy (the view
            // model's override); the fake mirrors that contract so the read-flag test asserts
            // the coordinator routes this very instance through the effect.
            readerChapter.chapter = readerChapter.chapter.copy(read = true)
        }

        override fun currentMangaId(): Long? {
            calls += "mangaId"
            return mangaIdResult
        }

        override fun autoUpdateTrackEnabled(): Boolean {
            calls += "autoTrack"
            return autoUpdateTrack
        }

        override fun updateTrackChapterRead(mangaId: Long, chapterNumber: Double) {
            calls += "track:" + mangaId + ":" + chapterNumber
        }

        override suspend fun removeAfterRead(readerChapter: ReaderChapter) {
            calls += "removeAfterRead:" + readerChapter.chapter.id
        }

        override suspend fun markDuplicateReadExistingEnabled(): Boolean {
            calls += "markDupes"
            return markDuplicates
        }

        override suspend fun unfilteredChapters(): List<Chapter> {
            calls += "unfiltered"
            return unfilteredResult
        }

        override suspend fun markChaptersRead(updates: List<ChapterUpdate>) {
            calls += "markReadList:" + updates.map { it.id }.joinToString()
        }
    }

    private val coordinator = ReaderProgressRecorder(handler)

    @BeforeEach
    fun reset() {
        calls.clear()
        nowResult = 1_000L
        incognito = false
        currentChapterIdResult = 1L
        mangaIdResult = 5L
        autoUpdateTrack = true
        markDuplicates = false
        unfilteredResult = emptyList()
        upsertThrows = false
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

    @Test
    fun `the read timer starts at the clock the effect reports`() {
        nowResult = 4_200L

        coordinator.restartReadTimer()

        assertEquals(listOf("now"), calls)
    }

    @Test
    fun `the history write records the measured session duration`() = runBlocking {
        nowResult = 1_000L
        coordinator.restartReadTimer()
        nowResult = 62_000L
        calls.clear()

        coordinator.recordHistory()

        assertEquals(
            listOf(
                "chapterId",
                "incognito",
                "now",
                "upsert:1:62000:61000",
            ),
            calls,
        )
        Unit
    }

    @Test
    fun `a successful history write consumes the timer`() = runBlocking {
        coordinator.restartReadTimer()
        nowResult = 62_000L
        coordinator.recordHistory()

        // The second write, without a restart, must measure zero - the timer was consumed.
        nowResult = 70_000L
        calls.clear()
        coordinator.recordHistory()

        assertEquals("upsert:1:70000:0", calls.last())
        Unit
    }

    @Test
    fun `the history write without a started timer records a zero duration`() = runBlocking {
        nowResult = 3_000L

        coordinator.recordHistory()

        assertEquals("upsert:1:3000:0", calls.last())
        Unit
    }

    @Test
    fun `no current chapter means no write and the timer survives`() = runBlocking {
        nowResult = 1_000L
        coordinator.restartReadTimer()
        currentChapterIdResult = null

        coordinator.recordHistory()
        assertEquals(listOf("now", "chapterId"), calls, "the chapter gate stops before the incognito gate")

        // The timer is not consumed by a skipped write: the next successful write measures
        // from the original start.
        currentChapterIdResult = 1L
        nowResult = 5_000L
        calls.clear()
        coordinator.recordHistory()
        assertEquals("upsert:1:5000:4000", calls.last())
        Unit
    }

    @Test
    fun `incognito mode stops the history write and keeps the timer running`() = runBlocking {
        nowResult = 1_000L
        coordinator.restartReadTimer()
        incognito = true

        coordinator.recordHistory()
        assertEquals(listOf("now", "chapterId", "incognito"), calls)

        incognito = false
        nowResult = 5_000L
        calls.clear()
        coordinator.recordHistory()
        assertEquals("upsert:1:5000:4000", calls.last())
        Unit
    }

    @Test
    fun `a failed history write does not discard the session duration`() = runBlocking {
        nowResult = 1_000L
        coordinator.restartReadTimer()
        upsertThrows = true
        nowResult = 2_000L

        try {
            coordinator.recordHistory()
            fail("the simulated history write failure must propagate, not be swallowed")
        } catch (e: IllegalStateException) {
            // expected: the write failed, so the caller sees it exactly as before
        }

        // The old code cleared the timer only after a successful write; a failed write keeps
        // the session's duration alive.
        upsertThrows = false
        nowResult = 7_000L
        calls.clear()
        coordinator.recordHistory()
        assertEquals("upsert:1:7000:6000", calls.last())
        Unit
    }

    @Test
    fun `completion marks the chapter read then runs the tracker gates in the old order`() = runBlocking {
        val readerChapter = ReaderChapter(chapter(1L, 12.3))

        coordinator.onChapterCompleted(readerChapter)

        assertEquals(
            listOf(
                "markRead:1",
                "incognito",
                "autoTrack",
                "mangaId",
                "track:5:12.3",
                "removeAfterRead:1",
                "markDupes",
            ),
            calls,
        )
        Unit
    }

    @Test
    fun `completion mutates the real chapter through the read-flag effect`() = runBlocking {
        val readerChapter = ReaderChapter(chapter(1L, 12.3))

        coordinator.onChapterCompleted(readerChapter)

        assertTrue(readerChapter.chapter.read, "the completion path must leave the chapter read")
        Unit
    }

    @Test
    fun `incognito mode skips only the tracker write`() = runBlocking {
        incognito = true
        markDuplicates = true

        coordinator.onChapterCompleted(ReaderChapter(chapter(1L, 12.3)))

        assertEquals(
            listOf(
                "markRead:1",
                "incognito",
                "removeAfterRead:1",
                "markDupes",
                "unfiltered",
                "markReadList:",
            ),
            calls,
            "the read flag, remove-after-read and duplicate sweep are not incognito-gated; " +
                "only the tracker write is",
        )
        Unit
    }

    @Test
    fun `the auto-update preference gate stops the tracker write`() = runBlocking {
        autoUpdateTrack = false

        coordinator.onChapterCompleted(ReaderChapter(chapter(1L, 12.3)))

        assertEquals(
            listOf("markRead:1", "incognito", "autoTrack", "removeAfterRead:1", "markDupes"),
            calls,
        )
        Unit
    }

    @Test
    fun `a missing manga stops the tracker write`() = runBlocking {
        mangaIdResult = null

        coordinator.onChapterCompleted(ReaderChapter(chapter(1L, 12.3)))

        assertEquals(
            listOf("markRead:1", "incognito", "autoTrack", "mangaId", "removeAfterRead:1", "markDupes"),
            calls,
        )
        Unit
    }

    @Test
    fun `the duplicate sweep marks only unread recognized twins of the completed number`() = runBlocking {
        markDuplicates = true
        unfilteredResult = listOf(
            chapter(2L, 12.3),
            chapter(3L, 12.3, read = true),
            chapter(4L, 12.300000190734863),
            chapter(5L, 13.0),
            chapter(6L, -1.0),
        )

        coordinator.onChapterCompleted(ReaderChapter(chapter(1L, 12.3)))

        assertEquals(
            "markReadList:2, 4",
            calls.last(),
            "unread twins only: the read twin, the distinct number and the unrecognized placeholder are skipped",
        )
        Unit
    }
}
