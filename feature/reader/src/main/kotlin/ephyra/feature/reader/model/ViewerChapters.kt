package ephyra.feature.reader.model

/**
 * Selects the chapter immediately before [current] in canonical reader order without applying
 * forward-only skip-read policy. Forward selection intentionally lives separately so revisiting
 * read material is never hidden by the setting.
 */
fun <T> selectPreviousNavigationItem(
    items: List<T>,
    current: T,
): T? {
    val index = items.indexOf(current)
    return if (index <= 0) null else items[index - 1]
}

/** Selects the first eligible item after [current] in canonical reader order. */
fun <T> selectNextNavigationItem(
    items: List<T>,
    current: T,
    eligible: (T) -> Boolean = { true },
): T? {
    val index = items.indexOf(current)
    if (index < 0) return null
    return items.asSequence()
        .drop(index + 1)
        .firstOrNull(eligible)
}

data class ViewerChapters(
    val currChapter: ReaderChapter,
    val prevChapter: ReaderChapter?,
    val nextChapter: ReaderChapter?,
) {

    fun ref() {
        currChapter.ref()
        prevChapter?.ref()
        nextChapter?.ref()
    }

    fun unref() {
        currChapter.unref()
        prevChapter?.unref()
        nextChapter?.unref()
    }
}
