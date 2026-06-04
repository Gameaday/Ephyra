package ephyra.presentation.reader.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import ephyra.core.common.preference.getAndSet
import ephyra.domain.reader.service.ReaderPreferences.Companion.ColorFilterMode
import ephyra.feature.reader.setting.ReaderSettingsViewModel
import ephyra.presentation.core.components.CheckboxItem
import ephyra.presentation.core.components.SettingsChipRow
import ephyra.presentation.core.components.SliderItem
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.util.collectAsState
import kotlinx.coroutines.launch

@Composable
internal fun ColumnScope.ColorFilterPage(ViewModel: ReaderSettingsViewModel) {
    val customBrightness by ViewModel.preferences.customBrightness().collectAsState()
    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_custom_brightness),
        pref = ViewModel.preferences.customBrightness(),
    )

    /*
     * Sets the brightness of the screen. Range is [-75, 100].
     * From -75 to -1 a semi-transparent black view is shown at the top with the minimum brightness.
     * From 1 to 100 it sets that value as brightness.
     * 0 sets system brightness and hides the overlay.
     */
    if (customBrightness) {
        val customBrightnessValue by ViewModel.preferences.customBrightnessValue().collectAsState()
        SliderItem(
            value = customBrightnessValue,
            valueRange = -75..100,
            steps = 0,
            label = stringResource(ephyra.app.core.common.R.string.pref_custom_brightness),
            onChange = { ViewModel.preferences.customBrightnessValue().set(it) },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }

    val colorFilter by ViewModel.preferences.colorFilter().collectAsState()
    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_custom_color_filter),
        pref = ViewModel.preferences.colorFilter(),
    )
    val scope = rememberCoroutineScope()
    if (colorFilter) {
        val colorFilterValue by ViewModel.preferences.colorFilterValue().collectAsState()
        SliderItem(
            value = colorFilterValue.red,
            valueRange = 0..255,
            steps = 0,
            label = stringResource(ephyra.app.core.common.R.string.color_filter_r_value),
            onChange = { newRValue ->
                scope.launch {
                    ViewModel.preferences.colorFilterValue().getAndSet {
                        getColorValue(it, newRValue, RED_MASK, 16)
                    }
                }
            },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        SliderItem(
            value = colorFilterValue.green,
            valueRange = 0..255,
            steps = 0,
            label = stringResource(ephyra.app.core.common.R.string.color_filter_g_value),
            onChange = { newGValue ->
                scope.launch {
                    ViewModel.preferences.colorFilterValue().getAndSet {
                        getColorValue(it, newGValue, GREEN_MASK, 8)
                    }
                }
            },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        SliderItem(
            value = colorFilterValue.blue,
            valueRange = 0..255,
            steps = 0,
            label = stringResource(ephyra.app.core.common.R.string.color_filter_b_value),
            onChange = { newBValue ->
                scope.launch {
                    ViewModel.preferences.colorFilterValue().getAndSet {
                        getColorValue(it, newBValue, BLUE_MASK, 0)
                    }
                }
            },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        SliderItem(
            value = colorFilterValue.alpha,
            valueRange = 0..255,
            steps = 0,
            label = stringResource(ephyra.app.core.common.R.string.color_filter_a_value),
            onChange = { newAValue ->
                scope.launch {
                    ViewModel.preferences.colorFilterValue().getAndSet {
                        getColorValue(it, newAValue, ALPHA_MASK, 24)
                    }
                }
            },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )

        val colorFilterMode by ViewModel.preferences.colorFilterMode().collectAsState()
        SettingsChipRow(ephyra.app.core.common.R.string.pref_color_filter_mode) {
            ColorFilterMode.mapIndexed { index, it ->
                FilterChip(
                    selected = colorFilterMode == index,
                    onClick = { ViewModel.preferences.colorFilterMode().set(index) },
                    label = { Text(stringResource(it.first)) },
                )
            }
        }
    }

    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_grayscale),
        pref = ViewModel.preferences.grayscale(),
    )
    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.pref_inverted_colors),
        pref = ViewModel.preferences.invertedColors(),
    )
}

private fun getColorValue(currentColor: Int, color: Int, mask: Long, bitShift: Int): Int {
    return (color shl bitShift) or (currentColor and mask.inv().toInt())
}

private const val ALPHA_MASK: Long = 0xFF000000
private const val RED_MASK: Long = 0x00FF0000
private const val GREEN_MASK: Long = 0x0000FF00
private const val BLUE_MASK: Long = 0x000000FF
