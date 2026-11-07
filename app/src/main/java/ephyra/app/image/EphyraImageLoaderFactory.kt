package ephyra.app.image

import android.content.Context
import android.graphics.Bitmap
import coil3.disk.DiskCache
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.memoryCacheMaxSizePercentWhileInBackground
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.util.DebugLogger
import coil3.ImageLoader
import ephyra.core.common.util.system.DeviceUtil
import ephyra.data.cache.CoverCache
import ephyra.data.coil.BufferedSourceFetcher
import ephyra.data.coil.HardwareGuardDecoder
import ephyra.data.coil.JxlCoilBridgeDecoder
import ephyra.data.coil.MangaCoverFetcher
import ephyra.data.coil.MangaCoverKeyer
import ephyra.data.coil.MangaKeyer
import ephyra.domain.source.service.SourceManager
import ephyra.presentation.core.util.system.animatorDurationScale
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toOkioPath
import java.io.File
import javax.inject.Provider

/**
 * Builds the application [ImageLoader] — everything that used to live in `App.newImageLoader`,
 * split out by B5 (`OPS-001`) so `App` is startup coordination only.
 *
 * The configuration is moved verbatim: the decoder chain, the cover fetchers keyed through
 * [CoverCache], the tiered memory/disk caches by [DeviceUtil.performanceTier], and the
 * fetch/decode parallelism caps. None of it changed with the move; see the comments inside
 * [create] for the reasoning each entry carries.
 *
 * @param app the application, for the performance tier, the animator scale and the cache
 * directory.
 * @param isVerboseLogging read once at [create] time — exactly when `App` used to read its own
 * field at build time — so the preference read keeps its original timing.
 */
class EphyraImageLoaderFactory(
    private val app: Context,
    private val networkHelper: NetworkHelper,
    private val coverCache: CoverCache,
    private val sourceManagerProvider: Provider<SourceManager>,
    private val isVerboseLogging: () -> Boolean,
) {

    /** Builds the singleton loader Coil will use for the whole process. */
    fun create(context: Context): ImageLoader {
        return ImageLoader.Builder(app).apply {
            val callFactoryLazy = lazy { networkHelper.client }
            components {
                // NetworkFetcher.Factory
                add(OkHttpNetworkFetcherFactory(callFactoryLazy::value))
                // Decoder.Factory — order matters; Coil tries factories in registration order:
                // 1. JXL bridge (awxkee jxl-coder 2.2.0 ships Coil 2; bridged into this Coil 3
                //    pipeline): static JXL, which the platform cannot decode. libjxl applies
                //    the embedded color profile internally, so the retired custom ICC
                //    display-profile override is gone by design.
                // 2. Platform animated drawables (GIF/WebP via coil-gif + ImageDecoderDecoder).
                // 3. HardwareGuard: wraps BitmapFactoryDecoder and downgrades oversized
                //    long-strip sources to software decoding instead of failing the
                //    hardware (AHardwareBuffer) decode at GL_MAX_TEXTURE_SIZE.
                //    Oversized JXL is guarded inside the bridge (decode-time downgrade)
                //    because Coil 2/3 decoder types cannot share one guard factory.
                add(JxlCoilBridgeDecoder.Factory())
                add(GifDecoder.Factory())
                add(HardwareGuardDecoder.Factory())
                // Fetcher.Factory
                add(BufferedSourceFetcher.Factory())
                val sourceMgr = sourceManagerProvider.get()
                add(MangaCoverFetcher.MangaCoverFactory(callFactoryLazy, coverCache, sourceMgr))
                add(MangaCoverFetcher.MangaFactory(callFactoryLazy, coverCache, sourceMgr))
                // Keyer
                add(MangaCoverKeyer(coverCache))
                add(MangaKeyer(coverCache))
            }

            memoryCache(
                MemoryCache.Builder()
                    // Tiered like the chapter/disk caches: on high-RAM devices (S24 class)
                    // a larger decoded-bitmap working set means library/updates grids
                    // recompose from memory instead of re-decoding from disk, which is
                    // where scroll jitter at 120Hz actually comes from. LOW keeps Coil's
                    // conservative default so memory pressure decides eviction there.
                    .maxSizePercent(
                        context,
                        when (DeviceUtil.performanceTier(app)) {
                            DeviceUtil.PerformanceTier.LOW -> 0.20
                            DeviceUtil.PerformanceTier.MEDIUM -> 0.25
                            DeviceUtil.PerformanceTier.HIGH -> 0.30
                        },
                    )
                    .build(),
            )
            // Coil 3 lifecycle-aware background trimming: when the app moves to the
            // background, the memory cache is evicted down to 10% of its capacity and
            // grown back on demand once resumed. Dropping the working set this aggressively
            // yields graphics memory to the rest of the system; the entries that are
            // discarded are re-decoded cheaply on return, which is the right trade while the
            // reader is not on screen. This replaces the old manual onTrimMemory
            // "clear everything on UI hidden" behaviour.
            memoryCacheMaxSizePercentWhileInBackground(0.1)

            diskCache {
                DiskCache.Builder()
                    .directory(File(app.cacheDir, "image_cache").toOkioPath())
                    .maxSizeBytes(
                        when (DeviceUtil.performanceTier(app)) {
                            DeviceUtil.PerformanceTier.LOW -> 50L * 1024 * 1024 // 50 MiB
                            DeviceUtil.PerformanceTier.MEDIUM -> 150L * 1024 * 1024 // 150 MiB
                            DeviceUtil.PerformanceTier.HIGH -> 300L * 1024 * 1024 // 300 MiB
                        },
                    )
                    .build()
            }

            crossfade((300 * app.animatorDurationScale).toInt())
            allowRgb565(false)
            // GPU-resident hardware bitmaps are the global default. Pages and covers land in
            // graphics memory (AHardwareBuffer) instead of the native heap, which removes the
            // CPU to GPU upload on every render frame and keeps large chapters off the Java
            // heap entirely. HardwareGuardDecoder routes long-strip pages that would exceed
            // GL_MAX_TEXTURE_SIZE to software decoding, so the texture ceiling is still
            // respected without a per-device configuration switch.
            bitmapConfig(Bitmap.Config.HARDWARE)
            if (isVerboseLogging()) logger(DebugLogger())

            // Coil spawns a new thread for every image load by default
            fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(8))
            decoderCoroutineContext(Dispatchers.IO.limitedParallelism(3))
        }
            .build()
    }
}
