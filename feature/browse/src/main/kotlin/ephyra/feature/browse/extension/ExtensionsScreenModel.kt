package ephyra.feature.browse.extension

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.system.LocaleHelper
import ephyra.domain.base.BasePreferences
import ephyra.domain.extension.interactor.GetExtensionsByType
import ephyra.domain.extension.model.Extension
import ephyra.domain.extension.model.InstallStep
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.source.service.SourcePreferences
import ephyra.presentation.core.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.TreeMap
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class ExtensionsScreenModel @Inject constructor(
    private val context: Application,
    private val preferences: SourcePreferences,
    private val basePreferences: BasePreferences,
    private val extensionManager: ExtensionManager,
    private val getExtensions: GetExtensionsByType,
) : ViewModel() {

    private val _state = MutableStateFlow<State>(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        val extensionMapper: (Map<String, InstallStep>) -> ((Extension) -> ExtensionUiModel.Item) = { map ->
            {
                ExtensionUiModel.Item(it, map[it.pkgName] ?: InstallStep.Idle)
            }
        }

        viewModelScope.launchIO {
            combine(
                state.map { it.searchQuery }
                    .distinctUntilChanged()
                    .debounce(SEARCH_DEBOUNCE_MILLIS)
                    .map { searchQueryPredicate(it ?: "") },
                state.map { it.currentDownloads }.distinctUntilChanged(),
                getExtensions.subscribe(),
            ) { predicate, downloads, (_updates, _installed, _available, _untrusted) ->
                val mapper = extensionMapper(downloads)
                buildMap {
                    val updates = _updates.mapNotNull { if (predicate(it)) mapper(it) else null }
                    if (updates.isNotEmpty()) {
                        put(
                            ExtensionUiModel.Header.Resource(ephyra.app.core.common.R.string.ext_updates_pending),
                            updates,
                        )
                    }

                    val installed = _installed.mapNotNull { if (predicate(it)) mapper(it) else null }
                    val untrusted = _untrusted.mapNotNull { if (predicate(it)) mapper(it) else null }
                    if (installed.isNotEmpty() || untrusted.isNotEmpty()) {
                        put(
                            ExtensionUiModel.Header.Resource(ephyra.app.core.common.R.string.ext_installed),
                            installed + untrusted,
                        )
                    }

                    val langGroups = TreeMap<String, MutableList<Extension.Available>>(LocaleHelper.comparator)
                    _available.forEach { if (predicate(it)) langGroups.getOrPut(it.lang) { mutableListOf() }.add(it) }
                    langGroups.forEach { (lang, exts) ->
                        put(
                            ExtensionUiModel.Header.Text(LocaleHelper.getSourceDisplayName(lang, context)),
                            exts.map(mapper),
                        )
                    }
                }
            }
                .collectLatest { items ->
                    _state.update { state ->
                        state.copy(
                            isLoading = false,
                            items = items,
                        )
                    }
                }
        }

        viewModelScope.launchIO { findAvailableExtensions() }

        preferences.extensionUpdatesCount().changes()
            .onEach { updates -> _state.update { it.copy(updates = updates) } }
            .launchIn(viewModelScope)

        basePreferences.extensionInstaller().changes()
            .onEach { installer -> _state.update { it.copy(installer = installer) } }
            .launchIn(viewModelScope)
    }

    fun searchQueryPredicate(query: String): (Extension) -> Boolean {
        val subqueries = query.split(",")
            .map { it.trim() }
            .filterNot { it.isBlank() }

        if (subqueries.isEmpty()) return { true }

        val parsedSubqueries = subqueries.map { it to it.toLongOrNull() }

        return { extension ->
            parsedSubqueries.any { (subquery, subqueryAsId) ->
                if (extension.name.contains(subquery, ignoreCase = true)) return@any true
                when (extension) {
                    is Extension.Installed -> extension.sources.any { source ->
                        source.name.contains(subquery, ignoreCase = true) ||
                            (source as? HttpSource)?.baseUrl?.contains(subquery, ignoreCase = true) == true ||
                            source.id == subqueryAsId
                    }

                    is Extension.Available -> extension.sources.any {
                        it.name.contains(subquery, ignoreCase = true) ||
                            it.baseUrl.contains(subquery, ignoreCase = true) ||
                            it.id == subqueryAsId
                    }

                    else -> false
                }
            }
        }
    }

    fun search(query: String?) {
        _state.update {
            it.copy(searchQuery = query)
        }
    }

    fun updateAllExtensions() {
        viewModelScope.launchIO {
            state.value.items.values.forEach { items ->
                items.forEach { item ->
                    val ext = item.extension
                    if (ext is Extension.Installed && ext.hasUpdate) {
                        updateExtension(ext)
                    }
                }
            }
        }
    }

    fun installExtension(extension: Extension.Available) {
        viewModelScope.launchIO {
            extensionManager.installExtension(extension).collectToInstallUpdate(extension)
        }
    }

    fun updateExtension(extension: Extension.Installed) {
        viewModelScope.launchIO {
            extensionManager.updateExtension(extension).collectToInstallUpdate(extension)
        }
    }

    fun cancelInstallUpdateExtension(extension: Extension) {
        extensionManager.cancelInstallUpdateExtension(extension)
        removeDownloadState(extension)
    }

    private fun addDownloadState(extension: Extension, installStep: InstallStep) {
        _state.update { it.copy(currentDownloads = it.currentDownloads + (extension.pkgName to installStep)) }
    }

    private fun removeDownloadState(extension: Extension) {
        _state.update { it.copy(currentDownloads = it.currentDownloads - extension.pkgName) }
    }

    private suspend fun Flow<InstallStep>.collectToInstallUpdate(extension: Extension) =
        this
            .onEach { installStep -> addDownloadState(extension, installStep) }
            .onCompletion { removeDownloadState(extension) }
            .collect()

    fun uninstallExtension(extension: Extension) {
        extensionManager.uninstallExtension(extension)
    }

    fun findAvailableExtensions() {
        viewModelScope.launchIO {
            _state.update { it.copy(isRefreshing = true) }

            extensionManager.findAvailableExtensions()

            delay(1.seconds)

            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun trustExtension(extension: Extension.Untrusted) {
        viewModelScope.launch {
            extensionManager.trust(extension)
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val items: ItemGroups = mutableMapOf(),
        val updates: Int = 0,
        val installer: BasePreferences.ExtensionInstaller? = null,
        val searchQuery: String? = null,
        val currentDownloads: Map<String, InstallStep> = emptyMap(),
    ) {
        val isEmpty = items.isEmpty()
    }
}

typealias ItemGroups = Map<ExtensionUiModel.Header, List<ExtensionUiModel.Item>>

object ExtensionUiModel {
    sealed interface Header {
        data class Resource(val textRes: Int) : Header
        data class Text(val text: String) : Header
    }

    data class Item(
        val extension: Extension,
        val installStep: InstallStep,
    )
}
