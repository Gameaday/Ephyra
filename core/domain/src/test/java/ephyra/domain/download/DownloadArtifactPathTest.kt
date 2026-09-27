package ephyra.domain.download

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The path rule was previously implemented three times — in `DownloadArtifactRecord`, in
 * `verifyDownloadArtifact`, and in `core:download`'s probe. This suite exists so the single
 * definition has direct coverage, and so a future edit that weakens it is caught at the rule rather
 * than only through one of its three callers.
 *
 * Both separators are exercised deliberately. A check that understood only `/` would accept
 * `series\..\..\outside`, which is one of the cases the duplicated implementations disagreed about.
 */
class DownloadArtifactPathTest {

    @Test
    fun `an ordinary relative path is safe`() {
        assertTrue(DownloadArtifactPath.isSafe("Series/Chapter"))
        assertTrue(DownloadArtifactPath.isSafe("Series Name/Chapter 001.cbz"))
        assertTrue(DownloadArtifactPath.isSafe("a/b/c/d"))
    }

    @Test
    fun `a blank path is unsafe`() {
        assertFalse(DownloadArtifactPath.isSafe(""))
        assertFalse(DownloadArtifactPath.isSafe("   "))
    }

    @Test
    fun `an absolute path is unsafe on either separator`() {
        assertFalse(DownloadArtifactPath.isSafe("/downloads/series/chapter"))
        assertFalse(DownloadArtifactPath.isSafe("\\downloads\\series\\chapter"))
    }

    @Test
    fun `a parent traversing segment is unsafe on either separator`() {
        assertFalse(DownloadArtifactPath.isSafe("series/../../outside"))
        assertFalse(DownloadArtifactPath.isSafe("series\\..\\outside"))
        assertFalse(DownloadArtifactPath.isSafe(".."))
        assertFalse(DownloadArtifactPath.isSafe("../leading"))
    }

    @Test
    fun `a dot segment that is not a parent traversal is safe`() {
        // "." is a legitimate relative-path component and is not an escape. Only ".." escapes, and
        // a path segment that merely starts with dots ("..hidden") is a real directory name.
        assertTrue(DownloadArtifactPath.isSafe("series/./chapter"))
        assertTrue(DownloadArtifactPath.isSafe("series/..hidden/chapter"))
        assertTrue(DownloadArtifactPath.isSafe("series/..dots/chapter"))
    }

    @Test
    fun `the rule agrees with the record constructor`() {
        // The point of extracting the rule: the validator and the constructor must not disagree.
        listOf(
            "Series/Chapter",
            "series/../../outside",
            "/absolute",
            "\\absolute",
            "series\\..\\outside",
            "",
        ).forEach { path ->
            val constructorAccepts = runCatching {
                DownloadArtifactRecord(
                    artifactId = DownloadArtifactId("a1"),
                    chapterId = "c1",
                    seriesId = "s1",
                    sourceId = "src1",
                    relativePath = path,
                    container = DownloadContainer.DIRECTORY,
                    pageCount = 1,
                    byteSize = 1L,
                    verifiedAt = 0L,
                )
            }.isSuccess

            assertTrue(
                constructorAccepts == DownloadArtifactPath.isSafe(path),
                "constructor and rule disagree for '$path': constructor=$constructorAccepts " +
                    "rule=${DownloadArtifactPath.isSafe(path)}",
            )
        }
    }
}
