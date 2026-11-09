package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The progress seam is decided by contract, not inlined in the view model - the rule R-A's
 * fourth seam exists to enforce.
 *
 * ## The defect this prevents
 *
 * ReaderViewModel owned the progress decisions as inline code: the read-timer field and its
 * start/consume arithmetic (restartReadTimer / updateHistory), the history-write gates, the
 * track-chapter-read gates (incognito, auto-update preference, resolved manga), and the
 * completion side of updateChapterProgressOnComplete (read flag, tracker write,
 * remove-after-read drive, mark-duplicates sweep) - the redesign audit F1's progress grouping.
 * Inline gates next to their write calls are exactly how a policy that once held across two
 * paths drifts: each edit site looks locally right.
 *
 * ## Why this is structural
 *
 * Which decisions the progress paths use cannot be asserted behaviourally without a device;
 * what can fail the build is the architectural property - the view model drives the
 * coordinator, the decision tables have exactly one home, and every effect the coordinator
 * drives is implemented (the unwired-contract failure mode, B-032, is caught by the build,
 * not by review).
 */
class ReaderProgressRecorderOwnershipTest {

    private fun sourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.isAbsolute) return direct
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        return File(".", relativePath).absoluteFile
    }

    /** Source lines with comments stripped, so prose cannot satisfy a rule about code. */
    private fun codeOf(relativePath: String): List<String> =
        sourceFile(relativePath)
            .readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .map { it.substringBefore("//").trim() }
            .filter { it.isNotEmpty() }

    /**
     * The view model drives the coordinator and consumes its public decisions.
     *
     * Asserted positively: a coordinator nothing drives is dead code that passes a negative
     * gate. Every public decision of the progress seam must have a call site in the view
     * model - the drive belongs there, the decision does not. (restartReadTimer stays the
     * loading seam's effect - the open path decides *when* a chapter starts - and its body
     * forwards to the progress recorder, which owns the timer state that decision starts.)
     */
    @Test
    fun `the view model drives the progress recorder`() {
        val vm = codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderViewModel.kt")

        assertTrue(
            vm.any { it.contains("ReaderProgressRecorder(") },
            "ReaderViewModel must construct the progress recorder; the seam exists so the " +
                "decisions leave the view model, not so they exist unused beside it",
        )
        for (member in listOf(
            "restartReadTimer",
            "recordHistory",
            "onChapterCompleted",
        )) {
            assertTrue(
                vm.any { it.contains("progressRecorder.$member") },
                "the $member decision must be consumed by the view model; an unconsumed " +
                    "decision is the B-032 unwired-contract failure mode",
            )
        }
    }

    /**
     * The progress decision tables have one home.
     *
     * The read-timer state and its start/consume arithmetic, the duplicate sweep's twin
     * comparison, and the completion ordering previously lived inline in the view model. A
     * copy is how two gates that once agreed drift apart. The coordinator owns them; the view
     * model must not re-derive them. (The preference reads, the history write, the tracker
     * launch, the read-flag mutation and the updateChapters write stay in the view model's
     * effect implementations - those are the translations this seam separates from the
     * decisions. The remove-after-read *decision* is the download seam's, stage 3 seam 3;
     * the progress recorder only drives through it.)
     */
    @Test
    fun `the progress decision tables have one home`() {
        val vm = codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderViewModel.kt")
        val coordinator =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/progress/ReaderProgressRecorder.kt")

        assertTrue(
            vm.none { it.contains("chapterReadStartTime") },
            "the read-timer state belongs to ReaderProgressRecorder; a copy in the view " +
                "model is the inline decision this seam exists to remove",
        )
        assertTrue(
            coordinator.any { it.contains("readStartMillis") },
            "ReaderProgressRecorder must own the read-timer state it is the single home of",
        )

        assertTrue(
            vm.none { it.contains("sameChapterNumber") },
            "the duplicate sweep's twin comparison belongs to ReaderProgressRecorder; a copy " +
                "in the view model is the inline decision this seam exists to remove",
        )
        assertTrue(
            coordinator.any { it.contains("sameChapterNumber") },
            "ReaderProgressRecorder must own the twin comparison it is the single home of",
        )

        assertTrue(
            coordinator.any { it.contains("readStartMillis?.let { endMillis - it }") },
            "ReaderProgressRecorder must own the session-duration arithmetic it is the " +
                "single home of",
        )
        assertTrue(
            vm.none { it.contains("endTime.time") },
            "the read-at/duration derivation belongs to ReaderProgressRecorder; the view " +
                "model's translation receives both values",
        )
    }

    /**
     * The view model implements the effect contract the coordinator drives.
     *
     * The coordinator drives twelve effects; an interface member without an override is the
     * unwired-contract failure mode - it compiles, tests green in isolation, and a progress
     * path silently skips a step. The member set stays a closed list: one joins by being
     * both driven and implemented.
     */
    @Test
    fun `the view model implements the effect contract the coordinator drives`() {
        val vm = codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderViewModel.kt")
            .joinToString("\n")
        val coordinator =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/progress/ReaderProgressRecorder.kt")

        assertTrue(
            Regex("ReaderProgressEffectHandler\\s*[,{}]").containsMatchIn(vm),
            "ReaderViewModel must declare the effect contract as a supertype, sole or later " +
                "entry in the declaration list; a coordinator that drives nothing implementable " +
                "is a dead seam",
        )
        for (member in listOf(
            "nowMillis",
            "isIncognito",
            "currentChapterId",
            "upsertHistory",
            "markChapterRead",
            "currentMangaId",
            "autoUpdateTrackEnabled",
            "updateTrackChapterRead",
            "removeAfterRead",
            "markDuplicateReadExistingEnabled",
            "unfilteredChapters",
            "markChaptersRead",
        )) {
            assertTrue(
                coordinator.any { it.contains("fun $member(") },
                "the effect set must stay a closed list; $member must be declared by the contract",
            )
            assertTrue(
                Regex("override (suspend )?fun $member\\(").containsMatchIn(vm),
                "$member must exist as an override in the view model; an unimplemented effect " +
                    "silently skips a step of a progress path",
            )
        }
    }
}
