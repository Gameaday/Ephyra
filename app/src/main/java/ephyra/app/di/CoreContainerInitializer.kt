package ephyra.app.di

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import ephyra.core.common.di.CoreContainer

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScreenEntryPoint {
    // Singletons
    fun basePreferences(): ephyra.domain.base.BasePreferences
    fun coverCache(): ephyra.data.cache.CoverCache
    fun sourceManager(): ephyra.domain.source.service.SourceManager
    fun networkHelper(): eu.kanade.tachiyomi.network.NetworkHelper
    fun libraryPreferences(): ephyra.domain.library.service.LibraryPreferences
    fun sourcePreferences(): ephyra.domain.source.service.SourcePreferences
    fun extensionRepoRepository(): ephyra.domain.extensionrepo.repository.ExtensionRepoRepository

    // Preferences needed for migration & core
    fun backupPreferences(): ephyra.domain.backup.service.BackupPreferences
    fun storagePreferences(): ephyra.domain.storage.service.StoragePreferences
    fun downloadPreferences(): ephyra.domain.download.service.DownloadPreferences
    fun getCategories(): ephyra.domain.category.interactor.GetCategories
    fun preferenceStore(): ephyra.core.common.preference.PreferenceStore

    // Serialization
    fun json(): kotlinx.serialization.json.Json
    fun xml(): nl.adaptivity.xmlutil.serialization.XML
}

@Deprecated("Use standard Hilt injection or Hilt EntryPoints instead. Retained strictly for legacy extension bridge.")
fun initializeCoreContainer(context: Context) {
    CoreContainer.init(context)
    val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, ScreenEntryPoint::class.java)

    // Dynamic fallback provider using ScreenEntryPoint reflection as a safety net
    CoreContainer.setFallbackProvider { requestedClass ->
        try {
            val matchingMethod = ScreenEntryPoint::class.java.methods.firstOrNull { method ->
                method.parameterTypes.isEmpty() && requestedClass.isAssignableFrom(method.returnType)
            }
            matchingMethod?.invoke(entryPoint)
        } catch (e: Throwable) {
            android.util.Log.w("CoreContainer", "Dynamic fallback failed for ${requestedClass.name}", e)
            null
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
