package ephyra.app.architecture

import androidx.compose.runtime.CompositionLocalProvider
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
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.Screen
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The permanent gate for the "destination missing from the runtime root graph" class of error.
 *
 * **The defect this prevents.** On the 2026-11-13 nightly, tapping any manga crashed with
 * `IllegalArgumentException: Destination with route MangaDetails cannot be found in navigation
 * graph ... startDestination route=home`. The textual gate (`NavGraphRegistrationTest`) read the
 * sources and saw every registration — it cannot see runtime state, so a feature whose Hilt
 * multibinding is lost, or whose `register` silently fails, leaves the graph missing destinations
 * while every static check stays green. The failure mode is vicious: registration happens once at
 * startup, and the defect surfaces later as a crash on an unrelated user tap.
 *
 * This gate builds the **real** root graph on the JVM — the real Hilt-injected
 * `Set<@JvmSuppressWildcards FeatureApi>`, registered exactly as `MainActivity` does — and then
 * asserts, without composing any screen:
 *
 * 1. the injected feature set contains every feature the app declares (`@Binds @IntoSet`
 *    contributions can be lost wholesale; this names the missing one),
 * 2. every feature-owned typed route resolves to a destination on the runtime graph, matched by
 *    the route's kotlinx-serialization serial name — the same lookup `NavController.navigate`
 *    performs, so a missing destination here is the crash the user would have hit,
 * 3. the feature-owned string routes resolve too.
 *
 * Destination content lambdas are never composed: the assertions run against the built graph, so
 * a screen that crashes for unrelated reasons cannot mask or flake this gate.
 *
 * Evidence level: E2 (Robolectric, real DI, real graph). It cannot see a defect that lives only on
 * a physical device's runtime; the 2026-11-13 crash reproduced nowhere in CI and remains under
 * device-side diagnosis — this gate is the regression net for the class, not the proof of that
 * specific crash's cause.
 */
@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [36],
    application = App::class,
    shadows = [ShadowAnimatedVectorResources::class],
)
class RootGraphDestinationGateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface FeatureApiEntryPoint {
        fun featureApis(): Set<@JvmSuppressWildcards FeatureApi>
    }

    /**
     * Every FeatureApi the app declares via `@Binds @IntoSet`. If a new feature is added, add it
     * here: the point of the assertion is that the injected set is compared against an
     * independently maintained list, not against whatever Hilt happened to deliver.
     */
    private val expectedFeatureSimpleNames = listOf(
        "MangaFeatureApi",
        "BrowseFeatureApi",
        "PlayerFeatureApi",
        "ReaderFeatureApi",
        "WebViewFeatureApi",
        "LibraryFeatureApi",
        "MoreFeatureApi",
        "StatsFeatureApi",
        "UpcomingFeatureApi",
        "CategoryFeatureApi",
        "SettingsFeatureApi",
    )

    /** A typed route a feature owns, and the feature that must contribute it. */
    private data class ExpectedTypedRoute(
        val owningFeature: String,
        val serialName: String,
    )

    /**
     * The typed routes registered by features, resolved through each route's own generated
     * `serializer()` — the same serial name `NavController.navigate(route)` hashes when it looks
     * the destination up. If a feature gains a navigable typed route, add it here.
     */
    private val expectedTypedRoutes = listOf(
        ExpectedTypedRoute("MangaFeatureApi", Screen.MangaDetails.serializer().descriptor.serialName),
        ExpectedTypedRoute("BrowseFeatureApi", Screen.Browse.serializer().descriptor.serialName),
        ExpectedTypedRoute("BrowseFeatureApi", Screen.GlobalSearch.serializer().descriptor.serialName),
        ExpectedTypedRoute("BrowseFeatureApi", Screen.BrowseSource.serializer().descriptor.serialName),
        ExpectedTypedRoute("BrowseFeatureApi", Screen.ExtensionDetails.serializer().descriptor.serialName),
        ExpectedTypedRoute("PlayerFeatureApi", Screen.VideoPlayer.serializer().descriptor.serialName),
        ExpectedTypedRoute("ReaderFeatureApi", Screen.BookReader.serializer().descriptor.serialName),
        ExpectedTypedRoute("WebViewFeatureApi", Screen.WebView.serializer().descriptor.serialName),
    )

    /** String routes registered by features (pattern form, as registered). */
    private val expectedStringRoutes = listOf(
        "manga_notes/{mangaId}",
    )

    private fun dumpGraph(navController: NavHostController): String = buildString {
        appendLine("startDestination=" + navController.graph.startDestinationRoute)
        navController.graph.forEach { destination ->
            appendLine("  route=" + destination.route)
        }
    }

    /** Builds the root graph from the real injected Set<FeatureApi>, exactly as MainActivity does. */
    private fun buildRealRootGraph(): NavHostController {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val apis = EntryPointAccessors
            .fromApplication(app, FeatureApiEntryPoint::class.java)
            .featureApis()

        var navControllerRef: NavHostController? = null
        composeTestRule.setContent {
            val navController = rememberNavController()
            navControllerRef = navController
            CompositionLocalProvider(LocalNavController provides navController) {
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
            }
        }
        composeTestRule.waitForIdle()
        return checkNotNull(navControllerRef) { "NavHost did not produce a NavController" }
    }

    @Test
    fun injectedFeatureSetContainsEveryDeclaredFeature() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val injected = EntryPointAccessors
            .fromApplication(app, FeatureApiEntryPoint::class.java)
            .featureApis()
            .map { it.javaClass.simpleName }

        val missing = expectedFeatureSimpleNames.filterNot { it in injected }
        assertTrue(
            "The injected Set<FeatureApi> is missing: " + missing.joinToString(", ") +
                ". Injected features were:\n" + injected.sorted().joinToString("\n") +
                "\nA lost @Binds @IntoSet contribution leaves the graph silently missing every " +
                "destination that feature owns — the 2026-11-13 MangaDetails crash class.",
            missing.isEmpty(),
        )
    }

    @Test
    fun everyFeatureOwnedTypedRouteResolvesOnTheRealRootGraph() {
        val navController = buildRealRootGraph()
        val routes = navController.graph.mapNotNull { it.route }

        val missing = expectedTypedRoutes.filter { expected ->
            routes.none { it.startsWith(expected.serialName) }
        }
        assertTrue(
            "Typed destinations missing from the runtime root graph:\n" +
                missing.joinToString("\n") { "  " + it.owningFeature + " -> " + it.serialName } +
                "\n\nRuntime graph destinations:\n" + dumpGraph(navController) +
                "\nA destination missing here is exactly the crash a user tap would produce " +
                "(\"Destination with route X cannot be found\").",
            missing.isEmpty(),
        )
    }

    @Test
    fun everyFeatureOwnedStringRouteResolvesOnTheRealRootGraph() {
        val navController = buildRealRootGraph()
        val routes = navController.graph.mapNotNull { it.route }

        val missing = expectedStringRoutes.filterNot { it in routes }
        assertTrue(
            "String destinations missing from the runtime root graph: " + missing.joinToString(", ") +
                "\nRuntime graph destinations:\n" + dumpGraph(navController),
            missing.isEmpty(),
        )
    }
}
