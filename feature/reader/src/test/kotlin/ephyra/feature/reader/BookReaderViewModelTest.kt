package ephyra.feature.reader

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import ephyra.core.common.preference.Preference
import ephyra.domain.reader.service.ReaderPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class BookReaderViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val readerPreferences: ReaderPreferences = mockk(relaxed = true)

    private val chapterPref: Preference<Int> = mockk(relaxed = true)
    private val scrollPref: Preference<Int> = mockk(relaxed = true)
    private val fontPref: Preference<Float> = mockk(relaxed = true)
    private val serifPref: Preference<Boolean> = mockk(relaxed = true)
    private val paginatedPref: Preference<Boolean> = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        coEvery { fontPref.get() } returns 18f
        every { fontPref.getSync() } returns 18f
        coEvery { serifPref.get() } returns true
        every { serifPref.getSync() } returns true
        coEvery { paginatedPref.get() } returns false
        every { paginatedPref.getSync() } returns false
        coEvery { chapterPref.get() } returns 0
        every { chapterPref.getSync() } returns 0
        coEvery { scrollPref.get() } returns 0
        every { scrollPref.getSync() } returns 0

        every { readerPreferences.bookReaderFontSize() } returns fontPref
        every { readerPreferences.bookReaderIsSerif() } returns serifPref
        every { readerPreferences.bookReaderIsPaginated() } returns paginatedPref
        every { readerPreferences.bookReaderLastChapter(any()) } returns chapterPref
        every { readerPreferences.bookReaderLastScroll(any()) } returns scrollPref
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading when no saved state exists`() {
        val viewModel = BookReaderViewModel(SavedStateHandle(), readerPreferences, testDispatcher)
        assertTrue(viewModel.state.value is BookReaderState.Loading)
    }

    @Test
    fun `loadBook with nonexistent file sets Error state`() = runTest {
        val viewModel = BookReaderViewModel(SavedStateHandle(), readerPreferences, testDispatcher)

        viewModel.state.test {
            assertEquals(BookReaderState.Loading, awaitItem())

            viewModel.loadBook("Test Book", "/nonexistent/path/book.epub")

            val state = awaitItem()
            assertTrue(state is BookReaderState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadBook with plain text file loads single chapter successfully`() = runTest {
        val tempFile = File.createTempFile("test_book", ".txt").apply {
            writeText("Line 1\nLine 2\nLine 3")
            deleteOnExit()
        }

        val viewModel = BookReaderViewModel(SavedStateHandle(), readerPreferences, testDispatcher)

        viewModel.state.test {
            assertEquals(BookReaderState.Loading, awaitItem())

            viewModel.loadBook("Plain Text Book", tempFile.absolutePath)

            val state = awaitItem() as BookReaderState.Success
            assertEquals("Plain Text Book", state.title)
            assertEquals(1, state.chapters.size)
            assertEquals("Line 1\nLine 2\nLine 3", state.chapters[0].bodyText)
            assertEquals(0, state.currentChapterIndex)
            assertFalse(state.hasPrevious)
            assertFalse(state.hasNext)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `navigation events and preference toggles update state`() = runTest {
        val tempFile = File.createTempFile("sample_book", ".txt").apply {
            writeText("Content")
            deleteOnExit()
        }

        val viewModel = BookReaderViewModel(
            SavedStateHandle(
                mapOf(
                    "title" to "Sample",
                    "bookUrl" to tempFile.absolutePath,
                ),
            ),
            readerPreferences,
            testDispatcher,
        )

        viewModel.state.test {
            val initial = awaitItem() as BookReaderState.Success
            assertEquals(18f, initial.fontSize)
            assertTrue(initial.isSerif)
            assertFalse(initial.isPaginated)

            viewModel.onEvent(BookReaderEvent.SetFontSize(24f))
            val fontState = awaitItem() as BookReaderState.Success
            assertEquals(24f, fontState.fontSize)
            verify { fontPref.set(24f) }

            viewModel.onEvent(BookReaderEvent.SetSerif(false))
            val serifState = awaitItem() as BookReaderState.Success
            assertFalse(serifState.isSerif)
            verify { serifPref.set(false) }

            viewModel.onEvent(BookReaderEvent.SetPaginated(true))
            val paginatedState = awaitItem() as BookReaderState.Success
            assertTrue(paginatedState.isPaginated)
            verify { paginatedPref.set(true) }

            viewModel.onEvent(BookReaderEvent.SaveScrollOffset(420))
            verify { scrollPref.set(420) }

            viewModel.onEvent(BookReaderEvent.ToggleToc(true))
            val tocState = awaitItem() as BookReaderState.Success
            assertTrue(tocState.showToc)

            viewModel.onEvent(BookReaderEvent.ToggleSettings(true))
            val settingsState = awaitItem() as BookReaderState.Success
            assertTrue(settingsState.showSettings)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadBook restores saved chapter and scroll offset from preferences`() = runTest {
        val tempFile = File.createTempFile("saved_progress_book", ".txt").apply {
            writeText("Some text")
            deleteOnExit()
        }

        coEvery { chapterPref.get() } returns 0
        coEvery { scrollPref.get() } returns 150

        val viewModel = BookReaderViewModel(SavedStateHandle(), readerPreferences, testDispatcher)

        viewModel.state.test {
            assertEquals(BookReaderState.Loading, awaitItem())
            viewModel.loadBook("Saved Book", tempFile.absolutePath, initialIndex = 0)

            val state = awaitItem() as BookReaderState.Success
            assertEquals(0, state.currentChapterIndex)
            assertEquals(150, state.initialScrollOffset)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
