package ephyra.app.architecture

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import ephyra.app.App
import ephyra.app.startup.ShadowAnimatedVectorResources
import ephyra.presentation.core.feature.FeatureApi
import ephyra.presentation.core.ui.navigation.Screen
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * TEMPORARY diagnostic (deleted after triage): behavioural repro for the device crash
 * "Destination with route MangaDetails cannot be found in navigation graph ... startDestination
 * route=home" (nightly cdd5a72, every entry path to the series page, persists across restart).
 *
 * The structural gate (NavGraphRegistrationTest) reads source text only and cannot see runtime
 * state. These tests build the real root graph on the JVM — test 1 from the real Hilt-injected
 * Set<FeatureApi>, exactly as MainActivity does; test 2 as a bare typed-route graph — and then
 * actually navigate to Screen.MangaDetails. On failure the assertion message carries the full
 * injected-feature list and the runtime graph's destination dump, which is the observation the
 * static analysis could not make.
 */
@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [36],
    application = App::class,
    shadows = [ShadowAnimatedVectorResources::class],
)
class MangaDetailsGraphReproTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface FeatureApiEntryPoint {
        fun featureApis(): Set<@JvmSuppressWildcards FeatureApi>
    }

    private fun dumpGraph(navController: NavHostController): String = buildString {
        appendLine("startDestination=" + navController.graph.startDestinationRoute)
        navController.graph.forEach { destination ->
            appendLine("  route=" + destination.route)
        }
    }

    @Test
    fun realRootGraphFromInjectedFeatureApisNavigatesToMangaDetails() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val apis = EntryPointAccessors
            .fromApplication(app, FeatureApiEntryPoint::class.java)
            .featureApis()
        val apiNames = apis.map { it.javaClass.name }.sorted()

        var navControllerRef: NavHostController? = null
        var failure: Throwable? = null

        composeTestRule.setContent {
            val navController = rememberNavController()
            navControllerRef = navController
            NavHost(
                navController = navController,
                startDestination = ScreenRoutes.Home.route,
            ) {
                composable(ScreenRoutes.Home.route) { }
                apis.forEach { api ->
                    try {
                        api.register(this, navController)
                    } catch (e: Exception) {
                        throw IllegalStateException(
                            "register failed for " + api.javaClass.name,
                            e,
                        )
                    }
                }
            }
            LaunchedEffect(Unit) {
                try {
                    navController.navigate(Screen.MangaDetails(1L, false))
                } catch (e: Throwable) {
                    failure = e
                }
            }
        }
        composeTestRule.waitForIdle()

        val navController = checkNotNull(navControllerRef)
        assertTrue(
            "Injected Set<FeatureApi> must contain MangaFeatureApi. Injected:\n" +
                apiNames.joinToString("\n"),
            apiNames.any { it.contains("MangaFeatureApi") },
        )
        val caught = failure
        if (caught != null) {
            fail(
                "navigate(Screen.MangaDetails) failed on the real root graph.\n" +
                    "Injected FeatureApis:\n" + apiNames.joinToString("\n") +
                    "\nRuntime graph destinations:\n" + dumpGraph(navController) +
                    "\nError:\n" + caught.stackTraceToString().take(3000),
            )
        }
    }

    @Test
    fun bareTypedRouteGraphNavigatesToMangaDetails() {
        var failure: Throwable? = null

        composeTestRule.setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "home") {
                composable("home") { }
                composable<Screen.MangaDetails> { }
            }
            LaunchedEffect(Unit) {
                try {
                    navController.navigate(Screen.MangaDetails(1L, false))
                } catch (e: Throwable) {
                    failure = e
                }
            }
        }
        composeTestRule.waitForIdle()

        val caught = failure
        if (caught != null) {
            fail(
                "Bare typed-route graph could not navigate to Screen.MangaDetails.\n" +
                    "Error:\n" + caught.stackTraceToString().take(3000),
            )
        }
    }
}
