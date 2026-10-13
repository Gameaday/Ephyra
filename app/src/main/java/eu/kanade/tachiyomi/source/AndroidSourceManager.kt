package eu.kanade.tachiyomi.source

import android.content.Context
import ephyra.app.extension.ExtensionManager
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.system.logcat
import ephyra.core.download.DownloadManager
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.SourceProfileCache
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.repository.StubSourceRepository
import ephyra.domain.source.service.SourceManager
import ephyra.source.local.LocalSource
import eu.kanade.tachiyomi.source.online.DynamicHttpSource
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import logcat.LogPriority
import nl.adaptivity.xmlutil.serialization.XML
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Provider

internal class AndroidSourceManager(
    private val context: Context,
    private val extensionManager: ExtensionManager,
    private val sourceRepository: StubSourceRepository,
    private val fileSystem: ephyra.source.local.io.LocalSourceFileSystem,
    private val coverManager: ephyra.source.local.image.LocalCoverManager,
    private val downloadManagerProvider: Provider<DownloadManager>,
    private val orchestratorProvider: Provider<ContentSourceOrchestrator>,
    private val profileCache: SourceProfileCache,
    private val preferenceStore: PreferenceStore,
    private val json: Json,
    private val xml: XML,
) : SourceManager {

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    private val sourcesMapFlow = MutableStateFlow(ConcurrentHashMap<Long, Source>())

    private val stubSourcesMap = ConcurrentHashMap<Long, StubSource>()

    override val catalogueSources: Flow<List<CatalogueSource>> = sourcesMapFlow.map {
        it.values.filterIsInstance<CatalogueSource>()
    }

    init {
        val heuristicProfilesFlow = preferenceStore.getStringSet("profiled_domains_list", emptySet())
            .changes()
            .map { domains ->
                domains.mapNotNull { domain ->
                    profileCache.get(domain)
                }
            }

        scope.launch {
            kotlinx.coroutines.flow.combine(
                // Collected (not just read) so the derived `installedExtensionsFlow` state stays
                // fresh for its `.value` readers (diagnostics, crash logs, search). The loaded set
                // itself is read from the authoritative map below, which updates synchronously.
                extensionManager.installedExtensionsFlow,
                heuristicProfilesFlow,
                extensionManager.isInitialized,
            ) { _, profiles, extensionsLoaded ->
                // Read the authoritative map synchronously instead of the value carried by the
                // combine emission: the derived list can lag a write, and this must be the loaded
                // set whenever `extensionsLoaded` is true.
                val extensions = extensionManager.installedExtensionsMap.value.values
                val mutableMap = ConcurrentHashMap<Long, Source>(
                    mapOf(
                        LocalSource.ID to LocalSource(
                            context,
                            fileSystem,
                            coverManager,
                            json,
                            xml,
                        ),
                    ),
                )

                // Add legacy extensions
                extensions.forEach { extension ->
                    try {
                        extension.sources.forEach {
                            mutableMap[it.id] = it
                            registerStubSource(StubSource.from(it))
                        }
                    } catch (e: Throwable) {
                        android.util.Log.e(
                            "AndroidSourceManager",
                            "Failed to load extension sources for ${extension.name}",
                            e,
                        )
                    }
                }

                // Add custom JS scrapers and heuristic sources
                val orchestrator = orchestratorProvider.get()
                profiles.forEach { profile ->
                    if (profile.enabled) {
                        try {
                            val source = eu.kanade.tachiyomi.source.online.DynamicHttpSource(profile, orchestrator)
                            mutableMap[source.id] = source
                            registerStubSource(StubSource.from(source))
                        } catch (e: Throwable) {
                            android.util.Log.e(
                                "AndroidSourceManager",
                                "Failed to load dynamic source for profile: ${profile.displayName}",
                                e,
                            )
                        }
                    }
                }

                sourcesMapFlow.value = mutableMap
                stubSourcesMap.keys.removeAll(mutableMap.keys)
                // Only report ready once the extension scan has finished. Before that the map holds
                // only the local source, and flipping this flag early told every caller "the
                // sources are loaded" while each extension source then resolved to a placeholder
                // stub that the UI showed as "source not installed".
                if (extensionsLoaded) {
                    _isInitialized.value = true
                }
            }
                .catch { e ->
                    logcat(LogPriority.ERROR, e) {
                        "Source map build failed; continuing with the sources resolved so far"
                    }
                    // Mark initialized even on failure. Leaving it false means every `getOrStub`
                    // call returns a nameless placeholder for the rest of the process — the app
                    // would present a valid, installed source as "source not installed" forever.
                    // Once initialized, `getOrStub` falls back to a named stub from the database.
                    _isInitialized.value = true
                }
                .collectLatest {}
        }

        scope.launch {
            sourceRepository.subscribeAll()
                .collectLatest { sources ->
                    val mutableMap = stubSourcesMap.toMutableMap()
                    sources.forEach {
                        mutableMap[it.id] = it
                    }
                }
        }
    }

    override fun get(sourceKey: Long): Source? {
        return sourcesMapFlow.value[sourceKey]
    }

    override fun getOrStub(sourceKey: Long): Source {
        sourcesMapFlow.value[sourceKey]?.let { return it }
        stubSourcesMap[sourceKey]?.let { return it }

        if (sourceKey == ephyra.domain.track.interactor.TrackerListImporter.AUTHORITY_SOURCE_ID) {
            val authorityStub = StubSource(
                id = sourceKey,
                lang = "all",
                name = context.stringResource(ephyra.app.core.common.R.string.authority_source_name),
            )
            stubSourcesMap[sourceKey] = authorityStub
            return authorityStub
        }

        val fallback = StubSource(id = sourceKey, lang = "", name = "")
        if (_isInitialized.value) {
            stubSourcesMap[sourceKey] = fallback
        }
        scope.launch {
            val actualStub = createStubSource(sourceKey)
            if (actualStub != fallback) {
                stubSourcesMap[sourceKey] = actualStub
            }
        }
        return fallback
    }

    override fun getOnlineSources() = sourcesMapFlow.value.values.filterIsInstance<HttpSource>()

    override fun getCatalogueSources() = sourcesMapFlow.value.values.filterIsInstance<CatalogueSource>()

    override fun getStubSources(): List<StubSource> {
        val onlineSourceIds = getOnlineSources().map { it.id }
        return stubSourcesMap.values.filterNot { it.id in onlineSourceIds }
    }

    private fun registerStubSource(source: StubSource) {
        scope.launch {
            val dbSource = sourceRepository.getStubSource(source.id)
            if (dbSource == source) return@launch
            sourceRepository.upsertStubSource(source.id, source.lang, source.name)
            if (dbSource != null) {
                downloadManagerProvider.get().renameSource(dbSource, source)
            }
        }
    }

    private suspend fun createStubSource(id: Long): StubSource {
        sourceRepository.getStubSource(id)?.let {
            return it
        }
        extensionManager.getSourceData(id)?.let {
            registerStubSource(it)
            return it
        }
        return StubSource(id = id, lang = "", name = "")
    }
}
