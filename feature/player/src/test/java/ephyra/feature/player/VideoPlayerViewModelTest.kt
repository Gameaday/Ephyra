package ephyra.feature.player

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlayerViewModelTest {

    @Test
    fun `initial state is loading with default values`() {
        val viewModel = VideoPlayerViewModel()
        val state = viewModel.state.value

        assertTrue(state.isLoading)
        assertFalse(state.isPlaying)
        assertEquals(0L, state.currentPosition)
        assertEquals(0L, state.duration)
        assertNull(state.streamUrl)
        assertNull(state.errorMessage)
    }

    @Test
    fun `init player updates title, streamUrl and loading state`() = runTest {
        val viewModel = VideoPlayerViewModel()

        viewModel.state.test {
            awaitItem()

            viewModel.onEvent(PlayerUiEvent.Init("Episode 1", "https://video.mp4"))

            val updated = awaitItem()
            assertEquals("Episode 1", updated.title)
            assertEquals("https://video.mp4", updated.streamUrl)
            assertFalse(updated.isLoading)
        }
    }

    @Test
    fun `events update playback state correctly`() = runTest {
        val viewModel = VideoPlayerViewModel()
        viewModel.onEvent(PlayerUiEvent.Init("Episode 1", "https://video.mp4"))

        viewModel.state.test {
            awaitItem()

            viewModel.onEvent(PlayerUiEvent.PlayPause(true))
            val playingState = awaitItem()
            assertTrue(playingState.isPlaying)

            viewModel.onEvent(PlayerUiEvent.SeekTo(5000L))
            val seekState = awaitItem()
            assertEquals(5000L, seekState.currentPosition)

            viewModel.onEvent(PlayerUiEvent.Retry)
            val retryState = awaitItem()
            assertTrue(retryState.isLoading)
            assertNull(retryState.errorMessage)
        }
    }
}
