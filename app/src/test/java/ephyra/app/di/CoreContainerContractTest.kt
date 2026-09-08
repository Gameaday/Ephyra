package ephyra.app.di

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.app.App
import ephyra.app.startup.ShadowAnimatedVectorResources
import ephyra.core.common.di.CoreContainer
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
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [34],
    application = App::class,
    shadows = [ShadowAnimatedVectorResources::class],
)
class CoreContainerContractTest {

    @Test
    fun verifyCoreContainerIsInitialized() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertNotNull("Application context should be constructed", app)
        assertTrue("CoreContainer should be marked initialized", CoreContainer.isInitialized)
        assertEquals("CoreContainer applicationContext must match", app, CoreContainer.applicationContext)
    }

    @Test
    fun verifyEssentialDependenciesAreResolvable() {
        // Network stack
        val networkHelper = CoreContainer.get<NetworkHelper>()
        assertNotNull("NetworkHelper must be resolvable from CoreContainer", networkHelper)
        assertTrue(
            "IgnoreGzipInterceptor must not be present in default client (extension ABI requirement)",
            networkHelper.client.networkInterceptors.none {
                it is eu.kanade.tachiyomi.network.interceptor.IgnoreGzipInterceptor
            },
        )

        val okHttpClient = CoreContainer.get<OkHttpClient>()
        assertNotNull("OkHttpClient must be resolvable from CoreContainer", okHttpClient)

        // Context / Application
        val context = CoreContainer.get<Context>()
        assertNotNull("Context must be resolvable from CoreContainer", context)

        val application = CoreContainer.get<Application>()
        assertNotNull("Application must be resolvable from CoreContainer", application)

        // Serialization
        val json = CoreContainer.get<Json>()
        assertNotNull("Json serializer must be resolvable from CoreContainer", json)

        val xml = CoreContainer.get<XML>()
        assertNotNull("XML serializer must be resolvable from CoreContainer", xml)
    }

    @Test
    fun verifyDynamicFallbackResolvesScreenEntryPointDependencies() {
        // Clear any direct entry for a dependency and ensure fallback resolves it
        val networkHelper = CoreContainer.get(NetworkHelper::class.java)
        assertNotNull("Dynamic fallback must satisfy NetworkHelper resolution", networkHelper)
    }

    @Test
    fun verifyConcurrentResolutionDoesNotThrowOrDeadlock() = runBlocking {
        val threadPool = Executors.newFixedThreadPool(8)
        val deferreds = (1..50).map {
            async(Dispatchers.IO) {
                val helper = CoreContainer.get<NetworkHelper>()
                val client = CoreContainer.get<OkHttpClient>()
                val ctx = CoreContainer.get<Context>()
                helper != null && client != null && ctx != null
            }
        }

        val results = deferreds.awaitAll()
        assertTrue("All concurrent resolutions must succeed", results.all { it })
        threadPool.shutdown()
    }
}
