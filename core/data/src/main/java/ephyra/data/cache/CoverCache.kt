package ephyra.data.cache

import android.content.Context
import ephyra.core.common.util.storage.DiskUtil
import ephyra.domain.manga.model.Manga
import java.io.File
import java.io.IOException
import java.io.InputStream
import ephyra.domain.manga.service.CoverCache as ICoverCache

/**
 * Durable cover store for remote and custom cover bytes.
 *
 * Remote cover files are keyed by a hash of thumbnail URL plus cover revision and live
 * in app-external storage so they survive process death and app restarts. Custom covers
 * are kept in a separate directory and are never removed by ordinary remote-cover pruning.
 * Coil owns decoding and its disposable memory cache; this class is the durable source
 * of truth.
 */
class CoverCache(private val context: Context) : ICoverCache {

    companion object {
        private const val COVERS_DIR = "covers"
        private const val CUSTOM_COVERS_DIR = "covers/custom"

        /** Default max age for cover pruning: 30 days in milliseconds. */
        private const val COVER_PRUNE_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

        /** Maximum durable remote-cover storage before least-recently-used pruning. */
        private const val COVER_CACHE_MAX_BYTES = 256L * 1024 * 1024
    }

    /**
     * Cache directory used for cache management.
     */
    private val cacheDir = getCacheDir(COVERS_DIR)

    private val customCoverCacheDir = getCacheDir(CUSTOM_COVERS_DIR)

    /**
     * Durable identity for a remote cover revision. Keeping the URL and revision together
     * prevents a forced refresh with an unchanged URL from serving stale bytes.
     */
    fun getCoverFile(mangaThumbnailUrl: String?, lastModified: Long): File? {
        return mangaThumbnailUrl?.let {
            File(cacheDir, DiskUtil.hashKeyForDisk("$it\u0000$lastModified"))
        }
    }

    override fun getCoverFile(manga: Manga): File? = getCoverFile(manga.thumbnailUrl, manga.coverLastModified)

    /**
     * Returns the custom cover from cache.
     *
     * @param mangaId the manga id.
     * @return cover image.
     */
    fun getCustomCoverFile(mangaId: Long?): File {
        return File(customCoverCacheDir, DiskUtil.hashKeyForDisk(mangaId.toString()))
    }

    override fun getCustomCoverFile(manga: Manga): File = getCustomCoverFile(manga.id)

    /**
     * Saves the given stream as the manga's custom cover to cache.
     */
    @Throws(IOException::class)
    override fun setCustomCoverToCache(manga: Manga, inputStream: InputStream) {
        writeFileAtomically(getCustomCoverFile(manga.id)) { temporary ->
            temporary.outputStream().use { output ->
                inputStream.use { input ->
                    input.copyTo(output)
                }
            }
        }
    }

    /**
     * Delete the cover files of the manga from the cache.
     */
    fun deleteFromCacheWithResult(manga: Manga, deleteCustomCover: Boolean = false): Int {
        var deleted = 0

        getCoverFile(manga.thumbnailUrl, manga.coverLastModified)?.let {
            if (it.exists() && it.delete()) ++deleted
        }

        if (deleteCustomCover) {
            if (deleteCustomCoverInternal(manga.id)) ++deleted
        }

        return deleted
    }

    override fun deleteFromCache(manga: Manga, deleteCustom: Boolean): Int {
        return deleteFromCacheWithResult(manga, deleteCustom)
    }

    override fun deleteAll() {
        cacheDir.deleteRecursively()
        customCoverCacheDir.deleteRecursively()
        cacheDir.mkdirs()
        customCoverCacheDir.mkdirs()
    }

    override fun deleteCustomCover(mangaId: Long) {
        deleteCustomCoverInternal(mangaId)
    }

    /**
     * Delete custom cover of the manga from the cache
     */
    fun deleteCustomCover(mangaId: Long?): Boolean {
        return deleteCustomCoverInternal(mangaId)
    }

    private fun deleteCustomCoverInternal(mangaId: Long?): Boolean {
        return getCustomCoverFile(mangaId).let {
            it.exists() && it.delete()
        }
    }

    /**
     * Removes cached cover files whose [java.io.File.lastModified] timestamp is older
     * than [maxAgeMs].
     */
    fun pruneOldCovers(
        protectedNames: Set<String> = emptySet(),
        maxAgeMs: Long = COVER_PRUNE_MAX_AGE_MS,
        maxBytes: Long = COVER_CACHE_MAX_BYTES,
    ): Int {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        val files = cacheDir.listFiles() ?: return 0
        var deleted = 0
        val candidates = files.filter { file ->
            !file.isDirectory && file.name !in protectedNames
        }

        for (file in candidates) {
            if (file.lastModified() <= cutoff && file.delete()) deleted++
        }

        val remaining = candidates.filter { it.exists() }.sortedBy { it.lastModified() }
        var totalBytes = cacheDir.listFiles().orEmpty().sumOf { it.length() }
        for (file in remaining) {
            if (totalBytes <= maxBytes) break
            val size = file.length()
            if (file.delete()) {
                totalBytes -= size
                deleted++
            }
        }
        return deleted
    }

    /**
     * Records a durable cache hit for least-recently-used retention. Failure to update
     * access time is non-fatal; pruning will simply use the previous known access time.
     */
    fun touch(file: File): Boolean {
        if (!file.isFile) return false
        return runCatching { file.setLastModified(System.currentTimeMillis()) }.getOrDefault(false)
    }

    /**
     * Returns the set of current remote-cover filenames for the given URLs and revisions.
     */
    fun coverFileNames(
        covers: List<Pair<String?, Long>>,
    ): Set<String> {
        return covers.mapNotNull { (url, lastModified) ->
            url?.let { DiskUtil.hashKeyForDisk("$it\u0000$lastModified") }
        }.toSet()
    }

    private fun getCacheDir(dir: String): File {
        return context.getExternalFilesDir(dir)
            ?.also { it.mkdirs() }
            ?: File(context.filesDir, dir).also { it.mkdirs() }
    }
}
