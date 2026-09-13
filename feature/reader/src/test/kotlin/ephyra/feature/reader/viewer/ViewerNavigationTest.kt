package ephyra.feature.reader.viewer

import ephyra.core.common.preference.InMemoryPreferenceStore
import ephyra.domain.base.BasePreferences
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.domain.ui.UiPreferences
import ephyra.feature.reader.ReaderActivity
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import ephyra.feature.reader.model.ViewerChapters
import ephyra.feature.reader.viewer.pager.L2RPagerViewer
import ephyra.feature.reader.viewer.pager.R2LPagerViewer
import ephyra.feature.reader.viewer.webtoon.WebtoonViewer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewerNavigationTest {

    private val activity: ReaderActivity = mockk(relaxed = true) {
        every { runOnUiThread(any()) } answers { firstArg<Runnable>().run() }
    }
    private val downloadManager: DownloadManager = mockk(relaxed = true)
    private val preferenceStore = InMemoryPreferenceStore()
    private val readerPreferences = ReaderPreferences(preferenceStore)
    private val uiPreferences = UiPreferences(preferenceStore)
    private val basePreferences: BasePreferences = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewerChapters(chapterId: Long, pageCount: Int, requestedPage: Int = 0): ViewerChapters {
        val chapter = Chapter.create().copy(
            id = chapterId,
            mangaId = 1L,
            name = "Chapter $chapterId",
            chapterNumber = chapterId.toDouble(),
        )
        val readerChapter = ReaderChapter(chapter)
        readerChapter.requestedPage = requestedPage
        val pagesList = (0 until pageCount).map { index ->
            ReaderPage(index = index, url = "http://page/$index", imageUrl = "http://page/$index.jpg").apply {
                this.chapter = readerChapter
            }
        }
        readerChapter.state = ReaderChapter.State.Loaded(pagesList)
        return ViewerChapters(readerChapter, null, null)
    }

    @Test
    fun `R2LPagerViewer moveLeft advances forward and moveRight retreats backward`() = runTest(testDispatcher) {
        val viewer = R2LPagerViewer(activity, downloadManager, readerPreferences, uiPreferences)
        val chapters = createViewerChapters(chapterId = 1L, pageCount = 5)
        viewer.setChapters(chapters)

        val firstPage = chapters.currChapter.pages!!.first()
        viewer.moveToPage(firstPage)

        val emittedPages = mutableListOf<Int>()
        backgroundScope.launch {
            viewer.targetPageRequest.collect { emittedPages.add(it) }
        }

        // In RTL manga, moving left moves forward in reading order (next page)
        viewer.moveLeft()
        val firstTarget = emittedPages.lastOrNull()
        assertTrue(firstTarget != null && firstTarget > viewer.itemsState.value.indexOf(firstPage))

        // Simulate viewer reaching the target page
        viewer.currentPage = viewer.itemsState.value[firstTarget!!]

        // In RTL manga, moving right moves backward in reading order (prev page)
        viewer.moveRight()
        val secondTarget = emittedPages.lastOrNull()
        assertTrue(secondTarget != null && secondTarget < firstTarget)
    }

    @Test
    fun `L2RPagerViewer moveRight advances forward and moveLeft retreats backward`() = runTest(testDispatcher) {
        val viewer = L2RPagerViewer(activity, downloadManager, readerPreferences, uiPreferences)
        val chapters = createViewerChapters(chapterId = 1L, pageCount = 5)
        viewer.setChapters(chapters)

        val firstPage = chapters.currChapter.pages!!.first()
        viewer.moveToPage(firstPage)

        val emittedPages = mutableListOf<Int>()
        backgroundScope.launch {
            viewer.targetPageRequest.collect { emittedPages.add(it) }
        }

        // In LTR, moving right moves forward (next page)
        viewer.moveRight()
        val firstTarget = emittedPages.lastOrNull()
        assertTrue(firstTarget != null && firstTarget > viewer.itemsState.value.indexOf(firstPage))

        // Simulate viewer reaching the target page
        viewer.currentPage = viewer.itemsState.value[firstTarget!!]

        // In LTR, moving left moves backward (prev page)
        viewer.moveLeft()
        val secondTarget = emittedPages.lastOrNull()
        assertTrue(secondTarget != null && secondTarget < firstTarget)
    }

    @Test
    fun `PagerViewer invokes onNextChapter and onPreviousChapter at boundaries`() = runTest(testDispatcher) {
        val viewer = L2RPagerViewer(activity, downloadManager, readerPreferences, uiPreferences)
        val chapters = createViewerChapters(chapterId = 1L, pageCount = 2)
        viewer.setChapters(chapters)

        var nextChapterCalled = false
        var prevChapterCalled = false
        viewer.onNextChapter = { nextChapterCalled = true }
        viewer.onPreviousChapter = { prevChapterCalled = true }

        // Start at first item (index 0)
        viewer.currentPage = viewer.itemsState.value.first()
        viewer.moveToPrevious()
        assertTrue(prevChapterCalled)

        // Move to last item (index count - 1)
        viewer.currentPage = viewer.itemsState.value.last()
        viewer.moveToNext()
        assertTrue(nextChapterCalled)
    }

    @Test
    fun `WebtoonViewer setChapters does not reset scroll position on subsequent emissions of same chapter`() = runTest(
        testDispatcher,
    ) {
        val viewer = WebtoonViewer(activity, downloadManager, readerPreferences, uiPreferences, basePreferences)
        val chapters = createViewerChapters(chapterId = 100L, pageCount = 10, requestedPage = 2)

        val scrollRequests = mutableListOf<Int>()
        backgroundScope.launch {
            viewer.scrollToIndexRequest.collect { scrollRequests.add(it) }
        }

        // 1. Initial load should trigger scroll to requestedPage
        viewer.setChapters(chapters)
        assertEquals(1, scrollRequests.size)

        // 2. Subsequent emission for the same chapter (e.g. background download update) must NOT re-emit scroll request
        viewer.setChapters(chapters)
        assertEquals(1, scrollRequests.size, "Same chapter emission must not re-trigger scrollToIndexRequest")
    }
}
