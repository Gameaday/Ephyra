package ephyra.feature.reader.viewer

import ephyra.core.common.preference.InMemoryPreferenceStore
import ephyra.domain.base.BasePreferences
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.domain.ui.UiPreferences
import ephyra.feature.reader.ReaderActivity
import ephyra.feature.reader.model.ChapterTransition
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

    private fun createViewerChapters(
        chapterId: Long,
        pageCount: Int,
        requestedPage: Int = 0,
        prevChapterId: Long? = null,
        nextChapterId: Long? = null,
    ): ViewerChapters {
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

        val prev = prevChapterId?.let { id ->
            val prevChap = Chapter.create().copy(
                id = id,
                mangaId = 1L,
                name = "Chapter $id",
                chapterNumber = id.toDouble(),
            )
            ReaderChapter(prevChap).apply { state = ReaderChapter.State.Wait }
        }

        val next = nextChapterId?.let { id ->
            val nextChap = Chapter.create().copy(
                id = id,
                mangaId = 1L,
                name = "Chapter $id",
                chapterNumber = id.toDouble(),
            )
            ReaderChapter(nextChap).apply { state = ReaderChapter.State.Wait }
        }

        return ViewerChapters(readerChapter, prev, next)
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
            viewer.targetPageRequest.collect { emittedPages.add(it.index) }
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
            viewer.targetPageRequest.collect { emittedPages.add(it.index) }
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

    @Test
    fun `WebtoonViewer items list remains stable when adjacent chapters load`() = runTest(testDispatcher) {
        val viewer = WebtoonViewer(activity, downloadManager, readerPreferences, uiPreferences, basePreferences)
        val chapters = createViewerChapters(chapterId = 2L, pageCount = 5, prevChapterId = 1L, nextChapterId = 3L)

        viewer.setChapters(chapters)
        val initialItems = viewer.itemsState.value
        // Expected: [ChapterTransition.Prev, Page 0..4, ChapterTransition.Next] = 7 items
        assertEquals(7, initialItems.size)
        assertTrue(initialItems.first() is ChapterTransition.Prev)
        assertTrue(initialItems.last() is ChapterTransition.Next)

        // Simulate background loader finishing loading the previous chapter
        val prevPages = (0 until 10).map { index ->
            ReaderPage(index = index, url = "http://prev/$index", imageUrl = "http://prev/$index.jpg").apply {
                this.chapter = chapters.prevChapter!!
            }
        }
        chapters.prevChapter!!.state = ReaderChapter.State.Loaded(prevPages)

        // Re-emit chapters (as ReaderViewModel does when background preload completes)
        viewer.setChapters(chapters)

        val updatedItems = viewer.itemsState.value
        // Items list must remain stable and NOT prepend the 10 pages of prevChapter
        assertEquals(
            7,
            updatedItems.size,
            "Items count must remain stable; prevChapter pages must not be prepended into active LazyColumn",
        )
        assertEquals(initialItems.first(), updatedItems.first())
        assertEquals(initialItems[1], updatedItems[1], "First page index must remain at index 1")
    }

    @Test
    fun `PagerViewer items list remains stable when adjacent chapters load`() = runTest(testDispatcher) {
        val viewer = L2RPagerViewer(activity, downloadManager, readerPreferences, uiPreferences)
        val chapters = createViewerChapters(chapterId = 2L, pageCount = 5, prevChapterId = 1L, nextChapterId = 3L)

        viewer.setChapters(chapters)
        val initialItems = viewer.itemsState.value
        assertEquals(7, initialItems.size)
        assertTrue(initialItems.first() is ChapterTransition.Prev)
        assertTrue(initialItems.last() is ChapterTransition.Next)

        // Simulate background loader finishing loading the previous chapter
        val prevPages = (0 until 10).map { index ->
            ReaderPage(index = index, url = "http://prev/$index", imageUrl = "http://prev/$index.jpg").apply {
                this.chapter = chapters.prevChapter!!
            }
        }
        chapters.prevChapter!!.state = ReaderChapter.State.Loaded(prevPages)

        viewer.setChapters(chapters)

        val updatedItems = viewer.itemsState.value
        assertEquals(7, updatedItems.size, "Pager items count must remain stable when adjacent chapters finish loading")
        assertTrue(updatedItems.first() is ChapterTransition.Prev, "Prev transition card must persist")
    }

    @Test
    fun `PagerViewer moveToPage sets animate false for instant seeks`() = runTest(testDispatcher) {
        val viewer = L2RPagerViewer(activity, downloadManager, readerPreferences, uiPreferences)
        val chapters = createViewerChapters(chapterId = 1L, pageCount = 10)
        viewer.setChapters(chapters)

        val targetRequests = mutableListOf<ephyra.feature.reader.viewer.pager.PagerViewer.TargetPage>()
        backgroundScope.launch {
            viewer.targetPageRequest.collect { targetRequests.add(it) }
        }

        // Programmatic seek (e.g. slider scrubbing) must NOT animate to ensure instant response
        val page5 = chapters.currChapter.pages!![5]
        viewer.moveToPage(page5)

        assertEquals(1, targetRequests.size)
        assertEquals(viewer.itemsState.value.indexOf(page5), targetRequests.first().index)
        assertEquals(false, targetRequests.first().animate, "moveToPage should specify animate = false for scrubbing")
    }
}
