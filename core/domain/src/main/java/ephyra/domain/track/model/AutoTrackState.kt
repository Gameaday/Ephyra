package ephyra.domain.track.model

import dev.icerock.moko.resources.StringResource
import ephyra.i18n.MR

enum class AutoTrackState(val titleRes: StringResource) {
    ALWAYS(ephyra.i18n.R.string.auto_track_always),
    ASK(ephyra.i18n.R.string.auto_track_ask),
    NEVER(ephyra.i18n.R.string.auto_track_never),
}
