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
fun initializeExtensionBridge(context: Context) {
    val appContext = context.applicationContext
    val entryPoint = EntryPointAccessors.fromApplication(
        appContext,
        ExtensionBridgeEntryPoint::class.java,
    )

    // Initialize canonical Injekt
    patchInjekt()

    // Register with official Injekt for dynamic extensions
    Injekt.addSingleton<Context>(appContext)
    Injekt.addSingleton<Application>(appContext as Application)
    Injekt.addSingletonFactory<SharedPreferences> {
        PreferenceManager.getDefaultSharedPreferences(appContext)
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

@Deprecated("Use initializeExtensionBridge instead.", ReplaceWith("initializeExtensionBridge(context)"))
fun initializeCoreContainer(context: Context) {
    initializeExtensionBridge(context)
}
