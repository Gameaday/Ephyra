package ephyra.domain.source.interactor

import ephyra.core.common.preference.getAndSet
import ephyra.domain.source.service.SourcePreferences

class ToggleLanguage(
    val preferences: SourcePreferences,
) {

    suspend fun await(language: String) {
        val isEnabled = language in preferences.enabledLanguages().get()
        preferences.enabledLanguages().getAndSet { enabled ->
            if (isEnabled) enabled.minus(language) else enabled.plus(language)
        }
    }
}
