package ephyra.core.download

import android.content.Context
import com.hippo.unifile.UniFile
import ephyra.core.archive.archiveReader
import ephyra.core.common.storage.extension
import ephyra.domain.download.DownloadArtifactProbe
import ephyra.domain.download.DownloadContainer

/** Read-only bridge from UniFile storage to the pure download-artifact verifier. */
class UniFileDownloadArtifactProbe(
    private val context: Context,
) {
    fun probe(relativePath: String, file: UniFile?): DownloadArtifactProbe {
        if (!isSafeRelativePath(relativePath)) {
            return DownloadArtifactProbe(exists = file != null, invalidReason = "unsafe artifact path")
        }
        if (file == null || !file.exists()) return DownloadArtifactProbe(exists = false)
        if (!file.canRead()) return DownloadArtifactProbe(exists = true, invalidReason = "artifact is not readable")

        return when {
            file.isDirectory -> probeDirectory(relativePath, file)
            file.extension.equals("cbz", ignoreCase = true) -> probeArchive(relativePath, file)
            else -> DownloadArtifactProbe(exists = true, invalidReason = "unsupported artifact container")
        }
    }

    private fun probeDirectory(relativePath: String, directory: UniFile): DownloadArtifactProbe {
        var pageCount = 0
        var byteSize = 0L
        directory.listFiles().orEmpty().forEach { child ->
            when {
                child.isDirectory -> {
                    val nested = probeDirectory("$relativePath/${child.name.orEmpty()}", child)
                    if (nested.invalidReason != null) {
                        return DownloadArtifactProbe(
                            exists = true,
                            invalidReason = nested.invalidReason,
                        )
                    }
                    pageCount += nested.pageCount
                    byteSize += nested.byteSize
                }
                isPageFile(child.name) -> {
                    pageCount++
                    byteSize += child.length().coerceAtLeast(0L)
                }
            }
        }
        return DownloadArtifactProbe(
            exists = true,
            relativePath = relativePath,
            container = DownloadContainer.DIRECTORY,
            pageCount = pageCount,
            byteSize = byteSize,
        )
    }

    private fun probeArchive(relativePath: String, file: UniFile): DownloadArtifactProbe {
        return runCatching {
            file.archiveReader(context).useEntries { entries ->
                val pages = entries.filter { it.isFile && isPageFile(it.name.substringAfterLast('/')) }.toList()
                DownloadArtifactProbe(
                    exists = true,
                    relativePath = relativePath,
                    container = DownloadContainer.CBZ,
                    pageCount = pages.size,
                    byteSize = file.length().coerceAtLeast(0L),
                )
            }
        }.getOrElse { error ->
            DownloadArtifactProbe(
                exists = true,
                invalidReason = "archive could not be read: ${error.message ?: "unknown error"}",
            )
        }
    }

    private fun isSafeRelativePath(path: String): Boolean {
        return path.isNotBlank() && !path.startsWith('/') && !path.startsWith('\\') &&
            path.split('/', '\\').none { it == ".." }
    }

    private fun isPageFile(name: String?): Boolean {
        val extension = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return extension in setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "jxl")
    }
}
