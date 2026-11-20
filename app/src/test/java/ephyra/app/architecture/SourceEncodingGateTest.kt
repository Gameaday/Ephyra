package ephyra.app.architecture

import ephyra.app.security.TrackedFileNames
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.invariantSeparatorsPath
import kotlin.io.path.relativeTo

/**
 * Source files must be valid UTF-8, not CP1252-mangled UTF-8.
 *
 * **What went wrong.** A PowerShell round-trip that read UTF-8 and wrote it back through a
 * non-UTF-8 default encoding stored every em dash, arrow and curly quote as its CP1252
 * rendering — a three-character sequence beginning with `a-circumflex` where one character
 * belonged. It happened in 22 files and 80 lines before anyone noticed, because it is confined
 * to comments and prose: the code still compiled and every test still passed.
 *
 * (This KDoc deliberately *describes* the corruption rather than quoting it. An earlier draft
 * pasted a real example inline, and the gate then failed on its own documentation.)
 *
 * **Why it is worth a gate.** The corruption is invisible when reading a diff (the mojibake
 * looks like a rendering problem in the viewer rather than a data problem in the file), it is
 * trivially reproducible by any tool that guesses the encoding, and it silently degrades every
 * comment it touches. Repairing it after the fact is easy; noticing it is not.
 *
 * **What this checks.** Only the fingerprints that cannot occur in correct UTF‑8 text: a
 * C1‑range lead byte (`U+00E2`, `U+00C3`, `U+00C2`) immediately followed by a CP1252
 * punctuation mapping (`U+2020`, `U+20AC`, `U+2122`, …). Legitimate accented Latin text such as
 * `é` or `ü` is not matched, because those are single code points and never appear in this
 * pairing.
 *
 * This is a text check, so it is deliberately narrow: it proves the *known* corruption is
 * absent, not that every file is well-formed UTF‑8.
 */
class SourceEncodingGateTest {

    private companion object {
        /**
         * The lead bytes CP1252 decoding produces for a multi‑byte UTF‑8 sequence, paired with
         * the punctuation those bytes map to. Built from code points so this file's own encoding
         * cannot be the thing under test.
         */
        val MOJIBAKE_MARKERS: List<Pair<Char, Char>> = listOf(
            Char(0x00E2) to Char(0x2020), // a-circumflex + dagger
            Char(0x00E2) to Char(0x20AC), // a-circumflex + euro
            Char(0x00E2) to Char(0x2122), // a-circumflex + trademark
            Char(0x00E2) to Char(0x00C2),
            Char(0x00C3) to Char(0x00C2), // A-tilde + A-circumflex: double-encoded UTF-8
            Char(0x00C3) to Char(0x20AC),
            Char(0x00C3) to Char(0x2018),
            Char(0x00C3) to Char(0x2019),
            Char(0x00C2) to Char(0x00B7), // A-circumflex + middle dot
            Char(0x00C2) to Char(0x00A0),
        )

        /** Directories that hold source, excluding build output. */
        val SOURCE_ROOTS = listOf(
            "app/src",
            "core",
            "feature",
            "presentation-core/src",
            "presentation-widget/src",
            "source-api/src",
            "source-local/src",
            "telemetry/src",
            "buildSrc/src",
        )
    }

    @Test
    fun `no source file contains CP1252-mangled UTF-8`() {
        val root = TrackedFileNames.repositoryRoot()
        val offenders = mutableListOf<String>()

        for (relative in SOURCE_ROOTS) {
            val dir = File(root, relative)
            if (!dir.isDirectory) continue

            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val path = file.relativeTo(root).invariantSeparatorsPath
                    file.readText().lineSequence().forEachIndexed { index, line ->
                        val found = MOJIBAKE_MARKERS.firstOrNull { (lead, mapped) ->
                            line.contains("$lead$mapped")
                        }
                        if (found != null) {
                            offenders += "$path:${index + 1}  ${line.trim()}"
                        }
                    }
                }
        }

        assertTrue(
            offenders.isEmpty(),
            "These lines hold CP1252-mangled UTF-8 — an em dash or arrow was stored as its " +
                "CP1252 bytes and written back out as UTF-8. Re-encode the line to CP1252 and " +
                "decode it as UTF-8 to recover the intended text:\n" +
                offenders.joinToString("\n") { "  $it" },
        )
    }

    /**
     * The detector must actually fire.
     *
     * Without this, a broken marker list — or a refactor that inverts `contains` — would leave
     * the gate above passing vacuously, which is the failure mode this suite has hit before.
     */
    @Test
    fun `the mojibake detector recognises a known corruption`() {
        val corrupted = "// source" + Char(0x00E2) + Char(0x20AC) + Char(0x201C) + "language map"
        val hit = MOJIBAKE_MARKERS.any { (lead, mapped) -> corrupted.contains("$lead$mapped") }

        assertTrue(hit, "the detector must match the corruption it exists to find")

        val clean = "// source\u2192language map"
        val falsePositive = MOJIBAKE_MARKERS.any { (lead, mapped) -> clean.contains("$lead$mapped") }

        assertTrue(
            !falsePositive,
            "correctly-encoded text must not be reported as corruption",
        )
    }
}
