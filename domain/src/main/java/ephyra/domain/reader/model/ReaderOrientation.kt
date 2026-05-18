package ephyra.domain.reader.model


/**
 * Screen orientation modes for the reader.
 *
 * [flag] values are intentionally equal to the corresponding
 * `android.content.pm.ActivityInfo.SCREEN_ORIENTATION_*` constants so that the
 * Android presentation layer can pass them directly to
 * `Activity.requestedOrientation` without a conversion map.  They are declared
 * here as plain Kotlin constants so that this domain class remains free of any
 * `android.*` dependency and can be tested on the JVM.
 */
enum class ReaderOrientation(
    val flag: Int,
    val stringRes: Int,
    val flagValue: Int,
) {
    DEFAULT(
        flag = -1, // ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        ephyra.i18n.R.string.label_default,
        0x00000000,
    ),
    FREE(
        flag = -1, // ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        ephyra.i18n.R.string.rotation_free,
        0x00000008,
    ),
    PORTRAIT(
        flag = 7, // ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        ephyra.i18n.R.string.rotation_portrait,
        0x00000010,
    ),
    LANDSCAPE(
        flag = 6, // ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        ephyra.i18n.R.string.rotation_landscape,
        0x00000018,
    ),
    LOCKED_PORTRAIT(
        flag = 1, // ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        ephyra.i18n.R.string.rotation_force_portrait,
        0x00000020,
    ),
    LOCKED_LANDSCAPE(
        flag = 0, // ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        ephyra.i18n.R.string.rotation_force_landscape,
        0x00000028,
    ),
    REVERSE_PORTRAIT(
        flag = 9, // ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        ephyra.i18n.R.string.rotation_reverse_portrait,
        0x00000030,
    ),
    ;

    companion object {
        const val MASK = 0x00000038

        private val flagMap = entries.associateBy { it.flagValue }

        fun fromPreference(preference: Int?): ReaderOrientation = flagMap[preference] ?: DEFAULT
    }
}
