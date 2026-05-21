package ephyra.feature.browse.extension.details

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ephyra.core.common.util.system.LocaleHelper
import ephyra.core.common.util.system.logcat
import ephyra.domain.extension.interactor.ExtensionSourceItem
import ephyra.domain.extension.interactor.GetExtensionSources
import ephyra.domain.extension.model.Extension
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.source.interactor.ToggleIncognito
import ephyra.domain.source.interactor.ToggleSource
import ephyra.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrl

@HiltViewModel(assistedFactory = ExtensionDetailsScreenModel.Factory::class)
class ExtensionDetailsScreenModel @AssistedInject constructor(
    @Assisted private val pkgName: String,
    @ApplicationContext private val context: Context,
    private val network: NetworkHelper,
    private val extensionManager: ExtensionManager,
    private val getExtensionSources: GetExtensionSources,
    private val toggleSource: ToggleSource,
    private val toggleIncognito: ToggleIncognito,
    private val preferences: SourcePreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events: Channel<ExtensionDetailsEvent> = Channel()
    val events: Flow<ExtensionDetailsEvent> = _events.receiveAsFlow()

    @AssistedFactory
    interface Factory {
        fun create(pkgName: String): ExtensionDetailsScreenModel
    }

    init {
        viewModelScope.launch {
            launch {
                extensionManager.installedExtensionsFlow
                    .map { it.firstOrNull { extension -> extension.pkgName == pkgName } }
                    .collectLatest { extension ->
                        if (extension == null) {
                            _events.send(ExtensionDetailsEvent.Uninstalled)
                            return@collectLatest
                        }
                        _state.update { state ->
                            state.copy(extension = extension)
                        }
                    }
            }
            launch {
                state.collectLatest { state ->
                    if (state.extension == null) return@collectLatest
                    getExtensionSources.subscribe(state.extension)
                        .map {
                            it.sortedWith(
                                compareBy(
                                    { !it.enabled },
                                    { item ->
                                        item.source.name.takeIf { item.labelAsName }
                                            ?: LocaleHelper.getSourceDisplayName(item.source.lang, context).lowercase()
                                    },
                                ),
                            )
                        }
                        .catch { throwable ->
                            logcat(LogPriority.ERROR, throwable)
                            _state.update { it.copy(_sources = persistentListOf()) }
                        }
                        .collectLatest { sources ->
                            _state.update { it.copy(_sources = sources.toImmutableList()) }
                        }
                }
            }
            launch {
                preferences.incognitoExtensions()
                    .changes()
                    .map { pkgName in it }
                    .distinctUntilChanged()
                    .collectLatest { isIncognito ->
                        _state.update { it.copy(isIncognito = isIncognito) }
                    }
            }
        }
    }

    fun onEvent(event: ExtensionDetailsScreenEvent) {
        when (event) {
            ExtensionDetailsScreenEvent.ClearCookies -> clearCookies()
            ExtensionDetailsScreenEvent.UninstallExtension -> uninstallExtension()
            is ExtensionDetailsScreenEvent.ToggleSource -> toggleSource(event.sourceId)
            is ExtensionDetailsScreenEvent.ToggleSources -> toggleSources(event.enable)
            is ExtensionDetailsScreenEvent.ToggleIncognito -> toggleIncognito(event.enable)
        }
    }

    private fun clearCookies() {
        val extension = state.value.extension ?: return

        val urls = extension.sources
            .filterIsInstance<HttpSource>()
            .mapNotNull { it.baseUrl.takeUnless { url -> url.isEmpty() } }
            .distinct()

        val cleared = urls.sumOf {
            try {
                network.cookieJar.remove(it.toHttpUrl())
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to clear cookies for $it" }
                0
            }
        }

        logcat { "Cleared $cleared cookies for: ${urls.joinToString()}" }
    }

    private fun uninstallExtension() {
        val extension = state.value.extension ?: return
        extensionManager.uninstallExtension(extension)
    }

    private fun toggleSource(sourceId: Long) {
        viewModelScope.launch {
            toggleSource.await(sourceId)
        }
    }

    private fun toggleSources(enable: Boolean) {
        val sourceIds = state.value.extension?.sources?.map { it.id } ?: return
        viewModelScope.launch {
            toggleSource.await(sourceIds, enable)
        }
    }

    private fun toggleIncognito(enable: Boolean) {
        val packageName = state.value.extension?.pkgName ?: return
        viewModelScope.launch {
            toggleIncognito.await(packageName, enable)
        }
    }

    @Immutable
    data class State(
        val extension: Extension.Installed? = null,
        val isIncognito: Boolean = false,
        private val _sources: ImmutableList<ExtensionSourceItem>? = null,
    ) {

        val sources: ImmutableList<ExtensionSourceItem>
            get() = _sources ?: persistentListOf()

        val isLoading: Boolean
            get() = extension == null || _sources == null
    }
}

sealed interface ExtensionDetailsEvent {
    data object Uninstalled : ExtensionDetailsEvent
}
