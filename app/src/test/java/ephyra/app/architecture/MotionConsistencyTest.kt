package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Structural gates for the motion that spans more than one screen.
 *
 * **The defects these prevent.** Each was a rule that held in one layer and not the other, so it is
 * invisible in review and produces no compile error:
 *
 * 1. **The reader had no entry animation.** `ReaderActivity` set only the *close* transition, so
 *    opening a chapter cut while leaving one slid. Nothing about the reader's code looked wrong; the
 *    open half was simply never registered.
 * 2. **Two distances for one transition.** The Activity anims for the shared-axis push moved 5% of
 *    the viewport over a flat 300ms while `MotionTokens` moved 30% with an asymmetric fade. Same
 *    name, same semantic, two different gestures depending on whether the destination happened to be
 *    an Activity or a nav route.
 * 3. **A shared cover with only one end.** `MotionPolicy` promises the container will not move when
 *    the cover carries the transition. A screen that renders the cover but provides no
 *    animated-visibility scope cannot match, so the promise left the transition with nothing to
 *    animate at all.
 * 4. **One boundary, three motions.** The window renderings that fixed (1) and (2) could not join
 *    the predictive back gesture: on Android 14+ the gesture runs the system's cross-activity
 *    animation and bypasses a custom close transition, so leaving the reader moved one way by
 *    gesture, another way by the toolbar's up arrow — and the entry slide paired with an exit that
 *    was not its reverse. That is the owner-reported inconsistency at this boundary (2026-11-08),
 *    and no app window animation can fix it by participating, because a window cannot reveal the
 *    activity underneath itself.
 * 5. **The platform's reveal was the residue.** Leaving the boundary to the platform — the fix for
 *    (4) — was consistent but ugly: the Android 14+ predictive reveal scales and slides the window,
 *    which reads as elements flying and zooming into the background, and the open path played no
 *    preview at all. The owner's motion audit rejected it (2026-11-09), and the directive locked
 *    the replacement: one minimal crossfade, no movement, owned by the app on every path.
 * 6. **Component motion still moved.** The boundary was one crossfade, but in-app navigation
 *    still slid: tab peers travelled horizontally, hierarchical pushes ran the shared axis X,
 *    the adaptive pane slid a quarter of the viewport, the calendar title rolled the full screen
 *    height, and the bottom bar grew and shrank in place. The owner's audit rejected movement
 *    everywhere it reads as elements flying (2026-11-09); this sweep replaces each with the
 *    policy crossfade or a drag-coupled dim.
 *
 * **The reversals, recorded.** Defects (1) and (2) were originally gated by requiring the window
 * pair; defect (4) inverted those gates and made the boundary the platform's; defect (5) is why
 * the boundary is back under the app — as a crossfade rather than a movement. The theme
 * (`Theme.Tachiyomi.ActivityBoundary`) renders every open and non-gesture close,
 * `ActivityBoundaryCrossfadeHost` routes the predictive gesture's commit through `finish()` so the
 * same close crossfade plays instead of the system reveal, and the one deliberate remaining
 * override — the unlock screen's zero-animation open, a privacy decision — is still named so a
 * sweep cannot delete it as "the last override" by mistake.
 *
 * These read the source, following the pattern established by `LibrarySeriesTransitionTest`: a
 * behavioural test would have to drive a transition nobody can drive in a unit test.
 */
class MotionConsistencyTest {

    private fun sourceFile(relativePath: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        throw AssertionError("$relativePath not found")
    }

