package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The image-action seam is decided by contract, not inlined in the view model - the rule
 * R-A's second seam exists to enforce.
 *
 * ## The defect this prevents
 *
 * ReaderViewModel owned the six image actions (save / share / set-cover / block / unblock /
 * hash-match) as inline flows: the filename rule and the per-manga relative path sat in the
 * view model beside two call sites, and the blocked-hash matching table was buried in a
 * private function. Decision code living next to the effects that execute it is how "one
 * more special case" creeps into the wrong layer - the same drift the loading seam (seam 1)
 * removed for loading.
 *
 * ## Why this is structural
 *
 * Which decisions the image actions use cannot be asserted behaviourally without a device;
 * what can fail the build is the architectural property - the view model drives the
 * coordinator, the filename and hash-match rules have exactly one home, and every effect
 * the coordinator drives is implemented (the unwired-contract failure mode, B-032, is
 * caught by the build, not by review).
 */
class ReaderImageActionsOwnershipTest {

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
     * gate. Every public decision of the image-action seam must have a call site in the view
     * model - the drive belongs there, the decision does not.
     */
    @Test
    fun `the view model drives the image-action coordinator`() {
        val vm = codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderViewModel.kt")

        assertTrue(
            vm.any { it.contains("ReaderImageActions(") },
            "ReaderViewModel must construct the image-action coordinator; the seam exists so " +
                "the decisions leave the view model, not so they exist unused beside it",
        )
        for (member in listOf(
            "saveImage",
            "shareImage",
            "setAsCover",
            "blockPage",
            "unblockPage",
            "findMatchingBlockedHash",
        )) {
            assertTrue(
                vm.any { it.contains("imageActions.$member") },
                "the $member decision must be consumed by the view model; an unconsumed " +
                    "decision is the B-032 unwired-contract failure mode",
            )
        }
    }

    /**
     * The filename and hash-match rules have one home.
     *
     * Both rules lived in the view model before this seam; a second copy is how two checks
     * that once agreed drift apart. The coordinator owns them; the view model must not
     * re-derive them.
     */
    @Test
    fun `the filename and hash-match rules have one home`() {
        val vm = codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderViewModel.kt")
        val actions =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/images/ReaderImageActions.kt")

        assertTrue(
            vm.none { it.contains("DiskUtil.buildValidFilename") },
            "the filename and relative-path rules belong to ReaderImageActions; a copy in " +
                "the view model is the inline decision this seam removes",
        )
        assertTrue(
            actions.any { it.contains("DiskUtil.buildValidFilename") },
            "ReaderImageActions must own the filename rule it is the single home of",
        )
        assertTrue(
            vm.none { it.contains("dHashDistance") },
            "the blocked-hash matching threshold belongs to ReaderImageActions; a copy in " +
                "the view model is how two checks that once agreed drift apart",
        )
        assertTrue(
            actions.any { it.contains("dHashDistance") },
            "ReaderImageActions must own the hash-match rule it is the single home of",
        )
    }

    /**
     * The view model implements the effect contract the coordinator drives.
     *
     * The coordinator drives eighteen effects; an interface member without an override is
     * the unwired-contract failure mode - it compiles, tests green in isolation, and an
     * image action silently skips a step. The member set stays a closed list: one joins by
     * being both driven and implemented.
     */
    @Test
    fun `the view model implements the effect contract the coordinator drives`() {
        val vm = codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderViewModel.kt")
            .joinToString("\n")
        val actions =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/images/ReaderImageActions.kt")

        assertTrue(
            vm.contains("ReaderImageEffectHandler {"),
            "ReaderViewModel must declare the effect contract; a coordinator that drives " +
                "nothing implementable is a dead seam",
        )
        for (member in listOf(
            "selectedPage",
            "currentManga",
            "folderPerManga",
            "resetSaveNotification",
            "showSaveNotificationError",
            "saveToPictures",
            "saveToCache",
            "clearShareCache",
            "reportSavedImage",
            "reportImageExported",
            "applyCover",
            "reportSetCoverResult",
            "pageDHash",
            "blockedHashes",
            "addBlockedHash",
            "removeBlockedHash",
            "reportBlockResult",
            "logError",
        )) {
            assertTrue(
                actions.any { it.contains("fun $member(") },
                "the effect set must stay a closed list; $member must be declared by the contract",
            )
            assertTrue(
                Regex("override (suspend )?fun $member\\(").containsMatchIn(vm),
                "$member must exist as an override in the view model; an unimplemented " +
                    "effect silently skips a step of an image action",
            )
        }
    }
}
