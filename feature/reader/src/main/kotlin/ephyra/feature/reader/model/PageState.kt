package ephyra.feature.reader.model

/**
 * Where a page is in its load lifecycle.
 *
 * The extension ABI has its own `Page.State` on the objects a source returns. This is the
 * reader's copy: [ReaderPage] does not extend the source DTO, so nothing in the reader
 * depends on `source-api` to describe a page.
 */
sealed interface PageState {
    data object Queue : PageState
    data object LoadPage : PageState
    data object DownloadImage : PageState
    data object Ready : PageState
    data class Error(val error: Throwable) : PageState
}
