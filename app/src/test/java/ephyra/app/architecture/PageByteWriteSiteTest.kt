package ephyra.app.architecture

import ephyra.app.security.TrackedFileNames
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Structural gate: the reader's page bytes must be cached **only** through the chapter's bounded
 * store.
 *
 * The defect this prevents is invisible from a test. `ReaderPage.cachedBytes` is a plain field, so
 * a write site that assigns it directly behaves exactly like a routed one, and the reader keeps its
 * unbounded working set while every test stays green — the budget describes a cache and not the
 * heap. Behavioural testing cannot catch it either: with no store attached, `cacheBytes` *is* a
 * direct assignment, so both paths look identical from a unit test.
 *
 * Only the three files that legitimately touch the field are named, each with the reason it may.
 * Anything else — a new viewer, a prefetcher, a download-warm path — is a production write site
 * that has bypassed the store.
 */
class PageByteWriteSiteTest {

    @Test
    fun `page bytes are cached only through the chapter store`() {
        val files = TrackedFileNames.inMainSources()
        // Guards the gate's own fail-open mode. `inMainSources` returns an empty list if git
        // cannot run, and "nothing offending" would then pass without checking anything.
        assertTrue(files.isNotEmpty(), "no tracked main sources found; the gate would be inert")

        // `inMainSources` returns paths relative to the repository root, not to the working
        // directory, so they are resolved against the root explicitly. Reading them relative to the
        // CWD throws FileNotFoundException from wherever the test runner happened to start -- a gate
        // that cannot read the tree is worse than one that reports offenders.
        val root = TrackedFileNames.repositoryRoot()
        val offenders = files
            .filter { it.endsWith(".kt") }
            .filter { file -> ASSIGNMENT.containsMatchIn(java.io.File(root, file).readText()) }
            .filterNot { file -> ALLOWED.any { file.endsWith(it) } }

        assertTrue(
            offenders.isEmpty(),
            "Page bytes must be cached through ReaderChapter.cacheBytes, not assigned directly:\n" +
                offenders.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun `the matcher distinguishes an assignment from a guard`() {
        // A gate that cannot fail is worse than no gate. These are the exact forms the viewer code
        // uses, so the matcher is proven against the real syntax rather than a paraphrase.
        val write = "?.also { page.cachedBytes = it }"
        val chainedWrite = "if (a) page.cachedBytes = bytes else null"
        val guard = "if (page.cachedBytes == null && page.mergedBitmap == null) {"
        val nullCheck = "initialValue = page.cachedBytes"

        assertTrue(ASSIGNMENT.containsMatchIn(write))
        assertTrue(ASSIGNMENT.containsMatchIn(chainedWrite))
        // `== null` must not read as an assignment; the character class excludes a second `=`.
        assertTrue(!ASSIGNMENT.containsMatchIn(guard))
        assertTrue(!ASSIGNMENT.containsMatchIn(nullCheck))
    }

    @Test
    fun `the allowed list has not grown`() {
        // The allowed list is a maintenance risk of its own: every entry is a place a new
        // unbounded write could hide. Its size is asserted so widening it is a deliberate edit.
        assertTrue(
            ALLOWED.size == 3,
            "the allowed write-site list grew to ${ALLOWED.size} entries: $ALLOWED",
        )
    }

    private companion object {
        /** Assignment only. `==` is excluded so a null-guard is never mistaken for a write. */
        val ASSIGNMENT = Regex("""cachedBytes\s*=[^=]""")

        val ALLOWED = listOf(
            // The bounded store itself: assigning the field IS its bookkeeping.
            "PageByteStoreOwner.kt",
            // The chapter's no-store fallback, used in tests and previews.
            "ReaderChapter.kt",
            // The explicit reload path, which must drop the payload before refetching.
            "ReaderPage.kt",
        )
    }
}
