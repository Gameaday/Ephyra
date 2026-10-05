package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Keeps CI to a single gate, and keeps the documentation gate falsifiable.
 *
 * Also holds the rule that lint and tests fail independently — see
 * `the unit test job is not gated behind the lint job` for why that is in this file.
 *
 * # Why this exists
 *
 * `ci.yml` and `build.yml` both ran `testDebugUnitTest` on every pull request, so every PR paid
 * for the full suite twice. `ci.yml` also ran `ktlintCheck`, which `buildSrc` registers as a bare
 * alias for `spotlessKotlinCheck` — a task `spotlessCheck` already depends on, so the lint ran
 * twice as well. `ci.yml` existed only because `build.yml` excluded `**.md`, which meant a
 * markdown-only PR ran *no* verification at all.
 *
 * Deleting `ci.yml` alone would have reintroduced that hole, so the exclusion came out of
 * `build.yml` at the same time. These tests hold both halves in place.
 *
 * # What this deliberately does not assert
 *
 * `assembleRelease` appears in both `build.yml` and `release.yml` and is *not* a duplicate:
 * `build.yml`'s is guarded to non-PR events and `release.yml`'s fires on `v*` tags. A naive
 * "no task in two workflows" rule would have flagged correct configuration, so the rule is
 * scoped to the task and the failure mode that actually cost time.
 */
class WorkflowRedundancyTest {

    private val workflows: Map<String, String> by lazy {
        val dir = File(repositoryRoot(), WORKFLOW_DIR)
        assertTrue(dir.isDirectory, "$WORKFLOW_DIR is missing")
        dir.listFiles { file -> file.extension == "yml" || file.extension == "yaml" }
            .orEmpty()
            .associate { it.name to it.readText() }
    }

    @Test
    fun `the unit test suite runs in exactly one workflow`() {
        // Scoped to this one task, not "any Gradle invocation". Every workflow legitimately runs
        // *some* Gradle task -- `nightly.yml` builds, `release.yml` releases, `benchmark.yml`
        // measures -- and a rule that flagged those would be flagging correct configuration.
        val runners = workflows.filterValues { text -> UNIT_TEST_TASK.containsMatchIn(text) }
            .keys
            .sorted()

        assertEquals(
            1,
            runners.size,
            "The unit test suite is invoked by more than one workflow ($runners), so every " +
                "pull request pays for it more than once. Exactly one workflow should own it.",
        )
        assertEquals(
            GATE_WORKFLOW,
            runners.single(),
            "The workflow that runs the unit tests should be the one that gates main, so there " +
                "is a single authority for whether a change is acceptable",
        )
    }

    @Test
    fun `the pull request gate does not exclude documentation-only changes`() {
        val gate = workflows.getValue(GATE_WORKFLOW)
        // `assertTrue` does not smart-cast, so the null case is discharged by `requireNotNull`
        // rather than left for the compiler to reject two lines later.
        val prPaths = PULL_REQUEST_PATHS.find(gate)?.groupValues?.getOrNull(1)
            ?: error(
                "$GATE_WORKFLOW no longer declares `paths` under `pull_request`. That is " +
                    "acceptable in itself -- an unfiltered trigger runs on every change -- but " +
                    "this rule cannot verify the guarantee, so it fails loudly instead of " +
                    "passing vacuously.",
            )
        assertTrue(
            !prPaths.contains(MARKDOWN_EXCLUSION) && !prPaths.contains(MARKDOWN_EXCLUSION_LEGACY),
            "$GATE_WORKFLOW excludes markdown from its pull request paths. A documentation-only " +
                "PR would then run no verification, which makes every documentation gate " +
                "structurally unfalsifiable.",
        )
    }

    @Test
    fun `every workflow is accounted for rather than silently ignored`() {
        // Without this the two tests above would pass vacuously if the directory listing broke.
        assertTrue(workflows.isNotEmpty(), "no workflow files were found")
        assertTrue(
            workflows.containsKey(GATE_WORKFLOW),
            "$GATE_WORKFLOW is missing; the gate workflow must exist for these rules to mean anything",
        )
    }

