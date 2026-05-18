package ephyra.domain.ui.model

import dev.icerock.moko.resources.StringResource
import ephyra.i18n.MR

enum class TabletUiMode(val titleRes: StringResource) {
    AUTOMATIC(ephyra.i18n.R.string.automatic_background),
    ALWAYS(ephyra.i18n.R.string.lock_always),
    LANDSCAPE(ephyra.i18n.R.string.landscape),
    NEVER(ephyra.i18n.R.string.lock_never),
}
