package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Structural gate for `ADR-0013`: adding a source type must not require editing the orchestrator.
 *
 * **What the coupling was.** Engine selection lived in an exhaustive `when` over `SourceType` inside
 * `ContentSourceOrchestrator`. Every source type added since had to be remembered by that branch, and
 * forgetting failed silently: an unmatched type fell through to the heuristic engine, so a Jellyfin
 * profile would be "configured" and quietly scraped as HTML, returning nothing.
 *
 * Selection is now a registry — each engine declares the types it serves via
 * `ContentSourceEngine.handles`. The behavioural half is covered by `ContentSourceOrchestratorTest`
 * (an unbound type falls back visibly). What is left to assert here is the thing a behaviour test
 * cannot see: that nobody has reintroduced the exhaustive branch.
 *
 * **What it does not claim.** It does not prove Jellyfin works. It proves that adding Jellyfin does
 * not require touching the orchestrator.
 */
class SourceTypeRegistryStructuralTest {

    @Test
    fun `the orchestrator does not switch exhaustively over source types`() {
        val text = code(ORCHESTRATOR)

        val exhaustiveSwitch = Regex("""when\s*\(\s*[\w.]*sourceType[\w.]*\s*\)""")
        assertTrue(
            !exhaustiveSwitch.containsMatchIn(text),
            "ContentSourceOrchestrator must resolve engines through the registry " +
                "(ContentSourceEngine.handles), not a `when` over SourceType. A new source type has " +
                "to be additive: an arm nobody remembers to write fails silently by falling through " +
                "to the heuristic engine.",
        )
    }

    /**
     * `fromString` is the other exhaustive list, and the more dangerous one.
     *
     * It parses a persisted string, so an unrecognised value cannot fail loudly — it falls back to
     * `HEURISTIC`, meaning an install that had a type configured would silently start scraping it as
     * HTML after a rename. Every declared name is therefore asserted present.
     */
    @Test
    fun `every declared source type is handled by fromString`() {
        val text = code(SOURCE_PROFILE)

        val enumBody = text.substringAfter("enum class SourceType").substringBefore("companion object")
        val declared = Regex("""^\s{4}([A-Z][A-Z_]+),?\s*$""", RegexOption.MULTILINE)
            .findAll(enumBody)
            .map { it.groupValues[1] }
            .toList()

        assertTrue(
            declared.isNotEmpty(),
            "failed to parse the SourceType enum body; this gate would otherwise pass on nothing",
        )

        val fromString = text.substringAfter("fun fromString")
        declared.forEach { name ->
            assertTrue(
                fromString.contains("\"$name\""),
                "SourceType.$name is declared but absent from fromString, so a profile persisted " +
                    "with it would resolve to HEURISTIC instead of itself",
            )
        }
    }

    /**
     * A name the app still stores must still resolve to something real.
     *
     * `JS_SCRAPER` and `HEURISTIC` were both retired (`ADR-0013`, `ADR-0015`) but are kept in
     * `fromString` deliberately — an install that persisted one would otherwise fall through to the
     * default and change behaviour with no error. This pins that both map to a *live* type and stay
     * out of the enum.
     */
    @Test
    fun `retired source-type names map to a live source type`() {
        val text = code(SOURCE_PROFILE)

        val fromString = text.substringAfter("fun fromString").substringBefore("}")
        // The retired names share one `when` branch with the live ones, so the check is that each is
        // present and that nothing maps a retired name to itself or to the other retired name.
        // Asserting an exact `"NAME" -> TYPE` substring would pass today and fail the moment someone
        // splits the branch into per-name arms, which would be a harmless refactor.
        listOf("JS_SCRAPER", "HEURISTIC").forEach { retired ->
            assertTrue(
                fromString.contains("\"$retired\""),
                "the stored name $retired must still be mapped; without this an install that " +
                    "persisted one resolves to the default and changes behaviour silently",
            )
        }
        assertTrue(
            !Regex("\"(JS_SCRAPER|HEURISTIC)\"\\s*->\\s*(HEURISTIC|JS_SCRAPER)").containsMatchIn(fromString),
            "a retired name must never map back to itself, nor to the other retired name",
        )

        val enumBody = text.substringAfter("enum class SourceType").substringBefore("companion object")
        listOf("JS_SCRAPER", "HEURISTIC").forEach { retired ->
            assertTrue(
                !enumBody.contains(retired),
                "$retired must stay out of the enum; only the persisted-name alias is kept",
            )
        }
    }

    /**
     * The registry must not become decorative.
     *
     * The plausible regression is restoring the `when` while leaving the registry in place: it
     * compiles, and every test that only exercises the heuristic path still passes while the registry
     * does nothing. Asserting the map is actually read catches that.
     */
    @Test
    fun `engine selection reads the registry rather than deciding inline`() {
        val text = code(ORCHESTRATOR)

        assertTrue(
            text.contains("enginesByType[profile.sourceType]"),
            "resolveEngineForProfile must look the type up in the registry it was given",
        )
        assertTrue(
            text.contains("engine.handles"),
            "the registry must be built from each engine's own declaration, not a list held in the " +
                "orchestrator — otherwise the two can disagree about what serves what",
        )
    }

    private fun code(path: String): String {
        val candidates = sequenceOf(
            File(System.getProperty("user.dir"), path),
            File(System.getProperty("user.dir") + "/../$path"),
            File(path),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("could not locate $path from ${System.getProperty("user.dir")}")
    }

    private companion object {
        const val ORCHESTRATOR =
            "core/domain/src/main/java/ephyra/domain/content/source/ContentSourceOrchestrator.kt"
        const val SOURCE_PROFILE =
            "core/domain/src/main/java/ephyra/domain/content/source/SourceProfile.kt"
    }
}
