package ephyra.app.architecture

import ephyra.app.security.TrackedFileNames
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Structural gate: a reader preference surfaced in a settings UI must actually be read.
 *
 * **The defect this prevents.** `sliderNavMode` was stored in `ReaderPreferences`, mirrored into
 * `PagerConfig`, and offered in **two** settings screens — one with a subtitle describing the
 * difference between "Instant" and "Smooth". A project-wide search found no read of
 * `config.sliderNavMode` anywhere: `PagerViewer.moveToPage` hardcoded `animate = false`, so the two
 * options produced byte-identical behaviour while the UI promised a distinction.
 *
 * **Why this is worth a gate rather than a one-line fix.** A preference is easy to add and easy to
 * stop consulting — nothing warns when a setting stops having an effect. It fails silently and
 * permanently, and it is a promise made to the user in their own settings screen. Asserting that
 * every preference *declared* is *read* catches the whole class, not just this instance.
 *
 * **What it does not claim.** That the reader of a preference uses it correctly. `sliderNavMode` was
 * read by nobody; a preference read in the wrong place is a different defect, and this gate would
 * pass it. The scope is deliberately "declared implies read", which is the property that was
 * violated.
 */
class ReaderPreferenceConsumptionTest {

    @Test
    fun `every reader preference offered in a settings screen is read somewhere`() {
        val root = TrackedFileNames.repositoryRoot()
        val sources = TrackedFileNames.inMainSources().filter { it.endsWith(".kt") }

        assertTrue(sources.isNotEmpty(), "no tracked main sources found; the gate would be inert")

        val allText = sources
            .joinToString("\n") { File(root, it).readText() }
            .ifEmpty { error("source text was empty; the gate would be inert") }

        // Every preference offered to the user, and the declaration that defines it.
        val offered = listOf(
            Preference("sliderNavMode", "seek-bar navigation mode"),
        )

        val unread = offered.filter { pref ->
            // A read looks like `something.sliderNavMode` — a property access, not a declaration or
            // an assignment. Counting the declaration alone would make the gate pass on a preference
            // that is stored and never consulted, which is the defect.
            val readPattern = Regex("""\.\s*${Regex.escape(pref.name)}\b""")
            !readPattern.containsMatchIn(allText)
        }

        assertTrue(
            unread.isEmpty(),
            "These reader preferences are surfaced in a settings screen but read by no production " +
                "code, so the setting does nothing while the UI describes an effect:\n" +
                unread.joinToString("\n") { "  ${it.name} (${it.description})" } +
                "\nEither wire the preference or delete the control offering it.",
        )
    }

    @Test
    fun `the seek-bar preference is consulted where the seek happens`() {
        val root = TrackedFileNames.repositoryRoot()
        val pager = File(
            root,
            "feature/reader/src/main/kotlin/ephyra/feature/reader/viewer/pager/PagerViewer.kt",
        )
        assertTrue(pager.exists(), "PagerViewer.kt not found at the expected path")

        val text = pager.readText()
        assertTrue(
            text.contains("sliderNavMode"),
            "the seek target is emitted in PagerViewer.moveToPage, so that is where the " +
                "sliderNavMode preference must be consulted",
        )
        assertTrue(
            // The original defect: the animate flag was hardcoded rather than read from config.
            !text.contains("TargetPage(position, animate = false)"),
            "moveToPage hardcodes animate = false, which ignores the sliderNavMode preference " +
                "entirely and makes 'Instant' and 'Smooth' behave identically",
        )
    }

    @Test
    fun `the seek signal is conflated so a drag does not queue stale targets`() {
        val root = TrackedFileNames.repositoryRoot()
        val pager = File(
            root,
            "feature/reader/src/main/kotlin/ephyra/feature/reader/viewer/pager/PagerViewer.kt",
        )
        val text = pager.readText()

        val match = Regex(
            """_targetPageRequest\s*=\s*MutableSharedFlow<[^>]*>\((.*?)\)""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        ).find(text)
        val declaration = match?.groupValues?.get(1)
        assertTrue(
            declaration != null && declaration.isNotEmpty(),
            "could not locate the _targetPageRequest declaration; the gate would check nothing",
        )

        assertTrue(
            Regex("""replay\s*=\s*1""").containsMatchIn(declaration.orEmpty()),
            "the seek request flow needs replay = 1 so a slow collector acts on the newest target " +
                "instead of churning through a queue of stale ones. Found: $declaration",
        )
    }

    private data class Preference(val name: String, val description: String)
}
