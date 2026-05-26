package ephyra.presentation.widget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.unit.ColorProvider
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import coil3.annotation.ExperimentalCoilApi
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import ephyra.core.common.core.security.SecurityPreferences
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.dpToPx
import ephyra.core.common.util.system.logcat
import ephyra.domain.manga.model.MangaCover
import ephyra.domain.updates.interactor.GetUpdates
import ephyra.domain.updates.model.UpdatesWithRelations
import ephyra.presentation.widget.components.CoverHeight
import ephyra.presentation.widget.components.CoverWidth
import ephyra.presentation.widget.components.LockedWidget
import ephyra.presentation.widget.components.UpdatesWidget
import ephyra.presentation.widget.util.appWidgetBackgroundRadius
import ephyra.presentation.widget.util.calculateRowAndColumnCount
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import logcat.LogPriority
import java.time.Instant
import java.time.ZonedDateTime

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun getUpdates(): GetUpdates
    fun securityPreferences(): SecurityPreferences
}

abstract class BaseUpdatesGridGlanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    abstract val foreground: ColorProvider
    abstract val background: ImageProvider
    abstract val topPadding: Dp
    abstract val bottomPadding: Dp

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as Application
        val entryPoint = EntryPointAccessors.fromApplication(app, WidgetEntryPoint::class.java)
        val getUpdates = entryPoint.getUpdates()
        val preferences = entryPoint.securityPreferences()

        val locked = preferences.useAuthenticator().get()
        val containerModifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .appWidgetBackground()
            .padding(top = topPadding, bottom = bottomPadding)
            .appWidgetBackgroundRadius()

        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(javaClass)
        val (rowCount, columnCount) = ids
            .flatMap { manager.getAppWidgetSizes(it) }
            .maxBy { it.height.value * it.width.value }
            .calculateRowAndColumnCount(topPadding, bottomPadding)

        provideContent {
            // If app lock enabled, don't do anything
            if (locked) {
                LockedWidget(
                    foreground = foreground,
                    modifier = containerModifier,
                )
                return@provideContent
            }

            val flow = remember {
                getUpdates
                    .subscribe(false, DateLimit.toEpochMilli())
                    .map { rawData ->
                        rawData.prepareData(app, rowCount, columnCount)
                    }
            }
            val data by flow.collectAsState(initial = null)
            UpdatesWidget(
                data = data,
                contentColor = foreground,
                topPadding = topPadding,
                bottomPadding = bottomPadding,
                modifier = containerModifier,
            )
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    private suspend fun List<UpdatesWithRelations>.prepareData(
        widgetContext: Context,
        rowCount: Int,
        columnCount: Int,
    ): ImmutableList<Pair<Long, Bitmap?>> {
        // Resize to cover size
        val widthPx = CoverWidth.value.toInt().dpToPx
        val heightPx = CoverHeight.value.toInt().dpToPx
        return withIOContext {
            this@prepareData
                .distinctBy { it.mangaId }
                .take(rowCount * columnCount)
                .map { updatesView ->
                    // Enforce local-cache only strategy for Glance rendering
                    val request = ImageRequest.Builder(widgetContext)
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
                        .networkCachePolicy(CachePolicy.DISABLED) // Do NOT download from network during composition
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build()

                    val result = widgetContext.imageLoader.execute(request).image
                    val bitmap = if (result != null) {
                        result.asDrawable(widgetContext.resources).toBitmap()
                    } else {
                        // Image is NOT yet pre-cached! Return null immediately (trigger default fallback placeholder)
                        // and delegate network download to the background worker to avoid blocking Glance composition.
                        try {
                            @Suppress("UNCHECKED_CAST")
                            val workClass = Class.forName(
                                "ephyra.app.data.work.WidgetUpdatesJob",
                            ) as Class<out ListenableWorker>
                            val workRequest = OneTimeWorkRequest.Builder(workClass).build()
                            WorkManager.getInstance(widgetContext).enqueue(workRequest)
                        } catch (e: Exception) {
                            logcat(LogPriority.WARN, e) { "Failed to enqueue WidgetUpdatesJob via reflection" }
                        }
                        null
                    }
                    Pair(updatesView.mangaId, bitmap)
                }
                .toImmutableList()
        }
    }

    companion object {
        val DateLimit: Instant
            get() = ZonedDateTime.now().minusMonths(3).toInstant()
    }
}
