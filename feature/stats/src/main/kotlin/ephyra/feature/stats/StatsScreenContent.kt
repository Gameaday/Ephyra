package ephyra.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ephyra.feature.stats.components.StatsItem
import ephyra.feature.stats.components.StatsOverviewItem
import ephyra.feature.stats.data.StatsData
import ephyra.i18n.MR
import ephyra.presentation.core.components.SectionCard
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.util.toDurationString
import java.util.Locale
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Composable
fun StatsScreenContent(
    state: StatsScreenState.Success,
    paddingValues: PaddingValues,
) {
    LazyColumn(
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        item {
            OverviewSection(state.overview)
        }
        item {
            TitlesStats(state.titles)
        }
        item {
            ChapterStats(state.chapters)
        }
        item {
            TrackerStats(state.trackers)
        }
    }
}

@Composable
private fun LazyItemScope.OverviewSection(
    data: StatsData.Overview,
) {
    val none = stringResource(ephyra.i18n.R.string.none)
    val context = LocalContext.current
    val readDurationString = remember(data.totalReadDuration) {
        data.totalReadDuration
            .toDuration(DurationUnit.MILLISECONDS)
            .toDurationString(context, fallback = none)
    }
    SectionCard(ephyra.i18n.R.string.label_overview_section) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
        ) {
            StatsOverviewItem(
                title = data.libraryMangaCount.toString(),
                subtitle = stringResource(ephyra.i18n.R.string.in_library),
                icon = Icons.Outlined.CollectionsBookmark,
            )
            StatsOverviewItem(
                title = readDurationString,
                subtitle = stringResource(ephyra.i18n.R.string.label_read_duration),
                icon = Icons.Outlined.Schedule,
            )
            StatsOverviewItem(
                title = data.completedMangaCount.toString(),
                subtitle = stringResource(ephyra.i18n.R.string.label_completed_titles),
                icon = Icons.Outlined.LocalLibrary,
            )
        }
    }
}

@Composable
private fun LazyItemScope.TitlesStats(
    data: StatsData.Titles,
) {
    SectionCard(ephyra.i18n.R.string.label_titles_section) {
        Row {
            StatsItem(
                data.globalUpdateItemCount.toString(),
                stringResource(ephyra.i18n.R.string.label_titles_in_global_update),
            )
            StatsItem(
                data.startedMangaCount.toString(),
                stringResource(ephyra.i18n.R.string.label_started),
            )
            StatsItem(
                data.localMangaCount.toString(),
                stringResource(ephyra.i18n.R.string.label_local),
            )
        }
    }
}

@Composable
private fun LazyItemScope.ChapterStats(
    data: StatsData.Chapters,
) {
    SectionCard(ephyra.i18n.R.string.chapters) {
        Row {
            StatsItem(
                data.totalChapterCount.toString(),
                stringResource(ephyra.i18n.R.string.label_total_chapters),
            )
            StatsItem(
                data.readChapterCount.toString(),
                stringResource(ephyra.i18n.R.string.label_read_chapters),
            )
            StatsItem(
                data.downloadCount.toString(),
                stringResource(ephyra.i18n.R.string.label_downloaded),
            )
        }
    }
}

@Composable
private fun LazyItemScope.TrackerStats(
    data: StatsData.Trackers,
) {
    val notApplicable = stringResource(ephyra.i18n.R.string.not_applicable)
    val meanScoreStr = remember(data.trackedTitleCount, data.meanScore) {
        if (data.trackedTitleCount > 0 && !data.meanScore.isNaN()) {
            // All other numbers are localized in English
            "%.2f ★".format(Locale.ENGLISH, data.meanScore)
        } else {
            notApplicable
        }
    }
    SectionCard(ephyra.i18n.R.string.label_tracker_section) {
        Row {
            StatsItem(
                data.trackedTitleCount.toString(),
                stringResource(ephyra.i18n.R.string.label_tracked_titles),
            )
            StatsItem(
                meanScoreStr,
                stringResource(ephyra.i18n.R.string.label_mean_score),
            )
            StatsItem(
                data.trackerCount.toString(),
                stringResource(ephyra.i18n.R.string.label_used),
            )
        }
    }
}
