package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The download seam is decided by contract, not inlined in the view model - the rule R-A's
 * third seam exists to enforce.
 *
 * ## The defect this prevents
 *
 * ReaderViewModel owned the download-ahead and remove-after-read decisions as inline gates:
 * downloadNextChapters (loader-type gate, already-downloaded gate, duplicate collapsing,
 * amount cap), cancelQueuedDownloads and deleteChapterIfNeeded / enqueueDeleteReadChapters
 * (slot arithmetic, requeue-slot drop, read-target gate) - the redesign audit F1's downloads
 * grouping. Inline gates next to their queue calls are exactly how a policy that once held
 * across two paths drifts: each edit site looks locally right.
 *
 * ## Why this is structural
 *
 * Which decisions the download paths use cannot be asserted behaviourally without a device;
 * what can fail the build is the architectural property - the view model drives the
 * coordinator, the decision tables have exactly one home, and every effect the coordinator
 * drives is implemented (the unwired-contract failure mode, B-032, is caught by the build,
 * not by review).
 */
class ReaderDownloadPolicyOwnershipTest {

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
     * gate. Every public decision of the download seam must have a call site in the view
     * model - the drive belongs there, the decision does not.
     */
    @Test
    fun `the view model drives the download policy`() {
        val vm = codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderViewModel.kt")

        assertTrue(
            vm.any { it.contains("ReaderDownloadPolicy(") },
            "ReaderViewModel must construct the download policy; the seam exists so the " +
                "decisions leave the view model, not so they exist unused beside it",
        )
        for (member in listOf(
            "isInDownloadAheadRange",
            "downloadNextChapters",
            "cancelQueuedDownloadOf",
            "removeAfterRead",
        )) {
            assertTrue(
                vm.any { it.contains("downloadPolicy.$member") },
                "the $member decision must be consumed by the view model; an unconsumed " +
                    "decision is the B-032 unwired-contract failure mode",
            )
        }
    }

    /**
     * The download-ahead and remove-after-read decision tables have one home.
     *
     * The trigger threshold, the duplicate collapsing + amount cap of the download-ahead
     * list, and the remove-after-read slot arithmetic previously lived inline beside the
     * view model's queue calls. A copy is how two gates that once agreed drift apart. The
     * coordinator owns them; the view model must not re-derive them. (The preference reads
     * and manager calls stay in the view model's effect implementations - those are the
     * translations this seam separates from the decisions.)
     */
    @Test
    fun `the download decision tables have one home`() {
        val vm = codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderViewModel.kt")
        val coordinator =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/downloads/ReaderDownloadPolicy.kt")

        assertTrue(
            vm.none { it.contains("removeDuplicates") },
            "the skip-duplicate shaping of the download-ahead list belongs to " +
                "ReaderDownloadPolicy; a copy in the view model is the inline decision this " +
                "seam exists to remove",
        )
        assertTrue(
            coordinator.any { it.contains("removeDuplicates") },
            "ReaderDownloadPolicy must own the duplicate collapsing it is the single home of",
        )

        assertTrue(
            vm.none { it.contains("> 0.25") },
            "the download-ahead trigger threshold belongs to ReaderDownloadPolicy; a copy in " +
                "the view model is the inline decision this seam exists to remove",
        )
        assertTrue(
            coordinator.any { it.contains("DOWNLOAD_AHEAD_TRIGGER") },
            "ReaderDownloadPolicy must own the trigger threshold it is the single home of",
        )

        assertTrue(
            vm.none { it.contains("- removeAfterReadSlots") },
            "the remove-after-read slot arithmetic belongs to ReaderDownloadPolicy; a copy in " +
                "the view model is the inline decision this seam exists to remove",
        )
        assertTrue(
            coordinator.any { it.contains("currentChapterPosition - removeAfterReadSlots") },
            "ReaderDownloadPolicy must own the slot arithmetic it is the single home of",
        )
    }

    /**
     * The view model implements the effect contract the coordinator drives.
     *
     * The coordinator drives fifteen effects; an interface member without an override is the
     * unwired-contract failure mode - it compiles, tests green in isolation, and a download
     * path silently skips a step. The member set stays a closed list: one joins by being
     * both driven and implemented.
     */
    @Test
    fun `the view model implements the effect contract the coordinator drives`() {
        val vm = codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderViewModel.kt")
            .joinToString("\n")
        val coordinator =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/downloads/ReaderDownloadPolicy.kt")

        assertTrue(
            Regex("ReaderDownloadEffectHandler\\s*[,{}]").containsMatchIn(vm),
            "ReaderViewModel must declare the effect contract as a supertype, sole or later " +
                "entry in the declaration list; a coordinator that drives nothing implementable " +
                "is a dead seam",
        )
        for (member in listOf(
            "currentManga",
            "nextChapter",
            "currentChapterLoadsFromDownload",
            "downloadAheadAmount",
            "awaitDownloadCacheReady",
            "isChapterDownloaded",
            "skipDuplicateChapters",
            "chaptersAfter",
            "downloadChapters",
            "queuedDownloadFor",
            "cancelQueuedDownload",
            "removeAfterReadSlots",
            "chapterList",
            "clearRequeueSlot",
            "enqueueChapterForDeletion",
        )) {
            assertTrue(
                coordinator.any { it.contains("fun $member(") },
                "the effect set must stay a closed list; $member must be declared by the contract",
            )
            assertTrue(
                Regex("override (suspend )?fun $member\\(").containsMatchIn(vm),
                "$member must exist as an override in the view model; an unimplemented effect " +
                    "silently skips a step of a download path",
            )
        }
    }
}
