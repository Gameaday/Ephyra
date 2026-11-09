package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The chapter-loading seam is decided by contract, not inlined in the view model - the rule
 * R-A's first seam exists to enforce.
 *
 * ## The defect this prevents
 *
 * ReaderViewModel owned loading, preload and recovery as roughly 200 lines of inline
 * orchestration, and the forward-buffer count existed twice - in preload() and in
 * tryPreloadNextChapterImages() - the duplication the reader redesign audit's F1/F3 recorded.
 * Two copies of the same gate are exactly how two checks that once agreed end up disagreeing:
 * each edit site looks locally right. The session coordinator (A2) proved the shape; this is
 * the loading seam of it.
 *
 * ## Why this is structural
 *
 * Which decisions the loading path uses cannot be asserted behaviourally without a device;
 * what can fail the build is the architectural property - the view model drives the
 * coordinator, the buffer count has exactly one home, and every effect the coordinator drives
 * is implemented (the unwired-contract failure mode, B-032, is caught by the build, not by
 * review).
 */
class ChapterLoadingOwnershipTest {

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
     * gate. Every public decision of the loading seam must have a call site in the view model -
     * the drive belongs there, the decision does not.
     */
    @Test
    fun `the view model drives the loading coordinator`() {
        val vm = codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderViewModel.kt")

        assertTrue(
            vm.any { it.contains("ChapterLoadingCoordinator(") },
            "ReaderViewModel must construct the loading coordinator; the seam exists so the " +
                "decisions leave the view model, not so they exist unused beside it",
        )
        for (member in listOf("openChapter", "preloadChapter", "adjacentStartPlan", "shouldPreloadNextChapterImages")) {
            assertTrue(
                vm.any { it.contains("chapterLoading.$member") },
                "the $member decision must be consumed by the view model; an unconsumed " +
                    "decision is the B-032 unwired-contract failure mode",
            )
        }
    }

    /**
     * The forward-buffer count has one home.
     *
     * The count existed twice before this seam (preload() and tryPreloadNextChapterImages());
     * a second copy is how two gates that once agreed drift apart. The coordinator owns it;
     * the view model must not re-derive it.
     */
    @Test
    fun `the forward-buffer count has one home`() {
        val vm = codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderViewModel.kt")
        val coordinator =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/loading/ChapterLoadingCoordinator.kt")

        assertTrue(
            vm.none { it.contains("Page.State.Queue") },
            "the forward-buffer count belongs to ChapterLoadingCoordinator; a copy in the view " +
                "model is the duplication the redesign audit's F3 recorded",
        )
        assertTrue(
            coordinator.any { it.contains("Page.State.Queue") },
            "ChapterLoadingCoordinator must own the forward-buffer count it is the single home of",
        )
    }

    /**
     * The view model implements the effect contract the coordinator drives.
     *
     * The coordinator drives eleven effects; an interface member without an override is the
     * unwired-contract failure mode - it compiles, tests green in isolation, and the open path
     * silently skips a step. The member set stays a closed list: one joins by being both driven
     * and implemented.
     */
    @Test
    fun `the view model implements the effect contract the coordinator drives`() {
        val vm = codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderViewModel.kt")
            .joinToString("\n")
        val coordinator =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/loading/ChapterLoadingCoordinator.kt")

        assertTrue(
            Regex("ChapterLoadingEffectHandler\\s*[,{}]").containsMatchIn(vm),
            "ReaderViewModel must declare the effect contract as a supertype, sole or later " +
                "entry in the declaration list; a coordinator that drives nothing implementable is " +
                "a dead seam",
        )
        for (member in listOf(
            "openSessionForChapter",
            "loadChapterPages",
            "wireFilteredPageReload",
            "queueBackgroundChapterPreload",
            "installViewerChapters",
            "recordOpenedChapterHistory",
            "restartReadTimer",
            "reportChapterLoadedToSession",
            "promoteDownloadedPreload",
            "startAdjacentImagePreload",
            "requestViewerReload",
        )) {
            assertTrue(
                coordinator.any { it.contains("fun $member(") },
                "the effect set must stay a closed list; $member must be declared by the contract",
            )
            assertTrue(
                Regex("override (suspend )?fun $member\\(").containsMatchIn(vm),
                "$member must exist as an override in the view model; an unimplemented effect " +
                    "silently skips a step of the open path",
            )
        }
    }
}
