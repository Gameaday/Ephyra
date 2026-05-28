package ephyra.app.ui.deeplink

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.app.App
import ephyra.app.ui.main.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = App::class)
class DeepLinkActivityTest {

    @Test
    fun verifyDeepLinkActivityLaunchesMainActivityOnValidSearchIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, DeepLinkActivity::class.java).apply {
            action = Intent.ACTION_SEARCH
            putExtra("query", "One Piece")
        }

        ActivityScenario.launch<DeepLinkActivity>(intent).use { scenario ->
            // Since the activity calls finish() in onCreate, it gets destroyed immediately.
            assertEquals("Activity must be in DESTROYED state", Lifecycle.State.DESTROYED, scenario.state)

            // Retrieve the next started activity from the shadow application
            val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            val nextStartedActivity = shadowApp.nextStartedActivity

            assertNotNull("An intent to launch another activity should be registered", nextStartedActivity)
            assertEquals(
                "The class to launch must be MainActivity",
                MainActivity::class.java.name,
                nextStartedActivity?.component?.className,
            )
            assertEquals(
                "The query extra should be carried over",
                "One Piece",
                nextStartedActivity?.getStringExtra("query"),
            )
        }
    }

    @Test
    fun verifyDeepLinkActivityLaunchesMainActivityOnValidSendIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, DeepLinkActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://ephyra.app/manga/123")
        }

        ActivityScenario.launch<DeepLinkActivity>(intent).use { scenario ->
            assertEquals("Activity must be in DESTROYED state", Lifecycle.State.DESTROYED, scenario.state)

            val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            val nextStartedActivity = shadowApp.nextStartedActivity

            assertNotNull("An intent to launch another activity should be registered", nextStartedActivity)
            assertEquals(
                "The class to launch must be MainActivity",
                MainActivity::class.java.name,
                nextStartedActivity?.component?.className,
            )
            assertEquals(
                "The text extra should be carried over",
                "https://ephyra.app/manga/123",
                nextStartedActivity?.getStringExtra(Intent.EXTRA_TEXT),
            )
        }
    }

    @Test
    fun verifyDeepLinkActivityHandlesInsecureIntentGracefully() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, DeepLinkActivity::class.java).apply {
            action = "ephyra.app.INSECURE_ACTION"
            putExtra("insecure_extra", 9999)
        }

        ActivityScenario.launch<DeepLinkActivity>(intent).use { scenario ->
            assertEquals("Activity must be in DESTROYED state", Lifecycle.State.DESTROYED, scenario.state)

            // Assert that no redirection happened to MainActivity
            val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            val nextStartedActivity = shadowApp.nextStartedActivity
            assertNull("No activity should be launched for insecure intent", nextStartedActivity)
        }
    }
}
