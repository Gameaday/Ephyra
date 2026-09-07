package ephyra.feature.migration.config

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.system.LocaleHelper
import ephyra.domain.source.model.Source
import ephyra.domain.source.service.SourceManager
import ephyra.domain.source.service.SourcePreferences
import ephyra.presentation.core.udf.BaseUdfViewModel
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MigrationConfigEvent {
    data class ToggleSelection(val id: Long) : MigrationConfigEvent
    data class ToggleSelectionConfig(val config: MigrationConfigViewModel.SelectionConfig) : MigrationConfigEvent
    data class OrderSource(val from: Int, val to: Int) : MigrationConfigEvent
    data object SaveSources : MigrationConfigEvent
}

data class MigrationSource(
    val source: Source,
    val isSelected: Boolean,
) {
    val id: Long
        inline get() = source.id

    val name: String
        inline get() = source.name

    val shortLanguage: String = LocaleHelper.getShortDisplayName(source.lang)
}

@HiltViewModel
class MigrationConfigViewModel @Inject constructor(
    val sourcePreferences: SourcePreferences,
    private val sourceManager: SourceManager,
) : BaseUdfViewModel<MigrationConfigViewModel.State, MigrationConfigEvent, Nothing>(State()) {

    private val sourcesComparator = { includedSources: List<Long> ->
        val rankMap = includedSources.withIndex().associate { (i, id) -> id to i }
        compareBy<MigrationSource>(
            { !it.isSelected },
            { rankMap.getOrDefault(it.id, Int.MAX_VALUE) },
            { with(it) { "$name ($shortLanguage)" } },
        )
    }

    init {
        viewModelScope.launch {
            initSources()
            updateState { it.copy(isLoading = false) }
        }
    }

    override fun onEvent(event: MigrationConfigEvent) {
        when (event) {
            is MigrationConfigEvent.ToggleSelection -> toggleSelection(event.id)
            is MigrationConfigEvent.ToggleSelectionConfig -> toggleSelection(event.config)
            is MigrationConfigEvent.OrderSource -> orderSource(event.from, event.to)
            MigrationConfigEvent.SaveSources -> saveSources()
        }
    }

    private fun updateSources(action: (List<MigrationSource>) -> List<MigrationSource>) {
        updateState { state ->
            val updatedSources = action(state.sources)
            val includedSources = updatedSources.mapNotNull { it.id.takeIf { _ -> it.isSelected } }
            state.copy(sources = updatedSources.sortedWith(sourcesComparator(includedSources)).toImmutableList())
        }
        saveSources()
    }

    private suspend fun initSources() {
        val languages = sourcePreferences.enabledLanguages().get()
        val pinnedSources = sourcePreferences.pinnedSources().get().mapNotNull { it.toLongOrNull() }
        val includedSources = sourcePreferences.migrationSources().get()
        val disabledSources = sourcePreferences.disabledSources().get()
            .mapNotNull { it.toLongOrNull() }
        val sources = sourceManager.getCatalogueSources()
            .asSequence()
            .filterIsInstance<HttpSource>()
            .filter { it.lang in languages }
            .map {
                val source = Source(
                    id = it.id,
                    lang = it.lang,
                    name = it.name,
                    supportsLatest = false,
                    isStub = false,
                )
                MigrationSource(
                    source = source,
                    isSelected = when {
                        includedSources.isNotEmpty() -> source.id in includedSources
                        pinnedSources.isNotEmpty() -> source.id in pinnedSources
                        else -> source.id !in disabledSources
                    },
                )
            }
            .toList()

        updateState { state ->
            state.copy(sources = sources.sortedWith(sourcesComparator(includedSources)).toImmutableList())
        }
    }

    fun toggleSelection(id: Long) {
        updateSources { sources ->
            sources.map { source ->
                source.copy(isSelected = if (source.source.id == id) !source.isSelected else source.isSelected)
            }
        }
    }

    fun toggleSelection(config: SelectionConfig) {
        val pinnedSources = sourcePreferences.pinnedSources().getSync().mapNotNull { it.toLongOrNull() }
        val disabledSources = sourcePreferences.disabledSources().getSync().mapNotNull { it.toLongOrNull() }
        val isSelected: (Long) -> Boolean = {
            when (config) {
                SelectionConfig.All -> true
                SelectionConfig.None -> false
                SelectionConfig.Pinned -> it in pinnedSources
                SelectionConfig.Enabled -> it !in disabledSources
            }
        }
        updateSources { sources ->
            sources.map { source ->
                source.copy(isSelected = isSelected(source.source.id))
            }
        }
    }

    fun orderSource(from: Int, to: Int) {
        updateSources {
            it.toMutableList()
                .apply {
                    add(to, removeAt(from))
                }
                .toList()
        }
    }

    fun saveSources() {
        currentState.sources
            .filter { source -> source.isSelected }
            .map { source -> source.source.id }
            .let { sources -> sourcePreferences.migrationSources().set(sources) }
    }

    data class State(
        val isLoading: Boolean = true,
        val sources: ImmutableList<MigrationSource> = persistentListOf(),
    )

    enum class SelectionConfig {
        All,
        None,
        Pinned,
        Enabled,
    }
}
