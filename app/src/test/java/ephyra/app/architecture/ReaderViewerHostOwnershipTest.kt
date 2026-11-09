package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The rendering path reaches its host by contract, not by cast - the rule `R-D` exists to enforce.
 *
 * ## The defect this prevents
 *
 * `ReaderScreen` obtained its viewer callbacks by casting `LocalContext.current` to
 * `ReaderActivity` (audit F6, 2026-11-08). The cast put the concrete Activity class inside the
 * rendering path, and its silent null was load-bearing: with a context that is not
 * `ReaderActivity`, both Compose readers silently stop rendering and the empty `AndroidView`
 * fallback paints nothing - a render decision made by a cast that hides the dependency instead of
 * declaring it.
 *
 * ## Why this is structural
 *
 * Which host the screen reaches cannot be asserted behaviourally without constructing an Activity,
 * and the cast never failed in the one production context that exists. What can fail is the
 * architectural property: the rendering path depends on a declared interface, the host implements
 * it, and every member of that interface is consumed - the unwired-contract failure mode
 * (`B-032`) is caught by the build, not by review.
 */
class ReaderViewerHostOwnershipTest {

    private fun sourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.isAbsolute) return direct
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        return File(".", relativePath).absoluteFile
    }

    /** Source lines with comments stripped, so prose cannot satisfy a rule about code. */
    private fun codeOf(relativePath: String): List<String> =
        sourceFile(relativePath)
            .readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .map { it.substringBefore("//").trim() }
            .filter { it.isNotEmpty() }

    private fun readerMainSources(): List<File> {
        val root = sourceFile("feature/reader/src/main")
        assertTrue(root.exists(), "feature/reader/src/main not found")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /**
     * No reader main source may cast to the concrete Activity.
     *
     * A cast is how the rendering path learned its host before R-D. Any reappearance - `as?` or
     * `as` - re-introduces the hidden dependency and the silent-null render decision hanging
     * off its failure.
     */
    @Test
    fun `no reader main source casts to the concrete activity`() {
        val cast = Regex("as(\\?)? ReaderActivity")
        val offenders = mutableListOf<String>()
        for (file in readerMainSources()) {
            for (line in codeOf(file.absolutePath)) {
                if (cast.containsMatchIn(line)) {
                    offenders += "${file.name}: $line"
                }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "the rendering path reaches its host through the ReaderViewerHost contract; a cast " +
                "to the concrete Activity re-introduces the hidden dependency and its silent-null " +
                "render decision:\n  " +
                offenders.joinToString("\n  "),
        )
    }

    /**
     * The screen takes the contract and routes every viewer callback through it.
     *
     * Asserted positively: deleting the cast alone would pass a negative gate. The screen must
     * declare the host parameter and route all four callbacks through it - a callback that still
     * reaches the Activity another way is a second host path.
     */
    @Test
    fun `the screen takes the host contract and routes the viewer callbacks through it`() {
        val screen = codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderScreen.kt")

        assertTrue(
            screen.any { it.contains("viewerHost: ReaderViewerHost") },
            "ReaderScreen must take the host by declared parameter, not by casting its context",
        )
        for (member in listOf("onPageSelected", "onPageLongTap", "toggleMenu", "requestPreloadChapter")) {
            assertTrue(
                screen.any { it.contains("viewerHost::$member") },
                "the $member callback must route through the host contract; reaching the Activity " +
                    "another way is a second host path",
            )
        }
    }

    /**
     * The activity implements the contract and passes itself at the composition call site.
     */
    @Test
    fun `the activity implements the host contract and passes itself`() {
        val activity =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderActivity.kt")

        assertTrue(
            activity.any { it.contains("class ReaderActivity : BaseActivity(), ReaderViewerHost") },
            "ReaderActivity must declare the contract; an interface the screen takes but the host " +
                "does not implement is an unwired contract",
        )
        assertTrue(
            activity.any { it.contains("viewerHost = this") },
            "the single production call site must pass the activity as the host",
        )
        for (member in listOf("onPageSelected", "onPageLongTap", "requestPreloadChapter", "toggleMenu")) {
            assertTrue(
                activity.any { it.contains("override fun $member") },
                "$member must exist as an override of the host contract",
            )
        }
    }

    /**
     * Every member of the contract is consumed by the screen.
     *
     * An interface member nothing routes is the `B-032` failure mode: it compiles, it tests green
     * in isolation, and it does nothing. The interface stays small on purpose - a member joins by
     * being routed, and this rule is what makes that a build decision instead of a review hope.
     */
    @Test
    fun `every host contract member is consumed by the screen`() {
        val host =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderViewerHost.kt")
        val screen =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderScreen.kt")
                .joinToString("\n")
        val declared =
            host.filter { it.startsWith("fun ") }.map { it.substringAfter("fun ").substringBefore("(") }
        assertTrue(
            declared.isNotEmpty(),
            "ReaderViewerHost must declare its members as single `fun `lines so this gate can read them",
        )
        val unconsumed = declared.filter { "viewerHost::$it" !in screen }
        assertTrue(
            unconsumed.isEmpty(),
            "every ReaderViewerHost member must be routed by ReaderScreen; an unconsumed member " +
                "is an unwired contract:\n  " +
                unconsumed.joinToString("\n  "),
        )
    }
}
