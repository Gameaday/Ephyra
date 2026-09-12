package ephyra.presentation.reader

import androidx.compose.runtime.Composable
import ephyra.feature.reader.setting.ReaderSettingsViewModel
import ephyra.presentation.reader.settings.ReaderSettingsSheet

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
