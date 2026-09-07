package ephyra.app.di

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import ephyra.core.common.di.CoreContainer

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ExtensionBridgeEntryPoint {
    // Singletons for legacy extension bridge
    fun basePreferences(): ephyra.domain.base.BasePreferences
    fun coverCache(): ephyra.data.cache.CoverCache
    fun sourceManager(): ephyra.domain.source.service.SourceManager
    fun networkHelper(): eu.kanade.tachiyomi.network.NetworkHelper
    fun preferenceStore(): ephyra.core.common.preference.PreferenceStore

    // Serialization
    fun json(): kotlinx.serialization.json.Json
    fun xml(): nl.adaptivity.xmlutil.serialization.XML
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
            eu.kanade.tachiyomi.network.NetworkHelper::class.java -> entryPoint.networkHelper()
            okhttp3.OkHttpClient::class.java -> entryPoint.networkHelper().client
            ephyra.core.common.preference.PreferenceStore::class.java -> entryPoint.preferenceStore()
            ephyra.domain.base.BasePreferences::class.java -> entryPoint.basePreferences()
            ephyra.data.cache.CoverCache::class.java -> entryPoint.coverCache()
            ephyra.domain.source.service.SourceManager::class.java -> entryPoint.sourceManager()
            kotlinx.serialization.json.Json::class.java -> entryPoint.json()
            nl.adaptivity.xmlutil.serialization.XML::class.java -> entryPoint.xml()
            else -> null
        }
    }

    // Singletons & Legacy Extension Bridge
    CoreContainer.register(Context::class.java) { CoreContainer.applicationContext }
    CoreContainer.register(android.app.Application::class.java) {
        CoreContainer.applicationContext as android.app.Application
    }
    CoreContainer.register(eu.kanade.tachiyomi.network.NetworkHelper::class.java) { entryPoint.networkHelper() }
    CoreContainer.register(okhttp3.OkHttpClient::class.java) { entryPoint.networkHelper().client }
    CoreContainer.register(ephyra.core.common.preference.PreferenceStore::class.java) { entryPoint.preferenceStore() }
    CoreContainer.register(ephyra.domain.base.BasePreferences::class.java) { entryPoint.basePreferences() }
    CoreContainer.register(ephyra.data.cache.CoverCache::class.java) { entryPoint.coverCache() }
    CoreContainer.register(ephyra.domain.source.service.SourceManager::class.java) { entryPoint.sourceManager() }
    CoreContainer.register(kotlinx.serialization.json.Json::class.java) { entryPoint.json() }
    CoreContainer.register(nl.adaptivity.xmlutil.serialization.XML::class.java) { entryPoint.xml() }
}
