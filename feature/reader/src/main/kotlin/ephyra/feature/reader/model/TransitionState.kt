package ephyra.feature.reader.model

/**
 * Direction-aware, presentation-friendly view of a chapter boundary, derived from
 * [ChapterTransition]. The reader UI binds directional icons, labels, and exit
 * affordances to this hierarchy instead of inspecting the raw transition types.
 */
sealed interface TransitionState {
    /** The boundary leading to the previous chapter ([targetChapter] may be `null` when the gap is unloaded). */
    data class ToPrevious(val targetChapter: ReaderChapter?) : TransitionState

    /** The boundary leading to the next chapter ([targetChapter] may be `null` when the gap is unloaded). */
    data class ToNext(val targetChapter: ReaderChapter?) : TransitionState

    /** Forward navigation with no next chapter available: the reader has caught up with the series. */
    data object EndOfSeries : TransitionState
}

/**
 * Maps a raw viewer [ChapterTransition] onto the [TransitionState] hierarchy.
 * A forward transition with no destination chapter is terminal: end of series.
 */
fun ChapterTransition.toTransitionState(): TransitionState = when (this) {
    is ChapterTransition.Prev -> TransitionState.ToPrevious(to)
    is ChapterTransition.Next -> if (to == null) TransitionState.EndOfSeries else TransitionState.ToNext(to)
}
