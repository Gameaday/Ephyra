package ephyra.domain.source.interactor

import ephyra.core.common.preference.getAndSet
import ephyra.domain.source.model.Source
import ephyra.domain.source.service.SourcePreferences

class ToggleSource(
    private val preferences: SourcePreferences,
) {

    suspend fun await(source: Source, enable: Boolean? = null) {
        await(source.id, enable ?: isEnabled(source.id))
    }

    suspend fun await(sourceId: Long, enable: Boolean? = null) {
        val isEnable = enable ?: isEnabled(sourceId)
        preferences.disabledSources().getAndSet { disabled ->
            if (isEnable) disabled.minus("$sourceId") else disabled.plus("$sourceId")
        }
    }

    suspend fun await(sourceIds: List<Long>, enable: Boolean) {
        val transformedSourceIds = sourceIds.map { it.toString() }
        preferences.disabledSources().getAndSet { disabled ->
            if (enable) disabled.minus(transformedSourceIds) else disabled.plus(transformedSourceIds)
        }
    }

    private suspend fun isEnabled(sourceId: Long): Boolean {
        return sourceId.toString() in preferences.disabledSources().get()
    }
}
