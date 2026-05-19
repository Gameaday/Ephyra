package ephyra.domain.ui.model


enum class AppTheme(val titleRes: Int?) {
    DEFAULT(ephyra.app.core.common.R.string.label_default),
    MONET(ephyra.app.core.common.R.string.theme_monet),
    EPHYRA(ephyra.app.core.common.R.string.theme_ephyra),
    NAGARE(ephyra.app.core.common.R.string.theme_nagare),
    ATOLLA(ephyra.app.core.common.R.string.theme_atolla),
    CATPPUCCIN(ephyra.app.core.common.R.string.theme_catppuccin),
    GREEN_APPLE(ephyra.app.core.common.R.string.theme_greenapple),
    LAVENDER(ephyra.app.core.common.R.string.theme_lavender),
    MIDNIGHT_DUSK(ephyra.app.core.common.R.string.theme_midnightdusk),
    NORD(ephyra.app.core.common.R.string.theme_nord),
    STRAWBERRY_DAIQUIRI(ephyra.app.core.common.R.string.theme_strawberrydaiquiri),
    TAKO(ephyra.app.core.common.R.string.theme_tako),
    TEALTURQUOISE(ephyra.app.core.common.R.string.theme_tealturquoise),
    TIDAL_WAVE(ephyra.app.core.common.R.string.theme_tidalwave),
    YINYANG(ephyra.app.core.common.R.string.theme_yinyang),
    YOTSUBA(ephyra.app.core.common.R.string.theme_yotsuba),
    MONOCHROME(ephyra.app.core.common.R.string.theme_monochrome),

    // Deprecated
    DARK_BLUE(null),
    HOT_PINK(null),
    BLUE(null),
}
