package ephyra.feature.settings.screen.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.util.htmlReadyLicenseContent
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes

@Composable
fun OpenSourceLicensesScreen(
    navController: NavController = LocalNavController.current,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(ephyra.app.core.common.R.string.licenses),
                navigateUp = { navController.popBackStack() },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        val context = LocalContext.current
        val libraries by produceLibraries(
            context.resources.getIdentifier("aboutlibraries", "raw", context.packageName),
        )
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = contentPadding,
            onLibraryClick = {
                navController.navigate(
                    ScreenRoutes.OpenSourceLibraryLicense.createRoute(it.name),
                )
            },
        )
    }
}
