package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Structural gate for the Library <-> Series transition.
 *
 * **The defects these prevent.** All three were live at the same time, and together they are what
 * made returning from a series page to the library feel wrong rather than merely plain:
 *
 * 1. **An unshared backdrop.** `MangaInfoBox` painted a full-bleed `AsyncImage` of the same manga at
 *    `blur(4.dp)` / `alpha(0.2f)`. It read as atmosphere on the way in, but it was a *second*
 *    rendering of the cover that was not a shared element, so on back three alphas ran at once — the
 *    shared cover shrinking into its cell, the blurry twin fading out over it, and the library grid
 *    fading in underneath. Nothing in the library matched it, because nothing in the library had one.
 * 2. **A decorative motion policy.** `MotionPolicy` encoded exactly the right rule for this pair
 *    and was covered by unit tests, but `MotionPolicy.plan` was called from nowhere but its own
 *    test. `MainActivity` hand-wrote `if (initialState.isHome() && targetState.isMangaDetails())`,
 *    so the rule was asserted in `:core:domain` and unenforced in the layer that moves things.
 * 3. **Two different back animations.** The manifest opts into `enableOnBackInvokedCallback`, but
 *    the series screen used a bare `popBackStack()`. The toolbar arrow played the shared-cover
 *    reverse; the back *gesture* got the system default. Same pair, two motions, and the gesture is
 *    the one users reach for.
 *
 * **Why these are asserted structurally.** Every one of them compiled, passed the existing suite,
 * and was invisible in review: the backdrop was commented as "atmosphere", the policy was
 * correct-but-unused, and the gesture "worked". Each is a fact about what the code *does not* do, so
 * a behavioural test would have to reproduce a transition nobody can drive in a unit test. These
 * assertions read the source instead.
 *
 * Comments are stripped before matching so prose describing a mechanism cannot satisfy — or trip —
 * a rule about the mechanism itself.
 */
class LibrarySeriesTransitionTest {

    private fun sourceFile(relativePath: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        throw AssertionError("$relativePath not found")
    }

    private fun codeOf(relativePath: String): String = sourceFile(relativePath)
        .readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//.*"), "")

    private val seriesHeader =
        "feature/manga/src/main/kotlin/ephyra/presentation/manga/components/MangaInfoHeader.kt"
    private val mainActivity = "app/src/main/java/ephyra/app/ui/main/MainActivity.kt"
    private val seriesScreen = "feature/manga/src/main/kotlin/ephyra/feature/manga/MangaScreen.kt"
    private val mangaCover = "presentation-core/src/main/java/ephyra/presentation/core/components/MangaCover.kt"
    private val motionTokens = "presentation-core/src/main/java/ephyra/presentation/core/theme/Motion.kt"

    /**
     * The series page must not draw a second copy of the cover.
     *
     * A blurred or alpha-faded `AsyncImage` of the cover inside `MangaInfoBox` is the specific
     * regression: it is not a shared element, so it has no counterpart in the library and it
     * animates on its own schedule underneath the one element that is supposed to carry the
     * transition. `AsyncImage` is still legitimately used for the *cover itself*, so this asserts on
     * the backdrop's distinguishing modifier rather than on the composable's presence.
     */
    @Test
    fun `series page does not draw an unshared cover backdrop`() {
        val infoBox = codeOf(seriesHeader).substringAfter("fun MangaInfoBox(")

        assertFalse(
            Regex("""\.blur\(""").containsMatchIn(infoBox),
            "MangaInfoBox must not blur a cover copy. A blurred, non-shared copy of the cover is " +
                "what made the return from a series page read as a suck: three alphas competing " +
                "underneath the one shared element. The library has no counterpart for it, so it " +
                "cannot be a shared element either. See MOTION_NAVIGATION_CONTRACT.",
        )
    }

    /**
     * The container must not crossfade when the cover is carrying the transition.
     *
     * `MotionTokens.containerEnter` maps `ContainerMotion.NONE` onto `EnterTransition.None`. If that
     * branch is ever "softened" into a fade, the two full screens dissolve underneath a travelling
     * cover again — the same failure as the backdrop, one layer up.
     */
    @Test
    fun `a still container is a real no-op rather than a fade`() {
        val noneBranch = codeOf(motionTokens)
            .substringAfter("fun containerEnter(")
            .substringBefore("fun containerExit(")

        assertTrue(
            Regex("""ContainerMotion\.NONE\s*->\s*EnterTransition\.None""").containsMatchIn(noneBranch),
            "ContainerMotion.NONE must map to EnterTransition.None. With the backdrop gone the " +
                "cover is the only thing that should move on Library <-> Series; a crossfade of " +
                "the two screens underneath it competes with it and is what made the return look " +
                "wrong. See MotionPlan.usesNoContainerMotion.",
        )
    }

