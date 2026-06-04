package ephyra.presentation.reader.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ephyra.domain.manga.model.readerOrientation
import ephyra.domain.manga.model.readingMode
import ephyra.domain.reader.model.ReaderOrientation
import ephyra.domain.reader.model.ReadingMode
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.feature.reader.setting.ReaderSettingsViewModel
import ephyra.feature.reader.viewer.webtoon.WebtoonViewer
import ephyra.presentation.core.components.CheckboxItem
import ephyra.presentation.core.components.HeadingItem
import ephyra.presentation.core.components.SettingsChipRow
import ephyra.presentation.core.components.SliderItem
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.util.collectAsState
import java.text.NumberFormat

@Composable
internal fun ColumnScope.ReadingModePage(ViewModel: ReaderSettingsViewModel) {
    HeadingItem(ephyra.app.core.common.R.string.pref_category_for_this_series)
    val manga by ViewModel.mangaFlow.collectAsStateWithLifecycle()

    val readingMode = remember(manga) { ReadingMode.fromPreference(manga?.readingMode?.toInt()) }
    SettingsChipRow(ephyra.app.core.common.R.string.pref_category_reading_mode) {
        ReadingMode.entries.map {
            FilterChip(
                selected = it == readingMode,
                onClick = { ViewModel.onChangeReadingMode(it) },
                label = { Text(stringResource(it.stringRes)) },
            )
        }
    }

    val orientation = remember(manga) { ReaderOrientation.fromPreference(manga?.readerOrientation?.toInt()) }
    SettingsChipRow(ephyra.app.core.common.R.string.rotation_type) {
        ReaderOrientation.entries.map {
            FilterChip(
                selected = it == orientation,
                onClick = { ViewModel.onChangeOrientation(it) },
                label = { Text(stringResource(it.stringRes)) },
            )
        }
    }

    val viewer by ViewModel.viewerFlow.collectAsStateWithLifecycle()
    if (viewer is WebtoonViewer) {
        WebtoonViewerSettings(ViewModel)
    } else {
        PagerViewerSettings(ViewModel)
    }
}

@Composable
private fun ColumnScope.PagerViewerSettings(ViewModel: ReaderSettingsViewModel) {
    HeadingItem(ephyra.app.core.common.R.string.pager_viewer)

    val navigationModePager by ViewModel.preferences.navigationModePager().collectAsState()
    val pagerNavInverted by ViewModel.preferences.pagerNavInverted().collectAsState()
    TapZonesItems(
        selected = navigationModePager,
        onSelect = ViewModel.preferences.navigationModePager()::set,
        invertMode = pagerNavInverted,
        onSelectInvertMode = ViewModel.preferences.pagerNavInverted()::set,
    )

    val imageScaleType by ViewModel.preferences.imageScaleType().collectAsState()
    SettingsChipRow(ephyra.app.core.common.R.string.pref_image_scale_type) {
        ReaderPreferences.ImageScaleType.mapIndexed { index, it ->
            FilterChip(
                selected = imageScaleType == index + 1,
                onClick = { ViewModel.preferences.imageScaleType().set(index + 1) },
                label = { Text(stringResource(it)) },
            )
        }
    }

    val zoomStart by ViewModel.preferences.zoomStart().collectAsState()
    SettingsChipRow(ephyra.app.core.common.R.string.pref_zoom_start) {
        ReaderPreferences.ZoomStart.mapIndexed { index, it ->
            FilterChip(
                selected = zoomStart == index + 1,
                onClick = { ViewModel.preferences.zoomStart().set(index + 1) },
                label = { Text(stringResource(it)) },
            )
        }
    }

    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_crop_borders),
        pref = ViewModel.preferences.cropBorders(),
    )

    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_landscape_zoom),
        pref = ViewModel.preferences.landscapeZoom(),
    )

    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_navigate_pan),
        pref = ViewModel.preferences.navigateToPan(),
    )

    val dualPageSplitPaged by ViewModel.preferences.dualPageSplitPaged().collectAsState()
    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_dual_page_split),
        pref = ViewModel.preferences.dualPageSplitPaged(),
    )

    if (dualPageSplitPaged) {
        CheckboxItem(
            label = stringResource(ephyra.app.core.common.R.string.pref_dual_page_invert),
            pref = ViewModel.preferences.dualPageInvertPaged(),
        )
    }

    val dualPageRotateToFit by ViewModel.preferences.dualPageRotateToFit().collectAsState()
    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_page_rotate),
        pref = ViewModel.preferences.dualPageRotateToFit(),
    )

    if (dualPageRotateToFit) {
        CheckboxItem(
            label = stringResource(ephyra.app.core.common.R.string.pref_page_rotate_invert),
            pref = ViewModel.preferences.dualPageRotateToFitInvert(),
        )
    }

    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_smart_page_combine),
        pref = ViewModel.preferences.smartCombinePaged(),
    )
}

