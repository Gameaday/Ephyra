package ephyra.core.common.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Universal, media-agnostic asynchronous events for Ephyra.
 */
sealed interface AppEvent {
    /** Events emitted by the local and remote Ingest engine. */
    sealed interface Ingest : AppEvent {
        data class ScanStarted(val sourceId: Long, val path: String) : Ingest
        data class ScanProgress(val sourceId: Long, val processed: Int, val total: Int) : Ingest
        data class ScanCompleted(val sourceId: Long, val itemsAdded: Int) : Ingest
        data class ScanFailed(val sourceId: Long, val error: String) : Ingest
    }

    /** Events triggered by database mutations and syncing. */
    sealed interface Database : AppEvent {
        data class ItemUpdated(val itemId: Long) : Database
        data object LibraryCleared : Database
    }

    /** System and environment events. */
    sealed interface System : AppEvent {
        data class NetworkStatusChanged(val isConnected: Boolean) : System
    }
}

/**
 * Enterprise-grade, coroutine-safe Event Bus.
 * Propagates non-blocking background notifications to the UI asynchronously.
 */
object AppEventBus {
    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    /**
     * Emits a new event to the bus.
     */
    fun emit(event: AppEvent) {
        _events.tryEmit(event)
    }
}
