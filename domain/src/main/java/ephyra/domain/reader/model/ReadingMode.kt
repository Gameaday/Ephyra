package ephyra.domain.reader.model


enum class ReadingMode(
    val stringRes: Int,
    val flagValue: Int,
    val direction: Direction? = null,
    val type: ViewerType? = null,
) {
    DEFAULT(ephyra.app.core.common.R.string.label_default, 0x00000000),
    LEFT_TO_RIGHT(
        ephyra.app.core.common.R.string.left_to_right_viewer,
        0x00000001,
        Direction.Horizontal,
        ViewerType.Pager,
    ),
    RIGHT_TO_LEFT(
        ephyra.app.core.common.R.string.right_to_left_viewer,
        0x00000002,
        Direction.Horizontal,
        ViewerType.Pager,
    ),
    VERTICAL(
        ephyra.app.core.common.R.string.vertical_viewer,
        0x00000003,
        Direction.Vertical,
        ViewerType.Pager,
    ),
    WEBTOON(
        ephyra.app.core.common.R.string.webtoon_viewer,
        0x00000004,
        Direction.Vertical,
        ViewerType.Webtoon,
    ),
    CONTINUOUS_VERTICAL(
        ephyra.app.core.common.R.string.vertical_plus_viewer,
        0x00000005,
        Direction.Vertical,
        ViewerType.Webtoon,
    ),
    ;

    companion object {
        const val MASK = 0x00000007

        private val flagMap = entries.associateBy { it.flagValue }

        fun fromPreference(preference: Int?): ReadingMode = flagMap[preference] ?: DEFAULT

        fun isPagerType(preference: Int): Boolean {
            val mode = fromPreference(preference)
            return mode.type is ViewerType.Pager
        }
    }

    sealed interface Direction {
        data object Horizontal : Direction
        data object Vertical : Direction
    }

    sealed interface ViewerType {
        data object Pager : ViewerType
        data object Webtoon : ViewerType
    }
}
