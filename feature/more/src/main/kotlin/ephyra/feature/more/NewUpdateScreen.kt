package ephyra.feature.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import ephyra.domain.release.service.AppUpdateDownloader
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.util.system.openInBrowser

@Composable
fun NewUpdateScreen(
    versionName: String,
    changelogInfo: String,
    releaseLink: String,
    downloadLink: String,
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current
    val appUpdateDownloader = remember { ephyra.core.common.di.CoreContainer.get<AppUpdateDownloader>() }
    val changelogInfoNoChecksum = remember {
        changelogInfo.replace("""---(\R|.)*Checksums(\R|.)*""".toRegex(), "")
    }

    NewUpdateScreen(
        versionName = versionName,
        changelogInfo = changelogInfoNoChecksum,
        onOpenInBrowser = { context.openInBrowser(releaseLink) },
        onRejectUpdate = { navController.popBackStack() },
        onAcceptUpdate = {
            appUpdateDownloader.start(
                url = downloadLink,
                title = versionName,
            )
            navController.popBackStack()
        },
    )
}
