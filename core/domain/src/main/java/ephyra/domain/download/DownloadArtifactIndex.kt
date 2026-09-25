package ephyra.domain.download

/**
 * Filesystem-owned chapter artifact identity. Room may index this relationship, but the artifact
 * remains authoritative only after filesystem verification.
 */
data class DownloadArtifactId(val value: String) {
    init {
        require(value.isNotBlank()) { "Download artifact id must not be blank" }
    }
}

enum class DownloadContainer {
    DIRECTORY,
    CBZ,
}

/**
 * A verified index entry for one downloaded chapter artifact.
 *
 * [relativePath] is rooted at the configured downloads directory and must never contain the
 * absolute storage root. The entry is valid only while the artifact still passes reconciliation.
 */
data class DownloadArtifactRecord(
    val artifactId: DownloadArtifactId,
    val chapterId: String,
    val seriesId: String,
    val sourceId: String,
    val relativePath: String,
    val container: DownloadContainer,
    val pageCount: Int,
    val byteSize: Long,
    val verifiedAt: Long,
) {
    init {
        require(chapterId.isNotBlank()) { "Chapter id must not be blank" }
        require(seriesId.isNotBlank()) { "Series id must not be blank" }
        require(sourceId.isNotBlank()) { "Source id must not be blank" }
        require(relativePath.isNotBlank()) { "Download relative path must not be blank" }
        require(!relativePath.startsWith('/') && !relativePath.startsWith('\\')) {
            "Download path must be relative to the configured downloads root"
        }
        require(!relativePath.split('/', '\\').any { it == ".." }) {
            "Download path must not escape the configured downloads root"
        }
        require(pageCount > 0) { "Download artifact must contain at least one page" }
        require(byteSize >= 0L) { "Download artifact size must not be negative" }
    }
}

sealed interface DownloadReconciliation {
    data object Unchanged : DownloadReconciliation
    data object Added : DownloadReconciliation
    data object Updated : DownloadReconciliation
    data object Removed : DownloadReconciliation
}

sealed interface DownloadArtifactIndexResult {
    data class Success(val change: DownloadReconciliation) : DownloadArtifactIndexResult
    data class NotFound(val artifactId: DownloadArtifactId) : DownloadArtifactIndexResult
    data class InvalidArtifact(val reason: String) : DownloadArtifactIndexResult
}

/**
 * Reconciled index of filesystem-owned artifacts. This repository is not a work queue and cannot
 * create, download, or delete files. A production adapter must coordinate those operations.
 */
interface DownloadArtifactIndex {
    suspend fun find(chapterId: String): DownloadArtifactRecord?
    suspend fun listForSeries(seriesId: String): List<DownloadArtifactRecord>
    suspend fun publishVerified(record: DownloadArtifactRecord): DownloadArtifactIndexResult
    suspend fun removeVerified(artifactId: DownloadArtifactId): DownloadArtifactIndexResult
}
