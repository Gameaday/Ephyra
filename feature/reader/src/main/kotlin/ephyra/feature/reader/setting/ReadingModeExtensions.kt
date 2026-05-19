package ephyra.feature.reader.setting

import ephyra.domain.reader.model.ReadingMode
import ephyra.feature.reader.ReaderActivity
import ephyra.feature.reader.viewer.Viewer
import ephyra.feature.reader.viewer.pager.L2RPagerViewer
import ephyra.feature.reader.viewer.pager.R2LPagerViewer
import ephyra.feature.reader.viewer.pager.VerticalPagerViewer
import ephyra.feature.reader.viewer.webtoon.WebtoonViewer
import ephyra.presentation.core.R
import ephyra.core.common.di.CoreContainer
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.reader.service.ReaderPreferences

val ReadingMode.iconRes: Int
    get() = when (this) {
        ReadingMode.DEFAULT -> R.drawable.ic_reader_default_24dp
        ReadingMode.LEFT_TO_RIGHT -> R.drawable.ic_reader_ltr_24dp
        ReadingMode.RIGHT_TO_LEFT -> R.drawable.ic_reader_rtl_24dp
        ReadingMode.VERTICAL -> R.drawable.ic_reader_vertical_24dp
        ReadingMode.WEBTOON -> R.drawable.ic_reader_webtoon_24dp
        ReadingMode.CONTINUOUS_VERTICAL -> R.drawable.ic_reader_continuous_vertical_24dp
    }

fun ReadingMode.toViewer(activity: ReaderActivity): Viewer {
    val downloadManager = CoreContainer.get<DownloadManager>()
    val readerPreferences = CoreContainer.get<ReaderPreferences>()
    return when (this) {
        ReadingMode.LEFT_TO_RIGHT -> L2RPagerViewer(activity, downloadManager, readerPreferences)
        ReadingMode.RIGHT_TO_LEFT -> R2LPagerViewer(activity, downloadManager, readerPreferences)
        ReadingMode.VERTICAL -> VerticalPagerViewer(activity, downloadManager, readerPreferences)
        ReadingMode.WEBTOON -> WebtoonViewer(activity, downloadManager, readerPreferences)
        ReadingMode.CONTINUOUS_VERTICAL -> WebtoonViewer(activity, downloadManager, readerPreferences, isContinuous = false)
        ReadingMode.DEFAULT -> throw IllegalStateException("Preference value must be resolved")
    }
}
