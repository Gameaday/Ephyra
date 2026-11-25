package eu.kanade.tachiyomi.source

import android.content.Context
import ephyra.app.extension.ExtensionManager
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.util.system.logcat
import ephyra.core.download.DownloadManager
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.repository.StubSourceRepository
import ephyra.domain.source.service.SourceManager
import ephyra.source.api.LegacySourceGateway
import ephyra.source.api.SourceGateway
import ephyra.source.local.LocalSource
import ephyra.presentation.core.util.system.legacyId
import eu.kanade.tachiyomi.source.CatalogueSource
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
    private val json: Json,
    private val xml: XML,
) : SourceManager {

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    // Store the legacy sources; wrap in LegacySourceGateway when exposing
    private val sourcesMapFlow = MutableStateFlow(ConcurrentHashMap<Long, Source>())

    private val stubSourcesMap = ConcurrentHashMap<Long, StubSource>()

    override val catalogueSourceFlow: Flow<List<SourceGateway>> = sourcesMapFlow.map { map ->
        map.values.map { LegacySourceGateway(it) }
    }

    init {
        scope.launch {
            kotlinx.coroutines.flow.combine(
                // Collected (not just read) so the derived `installedExtensionsFlow` state stays
                // fresh for its `.value` readers (diagnostics, crash logs, search). The loaded set
                // itself is read from the authoritative map below, which updates synchronously.
                extensionManager.installedExtensionsFlow,
                extensionManager.isInitialized,
            ) { _, extensionsLoaded ->
                // Read the authoritative map synchronously instead of the value carried by the
                // combine emission: the derived list can lag a write, and this must be the loaded
                // set whenever `extensionsLoaded` is true.
                val extensions = extensionManager.installedExtensionsState.value.values
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

    override fun get(sourceKey: Long): SourceGateway? {
        return sourcesMapFlow.value[sourceKey]?.let { LegacySourceGateway(it) }
    }

    override fun getOrStub(sourceKey: Long): SourceGateway {
        sourcesMapFlow.value[sourceKey]?.let { return LegacySourceGateway(it) }
        stubSourcesMap[sourceKey]?.let { return LegacySourceGateway(it) }

        if (sourceKey == ephyra.domain.track.interactor.TrackerListImporter.AUTHORITY_SOURCE_ID) {
            val authorityStub = StubSource(
                id = sourceKey,
                lang = "all",
                name = context.stringResource(ephyra.app.core.common.R.string.authority_source_name),
            )
            stubSourcesMap[sourceKey] = authorityStub
            return LegacySourceGateway(authorityStub)
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
        return LegacySourceGateway(fallback)
    }

    override fun getOnlineSources(): List<SourceGateway> {
        return sourcesMapFlow.value.values
            .toList()
            .filterIsInstance<HttpSource>()
            .map { LegacySourceGateway(it) }
    }

    override fun getCatalogueSources(): List<SourceGateway> {
        return sourcesMapFlow.value.values
            .toList()
            .filterIsInstance<CatalogueSource>()
            .map { LegacySourceGateway(it) }
    }

    override fun getStubSources(): List<StubSource> {
        val onlineSourceIds = getOnlineSources().map { (it.legacyId ?: 0L) }
        return stubSourcesMap.values.filterNot { it.id in onlineSourceIds }
    }

    private fun registerStubSource(source: StubSource) {
        scope.launch {
            val dbSource = sourceRepository.getStubSource(source.id)
            if (dbSource == source) return@launch
            sourceRepository.upsertStubSource(source.id, source.lang, source.name)
            if (dbSource != null) {
                downloadManagerProvider.get().renameSource(LegacySourceGateway(dbSource), LegacySourceGateway(source))
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