    /**
     * The job that runs the unit tests must not be gated behind the job that runs lint.
     *
     * **What went wrong.** `Build & Test App` declared `needs: quality`, so a failure in the lint
     * job marked the test job `skipped` — the suite never executed and the run reported no result
     * for it. Not hypothetical: on `579c7ba32` `Quality Checks` failed at `Check code format` and
     * `Build & Test App` was `skipped`, hiding two genuinely failing tests. Two regressions shipped
     * while nobody could see them, and the second one only surfaced by running the suite by hand.
     *
     * The failure mode is the dangerous direction. A lint failure is noisy and self-announcing, so
     * gating the tests behind it looks like harmless sequencing and is actually a way to suppress
     * signal: the cheaper gate decides whether the expensive one is ever allowed to speak.
     *
     * This asserts independence structurally. It cannot check that the tests actually pass — that is
     * what the suite is for — only that a lint failure is structurally incapable of cancelling them.
     */
    @Test
    fun `the unit test job is not gated behind the lint job`() {
        val gate = workflows.getValue(GATE_WORKFLOW)
        val testJob = jobBlock(gate, "build")

        assertTrue(
            testJob.isNotEmpty(),
            "could not locate the `build` job in $GATE_WORKFLOW; this rule would pass having read " +
                "nothing, which is the empty-slice failure mode already seen in this programme",
        )

        val needs = needsOf(testJob)
        assertTrue(
            LINT_JOB !in needs,
            "the `$TEST_JOB` job declares `needs: $LINT_JOB`, so a lint failure marks it " +
                "`skipped` and the unit tests never run. On 579c7ba32 that hid two failing tests. " +
                "Let the two jobs fail independently.",
        )
    }

    /**
     * The `needs:` list of a single job block.
     *
     * Read from the job's own body only, so a `needs:` belonging to another job cannot be
     * attributed to this one. `firstOrNull` because a job can only declare `needs` once, and taking
     * the first keeps a stray later match from being mistaken for this job's own.
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
     * The YAML body of a job: every line from its key to the next key at the job or top level.
     *
     * Scanned line by line rather than matched with one regex, because a regex that stops at the
     * wrong boundary produces a *plausible* answer instead of no answer - and this programme has
     * already been bitten by exactly that (`TabNavControllerLifetimeTest` slicing an empty body and
     * passing vacuously). Stopping at indent 0 as well as at the next two-space key is what keeps a
     * following job, or a top-level key, out of the slice.
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

    private fun repositoryRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null && !File(directory, "settings.gradle.kts").exists()) {
            directory = directory.parentFile
        }
        return requireNotNull(directory) { "Could not locate repository root" }
    }

    private companion object {
        const val WORKFLOW_DIR = ".github/workflows"
        const val GATE_WORKFLOW = "build.yml"

        /** Job id that runs `testDebugUnitTest`. */
        const val TEST_JOB = "build"

        /** Job id of the lint/architecture job that must not gate the tests. */
        const val LINT_JOB = "quality"

        /** A job key: exactly two spaces then a name and a colon. */
        val JOB_KEY = Regex("""^ {2}[A-Za-z0-9_-]+:""")

        /** A top-level key at indent 0, which ends the last job's body. */
        val TOP_LEVEL_KEY = Regex("""^[A-Za-z0-9_-]+:""")
        const val MARKDOWN_EXCLUSION = "!**/*.md"
        const val MARKDOWN_EXCLUSION_LEGACY = "!**.md"

        /**
         * A `gradlew` invocation of the unit-test task.
         *
         * Anchored on the task name rather than "any Gradle task", so a workflow that builds or
         * releases is not counted as also running the suite.
         */
        val UNIT_TEST_TASK = Regex("""gradlew(?:\.bat)?[^\n]*\btestDebugUnitTest\b""")

        /**
         * The `paths:` block under `pull_request:`. Tolerates the comment lines that document
         * why markdown is *not* excluded, which a stricter pattern would trip over and then
         * silently stop verifying anything.
         */
        val PULL_REQUEST_PATHS = Regex("""pull_request:(?:[^\n]*\n)*?\s*paths:\s*\n((?:\s*(?:-\s*.*|)\n)+)""")
    }
}
