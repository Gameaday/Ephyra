package ephyra.presentation.reader.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.feature.reader.setting.ReaderSettingsViewModel
import ephyra.presentation.core.components.CheckboxItem
import ephyra.presentation.core.components.SettingsChipRow
import ephyra.presentation.core.components.SliderItem
import ephyra.presentation.core.i18n.pluralStringResource
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.util.collectAsState
import ephyra.presentation.core.util.system.hasDisplayCutout

private val themes = listOf(
    ephyra.app.core.common.R.string.black_background to 1,
    ephyra.app.core.common.R.string.gray_background to 2,
    ephyra.app.core.common.R.string.white_background to 0,
    ephyra.app.core.common.R.string.automatic_background to 3,
)

private val flashColors = listOf(
    ephyra.app.core.common.R.string.pref_flash_style_black to ReaderPreferences.FlashColor.BLACK,
    ephyra.app.core.common.R.string.pref_flash_style_white to ReaderPreferences.FlashColor.WHITE,
    ephyra.app.core.common.R.string.pref_flash_style_white_black to ReaderPreferences.FlashColor.WHITE_BLACK,
)

@Composable
internal fun ColumnScope.GeneralPage(ViewModel: ReaderSettingsViewModel) {
    val readerTheme by ViewModel.preferences.readerTheme().collectAsState()

    val flashPageState by ViewModel.preferences.flashOnPageChange().collectAsState()

    val flashMillisPref = ViewModel.preferences.flashDurationMillis()
    val flashMillis by flashMillisPref.collectAsState()

    val flashIntervalPref = ViewModel.preferences.flashPageInterval()
    val flashInterval by flashIntervalPref.collectAsState()

    val flashColorPref = ViewModel.preferences.flashColor()
    val flashColor by flashColorPref.collectAsState()

    SettingsChipRow(ephyra.app.core.common.R.string.pref_reader_theme) {
        themes.map { (labelRes, value) ->
            FilterChip(
                selected = readerTheme == value,
                onClick = { ViewModel.preferences.readerTheme().set(value) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }

    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_show_page_number),
        pref = ViewModel.preferences.showPageNumber(),
    )

    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_fullscreen),
        pref = ViewModel.preferences.fullscreen(),
    )

    val isFullscreen by ViewModel.preferences.fullscreen().collectAsState()
    if (LocalActivity.current?.hasDisplayCutout() == true && isFullscreen) {
        CheckboxItem(
            label = stringResource(ephyra.app.core.common.R.string.pref_cutout_short),
            pref = ViewModel.preferences.drawUnderCutout(),
        )
    }

    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_keep_screen_on),
        pref = ViewModel.preferences.keepScreenOn(),
    )

    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_read_with_long_tap),
        pref = ViewModel.preferences.readWithLongTap(),
    )

    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_always_show_chapter_transition),
        pref = ViewModel.preferences.alwaysShowChapterTransition(),
    )

    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_page_transitions),
        pref = ViewModel.preferences.pageTransitions(),
    )

    val sliderNavModePref = ViewModel.preferences.sliderNavMode()
    val sliderNavMode by sliderNavModePref.collectAsState()
    SettingsChipRow(ephyra.app.core.common.R.string.pref_slider_nav_mode) {
        FilterChip(
            selected = sliderNavMode == ReaderPreferences.SLIDER_NAV_INSTANT,
            onClick = { sliderNavModePref.set(ReaderPreferences.SLIDER_NAV_INSTANT) },
            label = { Text(stringResource(ephyra.app.core.common.R.string.slider_nav_instant)) },
        )
        FilterChip(
            selected = sliderNavMode == ReaderPreferences.SLIDER_NAV_SMOOTH,
            onClick = { sliderNavModePref.set(ReaderPreferences.SLIDER_NAV_SMOOTH) },
            label = { Text(stringResource(ephyra.app.core.common.R.string.slider_nav_smooth)) },
        )
    }

    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_flash_page),
        pref = ViewModel.preferences.flashOnPageChange(),
    )
    if (flashPageState) {
        SliderItem(
            value = flashMillis / ReaderPreferences.MILLI_CONVERSION,
            valueRange = 1..15,
            label = stringResource(ephyra.app.core.common.R.string.pref_flash_duration),
            valueString = stringResource(ephyra.app.core.common.R.string.pref_flash_duration_summary, flashMillis),
            onChange = { flashMillisPref.set(it * ReaderPreferences.MILLI_CONVERSION) },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        SliderItem(
            value = flashInterval,
            valueRange = 1..10,
            label = stringResource(ephyra.app.core.common.R.string.pref_flash_page_interval),
            valueString = pluralStringResource(
                ephyra.app.core.common.R.plurals.pref_pages,
                flashInterval,
                flashInterval,
            ),
            onChange = {
                flashIntervalPref.set(it)
            },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        SettingsChipRow(ephyra.app.core.common.R.string.pref_flash_with) {
            flashColors.map { (labelRes, value) ->
                FilterChip(
                    selected = flashColor == value,
                    onClick = { flashColorPref.set(value) },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
    }
}
