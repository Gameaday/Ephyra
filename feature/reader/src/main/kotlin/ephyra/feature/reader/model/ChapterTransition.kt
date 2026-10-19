package ephyra.feature.reader.model

sealed class ChapterTransition {

    abstract val from: ReaderChapter
    abstract val to: ReaderChapter?

    class Prev(
        override val from: ReaderChapter,
        override val to: ReaderChapter?,
    ) : ChapterTransition()

    class Next(
        override val from: ReaderChapter,
        override val to: ReaderChapter?,
    ) : ChapterTransition()

    /**
     * Transitions are equal only when they are the same direction across the same boundary.
     *
     * The previous implementation also treated a `Prev(A, B)` as equal to a `Next(B, A)`. That made
     * `items.indexOf(currentPage)` resolve to the *opposite* transition after a chapter change —
     * for a forward crossing, the previous chapter's `Next(N, N+1)` matched the new chapter's
     * `Prev(N+1, N)` at index 0, and for a backward crossing it matched the new `Next` at the last
     * index. Any position computed from that index is one chapter-boundary item away from the page
     * it describes, which is how the reader could open on the wrong page after swiping off a
     * transition. It also broke the equals/hashCode contract, because the swapped form produced a
     * different hash.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChapterTransition) return false
        if (javaClass != other.javaClass) return false
        return from == other.from && to == other.to
    }

    override fun hashCode(): Int {
        var result = javaClass.hashCode()
        result = 31 * result + from.hashCode()
        result = 31 * result + (to?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "${javaClass.simpleName}(from=${from.chapter.url}, to=${to?.chapter?.url})"
    }
}
