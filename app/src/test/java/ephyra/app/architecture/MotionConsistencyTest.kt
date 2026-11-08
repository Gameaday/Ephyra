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

    /** Source with prose removed, so a comment describing a mechanism cannot satisfy a rule. */
    private fun codeOf(relativePath: String): String = sourceFile(relativePath)
        .readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//.*"), "")

    private val readerActivity =
        "feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderActivity.kt"
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
     * The reader is a separate Activity, so its only available motion is the window animation. It
     * must set both halves: the open half was missing, which made entering a chapter a cut.
     */
    @Test
    fun `the reader animates both entering and leaving`() {
        val code = codeOf(readerActivity)

        assertTrue(
            code.contains("OVERRIDE_TRANSITION_OPEN"),
            "ReaderActivity must override the OPEN transition. Without it the reader appears " +
                "instantly while returning from it slides, so the pair reads as broken in one " +
                "direction. It is a separate Activity, so the Compose shared-axis token cannot " +
                "apply and the window animation is the only motion available.",
        )
        assertTrue(
            code.contains("shared_axis_x_push_enter"),
            "The reader's entry must use the shared-axis push animation, the same semantic as the " +
                "Compose transition used to reach every other destination.",
        )
        assertTrue(
            code.contains("OVERRIDE_TRANSITION_CLOSE"),
            "ReaderActivity must keep overriding the CLOSE transition; the pop half of the pair.",
        )
    }

    /**
     * The Activity animations and the Compose token are two renderings of one transition, so they
     * must agree on how far the page travels.
     */
    @Test
    fun `activity animations travel as far as the compose token`() {
        val token = codeOf(motion)
        val travel = Regex("""SHARED_AXIS_X_TRAVEL\s*=\s*([0-9.]+)f""")
            .find(token)
            ?.groupValues
            ?.get(1)
            ?.toFloat()
            ?: throw AssertionError("SHARED_AXIS_X_TRAVEL not found in MotionTokens.kt")

        val percent = (travel * 100).toInt()

        listOf("push_enter", "push_exit", "pop_enter", "pop_exit").forEach { half ->
            val xml = sourceFile("$anim/shared_axis_x_$half.xml").readText()
            assertTrue(
                xml.contains("$percent%p"),
                "shared_axis_x_$half.xml must travel $percent% of the viewport to match " +
                    "SHARED_AXIS_X_TRAVEL ($travel). These files moved 5% while the Compose token " +
                    "moved 30%, so pushing into the reader or a WebView was a visibly smaller " +
                    "gesture than pushing into a nav destination — the same transition at two " +
                    "distances.",
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
     * The Activity animations are a rendering of the Compose token, so the durations must match the
     * token legs too — not just the travel, which is all the previous gate asserted. The XML push
     * ran 450ms while the Compose leg was being re-tuned, and nothing failed; this closes that hole.
     */
    @Test
    fun `activity animation durations match the compose token legs`() {
        val token = codeOf(motion)
        val durations = durationConstants(token)

        fun legs(function: String): List<Int> {
            val body = token.substringAfter("fun $function(").substringBefore("\n    fun ")
            val names = Regex("""durationMillis\s*=\s*(DURATION_[A-Z0-9_]+)""")
                .findAll(body)
                .map { it.groupValues[1] }
                .toList()
            assertTrue(
                names.size == 2,
                "$function must have exactly two timed legs (slide and fade); found ${names.size}.",
            )
            assertTrue(
                !body.contains("delayMillis"),
                "$function must not delay its fade: the halves have to settle together, or the " +
                    "incoming screen spends the start of its own journey invisible.",
            )
            return names.map { name ->
                durations[name] ?: throw AssertionError("$name is not a duration constant in MotionTokens.kt")
            }
        }

        listOf(
            "push_enter" to legs("m3SharedAxisXEnter"),
            "push_exit" to legs("m3SharedAxisXExit"),
            "pop_enter" to legs("m3SharedAxisXPopEnter"),
            "pop_exit" to legs("m3SharedAxisXPopExit"),
        ).forEach { (half, expected) ->
            val xml = sourceFile("$anim/shared_axis_x_$half.xml").readText()
            val actual = Regex("""android:duration="(\d+)""")
                .findAll(xml)
                .map { it.groupValues[1].toInt() }
                .toList()
            assertTrue(
                actual.size == 2,
                "shared_axis_x_$half.xml must have exactly two timed legs; found ${actual.size}.",
            )
            assertTrue(
                !xml.contains("android:startOffset"),
                "shared_axis_x_$half.xml must not offset one half against the other; the Compose " +
                    "legs start together and this file is their rendering.",
            )
            assertEquals(
                expected,
                actual,
                "shared_axis_x_$half.xml must run the same durations as the Compose token legs " +
                    "($expected ms), or the reader/WebView window animation is a different " +
                    "transition from the nav push it mirrors.",
            )
        }
    }

    /**
     * The policy's HIERARCHICAL durations are the numbers the renderings actually play — the last
     * edge of the triangle. The legs test above ties the Compose functions to the XML window
     * animations; this ties both to the policy. Without it, shortening HIERARCHICAL_BACK_DURATION_MILLIS
     * would leave the pop renderings at 250ms while the plan reported something else, and no gate
     * would name the difference.
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

        fun firstLeg(half: String): Int {
            val xml = sourceFile("$anim/shared_axis_x_$half.xml").readText()
            return Regex("""android:duration="(\d+)""")
                .find(xml)
                ?.groupValues
                ?.get(1)
                ?.toInt()
                ?: throw AssertionError("no timed leg found in shared_axis_x_$half.xml")
        }

        assertEquals(
            declared.getValue("FORWARD"),
            firstLeg("push_enter"),
            "The push-enter rendering must run the policy's HIERARCHICAL_FORWARD duration. The " +
                "window animation and the Compose push are renderings of one transition; a number " +
                "that differs between them is a policy the renderings did not implement.",
        )
        assertEquals(
            declared.getValue("BACK"),
            firstLeg("pop_enter"),
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
