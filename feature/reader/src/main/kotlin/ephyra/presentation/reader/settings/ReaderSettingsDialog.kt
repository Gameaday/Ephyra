package ephyra.presentation.reader.settings

import androidx.compose.runtime.Composable
import ephyra.feature.reader.setting.ReaderSettingsViewModel

@Composable
fun ReaderSettingsDialog(
    onDismissRequest: () -> Unit,
    onShowMenus: () -> Unit,
    onHideMenus: () -> Unit,
    ViewModel: ReaderSettingsViewModel,
    initialPage: Int = 0,
) {
    ReaderSettingsSheet(
        onDismissRequest = onDismissRequest,
        onShowMenus = onShowMenus,
        onHideMenus = onHideMenus,
        viewModel = ViewModel,
        initialPage = initialPage,
    )
}
