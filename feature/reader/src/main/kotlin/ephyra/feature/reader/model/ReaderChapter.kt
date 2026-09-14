package ephyra.feature.reader.model

import ephyra.core.common.util.system.logcat
import ephyra.domain.chapter.model.Chapter
import ephyra.feature.reader.loader.PageLoader
import kotlinx.coroutines.flow.MutableStateFlow

class ReaderChapter(var chapter: Chapter) {

    val stateFlow = MutableStateFlow<State>(State.Wait)
    var state: State
        get() = stateFlow.value
        set(value) {
            stateFlow.value = value
        }

    val pages: List<ReaderPage>?
        get() = (state as? State.Loaded)?.pages

    var pageLoader: PageLoader? = null

    var requestedPage: Int = 0
    var startingAtBeginning: Boolean = false
    var startFromEnd: Boolean = false

    private var references = 0

    fun ref() {
        references++
    }

    fun unref() {
        references--
        if (references == 0) {
            if (pageLoader != null) {
                logcat { "Recycling chapter ${chapter.name}" }
            }
            pageLoader?.recycle()
            pageLoader = null
            startingAtBeginning = false
            startFromEnd = false
            // Drop any merged bitmaps held by pages before dropping the page list so the
            // merged allocations become unreachable for the next GC; ART reclaims the pixel
            // buffers, so no explicit recycling is required.
            (state as? State.Loaded)?.pages?.forEach { page ->
                page.clearMergedBitmap()
            }
            state = State.Wait
        }
    }

    sealed interface State {
        data object Wait : State
        data object Loading : State
        data class Error(val error: Throwable) : State
        data class Loaded(val pages: List<ReaderPage>) : State
    }
}
