package ephyra.app.di

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.app.App
import ephyra.app.startup.ShadowAnimatedVectorResources
import ephyra.core.common.preference.PreferenceStore
import ephyra.data.cache.CoverCache
import ephyra.domain.base.BasePreferences
import ephyra.domain.source.service.SourceManager
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import nl.adaptivity.xmlutil.serialization.XML
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektionException
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

        val protoBuf = Injekt.get<ProtoBuf>()
        assertNotNull("ProtoBuf serializer must be resolvable from Injekt", protoBuf)
    }

    /**
     * The extension ABI exposes a minimal set. Every type below is an app internal an extension has
     * no business reaching; if one becomes resolvable again, the surface has silently widened.
     *
     * Mirrors upstream Mihon, which restricts the container an extension sees to Application,
     * Context, Json, ProtoBuf, XML, NetworkHelper and JavaScriptEngine (see
     * doc/UPSTREAM_MIHON_0_21_1_ADOPTION_PLAN.md, WS2).
     */
    @Test
    fun verifyInternalSingletonsAreNotExposedToExtensions() {
        assertUnregistered<CoverCache>()
        assertUnregistered<SourceManager>()
        assertUnregistered<PreferenceStore>()
        assertUnregistered<BasePreferences>()
        assertUnregistered<SharedPreferences>()
        assertUnregistered<OkHttpClient>()
    }

    @Test
    fun verifyConcurrentResolutionDoesNotThrowOrDeadlock() = runBlocking {
        val threadPool = Executors.newFixedThreadPool(8)
        val deferreds = (1..50).map {
            async(Dispatchers.IO) {
                val helper = Injekt.get<NetworkHelper>()
                val json = Injekt.get<Json>()
                val ctx = Injekt.get<Context>()
                helper != null && json != null && ctx != null
            }
        }

        val results = deferreds.awaitAll()
        assertTrue("All concurrent resolutions must succeed", results.all { it })
        threadPool.shutdown()
    }

    private inline fun <reified T : Any> assertUnregistered() {
        // Resolving an unregistered type must throw, not silently return something.
        assertThrows(InjektionException::class.java) {
            Injekt.get<T>()
        }
    }
}