    /** Whether a tracked-path file exists anywhere above this test's working directory. */
    private fun exists(relativePath: String): Boolean {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, relativePath).exists()) return true
            dir = dir.parentFile
        }
        return false
    }

    /** Source with prose removed, so a comment describing a mechanism cannot satisfy a rule. */
    private fun codeOf(relativePath: String): String = sourceFile(relativePath)
        .readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//.*"), "")

    private val readerActivity =
        "feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderActivity.kt"
    private val webViewActivity =
        "feature/webview/src/main/kotlin/ephyra/feature/webview/WebViewActivity.kt"
    private val secureActivityDelegate =
        "app/src/main/java/ephyra/app/ui/base/delegate/SecureActivityDelegate.kt"
    private val mainActivity = "app/src/main/java/ephyra/app/ui/main/MainActivity.kt"
    private val manifest = "app/src/main/AndroidManifest.xml"
    private val browseFeatureApi =
        "feature/browse/src/main/kotlin/ephyra/feature/browse/BrowseFeatureApi.kt"
    private val mangaFeatureApi =
        "feature/manga/src/main/kotlin/ephyra/feature/manga/MangaFeatureApi.kt"
    private val motion = "presentation-core/src/main/java/ephyra/presentation/core/theme/MotionTokens.kt"
    private val motionPolicy =
        "core/domain/src/main/java/ephyra/domain/navigation/motion/MotionPolicy.kt"
    private val historyItem =
        "feature/history/src/main/kotlin/ephyra/feature/history/components/HistoryItem.kt"
    private val updatesUiItem =
        "feature/updates/src/main/kotlin/ephyra/feature/updates/UpdatesUiItem.kt"

    private val anim = "presentation-core/src/main/res/anim"

    /**
     * The reader and the WebView are separate Activities, and their boundary motion is one
     * app-owned crossfade (owner directive 2026-11-09). The theme renders the window fades on
     * every open and non-gesture close; the host is what makes the *gesture* obey it, because a
     * committed predictive back otherwise lets the system finish the activity with its own
     * cross-activity reveal and the theme is bypassed. An activity without the host, or without
     * the reduce-motion local provided, splits one boundary back into motions that differ by how
     * the user leaves — the exact defect class this class exists for.
     */
    @Test
    fun `the activity boundary motion is one app-owned crossfade`() {
        listOf(
            "ReaderActivity" to readerActivity,
            "WebViewActivity" to webViewActivity,
        ).forEach { (name, path) ->
            val code = codeOf(path)
            assertTrue(
                code.contains("ActivityBoundaryCrossfadeHost("),
                "$name must wrap its content in ActivityBoundaryCrossfadeHost: the theme cannot " +
                    "intercept the predictive back gesture, so without the host a committed swipe " +
                    "lets the system finish the activity with its scale reveal — the motion the " +
                    "owner rejected — instead of the boundary crossfade.",
            )
            assertTrue(
                code.contains("LocalMotionPreference provides"),
                "$name must provide LocalMotionPreference: the host reads it to skip the gesture " +
                    "dim under the reduce-motion preference, and an unprovided local silently " +
                    "reduces nothing.",
            )
            assertFalse(
                code.contains("OVERRIDE_TRANSITION"),
                "$name must not override its window transitions. An override competes with the " +
                    "theme's crossfade on the non-gesture paths and returns the " +
                    "one-boundary-several-motions defect.",
            )
            assertFalse(
                code.contains("shared_axis_x_"),
                "$name must not name a shared-axis window resource; the boundary motion is the " +
                    "crossfade, per the rule above.",
            )
        }

        val boundaryTheme = "Theme.Tachiyomi.ActivityBoundary"
        assertEquals(
            2,
            sourceFile(manifest).readText().split(boundaryTheme).size - 1,
            "The manifest must declare the boundary theme on exactly the reader and the WebView " +
                "(2 occurrences of $boundaryTheme): fewer leaves one boundary on the platform's " +
                "default motion, more spreads the boundary theme to activities that are not this " +
                "boundary.",
        )

        assertTrue(
            codeOf(secureActivityDelegate).contains("overrideTransitionCompat"),
            "SecureActivityDelegate keeps the one deliberate override: the unlock screen opens " +
                "with no animation at all, a privacy decision rather than a motion-language one, " +
                "and this rule names it so a future sweep cannot delete it as 'the last override' " +
                "by mistake.",
        )
    }

    /**
     * The boundary crossfade is the policy's crossfade, and it moves nothing. The window fades
     * are the only new motion at this boundary, so every property the owner can perceive is
     * decided here: pure alpha (no translate/scale/rotate — the directive bans movement, and a
     * transform smuggled into an `<alpha>`-shaped file would be invisible in review), the
     * policy's crossfade duration so the window and Compose crossfades read as one gesture, the
     * fast-out-slow-in interpolator the Compose crossfade eases with, and all four activity
     * transitions pointed at the same fade pair so every open and close path renders identically.
     */
    @Test
    fun `the boundary window rendering is the policy crossfade and moves nothing`() {
        val crossfadeMillis = Regex("""CROSSFADE_DURATION_MILLIS\s*:\s*Int\s*=\s*(\d+)""")
            .find(codeOf(motionPolicy))
            ?.groupValues
            ?.get(1)
            ?: throw AssertionError(
                "MotionPolicy must declare CROSSFADE_DURATION_MILLIS as a readable literal.",
            )

        listOf("in", "out").forEach { half ->
            val xml = sourceFile("$anim/activity_boundary_fade_$half.xml").readText()
            assertTrue(
                xml.contains("android:duration=\"$crossfadeMillis\""),
                "activity_boundary_fade_$half must run the policy's crossfade duration " +
                    "($crossfadeMillis ms): the window crossfade and the Compose crossfades are " +
                    "one gesture with one duration, and a number that drifts makes the boundary " +
                    "read like a different motion language.",
            )
            assertTrue(
                xml.contains("@android:interpolator/fast_out_slow_in"),
                "activity_boundary_fade_$half must ease with fast-out-slow-in, the easing the " +
                    "Compose crossfade token uses, for the same reason as the duration.",
            )
            assertTrue(
                xml.contains("<alpha"),
                "activity_boundary_fade_$half must be an <alpha> animation.",
            )
            listOf("translate", "scale", "rotate").forEach { transform ->
                assertFalse(
                    xml.contains(transform),
                    "activity_boundary_fade_$half must not contain '$transform': the directive is " +
                        "no movement at this boundary, and a transform smuggled into the window " +
                        "fade returns the flying-and-zooming motion the owner rejected.",
                )
            }
        }

        assertTrue(
            codeOf(motion).contains("FastOutSlowInEasing"),
            "MotionTokens must name FastOutSlowInEasing: the window fades declare the same " +
                "easing by interpolator, and this ties the declaration to the token that " +
                "renders the in-app crossfade.",
        )

        val style = sourceFile("presentation-core/src/main/res/values/styles.xml").readText()
            .substringAfter("<style name=\"ActivityBoundaryWindowAnimations\"")
            .substringBefore("</style>")
        listOf(
            "activityOpenEnterAnimation",
            "activityOpenExitAnimation",
            "activityCloseEnterAnimation",
            "activityCloseExitAnimation",
        ).forEach { item ->
            assertTrue(
                style.contains("name=\"android:$item\""),
                "ActivityBoundaryWindowAnimations must set android:$item: the four activity " +
                    "transition slots are how a theme reaches every start path (intent, deep " +
                    "link, gesture commit), and a missing slot leaves that path on the " +
                    "platform's default motion.",
            )
        }
        assertTrue(
            style.contains("@anim/activity_boundary_fade_in") &&
                style.contains("@anim/activity_boundary_fade_out"),
            "ActivityBoundaryWindowAnimations must point at the boundary fade pair, not the " +
                "platform defaults.",
        )

        val themes = sourceFile("app/src/main/res/values/themes.xml").readText()
        assertTrue(
            themes.contains("<style name=\"Theme.Tachiyomi.ActivityBoundary\">"),
            "app themes must define Theme.Tachiyomi.ActivityBoundary — the style the manifest " +
                "declares on both boundary activities.",
        )
        assertTrue(
            themes.contains("@style/ActivityBoundaryWindowAnimations"),
            "Theme.Tachiyomi.ActivityBoundary must install ActivityBoundaryWindowAnimations as " +
                "its windowAnimationStyle.",
        )

        val host = codeOf(
            "presentation-core/src/main/java/ephyra/presentation/core/ui/navigation/ActivityBoundaryCrossfadeHost.kt",
        )
        assertTrue(
            host.contains("PredictiveBackProgress("),
            "ActivityBoundaryCrossfadeHost must participate through the shared " +
                "PredictiveBackProgress helper, not a bare PredictiveBackHandler — see " +
                "PredictiveBackGateTest.",
        )
        assertTrue(
            host.contains("onCancelled"),
            "ActivityBoundaryCrossfadeHost must restore its dim on cancellation: a cancelled " +
                "gesture leaves the scrim stranded otherwise, the exact defect the " +
                "PredictiveBack helper contract exists for.",
        )
        assertTrue(
            host.contains("effectiveReducedMotion"),
            "ActivityBoundaryCrossfadeHost must resolve reduced motion through " +
                "effectiveReducedMotion, so the OS animator scale and the in-app preference " +
                "merge instead of competing.",
        )
    }

    /**
     * The four `shared_axis_x_*.xml` window renderings were deleted with the activity overrides.
     * An orphan rendering is a second definition of the shared axis with no consumer and no gate
     * over it — the exact drift this class exists to prevent — so they must not come back.
     */
    @Test
    fun `the shared-axis window renderings stay deleted`() {
        listOf("push_enter", "push_exit", "pop_enter", "pop_exit").forEach { half ->
            assertFalse(
                exists("$anim/shared_axis_x_$half.xml"),
                "shared_axis_x_$half.xml must not come back: its consumers are gone, and an " +
                    "orphan window rendering is a second definition of the shared axis with no " +
                    "gate over it.",
            )
        }
    }

    /**
     * Every destination the policy names as a shared-cover host must provide the scope the shared
     * element needs, or the policy promises motion that cannot happen.
     *
     * `hostsSharedCover` is the "and" of two obligations; this is the other half of that sentence
     * written as a test, because the two live in different modules and nothing else couples them.
     */
    @Test
    fun `every shared cover host provides an animated visibility scope`() {
        val scope = "LocalNavAnimatedVisibilityScope provides"

        assertTrue(
            codeOf(mainActivity).contains(scope),
            "The Home destination must provide an animated-visibility scope: it is a shared-cover " +
                "host, and covers inside the tab shell are the source end of Library <-> Series.",
        )
        assertTrue(
            codeOf(mangaFeatureApi).contains(scope),
            "The series destination must provide an animated-visibility scope: it is the target " +
                "end of every shared-cover pair.",
        )

        val browse = codeOf(browseFeatureApi)
        // Boundaries are code, not comments: `codeOf` strips prose, so a `//` marker would not
        // exist here and the slice would silently become the rest of the file.
        assertTrue(
            browse.substringAfter("composable<Screen.BrowseSource>")
                .substringBefore("ScreenRoutes.SourcePreferences.route")
                .contains(scope),
            "The source-results destination must provide an animated-visibility scope. It lists " +
                "manga covers, and MainActivity names it a shared-cover host, so without the scope " +
                "the series page's cover has no counterpart and the transition has nothing to " +
                "animate.",
        )
        assertTrue(
            browse.substringAfter("composable<Screen.GlobalSearch>")
                .substringBefore("composable<Screen.BrowseSource>")
                .contains(scope),
            "The global-search destination must provide an animated-visibility scope, for the same " +
                "reason as the source-results destination.",
        )
    }

    /**
     * The pair must only be claimed by a push into a series or a pop out of one.
     *
     * A series page can navigate *forward* into a cover list ("browse more from this source"). When
     * the pair was keyed only on which entry is the series page, that forward move was treated as a
     * return: the container held still, the shorter backward timeline ran, and a shared cover was
     * expected on a target that does not necessarily render it. Direction must come from whether the
     * transition fired on the push or the pop path.
     */
    @Test
    fun `the shared cover pair is pop aware`() {
        val activity = codeOf(mainActivity)
        val pair = activity
            .substringAfter("fun motionRoutePair(")
            .substringBefore("private fun NavBackStackEntry.sharedCoverMangaId")

        assertTrue(
            pair.contains("!isPop && from.hostsSharedCover()"),
            "The forward branch of the pair must be push-gated (`!isPop`). Without the gate a " +
                "forward move out of a series page into a cover list is mistaken for a return, " +
                "and the pair's deliberate no-op container leaves that move with nothing to " +
                "animate.",
        )
        assertTrue(
            pair.contains("isPop && from.isMangaDetails()"),
            "The backward branch of the pair must be pop-gated (`isPop`), for the same reason.",
        )

        val direction = activity
            .substringAfter("fun motionDirectionFor(")
            .substringBefore("private fun NavBackStackEntry.sharedCoverMangaId")
        assertTrue(
            direction.contains("if (isPop)"),
            "Direction must be read from push vs pop, not from which entry is the series page. " +
                "Reading it from entry roles silently gave a forward move the backward timeline.",
        )
    }

    /**
     * A list item that opens a series must name its cover with the id it is going to open.
     *
     * The shared element matches by key. A key built from anything other than the id passed to
     * `Screen.MangaDetails` either fails to match — leaving a container that deliberately does not
     * move, i.e. a cut — or matches the wrong manga's cover.
     */
    @Test
    fun `history and updates name the cover for the manga they open`() {
        val history = codeOf(historyItem)
        assertTrue(
            history.contains("mangaId = history.mangaId"),
            "HistoryItem must key its cover on `history.mangaId`, the same id its onClickCover " +
                "passes to Screen.MangaDetails. It passed no id at all, so opening a series from " +
                "History could not share the cover.",
        )

        val updates = codeOf(updatesUiItem)
        assertTrue(
            updates.contains("mangaId = update.mangaId"),
            "UpdatesUiItem must key its cover on `update.mangaId`, the same id UpdatesScreen " +
                "passes to Screen.MangaDetails. It passed no id at all, so opening a series from " +
                "Updates could not share the cover.",
        )
    }

    /**
     * The HIERARCHICAL pair is the policy crossfade, and no slide rendering of it exists to
     * drift. The owner's motion audit (2026-11-09) removed the shared axis from ordinary
     * navigation — a cross-screen slide reads as elements flying — so the fallback's rendering is
     * the crossfade the boundary already plays, and any slide token that returns is a second
     * definition of a transition the policy no longer assigns.
     */
    @Test
    fun `the hierarchical fallback is the policy crossfade and has no slide rendering`() {
        val policy = codeOf(motionPolicy)
        assertTrue(
            Regex("""HIERARCHICAL_(FORWARD|BACK)_DURATION_MILLIS""").findAll(policy).none(),
            "MotionPolicy must not declare HIERARCHICAL_*_DURATION_MILLIS: the pair is the policy " +
                "crossfade now, and a private duration is a second timeline that no rendering plays.",
        )
        assertTrue(
            policy.contains("MotionRoutePair.HIERARCHICAL -> ContainerMotion.CROSSFADE"),
            "The HIERARCHICAL plan must assign the crossfade: a container that moves " +
                "reintroduces the flying screens the owner's motion audit rejected.",
        )

        val token = codeOf(motion)
        assertFalse(
            token.contains("m3SharedAxisX"),
            "MotionTokens must not define a shared-axis X rendering: its consumer (the " +
                "HIERARCHICAL plan) renders the crossfade, and an orphan slide function is the " +
                "wrong transition waiting to be picked.",
        )
        assertFalse(
            token.contains("m3TabSlide"),
            "MotionTokens must not define a tab-slide rendering: tab peers crossfade, and an " +
                "orphan slide function is the wrong transition waiting to be picked.",
        )
        assertFalse(
            token.contains("slideInHorizontally"),
            "MotionTokens must not import a slide primitive: every token it renders is pure " +
                "alpha or a no-op.",
        )
    }

    /**
     * The undeclared-route fallback is a policy decision, not an activity one. It used to be
     * hardcoded in the NavHost slots — a null from `motionRoutePair` fell through to direct
     * `m3SharedAxisX*` calls — which meant the durations and easing of most transitions in the
     * app were decided outside `MotionPolicy` and invisible to its tests.
     */
    @Test
    fun `the undeclared fallback is a policy plan, not an activity decision`() {
        val activity = codeOf(mainActivity)

        assertFalse(
            activity.contains("m3SharedAxisX"),
            "MainActivity must not name a shared-axis token directly. The fallback for undeclared " +
                "routes is the policy's HIERARCHICAL pair; naming the transition here re-decides " +
                "the policy locally, the exact defect the contract forbids.",
        )
        assertFalse(
            activity.contains("if (plan != null)"),
            "The NavHost transition slots must not branch on a nullable plan: a null branch is " +
                "where a local fallback hides. motionPlanFor resolves every transition to a plan.",
        )
        assertTrue(
            codeOf(motionPolicy).contains("HIERARCHICAL"),
            "MotionPolicy must declare the HIERARCHICAL pair — the declared form of the fallback.",
        )
        assertTrue(
            activity.contains("MotionRoutePair.HIERARCHICAL"),
            "motionPlanFor must default to MotionRoutePair.HIERARCHICAL when no declared pair " +
                "forms, so the fallback's timings live with the policy's other rules.",
        )
    }
}
