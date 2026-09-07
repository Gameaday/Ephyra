package ephyra.feature.download

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.domain.download.model.Download
import ephyra.domain.download.service.DownloadManager
import ephyra.presentation.core.udf.BaseUdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadQueueViewModel @Inject constructor(
    private val downloadManager: DownloadManager,
) : BaseUdfViewModel<DownloadQueueViewModel.State, DownloadQueueScreenEvent, Nothing>(State()) {

    val isDownloaderRunning: Flow<Boolean>
        get() = downloadManager.isDownloaderRunning

    init {
        viewModelScope.launch {
            combine(
                downloadManager.queueState,
                downloadManager.isDownloaderRunning,
            ) { queue, isRunning ->
                State(
                    downloads = queue.toImmutableList(),
                    isDownloaderRunning = isRunning,
                )
            }.distinctUntilChanged()
                .collect { newState ->
                    updateState { newState }
                }
        }
    }

    override fun onEvent(event: DownloadQueueScreenEvent) {
        when (event) {
            DownloadQueueScreenEvent.StartDownloads -> startDownloads()
            DownloadQueueScreenEvent.PauseDownloads -> pauseDownloads()
            DownloadQueueScreenEvent.ClearQueue -> clearQueue()
            is DownloadQueueScreenEvent.Reorder -> reorder(event.downloads)
            is DownloadQueueScreenEvent.Cancel -> cancel(event.downloads)
        }
    }

    private fun startDownloads() {
        downloadManager.startDownloads()
    }

    private fun pauseDownloads() {
        downloadManager.pauseDownloads()
    }

    private fun clearQueue() {
        downloadManager.clearQueue()
    }

    private fun reorder(downloads: List<Download>) {
        downloadManager.reorderQueue(downloads)
    }

    private fun cancel(downloads: List<Download>) {
        downloadManager.cancelQueuedDownloads(downloads)
    }

    fun <R : Comparable<R>> reorderQueue(selector: (Download) -> R, reverse: Boolean = false) {
        val reordered = currentState.downloads
            .groupBy { it.source.id }
            .values
            .flatMap { group ->
                group.sortedBy(selector).let { if (reverse) it.reversed() else it }
            }
        reorder(reordered)
    }

    @Immutable
    data class State(
        val downloads: ImmutableList<Download> = persistentListOf(),
        val isDownloaderRunning: Boolean = false,
    ) {
        val isEmpty: Boolean get() = downloads.isEmpty()
    }
}
