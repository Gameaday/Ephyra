package ephyra.domain.ui.model

enum class TabletUiMode(val titleRes: Int) {
    AUTOMATIC(ephyra.app.core.common.R.string.automatic_background),
    ALWAYS(ephyra.app.core.common.R.string.lock_always),
    LANDSCAPE(ephyra.app.core.common.R.string.landscape),
    NEVER(ephyra.app.core.common.R.string.lock_never),
}