    /**
     * The Android layer must ask the policy, not re-decide the rule.
     *
     * `MotionPolicy` was fully specified and fully tested while `MainActivity` hardcoded the same
     * decision in an `if`. A test on the policy therefore passed while the behaviour it describes
     * was never applied. Requiring the call to be *present* is the only thing that catches it.
     */
    @Test
    fun `nav transitions are driven by MotionPolicy rather than a hand written branch`() {
        val activity = codeOf(mainActivity)

        assertTrue(
            activity.contains("MotionPolicy.plan("),
            "MainActivity must resolve transitions through MotionPolicy.plan. The policy encodes " +
                "the rule for this pair and is unit tested, but if the NavHost does not consult it " +
                "the rule is decorative and those tests prove nothing about what the user sees.",
        )
        assertTrue(
            activity.contains("fun motionRoutePair("),
            "The route-pair naming must live in one function so forward and back cannot diverge. " +
                "MOTION_NAVIGATION_CONTRACT requires the back path to be the reverse of the " +
                "forward path; two separate predicates are how that quietly stops being true.",
        )

        val pair = activity
            .substringAfter("fun motionRoutePair(")
            .substringBefore("private fun NavBackStackEntry.sharedCoverMangaId")

        // Both directions, named in one `when`, over one predicate. The predicate is
        // `hostsSharedCover()` rather than a route equality test so that every list which carries
        // the cover — the tab shell, a source's results, global search — is one end of the same
        // pair; matching on `Home` alone silently dropped the last two.
        assertTrue(
            Regex("""hostsSharedCover\(\)\s*&&\s*to\.isMangaDetails\(\)\s*->\s*MotionRoutePair\.LIBRARY_SERIES""")
                .containsMatchIn(pair),
            "The forward direction must resolve to LIBRARY_SERIES.",
        )
        assertTrue(
            Regex("""isMangaDetails\(\)\s*&&\s*to\.hostsSharedCover\(\)\s*->\s*MotionRoutePair\.LIBRARY_SERIES""")
                .containsMatchIn(pair),
            "The back direction must resolve to LIBRARY_SERIES, the same pair as the forward " +
                "direction, over the same predicate. Predictive back reuses this model, so a pair " +
                "that differs on the way out gives the gesture a different animation from the " +
                "toolbar button.",
        )
        assertTrue(
            Regex("""MotionRoutePair\.LIBRARY_SERIES""").findAll(pair).count() == 2,
            "Exactly two branches may name LIBRARY_SERIES: one in, one out. A third is a pair " +
                "that was special-cased without a direction.",
        )
    }

    /**
     * Predictive back must be wired, and must not fight the chapter list's own back handling.
     *
     * With `enableOnBackInvokedCallback="true"` in the manifest and no handler on this screen, the
     * gesture animates nothing until the user releases it. The `enabled` guard matters as much as
     * the handler: while chapters are selected, `BackHandler` in the chapter list owns back, and a
     * second handler claiming the same gesture would make the two compete.
     */
    @Test
    fun `series page participates in predictive back`() {
        val screen = codeOf(seriesScreen)

        assertTrue(
            screen.contains("PredictiveBackProgress("),
            "The series page must use the shared PredictiveBackProgress helper. A bare " +
                "popBackStack() on a screen that opted into predictive back gives the user a swipe " +
                "that previews nothing and then plays a different animation from the toolbar arrow.",
        )
        assertTrue(
            screen.contains("isAnySelected"),
            "The predictive-back handler must be disabled while chapters are selected, because " +
                "BackHandler in the chapter list owns back in that state.",
        )
        assertTrue(
            screen.contains("onCancelled"),
            "A cancelled gesture must restore the surface. Without it the series page is left " +
                "scaled down at whatever value the finger last reached.",
        )
    }

    /**
     * The cover's shape must be the same on both ends of the flight.
     *
     * The shared element used to default to `MaterialTheme.shapes.extraSmall` (8dp) while the
     * library list item passed `ShapeTokens.coverImage` (16dp) explicitly. The cover therefore
     * changed corner radius mid-flight, and *which* radius it changed to depended on whether the
     * user was in grid or list layout.
     */
    @Test
    fun `the shared cover keeps one shape across the transition`() {
        val cover = codeOf(mangaCover)

        assertTrue(
            Regex("""shape:\s*Shape\s*=\s*ShapeTokens\.coverImage""").containsMatchIn(cover),
            "MangaCover must default to ShapeTokens.coverImage. It is the shared element on " +
                "Library <-> Series, so both ends must use the same cover token; a shared element " +
                "that reshapes itself while it travels is one of the things that makes a return " +
                "look wrong.",
        )
        assertFalse(
            Regex("""shape:\s*Shape\s*=\s*MaterialTheme\.shapes\.extraSmall""").containsMatchIn(cover),
            "MangaCover must not default to shapes.extraSmall. That was 8dp against the cover " +
                "token's 16dp, so the radius changed during the flight.",
        )
    }

