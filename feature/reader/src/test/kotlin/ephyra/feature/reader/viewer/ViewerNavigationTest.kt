package ephyra.feature.reader.viewer

import ephyra.core.common.preference.InMemoryPreferenceStore
import ephyra.domain.base.BasePreferences
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.domain.ui.UiPreferences
import ephyra.feature.reader.ReaderActivity
import ephyra.feature.reader.model.ChapterTransition
import ephyra.feature.reader.model.NavigationVector
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import ephyra.feature.reader.model.ViewerChapters
import ephyra.feature.reader.viewer.pager.L2RPagerViewer
import ephyra.feature.reader.viewer.pager.R2LPagerViewer
import ephyra.feature.reader.viewer.webtoon.WebtoonViewer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    @Test
    fun `PagerViewer startingAtBeginning always positions at first page even with previous lastPageRead`() = runTest(
        testDispatcher,
    ) {
        val viewer = L2RPagerViewer(activity, downloadManager, readerPreferences, uiPreferences)
        val chapters = createViewerChapters(chapterId = 10L, pageCount = 8)
        // Simulate chapter previously read to page 5
        chapters.currChapter.chapter = chapters.currChapter.chapter.copy(lastPageRead = 5L)
        // Explicit forward navigation
        chapters.currChapter.startingAtBeginning = true

        val targetRequests = mutableListOf<ephyra.feature.reader.viewer.pager.PagerViewer.TargetPage>()
        backgroundScope.launch {
            viewer.targetPageRequest.collect { targetRequests.add(it) }
        }

        viewer.setChapters(chapters)

        assertEquals(1, targetRequests.size)
        val firstPage = chapters.currChapter.pages!!.first()
        val firstPageIdx = viewer.itemsState.value.indexOf(firstPage)
        assertEquals(firstPageIdx, targetRequests.first().index)
        assertEquals(1, firstPageIdx, "First page must follow ChapterTransition.Prev at index 0")
    }

    @Test
    fun `PagerViewer startFromEnd positions at last page`() = runTest(testDispatcher) {
        val viewer = L2RPagerViewer(activity, downloadManager, readerPreferences, uiPreferences)
        val chapters = createViewerChapters(chapterId = 11L, pageCount = 8)
        chapters.currChapter.startFromEnd = true

        val targetRequests = mutableListOf<ephyra.feature.reader.viewer.pager.PagerViewer.TargetPage>()
        backgroundScope.launch {
            viewer.targetPageRequest.collect { targetRequests.add(it) }
        }

        viewer.setChapters(chapters)

        assertEquals(1, targetRequests.size)
        val lastPage = chapters.currChapter.pages!!.last()
        val lastPageIdx = viewer.itemsState.value.indexOf(lastPage)
        assertEquals(lastPageIdx, targetRequests.first().index)
    }

    @Test
    fun `PagerViewer asynchronous page loading properly invokes moveToPage once pages arrive`() = runTest(
        testDispatcher,
    ) {
        val viewer = L2RPagerViewer(activity, downloadManager, readerPreferences, uiPreferences)
        // Start chapter with null pages (loading state)
        val chapter = Chapter.create().copy(id = 12L, mangaId = 1L, name = "Chapter 12", chapterNumber = 12.0)
        val readerChapter = ReaderChapter(chapter).apply {
            state = ReaderChapter.State.Loading
            startingAtBeginning = true
        }
        val chapters = ViewerChapters(readerChapter, null, null)

        val targetRequests = mutableListOf<ephyra.feature.reader.viewer.pager.PagerViewer.TargetPage>()
        backgroundScope.launch {
            viewer.targetPageRequest.collect { targetRequests.add(it) }
        }

        // 1. Initial emission when pages are still loading from network (pages == null)
        viewer.setChapters(chapters)
        assertEquals(0, targetRequests.size, "No target page should be emitted while pages are null")

        // 2. Network completes and pages load
        val pages = (0 until 6).map { index ->
            ReaderPage(index = index, url = "http://p/$index", imageUrl = "http://p/$index.jpg").apply {
                this.chapter = readerChapter
            }
        }
        readerChapter.state = ReaderChapter.State.Loaded(pages)

        // Re-deliver chapters as ReaderViewModel does upon loaded state
        viewer.setChapters(chapters)

        assertEquals(1, targetRequests.size, "Target page must be emitted once pages arrive")
        val firstPage = pages.first()
        assertEquals(viewer.itemsState.value.indexOf(firstPage), targetRequests.first().index)
    }

    @Test
    fun `WebtoonViewer asynchronous page loading properly invokes moveToPage once pages arrive`() = runTest(
        testDispatcher,
    ) {
        val viewer = WebtoonViewer(activity, downloadManager, readerPreferences, uiPreferences, basePreferences)
        val chapter = Chapter.create().copy(id = 13L, mangaId = 1L, name = "Chapter 13", chapterNumber = 13.0)
        val readerChapter = ReaderChapter(chapter).apply {
            state = ReaderChapter.State.Loading
            startingAtBeginning = true
        }
        val chapters = ViewerChapters(readerChapter, null, null)

        val scrollRequests = mutableListOf<Int>()
        backgroundScope.launch {
            viewer.scrollToIndexRequest.collect { scrollRequests.add(it) }
        }

        // 1. Initial emission while pages are null
        viewer.setChapters(chapters)
        assertEquals(0, scrollRequests.size)

        // 2. Network completes and pages load
        val pages = (0 until 6).map { index ->
            ReaderPage(index = index, url = "http://p/$index", imageUrl = "http://p/$index.jpg").apply {
                this.chapter = readerChapter
            }
        }
        readerChapter.state = ReaderChapter.State.Loaded(pages)

        viewer.setChapters(chapters)

        assertEquals(1, scrollRequests.size, "Target page must be emitted once pages arrive")
        val firstPage = pages.first()
        assertEquals(viewer.itemsState.value.indexOf(firstPage), scrollRequests.first())
    }

    @Test
    fun `onPageAbsorb removes absorbed page and notifies ViewModel for chapter completion`() = runTest(testDispatcher) {
        val viewer = L2RPagerViewer(activity, downloadManager, readerPreferences, uiPreferences)
        val chapters = createViewerChapters(chapterId = 20L, pageCount = 3)
        viewer.setChapters(chapters)

        val pages = chapters.currChapter.pages!!
        val page0 = pages[0]
        val page1 = pages[1]

        viewer.currentPage = page1
        viewer.onPageAbsorb(page0, page1)

        assertFalse(viewer.itemsState.value.contains(page1), "Absorbed page must be removed from items")
        assertEquals(page0, viewer.currentPage, "Active page must shift to parent page when absorbed")
        verify {
            activity.viewModel.checkChapterCompletion(page0, NavigationVector.FORWARD)
        }
    }

    @Test
    fun `toggling smartCombine off reverts absorbed pages and restores item list`() = runTest(testDispatcher) {
        readerPreferences.smartCombinePaged().set(true)
        val viewer = L2RPagerViewer(activity, downloadManager, readerPreferences, uiPreferences)
        val chapters = createViewerChapters(chapterId = 21L, pageCount = 4)
        viewer.setChapters(chapters)

        val pages = chapters.currChapter.pages!!
        val page1 = pages[1]
        page1.isAbsorbed = true
        viewer.onPageAbsorb(pages[0], page1)

        assertFalse(viewer.itemsState.value.contains(page1))

        // Toggle smart combine OFF
        readerPreferences.smartCombinePaged().set(false)

        assertFalse(page1.isAbsorbed, "Page absorption flag must be cleared on disable")
        assertTrue(viewer.itemsState.value.contains(page1), "Items list must be rebuilt to include restored page")
        viewer.destroy()
    }
}
