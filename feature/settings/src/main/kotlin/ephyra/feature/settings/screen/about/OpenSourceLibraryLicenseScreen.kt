package ephyra.feature.settings.screen.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.navigation.NavController
import com.google.android.material.textview.MaterialTextView
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.util.htmlReadyLicenseContent
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.AppBarActions
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.navigation.LocalNavController
import kotlinx.collections.immutable.persistentListOf

@Composable
fun OpenSourceLibraryLicenseScreen(
    name: String,
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val libraries by produceLibraries(
        context.resources.getIdentifier("aboutlibraries", "raw", context.packageName),
    )
    val library = libraries?.libraries?.find { it.name == name }

    val website = library?.website

    Scaffold(
        topBar = {
            AppBar(
                title = name,
                navigateUp = { navController.popBackStack() },
                actions = {
                    if (!website.isNullOrEmpty()) {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(ephyra.app.core.common.R.string.website),
                                    icon = Icons.Default.Public,
                                    onClick = { uriHandler.openUri(website) },
                                ),
                            ),
                        )
                    }
                },
                scrollBehavior = it,
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(16.dp),
        ) {
            library?.licenses?.firstOrNull()?.htmlReadyLicenseContent?.let {
                HtmlLicenseText(html = it)
            }
        }
    }
}

@Composable
private fun HtmlLicenseText(html: String) {
    AndroidView(
        factory = {
            MaterialTextView(it)
        },
        update = {
            it.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
        },
    )
}
