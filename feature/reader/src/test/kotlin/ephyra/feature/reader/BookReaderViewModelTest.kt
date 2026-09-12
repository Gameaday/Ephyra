package ephyra.feature.reader

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
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

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading when no saved state exists`() {
        val viewModel = BookReaderViewModel(SavedStateHandle(), testDispatcher)
        assertTrue(viewModel.state.value is BookReaderState.Loading)
    }

    @Test
    fun `loadBook with nonexistent file sets Error state`() = runTest {
        val viewModel = BookReaderViewModel(SavedStateHandle(), testDispatcher)

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

        val viewModel = BookReaderViewModel(SavedStateHandle(), testDispatcher)

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

            viewModel.onEvent(BookReaderEvent.SetSerif(false))
            val serifState = awaitItem() as BookReaderState.Success
            assertFalse(serifState.isSerif)

            viewModel.onEvent(BookReaderEvent.SetPaginated(true))
            val paginatedState = awaitItem() as BookReaderState.Success
            assertTrue(paginatedState.isPaginated)

            viewModel.onEvent(BookReaderEvent.ToggleToc(true))
            val tocState = awaitItem() as BookReaderState.Success
            assertTrue(tocState.showToc)

            viewModel.onEvent(BookReaderEvent.ToggleSettings(true))
            val settingsState = awaitItem() as BookReaderState.Success
            assertTrue(settingsState.showSettings)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
