package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * One overlay owner for reader rendering effects - the rule `R-B` exists to enforce.
 *
 * ## The defect this prevents
 *
 * The reader carried two render paths for colour-filter and brightness effects: the Compose
 * `ReaderContentOverlay` in `ReaderScreen`, and an Activity-side `ColorMatrix` paint applied with
 * `View.setLayerType(LAYER_TYPE_HARDWARE, paint)`. The Stage 1 inventory (2026-11-08, E1 at
 * `0b3dd1bf`) found the View half had never rendered at all: every `Viewer.getView()` returns an
 * empty `View(activity)` fallback because rendering happens in Compose, so the night-mode
 * grayscale+invert `ColorMatrix` was applied to a view that draws nothing. It looked authoritative
 * - a full colour-matrix implementation with four preference collectors driving it - while being
 * dead since the Compose migration.
 *
 * The brightness half failed the other way: `State.brightnessOverlayValue` was rendered by the
 * Compose overlay but fed only by `ReaderEvent.SetBrightnessOverlayValue`, an event nobody
 * emitted. The dim scrim was dead code fed by an unwired contract - the `B-032` failure mode - and
 * the relay even had a green unit test proving it worked in isolation. Negative slider values were
 * meanwhile divided straight into `WindowManager.screenBrightness`, an undefined negative float.
 *
 * ## Why this is structural
 *
 * A behavioural test cannot prefer one path when both exist: whichever path renders, the tests
 * pass, which is exactly how a dead path survived the entire Compose migration with its own
 * collectors. What fails is the architectural property - one user setting, one owner, from event
 * to pixels.
 */
class ReaderRenderOwnershipTest {

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
     * No reader surface may paint through the View layer.
     *
     * `setLayerType` on a `Viewer.getView()` result is how the dead paint path was wired: it
     * compiles, it has a full implementation behind it, and it renders nothing in a Compose
     * viewer. Any reappearance is a second render architecture, which is what this gate exists to
     * make impossible rather than to catch in review.
     */
    @Test
    fun `no reader surface paints through the view layer`() {
        val forbidden = listOf("setLayerType", "LAYER_TYPE_HARDWARE", "getCombinedPaint")
        val offenders = mutableListOf<String>()
        for (file in readerMainSources()) {
            for (line in codeOf(file.absolutePath)) {
                if (forbidden.any { it in line }) {
                    offenders += "${file.name}: $line"
                }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "pixel-level render effects belong to the Compose overlay. A View-layer paint is a " +
                "second render architecture, and the previous one never rendered at all - it was " +
                "applied to the empty View(activity) fallback every Compose viewer returns:\n  " +
                offenders.joinToString("\n  "),
        )
    }

    /**
     * The Compose overlay is the single pixel owner, and it reads its inputs reactively.
     *
     * Asserted positively: a gate that only forbade the View path would pass if the overlay were
     * deleted too, leaving brightness and colour filtering owned by nobody. The overlay must
     * route through `ReaderContentOverlay(` and read `customBrightness()`/`customBrightnessValue()`
     * from the preferences rather than from relayed ViewModel state.
     */
    @Test
    fun `the compose overlay is the single pixel owner of brightness and colour`() {
        val screen =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderScreen.kt")

        assertTrue(
            screen.any { it.contains("ReaderContentOverlay(") },
            "the reader's pixel effects (dim scrim, colour filter) must render through " +
                "`ReaderContentOverlay` - the single owner decided by R-B",
        )
        assertTrue(
            screen.any { it.contains("customBrightness().collectAsState()") },
            "the overlay must read the custom-brightness preference reactively. The previous " +
                "wiring read State.brightnessOverlayValue, a field fed only by an event nobody " +
                "emitted, so the scrim was dead.",
        )
        assertTrue(
            screen.any { it.contains("customBrightnessValue().collectAsState()") },
            "the overlay must read the brightness slider's value reactively from the preference",
        )
        assertTrue(
            screen.any {
                it.contains("brightness = if (customBrightnessEnabled) customBrightnessValue else 0")
            },
            "the scrim must be fed the raw slider value only while custom brightness is enabled; " +
                "disabled means no scrim, and ReaderContentOverlay draws the black scrim exactly " +
                "when brightness < 0",
        )
    }

    /**
     * Window brightness honours the slider's documented contract.
     *
     * `ColorFilterPage` documents the range as -75..100: negative dims through the scrim with the
     * window at its minimum, 0 returns to system, positive sets window brightness. The window must
     * never receive the raw negative value - `screenBrightness` below 0 is reserved for the
     * `BRIGHTNESS_OVERRIDE_*` sentinels, so `value / 100f` with a negative value is undefined
     * platform input.
     */
    @Test
    fun `window brightness honours the documented slider contract`() {
        val activity =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderActivity.kt")

        assertTrue(
            activity.any { it.contains("value > 0 -> value / 100f") },
            "positive slider values map to window brightness as value / 100f",
        )
        assertTrue(
            activity.any { it.contains("BRIGHTNESS_OVERRIDE_NONE") },
            "a slider value of 0 means system brightness: the window must return to " +
                "BRIGHTNESS_OVERRIDE_NONE, matching the disabled path",
        )
        assertTrue(
            activity.any { it.contains("else -> 0f") },
            "negative slider values mean the Compose scrim dims while the window sits at its " +
                "minimum (0f); the raw negative must never be divided into screenBrightness",
        )
        assertTrue(
            activity.none { it.contains("layoutParams.screenBrightness = value / 100f") },
            "the raw division wrote an undefined negative float for dim values; the mapping must " +
                "be the explicit three-way contract above",
        )
    }

    /**
     * No unwired brightness relay may return.
     *
     * `State.brightnessOverlayValue` + `ReaderEvent.SetBrightnessOverlayValue` was an event
     * pipeline with no emitter: every part compiled, nothing failed, and the feature did nothing.
     * Forbidding the names in reader main sources keeps the relay from coming back as a second way
     * to feed the overlay.
     */
    @Test
    fun `no unwired brightness relay exists in reader main sources`() {
        val offenders = mutableListOf<String>()
        for (file in readerMainSources()) {
            for (line in codeOf(file.absolutePath)) {
                if (line.contains("brightnessOverlayValue") || line.contains("SetBrightnessOverlayValue")) {
                    offenders += "${file.name}: $line"
                }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "brightness reaches the overlay reactively from the preferences; a relayed state " +
                "field or event is a second feed path, and the last one had no emitter:\n  " +
                offenders.joinToString("\n  "),
        )
    }
}
