package eu.kanade.tachiyomi.source

import ephyra.domain.source.model.StubSource
import ephyra.domain.source.service.SourcePreferences
import ephyra.source.local.isLocal
import kotlinx.coroutines.runBlocking

fun Source.getNameForMangaInfo(preferences: SourcePreferences): String {
    val enabledLanguages = runBlocking { preferences.enabledLanguages().get() }
        .filterNot { it in listOf("all", "other") }
    val hasOneActiveLanguages = enabledLanguages.size == 1
    val isInEnabledLanguages = lang in enabledLanguages
    return when {
        // For edge cases where user disables a source they got manga of in their library.
        hasOneActiveLanguages && !isInEnabledLanguages -> toString()
        // Hide the language tag when only one language is used.
        hasOneActiveLanguages && isInEnabledLanguages -> name
        else -> toString()
    }
}

fun Source.isLocalOrStub(): Boolean = isLocal() || this is StubSource
