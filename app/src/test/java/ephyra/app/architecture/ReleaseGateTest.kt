package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Holds `REL-001` in place: publish paths consume the gate workflow's verdict, the verdict read
 * has exactly one implementation, and the publish paths never depend on the emulator gate that
 * cannot boot on GitHub runners.
 *
 * # Why this exists
 *
 * `release.yml` built, signed and published whatever a tag pointed at. Nothing on the tag path
 * ran or even consulted the gate workflow, so a tag cut on a red tree produced a signed release
 * with green-looking CI — the exact failure `REL-001` names. The fix is not to re-run the suite on
 * the release path: `WorkflowRedundancyTest` keeps the suite in exactly one workflow precisely so
 * there is a single authority for whether a change is acceptable. The fix is to make publish
 * paths *read* that authority's verdict for the exact commit being shipped.
 *
 * # What this deliberately does not assert
 *
 * It does not assert that the checks pass — that is the gate workflow's job. It asserts the
 * structure that makes an unverified publish impossible: the release `build` job cannot start
 * without the `gates` job, the check-run read exists in exactly one implementation, and neither
 * publish path references the connected-instrumentation gate, which fails at emulator boot on
 * GitHub runners (`B-050`) and would turn every publish red for a reason unrelated to the code —
 * the inert-gate failure mode `connected-instrumentation.yml`'s own header documents.
 */
class ReleaseGateTest {

    private val workflows: Map<String, String> by lazy {
        val dir = File(repositoryRoot(), WORKFLOW_DIR)
        assertTrue(dir.isDirectory, "$WORKFLOW_DIR is missing")
        dir.listFiles { file -> file.extension == "yml" || file.extension == "yaml" }
            .orEmpty()
            .associate { it.name to it.readText() }
    }

    @Test
    fun `the release path cannot build on an unverified commit`() {
        val release = workflows.getValue(RELEASE_WORKFLOW)

        val gatesJob = jobBlock(release, GATES_JOB)
        assertTrue(
            gatesJob.isNotEmpty(),
            "could not locate the `$GATES_JOB` job in $RELEASE_WORKFLOW; this rule would pass having " +
                "read nothing, which is the empty-slice failure mode already seen in this programme",
        )
        assertTrue(
            VERDICT_ACTION_REF in gatesJob,
            "the `$GATES_JOB` job must consume the gate verdict through $VERDICT_ACTION_REF; a " +
                "release path that does not consult the gate workflow can publish an unverified commit",
        )

        val buildJob = jobBlock(release, BUILD_JOB)
        assertTrue(buildJob.isNotEmpty(), "could not locate the `$BUILD_JOB` job in $RELEASE_WORKFLOW")
        assertTrue(
            GATES_JOB in needsOf(buildJob),
            "the `$BUILD_JOB` job does not declare `needs: $GATES_JOB`, so a failing or skipped " +
                "gate cannot stop an unverified commit from being signed and published",
        )
    }

    @Test
    fun `the nightly publish consumes the same gate verdict as the release path`() {
        // The nightly is an installable publish to a release channel, so the same verdict rule
        // applies. Sharing the composite action is what keeps this one rule rather than two.
        val nightly = workflows.getValue(NIGHTLY_WORKFLOW)
        assertTrue(
            VERDICT_ACTION_REF in nightly,
            "$NIGHTLY_WORKFLOW does not consume the gate verdict; an unverified push to main " +
                "currently ships an installable APK",
        )
    }

    @Test
    fun `the check-verdict logic has exactly one implementation`() {
        // The check-run read must live in the composite action alone. An inline copy in a
        // workflow would be a second authority for the same rule — the ci.yml failure mode,
        // wearing a new hat.
        val offenders = workflows.filterValues { CHECK_RUNS_READ in it }.keys.sorted()
        assertTrue(
            offenders.isEmpty(),
            "The check-run read is inlined in $offenders; it must exist only in $VERDICT_ACTION " +
                "so the verdict rule has one implementation",
        )
        assertTrue(
            CHECK_RUNS_READ in verdictAction,
            "$VERDICT_ACTION does not contain the check-run read; the gate this file exists to " +
                "hold has lost its implementation",
        )
    }

    @Test
    fun `the publish paths do not depend on the emulator gate that cannot boot`() {
        // B-050: the API 35 emulator does not boot on GitHub runners. Wiring that gate into a
        // publish path would fail every run for a reason unrelated to the code. The connected
        // gate stays labelled unserviceable in its own workflow; publish paths stay away from it.
        for (name in listOf(RELEASE_WORKFLOW, NIGHTLY_WORKFLOW)) {
            val workflow = workflows.getValue(name)
            assertTrue(
                CONNECTED_TASK !in workflow,
                "$name invokes $CONNECTED_TASK; the runner emulator does not boot (B-050), so this " +
                    "gate cannot run and must not be wired into a publish path",
            )
            assertTrue(
                CONNECTED_WORKFLOW !in workflow,
                "$name references $CONNECTED_WORKFLOW; a publish must not depend on the " +
                    "unserviceable emulator gate (B-050)",
            )
        }
    }

