package ephyra.feature.reader.setting

import ephyra.domain.reader.model.ReadingMode
import ephyra.presentation.core.R

val ReadingMode.iconRes: Int
    get() = when (this) {
        ReadingMode.DEFAULT -> R.drawable.ic_reader_default_24dp
        ReadingMode.LEFT_TO_RIGHT -> R.drawable.ic_reader_ltr_24dp
        ReadingMode.RIGHT_TO_LEFT -> R.drawable.ic_reader_rtl_24dp
        ReadingMode.VERTICAL -> R.drawable.ic_reader_vertical_24dp
        ReadingMode.WEBTOON -> R.drawable.ic_reader_webtoon_24dp
        ReadingMode.CONTINUOUS_VERTICAL -> R.drawable.ic_reader_continuous_vertical_24dp
    }
