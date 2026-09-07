package ephyra.feature.player

import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.presentation.core.udf.BaseUdfViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * UI State for the Video Player.
 */
@Immutable
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
    data class Init(val title: String, val streamUrl: String) : PlayerUiEvent
    data class PlayPause(val play: Boolean) : PlayerUiEvent
    data class SeekTo(val positionMs: Long) : PlayerUiEvent
    data object Retry : PlayerUiEvent
}

/**
 * ViewModel for the Anime/Video playback flow.
 */
@HiltViewModel
class VideoPlayerViewModel @Inject constructor() : BaseUdfViewModel<PlayerUiState, PlayerUiEvent, Nothing>(
    PlayerUiState(),
) {

    val uiState: StateFlow<PlayerUiState>
        get() = state

    /**
     * Initializes the player state with content information.
     */
    fun initPlayer(title: String, streamUrl: String) {
        onEvent(PlayerUiEvent.Init(title, streamUrl))
    }

    /**
     * Dispatcher for events coming from the video player screen composable.
     */
    override fun onEvent(event: PlayerUiEvent) {
        when (event) {
            is PlayerUiEvent.Init -> {
                updateState {
                    it.copy(
                        title = event.title,
                        streamUrl = event.streamUrl,
                        isLoading = false,
                    )
                }
            }
            is PlayerUiEvent.PlayPause -> {
                updateState { it.copy(isPlaying = event.play) }
            }
            is PlayerUiEvent.SeekTo -> {
                updateState { it.copy(currentPosition = event.positionMs) }
            }
            PlayerUiEvent.Retry -> {
                updateState { it.copy(errorMessage = null, isLoading = true) }
            }
        }
    }
}
