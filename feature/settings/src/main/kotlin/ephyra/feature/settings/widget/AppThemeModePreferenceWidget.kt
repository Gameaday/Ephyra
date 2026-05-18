package ephyra.feature.settings.widget

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ephyra.domain.ui.model.ThemeMode
import ephyra.presentation.core.i18n.stringResource

private val options = mapOf(
    ThemeMode.SYSTEM to ephyra.app.core.common.R.string.theme_system,
    ThemeMode.LIGHT to ephyra.app.core.common.R.string.theme_light,
    ThemeMode.DARK to ephyra.app.core.common.R.string.theme_dark,
)

@Composable
fun AppThemeModePreferenceWidget(
    value: ThemeMode,
    onItemClick: (ThemeMode) -> Unit,
) {
    BasePreferenceWidget(
        subcomponent = {
            MultiChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PrefsHorizontalPadding),
            ) {
                options.onEachIndexed { index, (mode, labelRes) ->
                    SegmentedButton(
                        checked = mode == value,
                        onCheckedChange = { onItemClick(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index,
                            options.size,
                        ),
                    ) {
                        Text(stringResource(labelRes))
                    }
                }
            }
        },
    )
}
