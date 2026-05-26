package ephyra.app.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import ephyra.core.common.util.system.dpToPx
import ephyra.core.common.util.system.logcat
import ephyra.domain.manga.model.MangaCover
import ephyra.domain.updates.interactor.GetUpdates
import logcat.LogPriority
import java.time.Instant
import java.time.ZonedDateTime

class WidgetUpdatesJob(
    private val context: Context,
    workerParams: WorkerParameters,
    private val getUpdates: GetUpdates,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        logcat(LogPriority.INFO) { "Starting background pre-caching of widget cover bitmaps..." }
        try {
            val dateLimit = ZonedDateTime.now().minusMonths(3).toInstant().toEpochMilli()
            val updates = getUpdates.await(false, dateLimit)

            // Standard widget dimensions: 58.dp x 87.dp
            val widthPx = 58.dpToPx
            val heightPx = 87.dpToPx

            // Distinct cover pre-caching requests
            updates
                .distinctBy { it.mangaId }
                .take(30)
                .forEach { updatesView ->
                    val request = ImageRequest.Builder(context)
                        .data(
                            MangaCover(
                                mangaId = updatesView.mangaId,
                                sourceId = updatesView.sourceId,
                                isMangaFavorite = true,
                                url = updatesView.coverData.url,
                                lastModified = updatesView.coverData.lastModified,
                            ),
                        )
                        .precision(Precision.EXACT)
                        .size(widthPx, heightPx)
                        .scale(Scale.FILL)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build()

                    context.imageLoader.execute(request)
                }

            // Force widget refresh reflectively to avoid direct Glance dependency in :app compile classpath
            try {
                val managerClass = Class.forName("androidx.glance.appwidget.GlanceAppWidgetManager")
                val managerInstance = managerClass.getConstructor(Context::class.java).newInstance(context)
                val getGlanceIdsMethod = managerClass.getMethod("getGlanceIds", Class::class.java)

                val widget1Class = Class.forName("ephyra.presentation.widget.UpdatesGridGlanceWidget")
                val widget2Class = Class.forName("ephyra.presentation.widget.UpdatesGridCoverScreenGlanceWidget")
                val widget1Instance = widget1Class.getConstructor().newInstance()
                val widget2Instance = widget2Class.getConstructor().newInstance()

                val ids1 = getGlanceIdsMethod.invoke(managerInstance, widget1Class) as List<*>
                val ids2 = getGlanceIdsMethod.invoke(managerInstance, widget2Class) as List<*>

                val updateMethod = Class.forName("androidx.glance.appwidget.GlanceAppWidget")
                    .getMethod("update", Context::class.java, Class.forName("androidx.glance.GlanceId"))

                ids1.forEach { id -> updateMethod.invoke(widget1Instance, context, id) }
                ids2.forEach { id -> updateMethod.invoke(widget2Instance, context, id) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to trigger widget updates reflectively" }
            }

            logcat(LogPriority.INFO) { "Widget cover pre-caching completed successfully." }
            return Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to pre-cache widget cover bitmaps" }
            return Result.retry()
        }
    }
}
