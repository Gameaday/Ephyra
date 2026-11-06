package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Structural gate for the persisted `SourceType` name list.
 *
 * `fromString` parses a string an install may have persisted years ago, so an unrecognised value
 * cannot fail loudly — it falls back to `REMOTE_EXTENSION`, meaning an install that had a type
 * configured would silently change behaviour after a rename. Every declared enum name is therefore
 * asserted present in `fromString`, and the retired names (`ADR-0013`, `ADR-0015`) are pinned to
 * still map to a live type while staying out of the enum body.
 */
class SourceTypeRegistryStructuralTest {

    @Test
    fun `every declared source type is handled by fromString`() {
        val text = code(SOURCE_TYPE)

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
                "SourceType.$name is declared but absent from fromString, so a persisted value " +
                    "would resolve to REMOTE_EXTENSION instead of itself",
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
        val text = code(SOURCE_TYPE)

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
        const val SOURCE_TYPE =
            "core/domain/src/main/java/ephyra/domain/content/source/SourceType.kt"
    }
}
