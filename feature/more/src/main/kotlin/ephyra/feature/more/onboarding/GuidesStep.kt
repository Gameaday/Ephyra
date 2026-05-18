package ephyra.feature.more.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.theme.TachiyomiPreviewTheme

internal class GuidesStep(
    private val onRestoreBackup: () -> Unit,
) : OnboardingStep {

    override val isComplete: Boolean = true

    @Composable
    override fun Content() {
        val handler = LocalUriHandler.current

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Text(stringResource(ephyra.app.core.common.R.string.onboarding_guides_new_user, stringResource(ephyra.app.core.common.R.string.app_name)))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { handler.openUri(GETTING_STARTED_URL) },
            ) {
                Text(stringResource(ephyra.app.core.common.R.string.getting_started_guide))
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Text(stringResource(ephyra.app.core.common.R.string.onboarding_guides_returning_user, stringResource(ephyra.app.core.common.R.string.app_name)))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRestoreBackup,
            ) {
                Text(stringResource(ephyra.app.core.common.R.string.pref_restore_backup))
            }
        }
    }
}

const val GETTING_STARTED_URL = "https://ephyra.app/docs/guides/getting-started"

@PreviewLightDark
@Composable
private fun GuidesStepPreview() {
    TachiyomiPreviewTheme {
        GuidesStep(
            onRestoreBackup = {},
        ).Content()
    }
}
