package ephyra.feature.reader

import android.os.ParcelFileDescriptor
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.archive.ArchiveReader
import ephyra.core.archive.EpubChapter
import ephyra.core.archive.EpubReader
import ephyra.core.common.di.IoDispatcher
import ephyra.domain.reader.service.ReaderPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed interface BookReaderState {
    data object Loading : BookReaderState

    data class Success(
        val title: String,
        val chapters: List<EpubChapter>,
        val currentChapterIndex: Int = 0,
        val initialScrollOffset: Int = 0,
        val fontSize: Float = 18f,
        val isSerif: Boolean = true,
        val isPaginated: Boolean = false,
        val showToc: Boolean = false,
        val showSettings: Boolean = false,
    ) : BookReaderState {
        val currentChapter: EpubChapter?
            get() = chapters.getOrNull(currentChapterIndex)
        val hasPrevious: Boolean
            get() = currentChapterIndex > 0
        val hasNext: Boolean
            get() = currentChapterIndex < chapters.size - 1
    }

    data class Error(val message: String) : BookReaderState
}

sealed interface BookReaderEvent {
    data class LoadBook(val title: String, val bookUrl: String, val initialIndex: Int = 0) : BookReaderEvent
    data class SelectChapter(val index: Int) : BookReaderEvent
    data object NextChapter : BookReaderEvent
    data object PreviousChapter : BookReaderEvent
    data class SetFontSize(val size: Float) : BookReaderEvent
    data class SetSerif(val isSerif: Boolean) : BookReaderEvent
    data class SetPaginated(val isPaginated: Boolean) : BookReaderEvent
    data class SaveScrollOffset(val offset: Int) : BookReaderEvent
    data class ToggleToc(val show: Boolean) : BookReaderEvent
    data class ToggleSettings(val show: Boolean) : BookReaderEvent
}

