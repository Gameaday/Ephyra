package ephyra.domain.track.model


enum class AutoTrackState(val titleRes: Int) {
    ALWAYS(ephyra.app.core.common.R.string.auto_track_always),
    ASK(ephyra.app.core.common.R.string.auto_track_ask),
    NEVER(ephyra.app.core.common.R.string.auto_track_never),
}
