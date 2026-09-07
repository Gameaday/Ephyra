package ephyra.feature.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.util.system.openInBrowser

@Composable
fun NewUpdateScreen(
    versionName: String,
    changelogInfo: String,
    releaseLink: String,
    downloadLink: String,
    navController: NavController = LocalNavController.current,
    viewModel: NewUpdateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val changelogInfoNoChecksum = remember {
        changelogInfo.replace("""---(\R|.)*Checksums(\R|.)*""".toRegex(), "")
    }

    NewUpdateScreen(
        versionName = versionName,
        changelogInfo = changelogInfoNoChecksum,
        onOpenInBrowser = { context.openInBrowser(releaseLink) },
        onRejectUpdate = { navController.popBackStack() },
        onAcceptUpdate = {
            viewModel.onEvent(
                NewUpdateEvent.AcceptUpdate(
                    downloadLink = downloadLink,
                    versionName = versionName,
                ),
            )
            navController.popBackStack()
        },
    )
}
