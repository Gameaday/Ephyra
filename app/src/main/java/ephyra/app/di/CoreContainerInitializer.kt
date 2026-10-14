package ephyra.app.di

import android.app.Application
import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.mihon.injekt.patchInjekt
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import nl.adaptivity.xmlutil.serialization.XML
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ExtensionBridgeEntryPoint {
    // Singletons for the legacy extension bridge
    fun networkHelper(): NetworkHelper

    // Serialization
    fun json(): Json
    fun xml(): XML
    fun protoBuf(): ProtoBuf
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

    // The extension ABI deliberately exposes a minimal set: exactly what a source needs and nothing
    // else, mirroring upstream Mihon's restriction. Internal singletons — CoverCache, SourceManager,
    // PreferenceStore, BasePreferences, SharedPreferences and OkHttpClient — are intentionally NOT
    // registered, so an extension cannot reach app internals it has no business touching.
    //
    // `JavaScriptEngine` is part of upstream's allowlist but does not exist in Ephyra yet; an
    // extension that resolves it will fail to load. That is a known, recorded divergence — see
    // doc/UPSTREAM_MIHON_0_21_1_ADOPTION_PLAN.md (WS2).
    Injekt.addSingleton<Context>(appContext)
    Injekt.addSingleton<Application>(appContext as Application)
    Injekt.addSingletonFactory<NetworkHelper> { entryPoint.networkHelper() }
    Injekt.addSingletonFactory<Json> { entryPoint.json() }
    Injekt.addSingletonFactory<XML> { entryPoint.xml() }
    Injekt.addSingletonFactory<ProtoBuf> { entryPoint.protoBuf() }
}

@Deprecated("Use initializeExtensionBridge instead.", ReplaceWith("initializeExtensionBridge(context)"))
fun initializeCoreContainer(context: Context) {
    initializeExtensionBridge(context)
}