    /**
     * The return must be quicker than the arrival.
     *
     * The policy was direction-free and a test asserted that symmetry ("enter and exit share one
     * duration"). It read as a correctness property and was the opposite: Material 3's shared-element
     * spec is deliberately asymmetric, because the user already knows where back goes. Running the
     * full forward timeline on the return is the most likely source of the owner-reported "awkward"
     * back, recorded in `REBUILD_STATUS.md` on 2026-09-28.
     *
     * Asserted on the source because the failure mode is a *silent* one: collapsing both directions
     * onto one duration compiles, animates, and looks deliberate. Only the asymmetry being named in
     * the policy keeps a later edit from flattening it again.
     */
    @Test
    fun `motion is directional and the return is shorter`() {
        val policy = codeOf("core/domain/src/main/java/ephyra/domain/navigation/motion/MotionPolicy.kt")

        assertTrue(
            policy.contains("enum class MotionDirection"),
            "MotionPolicy must model direction. A direction-free plan gives the return the same " +
                "timeline as the arrival, which is what made going back read as slow rather than " +
                "wrong. See REBUILD_STATUS.md 2026-09-28.",
        )
        assertTrue(
            policy.contains("SHARED_ELEMENT_BACK_DURATION_MILLIS"),
            "The backward duration must be its own named token, not derived from the forward one, " +
                "so that changing the arrival cannot silently change the return with it.",
        )

        val back = policy.substringAfter("const val SHARED_ELEMENT_BACK_DURATION_MILLIS")
            .substringBefore("const val CROSSFADE_DURATION_MILLIS")
        val forward = policy.substringAfter("const val SHARED_ELEMENT_DURATION_MILLIS")
            .substringBefore("const val SHARED_ELEMENT_BACK_DURATION_MILLIS")

        val backMillis = back.substringAfter(": Int = ").substringBefore("\n").trim().toInt()
        val forwardMillis = forward.substringAfter(": Int = ").substringBefore("\n").trim().toInt()

        assertTrue(
            backMillis < forwardMillis,
            "back=$backMillis must be shorter than forward=$forwardMillis",
        )
        assertTrue(backMillis > 0, "a faster return must still animate; 0 would read as a cut")
    }

    /**
     * The aspect ratio must be resolved before the shared element measures itself.
     *
     * The second candidate in the same ledger entry: `.aspectRatio(ratio)` sat *after* the
     * shared-element modifier, so the element's captured bounds were the intermediate size rather
     * than the final ratio-constrained box. The library grid and the series header request
     * different sizing (`fillMaxWidth()` vs `sizeIn(maxWidth = 100.dp)`), so the element was
     * interpolated between non-uniform shapes — a visible squash rather than a slide.
     *
     * Modifier order is invisible in review and produces no compile error, which is exactly why it
     * needs a gate.
     */
    @Test
    fun `the cover resolves its aspect ratio before the shared element`() {
        val cover = codeOf(mangaCover)
        val chain = cover.substringAfter("modifier = modifier")
            .substringBefore("contentScale = ContentScale.Crop")

        val ratioAt = chain.indexOf(".aspectRatio(")
        val sharedAt = chain.indexOf("sharedElementModifier")

        assertTrue(ratioAt >= 0, "the aspect ratio must still be applied; the slice did not find it")
        assertTrue(sharedAt >= 0, "the shared element must still be applied; the slice did not find it")
        assertTrue(
            ratioAt < sharedAt,
            "`.aspectRatio(ratio)` must come BEFORE the shared-element modifier. Applied after it, " +
                "the element measures the pre-ratio box and the flight interpolates between " +
                "non-uniform shapes — a squash, not a slide. See REBUILD_STATUS.md 2026-09-28.",
        )
    }

    /**
     * The shared element key has a single definition.
     *
     * The key is the only thing that decides whether the two covers are recognised as the same
     * element. A mismatch does not fail — it silently degrades to a crossfade, which is the exact
     * symptom this change set exists to remove, and it would be reported as "the animation feels
     * off" rather than as a bug.
     */
    @Test
    fun `the shared element key has a single definition`() {
        val cover = codeOf(mangaCover)

        assertTrue(
            cover.contains("MotionPolicy.mangaCoverKey("),
            "MangaCover must build the shared key via MotionPolicy.mangaCoverKey so the library " +
                "cell and the series header cannot disagree about it.",
        )
        assertFalse(
            Regex(""""manga_cover_""").containsMatchIn(cover),
            "The cover key must not be re-spelled as a literal here. The two ends live in " +
                "different modules; a typo in either one does not fail to compile, it just stops " +
                "the shared element matching.",
        )
    }
}
