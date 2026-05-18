package ephyra.domain.track.model


enum class AutoTrackState(val titleRes: Int) {
    ALWAYS(ephyra.i18n.R.string.auto_track_always),
    ASK(ephyra.i18n.R.string.auto_track_ask),
    NEVER(ephyra.i18n.R.string.auto_track_never),
}
