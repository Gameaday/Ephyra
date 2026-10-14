package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Freezes the target source contract from new legacy source dependencies.
 *
 * Existing compatibility code is intentionally outside the scanned target files; this guard fails
 * when a new target source-api file imports the Tachiyomi/Mihon source ABI or the transitional
 * domain source engine. Remove an entry only as part of an explicit compatibility migration.
 */
class SourceApiBoundaryTest {
    @Test
    fun `target source-api files do not import legacy source ABI or transitional engine`() {
        val root = repositoryRoot()
        val compatibilityFiles = setOf(
            "source-api/src/main/kotlin/ephyra/source/api/LegacySourceGateway.kt",
            "source-api/src/main/kotlin/eu/kanade/tachiyomi/source/online/HttpSource.kt",
            "source-api/src/main/kotlin/eu/kanade/tachiyomi/source/online/ParsedHttpSource.kt",
            "source-api/src/main/kotlin/eu/kanade/tachiyomi/source/online/ResolvableSource.kt",
            // Added with the image-address consolidation: it reasons about `Page`'s two URL fields,
            // so it needs the legacy `Page` type and cannot live under the target package. Same
            // category as HttpSource above — compatibility code that the boundary may not police.
            "source-api/src/main/kotlin/eu/kanade/tachiyomi/source/online/PageImageAddress.kt",
            "source-api/src/main/kotlin/eu/kanade/tachiyomi/source/CatalogueSource.kt",
            "source-api/src/main/kotlin/eu/kanade/tachiyomi/source/Source.kt",
        )
        val targetFiles = File(root, "source-api/src/main")
            .walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .filter { file -> file.relativeTo(root).invariantSeparatorsPath !in compatibilityFiles }
            .toList()

        assertTrue(targetFiles.isNotEmpty(), "No target source-api source files found")

        val forbiddenImports = listOf(
            "eu.kanade.tachiyomi.source",
            "ephyra.domain.content.source.ContentSourceEngine",
            "ephyra.domain.content.source.SourceProfile",
            "ephyra.domain.content.source.ContentSourceOrchestrator",
        )
        val violations = targetFiles.mapNotNull { file ->
            val text = file.readText()
            forbiddenImports
                .firstOrNull { importToken -> text.contains("import $importToken") }
                ?.let { importToken -> "${file.relativeTo(root)} imports $importToken" }
        }

        assertTrue(
            violations.isEmpty(),
            "Target source-api files must not depend on legacy/compatibility source APIs:\n" +
                violations.joinToString("\n"),
        )
    }

    private fun repositoryRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null && !File(directory, "settings.gradle.kts").exists()) {
            directory = directory.parentFile
        }
        return requireNotNull(directory) { "Could not locate repository root from ${File(".").absolutePath}" }
    }

    /**
     * The 1.4/1.5 ABI is gone, and this is what keeps it gone.
     *
     * **Why a token scan and not just the compile.** `rxjava` was an `api` dependency of
     * `source-api`, so any file here could import `rx.*` and still build. The removal deleted the
     * dependency, the `asObservable*` helpers, the `RxCoroutineBridge` files and every `Observable`
     * method on `Source`/`CatalogueSource`/`HttpSource`/`ParsedHttpSource`; a later change that
     * reintroduces one of them would also have to re-add the dependency, and this fails first with a
     * message that says why rather than a compile error about an unresolved reference.
     *
     * `SourceApiContractTest` pins the same property on the compiled ABI; this one pins it on the
     * source, so the two fail together if it ever comes back.
     */
    @Test
    fun `source-api main carries no RxJava surface`() {
        val root = repositoryRoot()
        val offenders = File(root, "source-api/src/main")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val text = file.readText()
                listOf("import rx.", "rx.Observable", "asObservableSuccess", "Observable<")
                    .filter { token -> text.contains(token) }
                    .map { token -> "${file.relativeTo(root).invariantSeparatorsPath} contains `$token`" }
            }
            .toList()

        assertTrue(
            offenders.isEmpty(),
            "source-api must not carry the removed 1.4/1.5 RxJava surface " +
                "(see doc/UPSTREAM_MIHON_0_21_1_ADOPTION_PLAN.md, WS1):\n" + offenders.joinToString("\n"),
        )
    }
}
