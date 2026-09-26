package ephyra.app.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Guards the `E4-lab` device-seeding path against drift.
 *
 * `app/src/debug/assets/ephyra-e4lab` holds copies of the committed catalog media so a debug build
 * can write them into the app's own storage. Copies drift: the catalog fixture is edited, the asset
 * is not, and a device capture is then made against bytes no test has ever seen — the exact failure
 * mode that produced the fabricated `E4` evidence this programme was rebuilt to eliminate.
 *
 * This asserts the copies are byte-identical, so a capture provably uses the same bytes as the `E2`
 * suites. It lives in the main test source set because it is an integrity check on committed
 * content, and it must keep running even though the seeder it guards is debug-only.
 */
class E4LabAssetParityTest {

    @Test
    fun `debug assets are byte-identical copies of the committed catalog media`() {
        val sourceDir = File(repositoryRoot(), FIXTURE_SOURCE)
        val assetDir = File(repositoryRoot(), DEBUG_ASSETS)
        assertTrue(sourceDir.isDirectory, "missing catalog media at $FIXTURE_SOURCE")
        assertTrue(assetDir.isDirectory, "missing debug assets at $DEBUG_ASSETS")

        val sourceNames = sourceDir.listFiles()
            ?.filter { it.isFile }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
        val assetNames = assetDir.listFiles()
            ?.filter { it.isFile }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()

        assertTrue(
            assetNames.isNotEmpty(),
            "no debug assets staged; a debug build would seed nothing and reader capture stays blocked",
        )
        assertEquals(
            sourceNames,
            assetNames,
            "debug assets and committed fixtures have diverged. Any staged asset that is no longer " +
                "a catalog fixture is stale and should be deleted, and any catalog fixture that is " +
                "not staged is unavailable to a device capture.",
        )

        sourceNames.forEach { name ->
            val source = File(sourceDir, name)
            val asset = File(assetDir, name)
            assertEquals(
                source.length(),
                asset.length(),
                "$name differs in size between the committed fixture and the staged debug asset, so " +
                    "a device capture would be running against different bytes than the E2 suites",
            )
            assertTrue(
                source.readBytes().contentEquals(asset.readBytes()),
                "$name is not byte-identical between the committed fixture and the staged debug asset",
            )
        }
    }

    private fun repositoryRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null && !File(directory, "settings.gradle.kts").exists()) {
            directory = directory.parentFile
        }
        requireNotNull(directory) { "could not locate the repository root from ${File(".").absolutePath}" }
        return directory
    }

    private companion object {
        const val FIXTURE_SOURCE = "feature/reader/src/test/resources/fixtures/reader"
        const val DEBUG_ASSETS = "app/src/debug/assets/ephyra-e4lab"
    }
}
