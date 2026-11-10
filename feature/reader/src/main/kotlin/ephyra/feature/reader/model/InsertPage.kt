package ephyra.feature.reader.model

/**
 * A synthetic page spliced into the pager's item list when a smart-combined page is split.
 *
 * It is the parent page's content in a slot of its own, so it shares the parent's
 * [ReaderPage.resource] handle — the sharing the old code approximated by copying the stream
 * lambda. Sharing the whole handle is strictly more correct: the insert renders exactly what the
 * parent renders, including a recorded merge or cached bytes, instead of re-reading the stream.
 */
class InsertPage(val parent: ReaderPage) :
    ReaderPage(parent.index, parent.url, parent.imageUrl, parent.resource) {

    override var chapter: ReaderChapter = parent.chapter

    init {
        status = State.Ready
    }
}
