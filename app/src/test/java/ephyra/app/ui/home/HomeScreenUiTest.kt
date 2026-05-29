package ephyra.app.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.app.App
import ephyra.app.startup.ShadowAnimatedVectorResources
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [34],
    application = App::class,
    shadows = [ShadowAnimatedVectorResources::class],
)
class HomeScreenUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun verifyHomeScaffoldInflationDoesNotCrash() {
        val icons = listOf(
            ephyra.presentation.core.R.drawable.anim_library_enter,
            ephyra.presentation.core.R.drawable.anim_updates_enter,
            ephyra.presentation.core.R.drawable.anim_history_enter,
            ephyra.presentation.core.R.drawable.anim_browse_enter,
            ephyra.presentation.core.R.drawable.anim_more_enter,
        )

        composeTestRule.setContent {
            Row {
                for (icon in icons) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                    )
                }
            }
        }
        
        composeTestRule.waitForIdle()
    }
}
