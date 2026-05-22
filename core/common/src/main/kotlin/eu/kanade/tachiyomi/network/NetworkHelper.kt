package eu.kanade.tachiyomi.network

import android.content.Context
import ephyra.core.common.util.system.DeviceUtil
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.IgnoreGzipInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

class NetworkHelper(
    private val context: Context,
    private val preferences: NetworkPreferences,
) {

    val cookieJar = AndroidCookieJar()

    private val clientBuilder: OkHttpClient.Builder = run {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .cache(
                Cache(
                    directory = File(context.cacheDir, "network_cache"),
                    // Scale the HTTP-level response cache with device capability so that
                    // source API responses carrying Cache-Control headers (page lists, image
                    // URL resolvers, etc.) survive longer in memory and avoid repeat network
                    // round-trips within a reading session.
                    maxSize = when (DeviceUtil.performanceTier(context)) {
                        DeviceUtil.PerformanceTier.LOW -> 5L * 1024 * 1024 // 5 MiB
                        DeviceUtil.PerformanceTier.MEDIUM -> 20L * 1024 * 1024 // 20 MiB
                        DeviceUtil.PerformanceTier.HIGH -> 50L * 1024 * 1024 // 50 MiB
                    },
                ),
            )
            .addInterceptor(UncaughtExceptionInterceptor())
            .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))
            .addNetworkInterceptor(IgnoreGzipInterceptor())
            .addNetworkInterceptor(BrotliInterceptor)

        // runBlocking is justified here: NetworkHelper is constructed once at app startup
        // before any user interaction, so there is no risk of an ANR from waiting on
        // DataStore's in-memory-cached preferences snapshot.
        if (preferences.verboseLogging().getSync()) {
            val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addNetworkInterceptor(httpLoggingInterceptor)
        }

        val dohProviderId = preferences.dohProvider().getSync()
        val dohProvider = dohProviders.find { it.id == dohProviderId }
        if (dohProvider != null) {
            builder.doh(dohProvider)
        } else {
            builder
        }
    }

    val nonCloudflareClient = clientBuilder.build()

    val client = clientBuilder
        .addInterceptor(
            CloudflareInterceptor(context, cookieJar, ::defaultUserAgentProvider),
        )
        .build()

    fun defaultUserAgentProvider() = preferences.defaultUserAgent().getSync().trim()
}
