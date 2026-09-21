package ephyra.app.di

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.app.App
import ephyra.app.startup.ShadowAnimatedVectorResources
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.serialization.XML
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [36],
    application = App::class,
    shadows = [ShadowAnimatedVectorResources::class],
)
class ExtensionBridgeContractTest {

    @Test
    fun verifyExtensionBridgeIsInitialized() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertNotNull("Application context should be constructed", app)
        val context = Injekt.get<Context>()
        assertEquals("Injekt Context must match application", app, context)
        val application = Injekt.get<Application>()
        assertEquals("Injekt Application must match application", app, application)
    }

    @Test
    fun verifyEssentialExtensionDependenciesAreResolvable() {
        // Network stack
        val networkHelper = Injekt.get<NetworkHelper>()
        assertNotNull("NetworkHelper must be resolvable from Injekt", networkHelper)
        assertTrue(
            "IgnoreGzipInterceptor must not be present in default client (extension ABI requirement)",
            networkHelper.client.networkInterceptors.none {
                it is eu.kanade.tachiyomi.network.interceptor.IgnoreGzipInterceptor
            },
        )

        val okHttpClient = Injekt.get<OkHttpClient>()
        assertNotNull("OkHttpClient must be resolvable from Injekt", okHttpClient)

        // Context / Application
        val context = Injekt.get<Context>()
        assertNotNull("Context must be resolvable from Injekt", context)

        val application = Injekt.get<Application>()
        assertNotNull("Application must be resolvable from Injekt", application)

        // Serialization
        val json = Injekt.get<Json>()
        assertNotNull("Json serializer must be resolvable from Injekt", json)

        val xml = Injekt.get<XML>()
        assertNotNull("XML serializer must be resolvable from Injekt", xml)
    }

    @Test
    fun verifyConcurrentResolutionDoesNotThrowOrDeadlock() = runBlocking {
        val threadPool = Executors.newFixedThreadPool(8)
        val deferreds = (1..50).map {
            async(Dispatchers.IO) {
                val helper = Injekt.get<NetworkHelper>()
                val client = Injekt.get<OkHttpClient>()
                val ctx = Injekt.get<Context>()
                helper != null && client != null && ctx != null
            }
        }

        val results = deferreds.awaitAll()
        assertTrue("All concurrent resolutions must succeed", results.all { it })
        threadPool.shutdown()
    }
}
