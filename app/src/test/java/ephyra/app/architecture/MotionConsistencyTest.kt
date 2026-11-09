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
 *
 * **The reversal, recorded.** Defects (1) and (2) were originally gated by requiring the window
 * pair; defect (4) is why those gates are now inverted. The Activity boundary is the platform's:
 * the reader and the WebView register no window transition, the deleted `shared_axis_x_*.xml`
 * renderings stay deleted, and the one deliberate remaining override — the unlock screen's
 * zero-animation open, a privacy decision — is named so a sweep cannot delete it as "the last
 * override" by mistake.
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

    /** Numeric value of every DURATION_ constant, resolving alias chains to their numbers. */
    private fun durationConstants(tokenCode: String): Map<String, Int> {
        val declared = Regex("""const val (DURATION_[A-Z0-9_]+)\s*=\s*([A-Za-z0-9_]+)""")
            .findAll(tokenCode)
            .associate { it.groupValues[1] to it.groupValues[2] }

        fun resolve(name: String): Int {
            var current = name
            val seen = mutableSetOf<String>()
            while (true) {
                val value = declared[current] ?: throw AssertionError("$current is not a duration constant")
                value.toIntOrNull()?.let { return it }
                check(current !in seen) { "duration alias cycle at $current" }
                seen += current
                current = value
            }
        }
        return declared.keys.associateWith { resolve(it) }
    }

    /**
     * The reader and the WebView are separate Activities, and their boundary motion is the
     * platform's. A registered window transition only plays on the paths that do not go through
     * the predictive back gesture — on Android 14+ the gesture runs the system's cross-activity
     * animation and bypasses a custom close transition — so an override splits one boundary into
     * motions that differ by how the user leaves. That is the owner-reported inconsistency
     * (2026-11-08) this rule prevents from returning.
     */
    @Test
    fun `the activity boundary motion belongs to the platform`() {
        listOf(
            "ReaderActivity" to readerActivity,
            "WebViewActivity" to webViewActivity,
        ).forEach { (name, path) ->
            val code = codeOf(path)
            assertFalse(
                code.contains("OVERRIDE_TRANSITION"),
                "$name must not override its window transitions. On Android 14+ the predictive " +
                    "back gesture runs the system's cross-activity animation and bypasses a custom " +
                    "close transition, so an override gives one boundary motions that differ by how " +
                    "the user leaves — the exact inconsistency the owner reported at this boundary.",
            )
            assertFalse(
                code.contains("shared_axis_x_"),
                "$name must not name a shared-axis window resource; the boundary motion is the " +
                    "platform's, per the rule above.",
            )
        }

        assertTrue(
            codeOf(secureActivityDelegate).contains("overrideTransitionCompat"),
            "SecureActivityDelegate keeps the one deliberate override: the unlock screen opens " +
                "with no animation at all, a privacy decision rather than a motion-language one, " +
                "and this rule names it so a future sweep cannot delete it as 'the last override' " +
                "by mistake.",
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
     * The policy's HIERARCHICAL durations are the numbers the renderings actually play. The window
     * renderings were deleted with the activity overrides (the boundary is the platform's), so the
     * Compose legs are the one rendering left and this ties the policy to them. Without it,
     * shortening HIERARCHICAL_BACK_DURATION_MILLIS would leave the pop rendering at 250ms while
     * the plan reported something else, and no gate would name the difference.
     */
    @Test
    fun `policy hierarchical durations are the numbers the renderings play`() {
        val policy = codeOf(motionPolicy)
        val declared = Regex("""HIERARCHICAL_(FORWARD|BACK)_DURATION_MILLIS\s*:\s*Int\s*=\s*(\d+)""")
            .findAll(policy)
            .associate { it.groupValues[1] to it.groupValues[2].toInt() }
        assertEquals(
            setOf("FORWARD", "BACK"),
            declared.keys,
            "MotionPolicy must declare both HIERARCHICAL durations as readable literals.",
        )

        val token = codeOf(motion)

        fun firstLegMillis(function: String): Int {
            val body = token.substringAfter("fun $function(").substringBefore("\n    fun ")
            val name = Regex("""durationMillis\s*=\s*(DURATION_[A-Z0-9_]+)""")
                .find(body)
                ?.groupValues
                ?.get(1)
                ?: throw AssertionError("no timed leg found in $function")
            return durationConstants(token)[name]
                ?: throw AssertionError("$name is not a duration constant in MotionTokens.kt")
        }

        assertEquals(
            declared.getValue("FORWARD"),
            firstLegMillis("m3SharedAxisXEnter"),
            "The push rendering must run the policy's HIERARCHICAL_FORWARD duration. The Compose " +
                "legs are the one rendering left of this transition; a number that differs " +
                "between the policy and its rendering is a policy the rendering did not implement.",
        )
        assertEquals(
            declared.getValue("BACK"),
            firstLegMillis("m3SharedAxisXPopEnter"),
            "The pop-enter rendering must run the policy's HIERARCHICAL_BACK duration. The pop " +
                "legs exist so the return is quicker than the arrival; a rendering that keeps the " +
                "old number turns that rule into a comment rather than a fact.",
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
