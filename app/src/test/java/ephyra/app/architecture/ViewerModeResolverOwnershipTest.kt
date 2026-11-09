package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The viewer-mode seam is decided by contract, not inlined in the view model - the rule R-A's
 * fifth seam exists to enforce.
 *
 * ## The defect this prevents
 *
 * ReaderViewModel owned the viewer-mode decisions as inline code: the reading-mode precedence
 * and its long-strip inference call, the orientation precedence, the set path that persists a
 * flag and rebuilds the chapter window, the crop-borders preference split, and a private
 * pager-type derivation the session record consumed - the redesign audit F1's viewer-mode
 * grouping. Inline precedence tables next to their write calls are exactly how a policy that
 * once held across two paths drifts: each edit site looks locally right.
 *
 * ## Why this is structural
 *
 * Which decisions the viewer-mode paths use cannot be asserted behaviourally without a device;
 * what can fail the build is the architectural property - the view model drives the
 * coordinator, the decision tables have exactly one home, and every effect the coordinator
 * drives is implemented (the unwired-contract failure mode, B-032, is caught by the build,
 * not by review).
 */
class ViewerModeResolverOwnershipTest {

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
     * gate. Every public decision of the viewer-mode seam must have a call site in the view
     * model - the drive belongs there, the decision does not. (isPagerType is included
     * because the session record's viewport mapping consumes it; before the seam, that
     * mapping re-derived pager-typeness inline.)
     */
    @Test
    fun `the view model drives the viewer mode resolver`() {
        val vm = codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderViewModel.kt")

        assertTrue(
            vm.any { it.contains("ViewerModeResolver(") },
            "ReaderViewModel must construct the viewer mode resolver; the seam exists so the " +
                "decisions leave the view model, not so they exist unused beside it",
        )
        for (member in listOf(
            "getMangaReadingMode",
            "setMangaReadingMode",
            "getMangaOrientation",
            "setMangaOrientationType",
            "toggleCropBorders",
            "isPagerType",
        )) {
            assertTrue(
                vm.any { it.contains("viewerModeResolver.$member") },
                "the $member decision must be consumed by the view model; an unconsumed " +
                    "decision is the B-032 unwired-contract failure mode",
            )
        }
    }

    /**
     * The viewer-mode decision tables have one home.
     *
     * The reading-mode precedence (with its long-strip inference call), the flag
     * interpretation through fromPreference, and the pager-type derivation previously lived
     * inline in the view model. A copy is how two gates that once agreed drift apart. The
     * coordinator owns them; the view model must not re-derive them. (The preference reads
     * and the flag write stay in the view model's effect implementations - those are the
     * translations this seam separates from the decisions.)
     */
    @Test
    fun `the viewer mode decision tables have one home`() {
        val vm = codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderViewModel.kt")
        val coordinator =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/viewermode/ViewerModeResolver.kt")

        assertTrue(
            vm.none { it.contains("DefaultReadingModeResolver") },
            "the reading-mode precedence belongs to ViewerModeResolver; a copy in the view " +
                "model is the inline decision this seam exists to remove",
        )
        assertTrue(
            coordinator.any { it.contains("DefaultReadingModeResolver.resolve") },
            "ViewerModeResolver must own the long-strip inference call it is the single home of",
        )

        assertTrue(
            vm.none { it.contains("fromPreference") },
            "the flag-to-mode interpretation belongs to ViewerModeResolver; the view model's " +
                "translations receive raw values",
        )
        assertTrue(
            coordinator.any { it.contains("fromPreference") },
            "ViewerModeResolver must own the flag interpretation it is the single home of",
        )

        assertTrue(
            vm.none { it.contains("ReadingMode.isPagerType(") },
            "the pager-type derivation belongs to ViewerModeResolver (its isPagerType decision); " +
                "a copy in the view model is a second home that can drift",
        )
        assertTrue(
            coordinator.any { it.contains("ReadingMode.isPagerType(") },
            "ViewerModeResolver must own the pager-type derivation it is the single home of",
        )
    }

    /**
     * The view model implements the effect contract the coordinator drives.
     *
     * The coordinator drives seventeen effects; an interface member without an override is
     * the unwired-contract failure mode - it compiles, tests green in isolation, and a
     * viewer-mode path silently skips a step. The member set stays a closed list: one joins
     * by being both driven and implemented. (currentMangaId is declared by this contract and
     * by the progress seam's; its single view-model override satisfies both, so it is asserted
     * here as an override that must exist, not as a second declaration.)
     */
    @Test
    fun `the view model implements the effect contract the coordinator drives`() {
        val vm = codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderViewModel.kt")
            .joinToString("\n")
        val coordinator =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/viewermode/ViewerModeResolver.kt")

        assertTrue(
            Regex("ViewerModeEffectHandler\\s*[,{]").containsMatchIn(vm),
            "ReaderViewModel must declare the effect contract as a supertype, sole or later " +
                "entry in the declaration list; a coordinator that drives nothing implementable " +
                "is a dead seam",
        )
        for (member in listOf(
            "defaultReadingModeValue",
            "defaultOrientationValue",
            "mangaReadingModeFlag",
            "mangaReaderOrientationFlag",
            "mangaContentType",
            "mangaGenres",
            "currentMangaId",
            "awaitSetReadingMode",
            "awaitSetOrientation",
            "viewerChaptersSnapshot",
            "saveRequestedPage",
            "reloadManga",
            "installReloadedManga",
            "emitReloadViewerChapters",
            "emitOrientationSet",
            "cropBordersEnabled",
            "storeCropBorders",
        )) {
            assertTrue(
                coordinator.any { it.contains("fun $member(") },
                "the effect set must stay a closed list; $member must be declared by the contract",
            )
            assertTrue(
                Regex("override (suspend )?fun $member\\(").containsMatchIn(vm),
                "$member must exist as an override in the view model; an unimplemented effect " +
                    "silently skips a step of a viewer-mode path",
            )
        }
    }
}
