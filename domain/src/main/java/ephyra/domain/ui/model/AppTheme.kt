package ephyra.domain.ui.model


enum class AppTheme(val titleRes: Int?) {
    DEFAULT(ephyra.i18n.R.string.label_default),
    MONET(ephyra.i18n.R.string.theme_monet),
    EPHYRA(ephyra.i18n.R.string.theme_ephyra),
    NAGARE(ephyra.i18n.R.string.theme_nagare),
    ATOLLA(ephyra.i18n.R.string.theme_atolla),
    CATPPUCCIN(ephyra.i18n.R.string.theme_catppuccin),
    GREEN_APPLE(ephyra.i18n.R.string.theme_greenapple),
    LAVENDER(ephyra.i18n.R.string.theme_lavender),
    MIDNIGHT_DUSK(ephyra.i18n.R.string.theme_midnightdusk),
    NORD(ephyra.i18n.R.string.theme_nord),
    STRAWBERRY_DAIQUIRI(ephyra.i18n.R.string.theme_strawberrydaiquiri),
    TAKO(ephyra.i18n.R.string.theme_tako),
    TEALTURQUOISE(ephyra.i18n.R.string.theme_tealturquoise),
    TIDAL_WAVE(ephyra.i18n.R.string.theme_tidalwave),
    YINYANG(ephyra.i18n.R.string.theme_yinyang),
    YOTSUBA(ephyra.i18n.R.string.theme_yotsuba),
    MONOCHROME(ephyra.i18n.R.string.theme_monochrome),

    // Deprecated
    DARK_BLUE(null),
    HOT_PINK(null),
    BLUE(null),
}
