package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import eu.kanade.tachiyomi.util.system.toShareIntent
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import okhttp3.Request
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaCoverScreenModel(
    private val mangaId: Long,
    private val getManga: GetManga = Injekt.get(),
    private val imageSaver: ImageSaver = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),

    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<Manga?>(null) {

    init {
        screenModelScope.launchIO {
            getManga.subscribe(mangaId)
                .collect { newManga -> mutableState.update { newManga } }
        }
    }

    fun saveCover(context: Context) {
        screenModelScope.launch {
            try {
                saveCoverInternal(context, temp = false)
                snackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.cover_saved),
                    withDismissAction = true,
                )
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                snackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.error_saving_cover),
                    withDismissAction = true,
                )
            }
        }
    }

    fun shareCover(context: Context) {
        screenModelScope.launch {
            try {
                val uri = saveCoverInternal(context, temp = true) ?: return@launch
                withUIContext {
                    context.startActivity(uri.toShareIntent(context))
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                snackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.error_sharing_cover),
                    withDismissAction = true,
                )
            }
        }
    }

    /**
     * Save manga cover Bitmap to picture or temporary share directory.
     * Respects the user's cover quality preference for the output format.
     *
     * Note: This path always goes through Coil (bitmap decode), so the original
     * source bytes are not available. For Original/Lossless modes we use WebP
     * lossless to preserve full fidelity; for Balanced we use WebP lossy.
     *
     * @param context The context for building and executing the ImageRequest
     * @return the uri to saved file
     */
    private suspend fun saveCoverInternal(context: Context, temp: Boolean): Uri? {
        val manga = state.value ?: return null
        val req = ImageRequest.Builder(context)
            .data(manga)
            .size(Size.ORIGINAL)
            .build()

        val libraryPreferences: LibraryPreferences = Injekt.get()
        val coverQuality = libraryPreferences.coverQuality().get()
        val (format, quality) = when (coverQuality) {
            LibraryPreferences.CoverQuality.Original,
            LibraryPreferences.CoverQuality.Lossless,
            -> {
                android.graphics.Bitmap.CompressFormat.WEBP_LOSSLESS to 100
            }
            LibraryPreferences.CoverQuality.Balanced -> {
                android.graphics.Bitmap.CompressFormat.WEBP_LOSSY to 90
            }
        }

        return withIOContext {
            val result = context.imageLoader.execute(req).image?.asDrawable(context.resources)

            // TODO: Handle animated cover
            val bitmap = result?.getBitmapOrNull() ?: return@withIOContext null
            imageSaver.save(
                Image.Cover(
                    bitmap = bitmap,
                    name = manga.title,
                    location = if (temp) Location.Cache else Location.Pictures.create(),
                    compressFormat = format,
                    compressQuality = quality,
                ),
            )
        }
    }

    /**
     * Update cover with local file.
     *
     * @param context Context.
     * @param data uri of the cover resource.
     */
    fun editCover(context: Context, data: Uri) {
        val manga = state.value ?: return
        screenModelScope.launchIO {
            context.contentResolver.openInputStream(data)?.use {
                try {
                    manga.editCover(Injekt.get(), it, updateManga, coverCache)
                    notifyCoverUpdated(context)
                } catch (e: Exception) {
                    notifyFailedCoverUpdate(context, e)
                }
            }
        }
    }

    fun deleteCustomCover(context: Context) {
        val mangaId = state.value?.id ?: return
        screenModelScope.launchIO {
            try {
                coverCache.deleteCustomCover(mangaId)
                updateManga.awaitUpdateCoverLastModified(mangaId)
                notifyCoverUpdated(context)
            } catch (e: Exception) {
                notifyFailedCoverUpdate(context, e)
            }
        }
    }

    /**
     * Set cover from a URL by downloading the image and saving it as a custom cover.
     * Respects the user's cover quality preference:
     * - Original: streams raw bytes directly, preserving the source format byte-for-byte.
     * - Lossless: decodes and re-encodes as WebP lossless (identical quality, smaller file).
     * - Balanced: decodes and re-encodes as WebP lossy at high quality (smallest file).
     *
     * @param context Context.
     * @param coverUrl URL of the cover image to download.
     * @param sourceId ID of the source (unused, kept for API compatibility).
     */
    fun setCoverFromUrl(context: Context, coverUrl: String, @Suppress("UNUSED_PARAMETER") sourceId: Long) {
        val manga = state.value ?: return
        screenModelScope.launchIO {
            try {
                val networkHelper: NetworkHelper = Injekt.get()
                val libraryPreferences: LibraryPreferences = Injekt.get()
                val coverQuality = libraryPreferences.coverQuality().get()
                val request = Request.Builder().url(coverUrl).build()
                val response = networkHelper.client.newCall(request).await()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("Failed to download cover: ${resp.code}")
                    }
                    when (coverQuality) {
                        LibraryPreferences.CoverQuality.Original -> {
                            resp.body.byteStream().use { input ->
                                manga.editCover(Injekt.get(), input, updateManga, coverCache)
                            }
                        }
                        LibraryPreferences.CoverQuality.Lossless,
                        LibraryPreferences.CoverQuality.Balanced,
                        -> {
                            val bytes = resp.body.bytes()
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                ?: throw IllegalStateException("Failed to decode cover image")
                            val format = android.graphics.Bitmap.CompressFormat.WEBP_LOSSLESS
                                .takeIf { coverQuality == LibraryPreferences.CoverQuality.Lossless }
                                ?: android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
                            val quality = if (coverQuality == LibraryPreferences.CoverQuality.Lossless) 100 else 90
                            val buffer = okio.Buffer()
                            bitmap.compress(format, quality, buffer.outputStream())
                            bitmap.recycle()
                            buffer.inputStream().use { input ->
                                manga.editCover(Injekt.get(), input, updateManga, coverCache)
                            }
                        }
                    }
                }
                notifyCoverUpdated(context)
            } catch (e: Exception) {
                notifyFailedCoverUpdate(context, e)
            }
        }
    }

    private fun notifyCoverUpdated(context: Context) {
        screenModelScope.launch {
            snackbarHostState.showSnackbar(
                context.stringResource(MR.strings.cover_updated),
                withDismissAction = true,
            )
        }
    }

    private fun notifyFailedCoverUpdate(context: Context, e: Throwable) {
        screenModelScope.launch {
            snackbarHostState.showSnackbar(
                context.stringResource(MR.strings.notification_cover_update_failed),
                withDismissAction = true,
            )
            logcat(LogPriority.ERROR, e)
        }
    }
}
