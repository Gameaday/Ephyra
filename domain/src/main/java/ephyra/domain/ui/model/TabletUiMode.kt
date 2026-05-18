package ephyra.domain.ui.model


enum class TabletUiMode(val titleRes: Int) {
    AUTOMATIC(ephyra.i18n.R.string.automatic_background),
    ALWAYS(ephyra.i18n.R.string.lock_always),
    LANDSCAPE(ephyra.i18n.R.string.landscape),
    NEVER(ephyra.i18n.R.string.lock_never),
}
