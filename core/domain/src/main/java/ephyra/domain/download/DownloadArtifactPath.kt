package ephyra.domain.download

/**
 * The single rule for whether a download artifact path is safe to resolve.
 *
 * **Why this exists rather than being inlined at each check.** The same predicate was implemented
 * three times: in [DownloadArtifactRecord]'s constructor, in [verifyDownloadArtifact], and again in
 * `core:download`'s `UniFileDownloadArtifactProbe.isSafeRelativePath`. Duplicated across a module
 * boundary, where no compiler enforces agreement, it is exactly the kind of rule that drifts — and
 * a path check that disagrees between the validator and the storage adapter is a sandbox escape, not
 * a cosmetic inconsistency. One definition, three call sites.
 *
 * **The rule:** a safe path is relative, non-blank, and contains no `..` segment on either
 * separator. Both separators are checked because Android storage is reachable through backslashes
 * on some hosts, and a check that only understood `/` would pass `series\..\..\outside`.
 */
object DownloadArtifactPath {
    /** True when [relativePath] is safe to resolve against the configured downloads root. */
    fun isSafe(relativePath: String): Boolean =
        relativePath.isNotBlank() &&
            !relativePath.startsWith('/') &&
            !relativePath.startsWith('\\') &&
            relativePath.split('/', '\\').none { it == ".." }
}
