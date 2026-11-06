package ephyra.feature.reader.model

/**
 * The chapter window the reader displays. Prev/next selection lives in
 * [ReaderChapterWindowAdapter] (backed by the domain ReaderChapterWindowPolicy, RDR-002); this
 * type only holds the resolved window.
 */
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
