package ephyra.feature.browse.extension

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.system.logcat
import ephyra.domain.extension.interactor.GetExtensionLanguages
import ephyra.domain.source.interactor.ToggleLanguage
import ephyra.domain.source.service.SourcePreferences
import ephyra.presentation.core.udf.BaseUdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import logcat.LogPriority
import javax.inject.Inject

@HiltViewModel
class ExtensionFilterViewModel @Inject constructor(
    private val preferences: SourcePreferences,
    private val getExtensionLanguages: GetExtensionLanguages,
    private val toggleLanguage: ToggleLanguage,
) : BaseUdfViewModel<ExtensionFilterState, ExtensionFilterScreenEvent, ExtensionFilterEvent>(
    ExtensionFilterState.Loading,
) {

    val events: Flow<ExtensionFilterEvent>
        get() = effects

    init {
        viewModelScope.launch {
            combine(
                getExtensionLanguages.subscribe(),
                preferences.enabledLanguages().changes(),
            ) { a, b -> a to b }
                .catch { throwable ->
                    logcat(LogPriority.ERROR, throwable)
                    emitEffect(ExtensionFilterEvent.FailedFetchingLanguages)
                }
                .collectLatest { (extensionLanguages, enabledLanguages) ->
                    updateState {
                        ExtensionFilterState.Success(
                            languages = extensionLanguages.toImmutableList(),
                            enabledLanguages = enabledLanguages.toImmutableSet(),
                        )
                    }
                }
        }
    }

    override fun onEvent(event: ExtensionFilterScreenEvent) {
        when (event) {
            is ExtensionFilterScreenEvent.Toggle -> {
                viewModelScope.launch {
                    toggleLanguage.await(event.language)
                }
            }
        }
    }

    fun toggle(language: String) {
        onEvent(ExtensionFilterScreenEvent.Toggle(language))
    }
}

sealed interface ExtensionFilterScreenEvent {
    data class Toggle(val language: String) : ExtensionFilterScreenEvent
}

sealed interface ExtensionFilterEvent {
    data object FailedFetchingLanguages : ExtensionFilterEvent
}

sealed interface ExtensionFilterState {

    @Immutable
    data object Loading : ExtensionFilterState

    @Immutable
    data class Success(
        val languages: ImmutableList<String>,
        val enabledLanguages: ImmutableSet<String> = persistentSetOf(),
    ) : ExtensionFilterState {

        val isEmpty: Boolean
            get() = languages.isEmpty()
    }
}
