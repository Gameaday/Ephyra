package ephyra.feature.player

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * UI State for the Video Player.
 */
data class PlayerUiState(
    val title: String = "",
    val sourceName: String = "",
    val isLoading: Boolean = true,
    val duration: Long = 0L,
    val currentPosition: Long = 0L,
    val isPlaying: Boolean = false,
    val streamUrl: String? = null,
    val errorMessage: String? = null,
)

/**
 * UI Events for the Video Player.
 */
sealed interface PlayerUiEvent {
    data class PlayPause(val play: Boolean) : PlayerUiEvent
    data class SeekTo(val positionMs: Long) : PlayerUiEvent
    data object Retry : PlayerUiEvent
}

/**
 * ViewModel for the Anime/Video playback flow.
 */
@HiltViewModel
class VideoPlayerViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /**
     * Initializes the player state with content information.
     */
    fun initPlayer(title: String, streamUrl: String) {
        _uiState.value = PlayerUiState(
            title = title,
            streamUrl = streamUrl,
            isLoading = false,
        )
    }

    /**
     * Dispatcher for events coming from the video player screen composable.
     */
    fun onEvent(event: PlayerUiEvent) {
        when (event) {
            is PlayerUiEvent.PlayPause -> {
                _uiState.value = _uiState.value.copy(isPlaying = event.play)
            }
            is PlayerUiEvent.SeekTo -> {
                _uiState.value = _uiState.value.copy(currentPosition = event.positionMs)
            }
            PlayerUiEvent.Retry -> {
                _uiState.value = _uiState.value.copy(errorMessage = null, isLoading = true)
            }
        }
    }
}
