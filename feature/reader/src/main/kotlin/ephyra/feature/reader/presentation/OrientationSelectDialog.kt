package ephyra.feature.reader.presentation

import androidx.compose.runtime.Composable
import ephyra.feature.reader.presentation.settings.ReaderSettingsSheet
import ephyra.feature.reader.setting.ReaderSettingsViewModel

@Composable
fun OrientationSelectDialog(
    onDismissRequest: () -> Unit,
    ViewModel: ReaderSettingsViewModel,
    onChange: (Int) -> Unit = {},
) {
    ReaderSettingsSheet(
        onDismissRequest = onDismissRequest,
        onShowMenus = {},
        onHideMenus = {},
        viewModel = ViewModel,
        initialPage = 0,
    )
}
