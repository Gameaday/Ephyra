package ephyra.feature.manga

sealed interface CoverSearchScreenEvent {
    data object Search : CoverSearchScreenEvent
    data object Refresh : CoverSearchScreenEvent
    data class Init(val mangaTitle: String, val currentSourceId: Long) : CoverSearchScreenEvent
}