@HiltViewModel
class BookReaderViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val readerPreferences: ReaderPreferences,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow<BookReaderState>(BookReaderState.Loading)
    val state: StateFlow<BookReaderState> = _state.asStateFlow()

    private var loadedUrl: String? = null

    init {
        val title: String? = savedStateHandle["title"]
        val bookUrl: String? = savedStateHandle["bookUrl"]
        val initialIndex: Int = savedStateHandle["initialChapterIndex"] ?: 0
        if (!title.isNullOrBlank() && !bookUrl.isNullOrBlank()) {
            loadBook(title, bookUrl, initialIndex)
        }
    }

    fun onEvent(event: BookReaderEvent) {
        when (event) {
            is BookReaderEvent.LoadBook -> loadBook(event.title, event.bookUrl, event.initialIndex)
            is BookReaderEvent.SelectChapter -> selectChapter(event.index)
            is BookReaderEvent.NextChapter -> nextChapter()
            is BookReaderEvent.PreviousChapter -> previousChapter()
            is BookReaderEvent.SetFontSize -> setFontSize(event.size)
            is BookReaderEvent.SetSerif -> setSerif(event.isSerif)
            is BookReaderEvent.SetPaginated -> setPaginated(event.isPaginated)
            is BookReaderEvent.SaveScrollOffset -> saveScrollOffset(event.offset)
            is BookReaderEvent.ToggleToc -> toggleToc(event.show)
            is BookReaderEvent.ToggleSettings -> toggleSettings(event.show)
        }
    }

    fun loadBook(title: String, bookUrl: String, initialIndex: Int = 0) {
        if (loadedUrl == bookUrl && _state.value is BookReaderState.Success) return
        loadedUrl = bookUrl
        savedStateHandle["title"] = title
        savedStateHandle["bookUrl"] = bookUrl
        savedStateHandle["initialChapterIndex"] = initialIndex

        viewModelScope.launch {
            _state.value = BookReaderState.Loading
            val chapters = withContext(ioDispatcher) {
                extractChapters(bookUrl, title)
            }

            if (chapters.isNotEmpty()) {
                val savedChapter = readerPreferences.bookReaderLastChapter(bookUrl).get()
                val targetIndex = if (initialIndex == 0 && savedChapter in chapters.indices) {
                    savedChapter
                } else {
                    initialIndex
                }
                val clampedIndex = targetIndex.coerceIn(0, chapters.size - 1)
                val savedScroll = if (clampedIndex == savedChapter) {
                    readerPreferences.bookReaderLastScroll(bookUrl).get()
                } else {
                    0
                }

                _state.value = BookReaderState.Success(
                    title = title,
                    chapters = chapters,
                    currentChapterIndex = clampedIndex,
                    initialScrollOffset = savedScroll,
                    fontSize = readerPreferences.bookReaderFontSize().get(),
                    isSerif = readerPreferences.bookReaderIsSerif().get(),
                    isPaginated = readerPreferences.bookReaderIsPaginated().get(),
                )
            } else {
                _state.value = BookReaderState.Error(
                    "No readable text content found at '$bookUrl'. " +
                        "Ensure the file exists and is a valid EPUB or text document.",
                )
            }
        }
    }

    private fun extractChapters(bookUrl: String, fallbackTitle: String): List<EpubChapter> {
        val file = File(bookUrl)
        if (!file.exists() || !file.isFile) {
            return emptyList()
        }

        if (file.extension.equals("epub", ignoreCase = true)) {
            val parsed = runCatching {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                ArchiveReader(pfd).use { archive ->
                    EpubReader(archive).getChapters()
                }
            }.getOrNull()

            if (!parsed.isNullOrEmpty()) {
                return parsed
            }
        }

        // Plain text fallback
        val text = runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
        return if (text != null) {
            listOf(
                EpubChapter(
                    id = file.name,
                    title = fallbackTitle.ifBlank { file.nameWithoutExtension },
                    bodyText = text,
                ),
            )
        } else {
            emptyList()
        }
    }

    private fun selectChapter(index: Int) {
        _state.update { current ->
            val success = current as? BookReaderState.Success ?: return@update current
            if (index in success.chapters.indices) {
                savedStateHandle["initialChapterIndex"] = index
                loadedUrl?.let { url ->
                    readerPreferences.bookReaderLastChapter(url).set(index)
                    readerPreferences.bookReaderLastScroll(url).set(0)
                }
                success.copy(currentChapterIndex = index, initialScrollOffset = 0, showToc = false)
            } else {
                success
            }
        }
    }

    private fun nextChapter() {
        _state.update { current ->
            val success = current as? BookReaderState.Success ?: return@update current
            if (success.hasNext) {
                val next = success.currentChapterIndex + 1
                savedStateHandle["initialChapterIndex"] = next
                loadedUrl?.let { url ->
                    readerPreferences.bookReaderLastChapter(url).set(next)
                    readerPreferences.bookReaderLastScroll(url).set(0)
                }
                success.copy(currentChapterIndex = next, initialScrollOffset = 0)
            } else {
                success
            }
        }
    }

    private fun previousChapter() {
        _state.update { current ->
            val success = current as? BookReaderState.Success ?: return@update current
            if (success.hasPrevious) {
                val prev = success.currentChapterIndex - 1
                savedStateHandle["initialChapterIndex"] = prev
                loadedUrl?.let { url ->
                    readerPreferences.bookReaderLastChapter(url).set(prev)
                    readerPreferences.bookReaderLastScroll(url).set(0)
                }
                success.copy(currentChapterIndex = prev, initialScrollOffset = 0)
            } else {
                success
            }
        }
    }

    private fun saveScrollOffset(offset: Int) {
        loadedUrl?.let { url ->
            readerPreferences.bookReaderLastScroll(url).set(offset)
        }
    }

    private fun setFontSize(size: Float) {
        val clamped = size.coerceIn(12f, 36f)
        readerPreferences.bookReaderFontSize().set(clamped)
        _state.update { current ->
            val success = current as? BookReaderState.Success ?: return@update current
            success.copy(fontSize = clamped)
        }
    }

    private fun setSerif(isSerif: Boolean) {
        readerPreferences.bookReaderIsSerif().set(isSerif)
        _state.update { current ->
            val success = current as? BookReaderState.Success ?: return@update current
            success.copy(isSerif = isSerif)
        }
    }

    private fun setPaginated(isPaginated: Boolean) {
        readerPreferences.bookReaderIsPaginated().set(isPaginated)
        _state.update { current ->
            val success = current as? BookReaderState.Success ?: return@update current
            success.copy(isPaginated = isPaginated)
        }
    }

    private fun toggleToc(show: Boolean) {
        _state.update { current ->
            val success = current as? BookReaderState.Success ?: return@update current
            success.copy(showToc = show)
        }
    }

    private fun toggleSettings(show: Boolean) {
        _state.update { current ->
            val success = current as? BookReaderState.Success ?: return@update current
            success.copy(showSettings = show)
        }
    }
}
