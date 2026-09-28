package ephyra.app.architecture

import ephyra.app.security.TrackedFileNames
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Structural gate: a ledger row that claims a contract is wired into production must name a file
 * that establishes it, and that file must exist in the tree.
 *
 * **Why this gate exists.** Three separate claims in this ledger were falsified by opening the file
 * they talked about, and all three had the same shape:
 *
 * - `MED-002` said "not production-wired" when the work was five commits old and fully wired. A row
 *   reading "unwired" is as wrong as one reading "wired", and nothing caught either.
 * - `B-034` listed two entries (`LibraryFlags`, `ContentMappersReverse`) that **are not types at
 *   all**. They were reached by trusting a name.
 * - `StartupStep` carried a docstring asserting it modelled `App.kt` "1:1". It did not:
 *   `RECONCILE_SOURCES` had no implementation anywhere, the step count was 4 against 7 real
 *   phases, and neither existing phase vocabulary corresponded to the enum. **That claim survived a
 *   correction and was still wrong when written down.**
 *
 * The common cause is reading a *shape* — a name, a count, an absence of callers — instead of opening
 * the governing document. `B-033` had already established that name-based checks give false
 * positives; this gate is that lesson generalised and applied to the ledger itself.
 *
 * **What it does and does not check.** It verifies a wiring claim is *anchored*: a cited file
 * exists. It cannot verify the claim is still true, because that is a semantic question no regex
 * answers, and this programme has a documented history of name-based checks producing false
 * positives. What it does is make the claim **falsifiable and rot-detecting** — a renamed or deleted
 * file takes its citation with it and the row fails until a human updates it. That is the property
 * that was missing.
 *
 * **Deliberately narrow.** A gate that adjudicated whether a contract is *really* wired would be a
 * count in disguise, and [`BUILD_HEALTH.md`](file:///C:/Project/Android/Ephyra/doc/BUILD_HEALTH.md)
 * is explicit that counts never gate. Anchoring is a property of the document's shape, so it can be
 * asserted structurally and does not rot when a legitimate edge is retired.
 */
class LedgerWiringClaimTest {

    @Test
    fun `every wiring claim in the task ledger cites a file that exists`() {
        val rows = wiringRows(ledgerFile().readText())

        // Guards the gate's own fail-open mode: if the parser stops matching, "no offenders" would
        // pass without having checked anything. That is the sixth inert gate in this programme's
        // history, and the reason this assertion comes first.
        assertTrue(
            rows.isNotEmpty(),
            "no wiring claims parsed from the task ledger; the gate would be inert",
        )

        val tracked = trackedPaths()
        assertTrue(tracked.isNotEmpty(), "git ls-files returned nothing; the gate would be inert")

        val byBasename = tracked.groupBy { it.substringAfterLast('/') }
        val offenders = rows.mapNotNull { row ->
            val missing = row.citations.filterNot { resolves(it, tracked, byBasename) }
            if (missing.isEmpty()) null else "${row.id} cites ${missing.joinToString()}"
        }

        assertTrue(
            offenders.isEmpty(),
            "Ledger rows assert production wiring but cite files that are not in the tree. A claim " +
                "with no resolvable anchor is exactly the failure this gate exists to catch:\n" +
                offenders.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun `every wiring claim names at least one resolvable file anchor`() {
        val rows = wiringRows(ledgerFile().readText())
        assertTrue(rows.isNotEmpty(), "no wiring claims parsed; the gate would be inert")

        val unanchored = rows.filter {
            it.citations.none { it.endsWith(".kt") || it.endsWith(".md") || it.contains('/') }
        }
            .map { it.id }

        assertTrue(
            unanchored.isEmpty(),
            "These rows assert production wiring without naming a file that establishes it. A type " +
                "name is not an anchor: resolving one to a file is the name-based guess that produced " +
                "the `LibraryFlags` and `ContentMappersReverse` errors, so a row that wants to be " +
                "checkable has to name the file:\n" + unanchored.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun `the citation parser recognises the forms the ledger actually uses`() {
        // A parser proven only against a paraphrase is how a gate comes to match nothing. These are
        // the real citation shapes used in the ledger, including anchors and line suffixes.
        val anchors = citationsIn("see `doc/evidence/README.md#e4lab-def-001-pinch-transform`")
        assertTrue(
            anchors.contains("doc/evidence/README.md"),
            "a markdown anchor must be stripped, got $anchors",
        )

        val lineNumbers = citationsIn("`core/domain/src/main/java/ephyra/domain/x/Y.kt:12`")
        assertTrue(
            lineNumbers.contains("core/domain/src/main/java/ephyra/domain/x/Y.kt"),
            "a :line suffix must be stripped, got $lineNumbers",
        )

        val memberAccess = citationsIn("`ReaderChapter.cacheBytes` and `ReaderPage.kt`")
        assertTrue(
            memberAccess.contains("ReaderPage.kt") && !memberAccess.contains("ReaderChapter.cacheBytes"),
            "a member reference is not a file citation, got $memberAccess",
        )

        val prose = citationsIn("the contract is wired into production")
        assertTrue(prose.isEmpty(), "prose must not parse as a citation, got $prose")
    }

    @Test
    fun `rows that disclaim wiring are not treated as asserting it`() {
        // Most rows cite files while explicitly saying the work is *not* wired. Treating those as
        // wiring claims would be a false positive on the majority of the table, and a gate that
        // cries wolf is a gate that gets ignored.
        val disclaimed = "| MED-004 | x | Agent | Not production-wired. | CODE_COMPLETE |"
        val unwired = "| RDR-005 | x | Agent | still unwired, not production wired | IN_PROGRESS |"
        val asserted = "| MED-002 | x | Agent | **PRODUCTION-WIRED**, verified. | X |"
        val wiredPhrase = "| DEF-006 | x | Agent | now wired into production | X |"

        assertTrue(!isWiringRow(disclaimed), "a row disclaiming wiring must not match")
        assertTrue(!isWiringRow(unwired), "a row saying 'unwired' must not match")
        assertTrue(isWiringRow(asserted), "a row asserting production wiring must match")
        assertTrue(isWiringRow(wiredPhrase), "'wired into production' must match")
    }

    @Test
    fun `only the task ledger table is scanned`() {
        // The change log and phase-gate tables discuss wiring in prose and would otherwise be
        // parsed as rows, producing offenders with no row id and training everyone to ignore this.
        val markdown = """
            ## Task ledger

            | ID | Task | Owner | Evidence | Status |
            |---|---|---|---|---|
            | MED-002 | x | Agent | **PRODUCTION-WIRED** via `ReaderPage.kt`. | X |

            ## Phase gates

            | Phase | Status | Evidence |
            |---|---|---|
            | 7Paged reader | **VERIFIED** | `RDR-004` is complete and production-wired. |

            ## Change log

            | Date | Change | Evidence |
            |---|---|---|
            | 2026-09-26 | something production-wired | `Nope.kt` |
        """.trimIndent()

        val rows = wiringRows(markdown)
        assertTrue(
            rows.map { it.id } == listOf("MED-002"),
            "only the task ledger table should be scanned, got ${rows.map { it.id }}",
        )
    }

    @Test
    fun `a citation resolves by full path or by unique basename`() {
        val tracked = setOf(
            "feature/reader/src/main/kotlin/ReaderPage.kt",
            "core/domain/src/main/kotlin/ReaderSession.kt",
        )
        val byBasename = tracked.groupBy { it.substringAfterLast('/') }

        assertTrue(resolves("core/domain/src/main/kotlin/ReaderSession.kt", tracked, byBasename))
        assertTrue(resolves("ReaderPage.kt", tracked, byBasename))
        assertTrue(!resolves("DoesNotExist.kt", tracked, byBasename))
    }

    private data class WiringRow(val id: String, val citations: List<String>)

    /**
     * Rows of the **task ledger** table that assert production wiring.
     *
     * Scoped to one table deliberately. The phase-gate and change-log tables also discuss wiring,
     * and scanning them produces rows with no meaningful id — a false positive on most of the
     * document, which is how a gate gets ignored.
     */
    private fun wiringRows(markdown: String): List<WiringRow> =
        markdown.substringAfter(TASK_LEDGER_HEADING, missingDelimiterValue = "")
            .substringBefore("\n## ")
            .lines()
            .filter { it.startsWith("|") }
            .filter { isWiringRow(it) }
            .map { line ->
                val id = line.substringAfter('|').trim().substringBefore('|').trim()
                WiringRow(id, citationsIn(line))
            }

    private fun isWiringRow(line: String): Boolean {
        if (!line.startsWith("|")) return false
        // Skip the header and separator rows.
        if (line.contains("---")) return false
        val lower = line.lowercase()
        val disclaims = listOf("not production-wired", "not production wired", "unwired", "never used")
        if (disclaims.any { lower.contains(it) }) return false
        val asserts = listOf(
            "production-wired",
            "production wired",
            "is wired",
            "now wired",
            "are wired",
            "wired into production",
        )
        return asserts.any { lower.contains(it) }
    }

    /**
     * File paths cited in backticks, with any `:line` and `#anchor` suffix removed.
     *
     * A member reference such as `ReaderChapter.cacheBytes` is not a file and is dropped: it names
     * a symbol, and resolving a symbol to a file is the guess this gate exists to stop relying on.
     */
    private fun citationsIn(text: String): List<String> =
        BACKTICKED.findAll(text)
            .map { it.groupValues[1] }
            .map { it.substringBefore('#').substringBefore(':') }
            .map { it.trim() }
            .filter { it.endsWith(".kt") || it.endsWith(".md") || it.contains('/') }
            .filter { !it.contains(' ') || it.contains('/') }
            .distinct()
            .toList()

    private fun resolves(citation: String, tracked: Set<String>, byBasename: Map<String, List<String>>): Boolean =
        citation in tracked || byBasename[citation.substringAfterLast('/')]?.isNotEmpty() == true

    private fun ledgerFile(): File = File(TrackedFileNames.repositoryRoot(), LEDGER)

    private fun trackedPaths(): Set<String> =
        (TrackedFileNames.inMainSources() + TrackedFileNames.inSourceSet("doc")).toSet()

    private companion object {
        const val LEDGER = "doc/REBUILD_STATUS.md"
        const val TASK_LEDGER_HEADING = "## Task ledger"

        /** A backticked span, captured. */
        val BACKTICKED = Regex("`([^`]+)`")
    }
}
