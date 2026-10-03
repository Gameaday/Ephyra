package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Keeps the extension ABI behind one owner.
 *
 * **What this forbids.** The reader and the downloader must not learn *how* an extension produces an
 * image address. That knowledge is `Page.url` versus `Page.imageUrl`, whether the source customises
 * the image-URL chain, and how to ask it — and it used to be spelled out in both consumers
 * independently. Each copy had to be corrected separately more than once, and they drifted apart in
 * exactly the way that made the reported MangaDex failure hard to localise: a read and a download of
 * the same chapter could resolve different fields and cache the same bytes twice.
 *
 * `resolvePageImage` owns it now. A Jellyfin or local-archive consumer will not learn any of these
 * rules; it asks one question.
 *
 * **Why a structural gate and not a code review.** The duplication was invisible precisely because it
 * was legal — every line compiled, and every line was defensible on its own. Only a test can keep a
 * second copy from being written by someone who has no reason to know the first exists.
 */
class ExtensionAbiContainmentTest {

    /** Resolution must go through the one owner. */
    @Test
    fun `consumers resolve page images through the owner, not by asking the source`() {
        CONSUMERS.forEach { (dir, _) ->
            filesIn(dir).forEach { file ->
                val body = file.readText()
                val offenders = Regex("""\.getImageUrl\(""").findAll(body).count()
                assertTrue(
                    offenders == 0,
                    "${file.name} calls source.getImageUrl directly; call resolvePageImage instead",
                )
            }
        }
    }

    /**
     * The capability probe is the owner's business.
     *
     * One use is allowed outside it: the cache gate, which answers a different question — can this
     * stored list ever produce an address — and is not resolution. Probing it in order to resolve is
     * the leak this test exists to prevent.
     */
    @Test
    fun `consumers do not probe the image chain to decide how to resolve`() {
        CONSUMERS.forEach { (dir, _) ->
            filesIn(dir).forEach { file ->
                val body = file.readText()
                assertTrue(
                    !body.contains("capabilities.customisesImageUrlChain"),
                    "${file.name} reads capabilities.customisesImageUrlChain; that decision belongs " +
                        "to resolvePageImage",
                )
            }
        }
    }

    /**
     * The owner's name must appear in the consumers, so the gate above cannot pass because the code
     * moved somewhere else entirely.
     */
    @Test
    fun `consumers reach the owner rather than a local copy of the rules`() {
        val root = repositoryRoot()
        val reader = File(root, "feature/reader/src/main/kotlin/ephyra/feature/reader/loader/HttpPageLoader.kt")
        val downloader = File(root, "core/download/src/main/kotlin/ephyra/core/download/Downloader.kt")
        listOf(reader, downloader).forEach {
            assertTrue(it.readText().contains("resolvePageImage"), it.name)
        }
    }

    /**
     * Every directory this gate claims to cover must actually contain Kotlin, or the gate passes
     * vacuously.
     *
     * **How that happened here.** An earlier version resolved paths relative to the test's working
     * directory, which for `:app` is `app/`, not the repository root. `File("feature/reader/…")` did
     * not exist, `walkTopDown()` yielded nothing, and `all {}` over an empty list is `true`. Both
     * "forbid" tests went green while inspecting zero files — the exact shape of a gate that reports
     * a property as proven while proving nothing.
     */
    @Test
    fun `the gate is inspecting real files, not an empty list`() {
        CONSUMERS.forEach { (dir, expected) ->
            val found = filesIn(dir)
            assertTrue(
                found.isNotEmpty(),
                "$dir yielded no Kotlin files; this gate would pass vacuously",
            )
            assertTrue(
                found.size >= expected,
                "$dir yielded ${found.size} files, expected at least $expected",
            )
        }
    }

    private fun filesIn(dir: String): List<File> =
        File(repositoryRoot(), dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun repositoryRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null && !File(directory, "settings.gradle.kts").exists()) {
            directory = directory.parentFile
        }
        return requireNotNull(directory) { "Could not locate repository root" }
    }

    private companion object {
        /** Directory to a floor on how many Kotlin files it must contain. */
        val CONSUMERS = listOf(
            "feature/reader/src/main" to 10,
            "core/download/src/main" to 1,
        )
    }
}
