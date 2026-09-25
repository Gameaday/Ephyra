package ephyra.domain.download

/**
 * Filesystem facts supplied by the storage adapter after it has resolved an artifact path.
 * The verifier deliberately does not know about UniFile, Room, queues, or Android.
 */
data class DownloadArtifactProbe(
    val exists: Boolean,
    val relativePath: String? = null,
    val container: DownloadContainer? = null,
    val pageCount: Int = 0,
    val byteSize: Long = 0L,
)

sealed interface DownloadArtifactVerification {
    data class Valid(
        val record: DownloadArtifactRecord,
        val change: DownloadReconciliation,
    ) : DownloadArtifactVerification

    data class Missing(val artifactId: DownloadArtifactId) : DownloadArtifactVerification
    data class Invalid(val artifactId: DownloadArtifactId, val reason: String) : DownloadArtifactVerification
}

/**
 * Verifies an expected target artifact against an adapter-provided filesystem probe.
 *
 * The expected record is created by the caller only after resolving target identities. This
 * function never mutates storage or the index. A changed path/container/page-count/size is an
 * explicit update, not an implicit re-download.
 */
fun verifyDownloadArtifact(
    indexed: DownloadArtifactRecord?,
    expected: DownloadArtifactRecord,
    probe: DownloadArtifactProbe,
    verifiedAt: Long,
): DownloadArtifactVerification {
    if (!probe.exists) return DownloadArtifactVerification.Missing(expected.artifactId)

    val actualContainer = probe.container
        ?: return DownloadArtifactVerification.Invalid(expected.artifactId, "container is unknown")
    val actualPath = probe.relativePath
        ?: return DownloadArtifactVerification.Invalid(expected.artifactId, "relative path is unknown")
    if (actualPath.isBlank() || actualPath.startsWith('/') || actualPath.startsWith('\\') ||
        actualPath.split('/', '\\').any { it == ".." }
    ) {
        return DownloadArtifactVerification.Invalid(expected.artifactId, "relative path is unsafe")
    }
    if (probe.pageCount <= 0) {
        return DownloadArtifactVerification.Invalid(expected.artifactId, "page count must be positive")
    }
    if (probe.byteSize < 0L) {
        return DownloadArtifactVerification.Invalid(expected.artifactId, "byte size must not be negative")
    }
    if (actualContainer == DownloadContainer.CBZ && probe.byteSize == 0L) {
        return DownloadArtifactVerification.Invalid(expected.artifactId, "empty archive")
    }

    val actual = expected.copy(
        relativePath = actualPath,
        container = actualContainer,
        pageCount = probe.pageCount,
        byteSize = probe.byteSize,
        verifiedAt = verifiedAt,
    )
    val change = when {
        indexed == null -> DownloadReconciliation.Added
        indexed.artifactId != actual.artifactId ||
            indexed.chapterId != actual.chapterId ||
            indexed.seriesId != actual.seriesId ||
            indexed.sourceId != actual.sourceId ||
            indexed.relativePath != actual.relativePath ||
            indexed.container != actual.container ||
            indexed.pageCount != actual.pageCount ||
            indexed.byteSize != actual.byteSize -> DownloadReconciliation.Updated
        else -> DownloadReconciliation.Unchanged
    }
    return DownloadArtifactVerification.Valid(actual, change)
}
