package ephyra.app.di

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.mihon.injekt.patchInjekt
import ephyra.core.common.di.CoreContainer
import ephyra.core.common.preference.PreferenceStore
import ephyra.data.cache.CoverCache
import ephyra.domain.base.BasePreferences
import ephyra.domain.source.service.SourceManager
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.serialization.XML
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ExtensionBridgeEntryPoint {
    // Singletons for legacy extension bridge
    fun basePreferences(): BasePreferences
    fun coverCache(): CoverCache
    fun sourceManager(): SourceManager
    fun networkHelper(): NetworkHelper
    fun preferenceStore(): PreferenceStore

    // Serialization
    fun json(): Json
    fun xml(): XML
}

@Deprecated("Use ExtensionBridgeEntryPoint instead.", ReplaceWith("ExtensionBridgeEntryPoint"))
typealias ScreenEntryPoint = ExtensionBridgeEntryPoint

@Deprecated("Use standard Hilt injection or Hilt EntryPoints instead. Retained strictly for legacy extension bridge.")
fun initializeCoreContainer(context: Context) {
    CoreContainer.init(context)
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        ExtensionBridgeEntryPoint::class.java,
    )

    // Direct type-safe fallback provider for legacy extension bridge (no reflection)
    CoreContainer.setFallbackProvider { requestedClass ->
        when (requestedClass) {
            NetworkHelper::class.java -> entryPoint.networkHelper()
            OkHttpClient::class.java -> entryPoint.networkHelper().client
            PreferenceStore::class.java -> entryPoint.preferenceStore()
            BasePreferences::class.java -> entryPoint.basePreferences()
            CoverCache::class.java -> entryPoint.coverCache()
            SourceManager::class.java -> entryPoint.sourceManager()
            Json::class.java -> entryPoint.json()
            XML::class.java -> entryPoint.xml()
            SharedPreferences::class.java ->
                PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            else -> null
        }
    }

    // Initialize canonical Injekt
    patchInjekt()

    // Singletons & Legacy Extension Bridge
    CoreContainer.register(Context::class.java) { CoreContainer.applicationContext }
    CoreContainer.register(Application::class.java) {
        CoreContainer.applicationContext as Application
    }
    CoreContainer.register(SharedPreferences::class.java) {
        PreferenceManager.getDefaultSharedPreferences(CoreContainer.applicationContext)
    }
    CoreContainer.register(NetworkHelper::class.java) { entryPoint.networkHelper() }
    CoreContainer.register(OkHttpClient::class.java) { entryPoint.networkHelper().client }
    CoreContainer.register(PreferenceStore::class.java) { entryPoint.preferenceStore() }
    CoreContainer.register(BasePreferences::class.java) { entryPoint.basePreferences() }
    CoreContainer.register(CoverCache::class.java) { entryPoint.coverCache() }
    CoreContainer.register(SourceManager::class.java) { entryPoint.sourceManager() }
    CoreContainer.register(Json::class.java) { entryPoint.json() }
    CoreContainer.register(XML::class.java) { entryPoint.xml() }

    // Register with official Injekt
    Injekt.addSingleton<Context>(CoreContainer.applicationContext)
    Injekt.addSingleton<Application>(CoreContainer.applicationContext as Application)
    Injekt.addSingletonFactory<SharedPreferences> {
        PreferenceManager.getDefaultSharedPreferences(CoreContainer.applicationContext)
    }
    Injekt.addSingletonFactory<NetworkHelper> { entryPoint.networkHelper() }
    Injekt.addSingletonFactory<OkHttpClient> { entryPoint.networkHelper().client }
    Injekt.addSingletonFactory<PreferenceStore> { entryPoint.preferenceStore() }
    Injekt.addSingletonFactory<BasePreferences> { entryPoint.basePreferences() }
    Injekt.addSingletonFactory<CoverCache> { entryPoint.coverCache() }
    Injekt.addSingletonFactory<SourceManager> { entryPoint.sourceManager() }
    Injekt.addSingletonFactory<Json> { entryPoint.json() }
    Injekt.addSingletonFactory<XML> { entryPoint.xml() }
}
