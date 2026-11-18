package ephyra.feature.manga

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.di.IoDispatcher
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.saver.Image
import ephyra.core.common.saver.ImageSaver
import ephyra.core.common.saver.Location
import ephyra.core.common.util.system.logcat
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.service.CoverCache
import ephyra.domain.source.service.SourceManager
import ephyra.presentation.core.udf.BaseUdfViewModel
import ephyra.presentation.core.util.manga.editCover
import ephyra.presentation.core.util.system.encoder
import ephyra.presentation.core.util.system.getBitmapOrNull
import ephyra.source.local.image.LocalCoverManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import okhttp3.Request
import javax.inject.Inject

@HiltViewModel
class MangaCoverViewModel @Inject constructor(
    private val getManga: GetManga,
    private val imageSaver: ImageSaver,
    private val coverCache: CoverCache,
    private val updateManga: UpdateManga,
    private val libraryPreferences: LibraryPreferences,
    private val sourceManager: SourceManager,
    private val networkHelper: NetworkHelper,
    private val application: Application,
    private val localCoverManager: LocalCoverManager,
    // Injectable so a test can drive this on its own scheduler. `launchIO`/`withIOContext` are
    // `Dispatchers.IO`, a real thread pool that `Dispatchers.setMain` and `advanceUntilIdle()`
    // cannot reach, so a test could only race it or sleep. Production is unchanged:
    // `@IoDispatcher` binds `Dispatchers.IO`. See `InjectableViewModelDispatcherTest`.
    @IoDispatcher
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BaseUdfViewModel<Manga?, MangaCoverScreenEvent, MangaCoverEffect>(null) {

    private var isInitialized = false

    fun init(mangaId: Long) {
        if (isInitialized) return
        isInitialized = true

        viewModelScope.launch(ioDispatcher) {
            getManga.subscribe(mangaId)
                .collect { newManga -> updateState { newManga } }
        }
    }

    override fun onEvent(event: MangaCoverScreenEvent) {
        when (event) {
            is MangaCoverScreenEvent.SaveCover -> saveCover()
            is MangaCoverScreenEvent.ShareCover -> shareCover()
            is MangaCoverScreenEvent.EditCover -> editCover(event.data)
            is MangaCoverScreenEvent.DeleteCustomCover -> deleteCustomCover()
            is MangaCoverScreenEvent.SetCoverFromUrl -> setCoverFromUrl(event.coverUrl, event.sourceId)
        }
    }

    private fun saveCover() {
        viewModelScope.launch {
            try {
                saveCoverInternal(temp = false)
                emitEffect(
                    MangaCoverEffect.ShowSnackbar(
                        application.stringResource(ephyra.app.core.common.R.string.cover_saved),
                    ),
                )
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                emitEffect(
                    MangaCoverEffect.ShowSnackbar(
                        application.stringResource(ephyra.app.core.common.R.string.error_saving_cover),
                    ),
                )
            }
        }
    }

    private fun shareCover() {
        viewModelScope.launch {
            try {
                val uri = saveCoverInternal(temp = true) ?: return@launch
                // Emit effect â€” the UI layer handles startActivity with Activity context
                emitEffect(MangaCoverEffect.StartShare(uri))
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                emitEffect(
                    MangaCoverEffect.ShowSnackbar(
                        application.stringResource(ephyra.app.core.common.R.string.error_sharing_cover),
                    ),
                )
            }
        }
    }

    private suspend fun saveCoverInternal(temp: Boolean): Uri? {
        val manga = state.value ?: return null
        val req = ImageRequest.Builder(application)
            .data(manga)
            .size(Size.ORIGINAL)
            .build()

        // Shares always use PNG for compatibility; internal saves use user's preferred format
        val encoder = if (temp) {
            { bmp: android.graphics.Bitmap, os: java.io.OutputStream ->
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, os)
                Unit
            }
        } else {
            libraryPreferences.imageFormat().get().encoder()
        }

        return withContext(ioDispatcher) {
            val result = application.imageLoader.execute(req).image?.asDrawable(application.resources)

            // TODO: Handle animated cover
            val bitmap = result?.getBitmapOrNull() ?: return@withContext null
            imageSaver.save(
                Image.Cover(
                    bitmap = bitmap,
                    name = manga.title,
                    location = if (temp) Location.Cache else Location.Pictures.create(),
                    encoder = encoder,
                ),
            )
        }
    }

    private fun editCover(data: Uri) {
        val manga = state.value ?: return
        viewModelScope.launch(ioDispatcher) {
            application.contentResolver.openInputStream(data)?.use {
                try {
                    manga.editCover(localCoverManager, it, updateManga, coverCache)
                    notifyCoverUpdated()
                } catch (e: Exception) {
                    notifyFailedCoverUpdate(e)
                }
            }
        }
    }

    private fun deleteCustomCover() {
        val id = state.value?.id ?: return
        viewModelScope.launch(ioDispatcher) {
            try {
                coverCache.deleteCustomCover(id)
                updateManga.awaitUpdateCoverLastModified(id)
                notifyCoverUpdated()
            } catch (e: Exception) {
                notifyFailedCoverUpdate(e)
            }
        }
    }

    private fun setCoverFromUrl(coverUrl: String, sourceId: Long) {
        val manga = state.value ?: return
        viewModelScope.launch(ioDispatcher) {
            try {
                val source = sourceManager.get(sourceId)
                val httpSource = source as? HttpSource
                val client = httpSource?.client ?: networkHelper.client
                val request = Request.Builder()
                    .url(coverUrl)
                    .apply { httpSource?.headers?.let { headers(it) } }
                    .build()
                val response = client.newCall(request).await()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("Failed to download cover: ${resp.code}")
                    }
                    resp.body.byteStream().use { input ->
                        manga.editCover(localCoverManager, input, updateManga, coverCache)
                    }
                }
                notifyCoverUpdated()
            } catch (e: Exception) {
                notifyFailedCoverUpdate(e)
            }
        }
    }

    private fun notifyCoverUpdated() {
        viewModelScope.launch {
            emitEffect(
                MangaCoverEffect.ShowSnackbar(
                    application.stringResource(ephyra.app.core.common.R.string.cover_updated),
                ),
            )
        }
    }

    private fun notifyFailedCoverUpdate(e: Throwable) {
        viewModelScope.launch {
            emitEffect(
                MangaCoverEffect.ShowSnackbar(
                    application.stringResource(ephyra.app.core.common.R.string.notification_cover_update_failed),
                ),
            )
            logcat(LogPriority.ERROR, e)
        }
    }
}
