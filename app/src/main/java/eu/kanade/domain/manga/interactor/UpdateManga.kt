package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.model.SManga
import logcat.LogPriority
import okhttp3.Request
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.model.LockedField
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime

class UpdateManga(
    private val mangaRepository: MangaRepository,
    private val fetchInterval: FetchInterval,
) {

    suspend fun await(mangaUpdate: MangaUpdate): Boolean {
        return mangaRepository.update(mangaUpdate)
    }

    suspend fun awaitAll(mangaUpdates: List<MangaUpdate>): Boolean {
        return mangaRepository.updateAll(mangaUpdates)
    }

    suspend fun awaitUpdateFromSource(
        localManga: Manga,
        remoteManga: SManga,
        manualFetch: Boolean,
        coverCache: CoverCache = Injekt.get(),
        libraryPreferences: LibraryPreferences = Injekt.get(),
        downloadManager: DownloadManager = Injekt.get(),
        trackPreferences: TrackPreferences = Injekt.get(),
        networkHelper: NetworkHelper = Injekt.get(),
    ): Boolean {
        val remoteTitle = try {
            remoteManga.title
        } catch (_: UninitializedPropertyAccessException) {
            ""
        }

        // When a manga has a canonical ID (linked to an authoritative tracker), its metadata
        // was enriched from the tracker's verified data. Preserve that authoritative metadata
        // during content source updates to prevent flickering between content-source and
        // authority values on pull-to-refresh. The authority refresh runs separately and
        // handles updating authority-owned fields.
        val hasAuthorityMetadata = localManga.canonicalId != null
        val locked = localManga.lockedFields
        // Per-field content source priority: when a field's bit is set, the content source
        // value takes precedence over the authority source.
        val contentSourcePriorityFields = if (hasAuthorityMetadata) {
            trackPreferences.contentSourcePriorityFields().get()
        } else {
            0L
        }

        // Helper: should a field be skipped? True when the field is locked, or when the
        // authority has priority and the local value is already populated.
        fun shouldPreserve(field: Long, existingIsPopulated: Boolean): Boolean {
            if (LockedField.isLocked(locked, field)) return true
            if (hasAuthorityMetadata &&
                !LockedField.isLocked(contentSourcePriorityFields, field) &&
                existingIsPopulated
            ) {
                return true
            }
            return false
        }

        val title = run {
            if (remoteTitle.isEmpty()) return@run null
            if (shouldPreserve(LockedField.TITLE, localManga.title.isNotBlank())) return@run null
            if (localManga.favorite && !libraryPreferences.updateMangaTitles().get()) return@run null
            if (remoteTitle == localManga.title) return@run null
            remoteTitle
        }

        val coverPreserved = shouldPreserve(LockedField.COVER, !localManga.thumbnailUrl.isNullOrBlank())

        // When the cover URL changes we compare the actual image content (via dHash) rather
        // than just the URL string, so that CDN path rotations serving the same image don't
        // cause unnecessary cache invalidation and UI flickering.
        val newCoverUrl = remoteManga.thumbnail_url?.takeIf { it.isNotEmpty() }
        val coverUrlChanged = !coverPreserved &&
            !newCoverUrl.isNullOrEmpty() &&
            newCoverUrl != localManga.thumbnailUrl &&
            !localManga.isLocal()

        // Try to determine whether the incoming URL actually serves a different image.
        // Returns Pair(isSameImage, newHash) when comparison succeeds, null when it fails.
        // null → fall through to full-replace (old behaviour, guaranteed safe).
        val coverComparison: Pair<Boolean, Long>? = if (coverUrlChanged && !localManga.hasCustomCover(coverCache)) {
            computeCoversAreSameImage(
                oldCoverFile = coverCache.getCoverFile(localManga.thumbnailUrl),
                newCoverUrl = newCoverUrl!!,
                localCoverHash = localManga.coverHash,
                networkHelper = networkHelper,
            )
        } else {
            null
        }
        // true  → same image confirmed; skip cache invalidation entirely (no flicker).
        // false → genuinely different image; invalidate as normal.
        // null  → comparison failed; conservatively fall through to full-replace.
        val sameImageViaHash = coverComparison?.first == true

        val coverLastModified = when {
            coverPreserved -> null
            remoteManga.thumbnail_url.isNullOrEmpty() -> null
            localManga.thumbnailUrl == remoteManga.thumbnail_url -> null
            localManga.isLocal() -> Instant.now().toEpochMilli()
            // Same image confirmed via hash: quiet URL update, no cache delete, no Coil reload.
            sameImageViaHash -> null
            localManga.hasCustomCover(coverCache) -> {
                coverCache.deleteFromCache(localManga, false)
                null
            }
            else -> {
                coverCache.deleteFromCache(localManga, false)
                Instant.now().toEpochMilli()
            }
        }

        // When the URL changed but image content is identical, copy the already-cached file
        // to the new URL's cache path so Coil resolves the new key from disk instantly — no
        // network round-trip, no loading placeholder, no visible flicker.
        if (sameImageViaHash) {
            val oldFile = coverCache.getCoverFile(localManga.thumbnailUrl)
            val newFile = coverCache.getCoverFile(newCoverUrl)
            if (oldFile != null && newFile != null && oldFile.exists() && oldFile != newFile) {
                try {
                    oldFile.copyTo(newFile, overwrite = true)
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to copy cover cache to new path for '${localManga.title}'" }
                }
            }
        }

        val thumbnailUrl = when {
            coverPreserved -> null
            remoteManga.thumbnail_url == localManga.thumbnailUrl -> null
            else -> remoteManga.thumbnail_url?.takeIf { it.isNotEmpty() }
        }

        val author = when {
            shouldPreserve(LockedField.AUTHOR, !localManga.author.isNullOrBlank()) -> null
            remoteManga.author == localManga.author -> null
            else -> remoteManga.author
        }
        val artist = when {
            shouldPreserve(LockedField.ARTIST, !localManga.artist.isNullOrBlank()) -> null
            remoteManga.artist == localManga.artist -> null
            else -> remoteManga.artist
        }
        val description = when {
            shouldPreserve(LockedField.DESCRIPTION, !localManga.description.isNullOrBlank()) -> null
            remoteManga.description == localManga.description -> null
            else -> remoteManga.description
        }
        val remoteGenres = remoteManga.getGenres()
        val genre = when {
            shouldPreserve(LockedField.GENRE, !localManga.genre.isNullOrEmpty()) -> null
            remoteGenres == localManga.genre -> null
            else -> remoteGenres
        }
        val remoteStatus = remoteManga.status.toLong()
        val status = when {
            shouldPreserve(LockedField.STATUS, localManga.status != 0L) -> null
            remoteStatus == localManga.status -> null
            else -> remoteStatus
        }
        val updateStrategy = remoteManga.update_strategy.takeIf { it != localManga.updateStrategy }
        val initialized = true.takeIf { !localManga.initialized }

        // sameImageViaHash?.second is the dHash of the new cover image when comparison
        // succeeded. Store it so future syncs can skip re-downloading to compare.
        val newCoverHashForDb: Long? = coverComparison?.second

        val hasChanges = title != null || coverLastModified != null || thumbnailUrl != null ||
            author != null || artist != null || description != null || genre != null ||
            status != null || updateStrategy != null || initialized != null ||
            newCoverHashForDb != null

        if (!hasChanges) return true

        val success = mangaRepository.update(
            MangaUpdate(
                id = localManga.id,
                title = title,
                coverLastModified = coverLastModified,
                author = author,
                artist = artist,
                description = description,
                genre = genre,
                thumbnailUrl = thumbnailUrl,
                status = status,
                updateStrategy = updateStrategy,
                initialized = initialized,
                coverHash = newCoverHashForDb,
            ),
        )
        if (success && title != null) {
            downloadManager.renameManga(localManga, title)
        }
        return success
    }

    suspend fun awaitUpdateFetchInterval(
        manga: Manga,
        dateTime: ZonedDateTime = ZonedDateTime.now(),
        window: Pair<Long, Long> = fetchInterval.getWindow(dateTime),
    ): Boolean {
        return mangaRepository.update(
            fetchInterval.toMangaUpdate(manga, dateTime, window),
        )
    }

    suspend fun awaitUpdateLastUpdate(mangaId: Long): Boolean {
        return mangaRepository.update(MangaUpdate(id = mangaId, lastUpdate = Instant.now().toEpochMilli()))
    }

    suspend fun awaitUpdateCoverLastModified(mangaId: Long): Boolean {
        return mangaRepository.update(MangaUpdate(id = mangaId, coverLastModified = Instant.now().toEpochMilli()))
    }

    suspend fun awaitUpdateFavorite(mangaId: Long, favorite: Boolean): Boolean {
        val dateAdded = when (favorite) {
            true -> Instant.now().toEpochMilli()
            false -> 0
        }
        return mangaRepository.update(
            MangaUpdate(id = mangaId, favorite = favorite, dateAdded = dateAdded),
        )
    }

    /**
     * Compares the content of the current cached cover with the image at [newCoverUrl] by
     * computing their perceptual hashes (dHash).
     *
     * This is called BEFORE any cache-invalidation decisions so that CDN path rotations that
     * serve the same image at a new URL can be detected and silently redirected without
     * triggering a `coverLastModified` update, a `CoverCache.deleteFromCache()` call, or a
     * Coil memory-cache miss — all of which cause visible UI flickering on pull-to-refresh.
     *
     * Decision flow:
     * ```
     * computeCoversAreSameImage() returns Pair(isSame=true,  hash) → skip invalidation (no flicker)
     *                                     Pair(isSame=false, hash) → full update (image changed)
     *                                     null                     → comparison failed → full update (safe fallback)
     * ```
     *
     * The [newCoverUrl] image is fetched with a plain OkHttp GET (no source-specific headers).
     * This works for the common case of publicly accessible CDN URLs.  If the request fails
     * for any reason the function returns null and the caller falls back to the existing
     * full-replace path — so failures are always safe.
     *
     * @param oldCoverFile  The locally cached cover file for the current [Manga.thumbnailUrl], or
     *                      null if not cached.  When null and [localCoverHash] is also null the
     *                      comparison cannot proceed and null is returned.
     * @param newCoverUrl   The incoming cover URL to evaluate.
     * @param localCoverHash The previously stored dHash for the current cover (from the DB),
     *                       used instead of re-reading [oldCoverFile] when available.
     * @param networkHelper  OkHttp client wrapper used to fetch [newCoverUrl].
     * @return Pair(areSameImage, newHash) on success, null on failure.
     */
    private suspend fun computeCoversAreSameImage(
        oldCoverFile: File?,
        newCoverUrl: String,
        localCoverHash: Long?,
        networkHelper: NetworkHelper,
    ): Pair<Boolean, Long>? {
        return try {
            // Step 1: get (or compute) the hash of the existing cover.
            val oldHash: Long = if (localCoverHash != null) {
                // Already stored in DB from a previous download — free, no I/O.
                localCoverHash
            } else {
                // No stored hash; compute from the locally cached file.
                val file = oldCoverFile?.takeIf { it.exists() } ?: return null
                ImageUtil.computeDHash(file.inputStream()) ?: return null
            }

            // Step 2: fetch the new cover and compute its hash.
            // Use a no-store cache control so we actually read the bytes; we compute
            // the hash and discard the body — Coil will re-fetch when needed (from
            // the CoverCache copy we may have placed there).
            val request = Request.Builder()
                .url(newCoverUrl)
                .cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
                .build()

            val newHash: Long = networkHelper.client.newCall(request).awaitSuccess().use { response ->
                val body = response.body ?: return null
                ImageUtil.computeDHash(body.byteStream()) ?: return null
            }

            // Step 3: compare.
            val areSame = ImageUtil.dHashDistance(oldHash, newHash) <= ImageUtil.COVER_DHASH_THRESHOLD
            Pair(areSame, newHash)
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "Cover hash comparison failed for $newCoverUrl — falling back to full update" }
            null
        }
    }
}
