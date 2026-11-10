package ephyra.feature.reader.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The page's payload is reachable only through its [PageResource] handle — the rule R-C (Stage 4 of
 * the reader modular redesign, 2026-11-09) exists to enforce.
 *
 * ## The defect this prevents
 *
 * Audit finding F5 (2026-11-09): [ReaderPage] carried its payload — the open stream lambda, the
 * cached encoded bytes, the smart-combine merge bitmap and the recorded dimensions — as plain
 * fields on the page object. Every adapter, viewer and dialog that held a page could therefore
 * reach its bytes, and payload lifetime was page lifetime: as long as any page object stayed
 * referenced (the pager's item list, a retained chapter, a dialog), its memory stayed live. That
 * is the mechanism behind the long-series memory exhaustion (bug-wave item 4 / MED-002), and no
 * behavioural test can see it, because an unbounded field behaves exactly like a routed one.
 *
 * ## Why this is structural
 *
 * The sweep moved every payload member onto [PageResource] and gave the chapter ownership of the
 * handle's lifecycle. That property is enforced by the build rather than by review: the payload
 * members no longer exist on the page, and this gate fails the build if they come back. The page
 * still mutates in three places — inherited [eu.kanade.tachiyomi.source.model.Page.status]
 * (extension ABI) and the session navigation flags — so the gate names those members too, making
 * a fourth mutable member a deliberate edit rather than an accident.
 */
class ReaderPageResourceOwnershipTest {

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

    private fun declaredVars(code: List<String>): Set<String> =
        code.filter { it.contains("var ") }
            .map { it.substringAfter("var ").substringBefore(":").substringBefore("=").trim() }
            .toSet()

    private val readerModelDir = "feature/reader/src/main/kotlin/ephyra/feature/reader/model"

    /**
     * The page holds no payload: no bitmap, byte or stream types appear in its code, and the only
     * mutable members are the identity anchor (the owning chapter) and the three session flags.
     */
    @Test
    fun `the page holds no payload and its mutable members are named`() {
        val page = codeOf("$readerModelDir/ReaderPage.kt")

        for (token in listOf("Bitmap", "ByteArray", "InputStream")) {
            assertTrue(
                page.none { it.contains(token) },
                "ReaderPage must not mention $token: payload lives on PageResource, and a payload " +
                    "member on the page re-couples payload lifetime to page lifetime",
            )
        }

        assertEquals(
            setOf("chapter", "isAbsorbed", "isBlockedByFilter", "needsFreshAddress"),
            declaredVars(page),
            "ReaderPage's mutable members are the chapter anchor and the session flags; anything " +
                "else joining them is a deliberate edit, not an accident",
        )
    }

    /** The handle is a val on the page — created with it, never replaced. */
    @Test
    fun `the page exposes the resource handle as an immutable val`() {
        val page = codeOf("$readerModelDir/ReaderPage.kt")
        assertTrue(
            page.any { it.contains("val resource: PageResource") },
            "ReaderPage must expose its payload through `val resource: PageResource`; a var would " +
                "allow the handle to be swapped out from under the chapter that owns its lifecycle",
        )
    }

    /** The handle owns exactly the payload the sweep moved, and nothing else. */
    @Test
    fun `the resource owns the payload members`() {
        val resource = codeOf("$readerModelDir/PageResource.kt")
        assertEquals(
            setOf("stream", "cachedBytes", "mergedBitmap", "width", "height"),
            declaredVars(resource),
            "PageResource's mutable members are the five payload slots the sweep moved; a sixth " +
                "joins by a deliberate edit of this gate",
        )
    }

    /**
     * The insert page shares its parent's handle — the whole handle, not a copied stream lambda.
     */
    @Test
    fun `the insert page shares the parent handle`() {
        val insert = codeOf("$readerModelDir/InsertPage.kt")
        assertTrue(
            insert.any { it.contains("parent.resource") },
            "InsertPage must pass parent.resource to the constructor: the insert renders exactly " +
                "what the parent renders, including a recorded merge or cached bytes",
        )
        assertTrue(
            insert.none { it.contains("stream") },
            "InsertPage must not touch the stream directly; the old code copied the stream lambda, " +
                "which re-read the source instead of sharing the rendered payload",
        )
    }

    /**
     * The chapter owns disposal: the ref/unref boundary clears the handle's payload, not a page
     * forwarder.
     */
    @Test
    fun `chapter disposal clears the handle payload`() {
        val chapter = codeOf("$readerModelDir/ReaderChapter.kt")
        assertTrue(
            chapter.any { it.contains("page.resource.clearLoadedImage()") },
            "ReaderChapter.unref must clear the payload through the handle: disposal owned anywhere " +
                "else re-couples payload lifetime to something that is not the chapter",
        )
    }

    /**
     * The automatic background samples the recorded page identity, not a page re-derived from the
     * display index (audit F7).
     */
    @Test
    fun `the screen samples the recorded page identity`() {
        val screen =
            codeOf("feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderScreen.kt")
        assertTrue(
            screen.any { it.contains("state.currentReaderPage") },
            "ReaderScreen must read state.currentReaderPage: the display index counts only visible " +
                "pages, so re-deriving a page from it sampled the wrong page's bytes",
        )
        assertTrue(
            screen.any { it.contains("renderedSurfaceBytes") },
            "ReaderScreen must sample through the rendered-surface signal, which distinguishes a " +
                "page that actually rendered from bytes that merely sit cached",
        )
        assertTrue(
            screen.none { it.contains("getOrNull(state.currentPage)") },
            "ReaderScreen must not index the page list with the display position: that read " +
                "resolved to the next page's bytes and tinted the canvas for a page the user " +
                "was not looking at",
        )
    }
}
