package ephyra.app.startup

import android.app.Application
import android.content.res.Resources
import android.content.res.XmlResourceParser
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.app.App
import ephyra.app.ui.main.MainActivity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowResources
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * Custom Robolectric Shadow to intercept and redirect animated vector drawables (AVDs).
 *
 * Jetpack Compose's `painterResource` calls `loadVectorResource` under Robolectric.
 * Under Robolectric, `loadVectorResource` throws an exception if the XML root tag of
 * a resource is `<animated-vector>` instead of `<vector>`. This shadow redirects all
 * dynamic XML assets starting with "anim_" to a safe static vector drawable to bypass
 * this Robolectric limitation.
 *
 * To avoid ClassCastException in Robolectric's internals (which explicitly casts
 * Shadows.shadowOf(context.resources) to ShadowResources), we inherit from ShadowResources
 * and shadow the public Resources class. We configure Gradle to exclude this specific
 * class from class scanning during JUnit test discovery to avoid NoClassDefFoundError
 * on internal Android package-private types referenced by ShadowResources.
 */
@Implements(value = Resources::class)
class ShadowAnimatedVectorResources : ShadowResources() {
    @RealObject
    private lateinit var realResources: Resources

    @Implementation
    override fun loadXmlResourceParser(resId: Int, type: String): XmlResourceParser {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = try {
            context.resources.getResourceEntryName(resId)
        } catch (e: Exception) {
            ""
        }
        println("ShadowAnimatedVectorResources: loadXmlResourceParser(resId=$resId, type=$type) -> name='$name'")
        if (name.startsWith("anim_")) {
            val safeId = ephyra.presentation.core.R.drawable.ic_book_24dp
            println("ShadowAnimatedVectorResources: Redirecting animated vector '$name' to safe vector drawable")
            return Shadow.directlyOn<XmlResourceParser, Resources>(
                realResources,
                Resources::class.java,
                "loadXmlResourceParser",
                ClassParameter.from(java.lang.Integer.TYPE, safeId),
                ClassParameter.from(String::class.java, type),
            )
        }
        return Shadow.directlyOn<XmlResourceParser, Resources>(
            realResources,
            Resources::class.java,
            "loadXmlResourceParser",
            ClassParameter.from(java.lang.Integer.TYPE, resId),
            ClassParameter.from(String::class.java, type),
        )
    }

    @Implementation
    override fun loadXmlResourceParser(file: String, id: Int, assetCookie: Int, type: String): XmlResourceParser {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = try {
            context.resources.getResourceEntryName(id)
        } catch (e: Exception) {
            ""
        }
        println(
            "ShadowAnimatedVectorResources: loadXmlResourceParser(file='$file', id=$id, cookie=$assetCookie, type=$type) -> name='$name'",
        )
        if (name.startsWith("anim_")) {
            val safeId = ephyra.presentation.core.R.drawable.ic_book_24dp
            println(
                "ShadowAnimatedVectorResources: Redirecting animated vector '$name' to safe vector drawable (overload 2)",
            )
            return loadXmlResourceParser(safeId, type)
        }
        return Shadow.directlyOn<XmlResourceParser, Resources>(
            realResources,
            Resources::class.java,
            "loadXmlResourceParser",
            ClassParameter.from(String::class.java, file),
            ClassParameter.from(java.lang.Integer.TYPE, id),
            ClassParameter.from(java.lang.Integer.TYPE, assetCookie),
            ClassParameter.from(String::class.java, type),
        )
    }
}

/**
 * Robolectric-based integration tests running on the local JVM.
 *
 * These tests mock/shadow the Android framework to verify application startup and
 * primary activity launching without requiring a physical device or emulator.
 * This guarantees that critical initialization blocks, DI resolution pathways,
 * and entry point lifecycles remain free of runtime crashes.
 */
@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [34],
    application = App::class,
    shadows = [ShadowAnimatedVectorResources::class],
)
class AppStartupTest {

    /**
     * Verifies that the custom [App] class is successfully initialized by the Android
     * runtime, and all essential sequential startup phases (logging, crash handler, DI)
     * are traversed and marked complete without causing process crashes.
     */
    @Test
    fun verifyAppStartupSucceeds() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertNotNull("Application context should be constructed", app)
        assertTrue("Constructed application context must be App instance", app is App)

        // Verify sequential progress of crucial startup guard phases
        assertTrue(
            "Logging initialization phase should be completed",
            StartupGuard.awaitPhase("logging", timeoutMs = 100),
        )
        assertTrue("Crash handler phase should be completed", StartupGuard.awaitPhase("crash_handler", timeoutMs = 100))
        assertTrue(
            "DI container registration phase should be completed",
            StartupGuard.awaitPhase("di_container", timeoutMs = 100),
        )
        assertTrue(
            "Notifications registration phase should be completed",
            StartupGuard.awaitPhase("notifications", timeoutMs = 100),
        )
        assertTrue(
            "Reactive bindings configuration phase should be completed",
            StartupGuard.awaitPhase("reactive_bindings", timeoutMs = 100),
        )
    }

    /**
     * Verifies that [MainActivity] can be bootstrapped, initialized, and created
     * under the simulated Android environment.
     *
     * This triggers Hilt dependency injection, Activity onCreate/onStart lifecycles,
     * and compiles/registers the Jetpack Compose component hierarchy. Any broken DI graphs,
     * missing layout components, or lifecycle exceptions will instantly cause this test to fail.
     */
    @Test
    fun verifyMainActivityLaunchDoesNotCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull("MainActivity instance must be successfully injected and created", activity)

                // Confirm the activity was configured with the primary task root status
                assertTrue(
                    "MainActivity should be in task root or finished safely",
                    activity.isTaskRoot || activity.isFinishing,
                )
            }
        }
    }
}
