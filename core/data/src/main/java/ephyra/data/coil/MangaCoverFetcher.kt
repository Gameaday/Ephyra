package ephyra.data.coil

import androidx.core.net.toUri
import coil3.Extras
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.getOrDefault
import coil3.request.Options
import com.hippo.unifile.UniFile
import ephyra.core.common.util.system.logcat
import ephyra.data.cache.CoverCache
import ephyra.data.cache.writeFileAtomically
import ephyra.data.coil.MangaCoverFetcher.Companion.USE_CUSTOM_COVER_KEY
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaCover
import ephyra.domain.source.service.SourceManager
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.online.HttpSource
import logcat.LogPriority
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.Source
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException

/**
 * A [Fetcher] that fetches cover image for [Manga] object.
 *
 * It uses [Manga.thumbnailUrl] if custom cover is not set by the user.
 * Remote cover bytes are persisted in [CoverCache] for both library and browse
 * content. Coil owns decoding and memory caching; it is not a second durable
 * source of truth for remote covers. Existing Coil disk snapshots are migrated
 * into the dedicated store on first read.
 *
 * Available request parameter:
 * - [USE_CUSTOM_COVER_KEY]: Use custom cover if set by user, default is true
 */
class MangaCoverFetcher(
    private val url: String?,
    private val options: Options,
    private val coverFileLazy: Lazy<File?>,
    private val customCoverFileLazy: Lazy<File>,
    private val diskCacheKeyLazy: Lazy<String>,
    private val sourceLazy: Lazy<HttpSource?>,
    private val callFactoryLazy: Lazy<Call.Factory>,
    private val imageLoader: ImageLoader,
    private val touchCoverCache: (File) -> Unit,
) : Fetcher {

    private val diskCacheKey: String
        get() = diskCacheKeyLazy.value

    override suspend fun fetch(): FetchResult {
        // Use custom cover if exists
        val useCustomCover = options.extras.getOrDefault(USE_CUSTOM_COVER_KEY)
        if (useCustomCover) {
            val customCoverFile = customCoverFileLazy.value
            if (customCoverFile.exists()) {
                return fileLoader(customCoverFile)
            }
        }

        // diskCacheKey is thumbnail_url
        if (url == null) error("No cover specified")
        return when (getResourceType(url)) {
            Type.File -> fileLoader(File(url.substringAfter("file://")))
            Type.URI -> fileUriLoader(url)
            Type.URL -> httpLoader()
            null -> error("Invalid image")
        }
    }

    private fun fileLoader(file: File): FetchResult {
        return SourceFetchResult(
            source = ImageSource(
                file = file.toOkioPath(),
                fileSystem = FileSystem.SYSTEM,
                diskCacheKey = diskCacheKey,
            ),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    private fun fileUriLoader(uri: String): FetchResult {
        val uniFile = UniFile.fromUri(options.context, uri.toUri())
            ?: throw IOException("Could not open file at the specified URI")
        val source = uniFile.openInputStream()
            .source()
            .buffer()
        return SourceFetchResult(
            source = ImageSource(source = source, fileSystem = FileSystem.SYSTEM),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    private suspend fun httpLoader(): FetchResult {
        val coverCacheFile = coverFileLazy.value ?: error("No cover specified")
        if (coverCacheFile.exists() && options.diskCachePolicy.readEnabled) {
            touchCoverCache(coverCacheFile)
            logcat(LogPriority.DEBUG) { "Cover cache durable hit" }
            return fileLoader(coverCacheFile)
        }

        var snapshot = readFromDiskCache()
        try {
            // Migrate a legacy Coil disk entry into the dedicated durable store once.
            // A local val for the reads, so the nulling below cannot affect this path's own use of
            // the snapshot; only the enclosing catch cares.
            val openSnapshot = snapshot
            if (openSnapshot != null) {
                val migratedCover = moveSnapshotToCoverCache(openSnapshot, coverCacheFile)
                if (migratedCover != null) {
                    // Close before removing. Coil's own `openSnapshot` contract: "An open snapshot
                    // prevents opening a new Editor or deleting the entry on disk" — so removing
                    // first was a silent no-op, and an open snapshot also blocks the trim. The
                    // result was a permanent duplicate of every migrated cover, re-copied on every
                    // request for that key, plus a handle that was never released.
                    openSnapshot.close()
                    snapshot = null
                    removeLegacyDiskEntry()
                    logcat(LogPriority.DEBUG) { "Cover cache migrated legacy disk entry" }
                    return fileLoader(migratedCover)
                }

                return SourceFetchResult(
                    source = openSnapshot.toImageSource(),
                    mimeType = "image/*",
                    dataSource = DataSource.DISK,
                )
            }

            logcat(LogPriority.DEBUG) { "Cover cache network fetch" }
            val response = executeNetworkRequest()
            val responseBody = checkNotNull(response.body) { "Null response source" }
            try {
                val persistedCover = writeResponseToCoverCache(response, coverCacheFile)
                if (persistedCover != null) {
                    logcat(LogPriority.DEBUG) { "Cover cache durable write" }
                    return fileLoader(persistedCover)
                }

                return SourceFetchResult(
                    source = ImageSource(source = responseBody.source(), fileSystem = FileSystem.SYSTEM),
                    mimeType = "image/*",
                    dataSource = if (response.cacheResponse != null) DataSource.DISK else DataSource.NETWORK,
                )
            } catch (e: Exception) {
                responseBody.close()
                throw e
            }
        } catch (e: Exception) {
            snapshot?.close()
            throw e
        }
    }

    private suspend fun executeNetworkRequest(): Response {
        val client = sourceLazy.value?.client ?: callFactoryLazy.value
        val response = client.newCall(newRequest()).await()
        if (!response.isSuccessful && response.code != HTTP_NOT_MODIFIED) {
            response.close()
            throw IOException(response.message)
        }
        return response
    }

    private fun newRequest(): Request {
        val request = Request.Builder().apply {
            url(url!!)

            val sourceHeaders = sourceLazy.value?.headers
            if (sourceHeaders != null) {
                headers(sourceHeaders)
            }
        }

        when {
            options.networkCachePolicy.readEnabled -> {
                // don't take up okhttp cache
                request.cacheControl(CACHE_CONTROL_NO_STORE)
            }

            else -> {
                // This causes the request to fail with a 504 Unsatisfiable Request.
                request.cacheControl(CACHE_CONTROL_NO_NETWORK_NO_CACHE)
            }
        }

        return request.build()
    }

    /**
     * Copies a legacy Coil disk-cache entry into the durable cover store.
     *
     * **Deliberately does not remove the legacy entry.** The caller still holds [snapshot] open, and
     * Coil's contract is explicit that an open snapshot prevents the entry from being deleted on
     * disk — so removing here was a silent no-op. The entry then survived, the migration re-ran for
     * every request for that key forever, and because the snapshot was never closed on the migrated
     * path the entry could not be trimmed either: a permanent duplicate plus a permanently held
     * handle. The caller closes the snapshot first and only then removes, via
     * [removeLegacyDiskEntry].
     */
    private fun moveSnapshotToCoverCache(snapshot: DiskCache.Snapshot, cacheFile: File?): File? {
        if (cacheFile == null) return null
        return try {
            imageLoader.diskCache?.fileSystem?.source(snapshot.data)?.use { input ->
                writeSourceToCoverCache(input, cacheFile)
            }
            cacheFile.takeIf { it.exists() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to write snapshot data to cover cache ${cacheFile.name}" }
            null
        }
    }

    /**
     * Deletes the legacy Coil disk entry for [diskCacheKey], now that its snapshot is closed.
     *
     * Best effort by design: the bytes are already durably in the cover store, so a failure here
     * costs a redundant migration later, never a missing cover. Logged rather than thrown for the
     * same reason.
     */
    private fun removeLegacyDiskEntry() {
        try {
            // `remove` reports whether the entry went away, so a false here is logged rather than
            // discovered later as a migration that mysteriously re-runs on every request.
            if (imageLoader.diskCache?.remove(diskCacheKey) == false) {
                logcat(LogPriority.WARN) {
                    "Legacy cover disk entry for this key was not removed after migration; it will be migrated again"
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to remove legacy cover disk entry after migration" }
        }
    }

    private fun writeResponseToCoverCache(response: Response, cacheFile: File?): File? {
        if (cacheFile == null || !options.diskCachePolicy.writeEnabled) return null
        return try {
            // Bounded, deliberately not `peekBody(Long.MAX_VALUE)`. `peekBody` buffers the entire
            // body into memory *before* the write starts, on a fetcher pool eight requests wide, so
            // one oversized — or hostile — "cover" is read whole into the heap for no benefit. The
            // body has to be peeked rather than consumed because the caller still hands the original
            // to Coil when this returns null, and `peekBody(n)` *truncates* instead of throwing, so
            // the cap is checked first and truncation is detected: a cover is persisted only when
            // the whole of it is in hand. An oversized cover is still served from this response and
            // simply not cached, which costs a re-fetch later and never a corrupt file.
            val declaredLength = response.body.contentLength()
            if (declaredLength > MAX_PERSISTED_COVER_BYTES) {
                logcat(LogPriority.DEBUG) {
                    "Skipping cover cache write: $declaredLength bytes exceeds the $MAX_PERSISTED_COVER_BYTES byte cap"
                }
                return null
            }
            val peeked = response.peekBody(MAX_PERSISTED_COVER_BYTES)
            if (declaredLength < 0L && peeked.contentLength() >= MAX_PERSISTED_COVER_BYTES) {
                // Unknown length and the cap was reached, so this may be a prefix of a larger body.
                logcat(LogPriority.DEBUG) { "Skipping cover cache write: unterminated body reached the size cap" }
                return null
            }
            peeked.source().use { input ->
                writeSourceToCoverCache(input, cacheFile)
            }
            cacheFile.takeIf { it.exists() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to write response data to cover cache ${cacheFile.name}" }
            null
        }
    }

    private fun writeSourceToCoverCache(input: Source, cacheFile: File) {
        writeFileAtomically(cacheFile) { temporary ->
            temporary.sink().buffer().use { output ->
                output.writeAll(input)
            }
        }
    }

    private fun readFromDiskCache(): DiskCache.Snapshot? {
        return if (options.diskCachePolicy.readEnabled) {
            imageLoader.diskCache?.openSnapshot(diskCacheKey)
        } else {
            null
        }
    }

    private fun DiskCache.Snapshot.toImageSource(): ImageSource {
        return ImageSource(
            file = data,
            fileSystem = FileSystem.SYSTEM,
            diskCacheKey = diskCacheKey,
            closeable = this,
        )
    }

    private fun getResourceType(cover: String?): Type? {
        return when {
            cover.isNullOrEmpty() -> null
            cover.startsWith("http", true) || cover.startsWith("Custom-", true) -> Type.URL
            cover.startsWith("/") || cover.startsWith("file://") -> Type.File
            cover.startsWith("content") -> Type.URI
            else -> null
        }
    }

    private enum class Type {
        File,
        URI,
        URL,
    }

    class MangaFactory(
        private val callFactoryLazy: Lazy<Call.Factory>,
        private val coverCache: CoverCache,
        private val sourceManager: SourceManager,
    ) : Fetcher.Factory<Manga> {

        override fun create(data: Manga, options: Options, imageLoader: ImageLoader): Fetcher {
            return MangaCoverFetcher(
                url = data.thumbnailUrl,
                options = options,
                coverFileLazy = lazy { coverCache.getCoverFile(data.thumbnailUrl, data.coverLastModified) },
                customCoverFileLazy = lazy { coverCache.getCustomCoverFile(data.id) },
                diskCacheKeyLazy = lazy {
                    imageLoader.components.key(data, options)
                        ?: error("No disk cache key for $data")
                },
                sourceLazy = lazy {
                    val effectiveSourceId = data.metadataSource?.takeIf { it > 0 } ?: data.source
                    sourceManager.get(effectiveSourceId) as? HttpSource
                },
                callFactoryLazy = callFactoryLazy,
                imageLoader = imageLoader,
                touchCoverCache = coverCache::touch,
            )
        }
    }

    class MangaCoverFactory(
        private val callFactoryLazy: Lazy<Call.Factory>,
        private val coverCache: CoverCache,
        private val sourceManager: SourceManager,
    ) : Fetcher.Factory<MangaCover> {

        override fun create(data: MangaCover, options: Options, imageLoader: ImageLoader): Fetcher {
            return MangaCoverFetcher(
                url = data.url,
                options = options,
                coverFileLazy = lazy { coverCache.getCoverFile(data.url, data.lastModified) },
                customCoverFileLazy = lazy { coverCache.getCustomCoverFile(data.mangaId) },
                diskCacheKeyLazy = lazy {
                    imageLoader.components.key(data, options)
                        ?: error("No disk cache key for $data")
                },
                sourceLazy = lazy { sourceManager.get(data.sourceId) as? HttpSource },
                callFactoryLazy = callFactoryLazy,
                imageLoader = imageLoader,
                touchCoverCache = coverCache::touch,
            )
        }
    }

    companion object {
        val USE_CUSTOM_COVER_KEY = Extras.Key(true)

        private val CACHE_CONTROL_NO_STORE = CacheControl.Builder().noStore().build()
        private val CACHE_CONTROL_NO_NETWORK_NO_CACHE = CacheControl.Builder().noCache().onlyIfCached().build()

        private const val HTTP_NOT_MODIFIED = 304

        /**
         * Largest cover body written to the durable cover store: 8 MiB.
         *
         * A cover is a thumbnail; nothing legitimate approaches this, so the cap only ever rejects
         * a source serving something that is not an image (an HTML error page, a full-size page, or
         * a body that never ends). Generous enough that no real cover is rejected, small enough that
         * eight concurrent fetches cannot add up to an OOM.
         */
        private const val MAX_PERSISTED_COVER_BYTES = 8L * 1024 * 1024
    }
}