@Composable
private fun ColumnScope.WebtoonViewerSettings(ViewModel: ReaderSettingsViewModel) {
    val numberFormat = remember { NumberFormat.getPercentInstance() }

    HeadingItem(ephyra.app.core.common.R.string.webtoon_viewer)

    val navigationModeWebtoon by ViewModel.preferences.navigationModeWebtoon().collectAsState()
    val webtoonNavInverted by ViewModel.preferences.webtoonNavInverted().collectAsState()
    TapZonesItems(
        selected = navigationModeWebtoon,
        onSelect = ViewModel.preferences.navigationModeWebtoon()::set,
        invertMode = webtoonNavInverted,
        onSelectInvertMode = ViewModel.preferences.webtoonNavInverted()::set,
    )

    val webtoonSidePadding by ViewModel.preferences.webtoonSidePadding().collectAsState()
    SliderItem(
        value = webtoonSidePadding,
        valueRange = ReaderPreferences.let { it.WEBTOON_PADDING_MIN..it.WEBTOON_PADDING_MAX },
        label = stringResource(ephyra.app.core.common.R.string.pref_webtoon_side_padding),
        valueString = numberFormat.format(webtoonSidePadding / 100f),
        onChange = {
            ViewModel.preferences.webtoonSidePadding().set(it)
        },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )

    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_crop_borders),
        pref = ViewModel.preferences.cropBordersWebtoon(),
    )

    val dualPageSplitWebtoon by ViewModel.preferences.dualPageSplitWebtoon().collectAsState()
    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_dual_page_split),
        pref = ViewModel.preferences.dualPageSplitWebtoon(),
    )

    if (dualPageSplitWebtoon) {
        CheckboxItem(
            label = stringResource(ephyra.app.core.common.R.string.pref_dual_page_invert),
            pref = ViewModel.preferences.dualPageInvertWebtoon(),
        )
    }

    val dualPageRotateToFitWebtoon by ViewModel.preferences.dualPageRotateToFitWebtoon().collectAsState()
    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_page_rotate),
        pref = ViewModel.preferences.dualPageRotateToFitWebtoon(),
    )

    if (dualPageRotateToFitWebtoon) {
        CheckboxItem(
            label = stringResource(ephyra.app.core.common.R.string.pref_page_rotate_invert),
            pref = ViewModel.preferences.dualPageRotateToFitInvertWebtoon(),
        )
    }

    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_double_tap_zoom),
        pref = ViewModel.preferences.webtoonDoubleTapZoomEnabled(),
    )
    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_webtoon_disable_zoom_out),
        pref = ViewModel.preferences.webtoonDisableZoomOut(),
    )
}

@Composable
private fun ColumnScope.TapZonesItems(
    selected: Int,
    onSelect: (Int) -> Unit,
    invertMode: ReaderPreferences.TappingInvertMode,
    onSelectInvertMode: (ReaderPreferences.TappingInvertMode) -> Unit,
) {
    SettingsChipRow(ephyra.app.core.common.R.string.pref_viewer_nav) {
        ReaderPreferences.TapZones.mapIndexed { index, it ->
            FilterChip(
                selected = selected == index,
                onClick = { onSelect(index) },
                label = { Text(stringResource(it)) },
            )
        }
    }

    if (selected != 5) {
        SettingsChipRow(ephyra.app.core.common.R.string.pref_read_with_tapping_inverted) {
            ReaderPreferences.TappingInvertMode.entries.map {
                FilterChip(
                    selected = it == invertMode,
                    onClick = { onSelectInvertMode(it) },
                    label = { Text(stringResource(it.titleRes)) },
                )
            }
        }
    }
}