    @Test
    fun `the release artifact is verified before it is published`() {
        val release = workflows.getValue(RELEASE_WORKFLOW)
        val buildJob = jobBlock(release, BUILD_JOB)
        assertTrue(buildJob.isNotEmpty(), "could not locate the `$BUILD_JOB` job in $RELEASE_WORKFLOW")

        // A signing step can succeed while producing an artifact that is not verifiable, is not
        // the version the tag claims, or ships without the mapping that de-obfuscates its
        // crashes. Each assertion names executable content of the job, not its comments.
        assertTrue(
            SIGNATURE_VERIFY in buildJob,
            "the release build does not invoke apksigner verify on the signed APKs",
        )
        assertTrue(
            VERSION_EXTRACT in buildJob,
            "the release build does not read the APK's versionName, so the tag cannot be checked against it",
        )
        assertTrue(
            MAPPING_GATE in buildJob,
            "the release build does not require the R8 mapping before publishing",
        )
    }

    @Test
    fun `every publish workflow is accounted for rather than silently ignored`() {
        assertTrue(workflows.isNotEmpty(), "no workflow files were found")
        assertTrue(
            workflows.containsKey(RELEASE_WORKFLOW) && workflows.containsKey(NIGHTLY_WORKFLOW),
            "$RELEASE_WORKFLOW or $NIGHTLY_WORKFLOW is missing; these rules would pass vacuously",
        )
        assertTrue(
            File(repositoryRoot(), VERDICT_ACTION).isFile,
            "$VERDICT_ACTION is missing; the publish gates have nothing to stand on",
        )
    }

    /**
     * The `needs:` list of a single job block. Copied from `WorkflowRedundancyTest` rather than
     * shared: these are file-reading gates whose helpers must stay independently readable, and a
     * common helper would make one test the load-bearing dependency of the other.
     */
    private fun needsOf(job: String): List<String> =
        job.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("needs:") }
            ?.substringAfter("needs:")
            ?.trim()
            ?.trim('[', ']', ' ')
            ?.split(',')
            ?.map { it.trim().trim('"', '\'') }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    /**
     * The YAML body of a job, from its key to the next job or top-level key. Copied from
     * `WorkflowRedundancyTest` for the same reason as [needsOf].
     */
    private fun jobBlock(workflow: String, job: String): String {
        val lines = workflow.lines()
        val start = lines.indexOfFirst { it.trimEnd() == "  $job:" }
        if (start == -1) return ""

        val body = StringBuilder()
        for (i in start + 1 until lines.size) {
            val line = lines[i]
            val isJobKey = JOB_KEY.matches(line)
            val isTopLevelKey = TOP_LEVEL_KEY.matches(line)
            if (isJobKey || isTopLevelKey) break
            body.appendLine(line)
        }
        return body.toString()
    }

    private val verdictAction: String by lazy {
        File(repositoryRoot(), VERDICT_ACTION).readText()
    }

    private fun repositoryRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null && !File(directory, "settings.gradle.kts").exists()) {
            directory = directory.parentFile
        }
        return requireNotNull(directory) { "Could not locate repository root" }
    }

    private companion object {
        const val WORKFLOW_DIR = ".github/workflows"
        const val VERDICT_ACTION = ".github/actions/require-green-checks/action.yml"
        const val VERDICT_ACTION_REF = "./.github/actions/require-green-checks"
        const val RELEASE_WORKFLOW = "release.yml"
        const val NIGHTLY_WORKFLOW = "nightly.yml"
        const val GATES_JOB = "gates"
        const val BUILD_JOB = "build"
        const val CHECK_RUNS_READ = "check-runs"
        const val CONNECTED_TASK = "connectedDebugAndroidTest"
        const val CONNECTED_WORKFLOW = "connected-instrumentation"
        const val SIGNATURE_VERIFY = "\"\${APKSIGNER}\" verify"
        const val VERSION_EXTRACT = "versionName='"
        const val MAPPING_GATE = "mapping.txt"
        val JOB_KEY = Regex("""^ {2}[A-Za-z0-9_-]+:""")
        val TOP_LEVEL_KEY = Regex("""^[A-Za-z0-9_-]+:""")
    }
}
