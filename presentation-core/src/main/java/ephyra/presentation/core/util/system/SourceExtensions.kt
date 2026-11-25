package ephyra.presentation.core.util.system

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.service.SourcePreferences
import ephyra.domain.source.model.SourceFilter
import ephyra.source.api.LegacySourceGateway
import ephyra.source.api.SourceGateway
import ephyra.source.local.LocalSource
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.ResolvableSource

fun Source.icon(extensionManager: ExtensionManager): ImageBitmap? {
    return (extensionManager.getAppIconForSource(id) as? Drawable)
        ?.toBitmap()
        ?.asImageBitmap()
}

/** Returns an icon for a domain [Source] model using the given [ExtensionManager]. */
fun ephyra.domain.source.model.Source.icon(extensionManager: ExtensionManager): ImageBitmap? {
    return (extensionManager.getAppIconForSource(id) as? Drawable)
        ?.toBitmap()
        ?.asImageBitmap()
}

/** Returns a human-readable name suitable for display next to a manga info label. */
fun Source.getNameForMangaInfo(): String = toString()

fun Source.getNameForMangaInfo(preferences: SourcePreferences): String {
    val enabledLanguages = preferences.enabledLanguages().getSync()
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

/** Returns a human-readable name suitable for display next to a manga info label. */
fun SourceGateway.getNameForMangaInfo(): String = when (this) {
    is LegacySourceGateway -> delegate.getNameForMangaInfo()
    else -> descriptor.displayName
}

/** Returns a human-readable name suitable for display next to a manga info label. */
fun SourceGateway.getNameForMangaInfo(preferences: SourcePreferences): String = when (this) {
    is LegacySourceGateway -> delegate.getNameForMangaInfo(preferences)
    else -> descriptor.displayName
}

/** Fetches updated manga details from the source. */
suspend fun SourceGateway.getMangaDetails(manga: SManga): SManga = when (this) {
    is LegacySourceGateway -> delegate.getMangaDetails(manga)
    else -> throw UnsupportedOperationException("getMangaDetails not supported on this gateway type")
}

/** Returns the legacy [Source] delegate if this is a [LegacySourceGateway]. */
fun SourceGateway.asLegacySource(): Source? = when (this) {
    is LegacySourceGateway -> delegate
    else -> null
}

/** Returns the legacy [LocalSource] if this wraps one. */
fun SourceGateway.asLocalSource(): LocalSource? = when (this) {
    is LegacySourceGateway -> delegate as? LocalSource
    else -> null
}

/** Returns the legacy [HttpSource] if this wraps one. */
fun SourceGateway.asHttpSource(): HttpSource? = when (this) {
    is LegacySourceGateway -> delegate as? HttpSource
    else -> null
}

/** Returns the legacy [CatalogueSource] if this wraps one. */
fun SourceGateway.asCatalogueSource(): CatalogueSource? = when (this) {
    is LegacySourceGateway -> delegate as? CatalogueSource
    else -> null
}

/** Returns the legacy [ConfigurableSource] if this wraps one. */
fun SourceGateway.asConfigurableSource(): ConfigurableSource? = when (this) {
    is LegacySourceGateway -> delegate as? ConfigurableSource
    else -> null
}

/** Returns the legacy [LocalSource] if this wraps one. */
fun SourceGateway.asLocalSourceOrNull(): LocalSource? = asLocalSource()

/** Returns the legacy [HttpSource] if this wraps one. */
fun SourceGateway.asHttpSourceOrNull(): HttpSource? = asHttpSource()

/** Returns the legacy [CatalogueSource] if this wraps one. */
fun SourceGateway.asCatalogueSourceOrNull(): CatalogueSource? = asCatalogueSource()

/** Returns the legacy [UnmeteredSource] if this wraps one. */
fun SourceGateway.asUnmeteredSource(): UnmeteredSource? = when (this) {
    is LegacySourceGateway -> delegate as? UnmeteredSource
    else -> null
}

/** Returns the legacy [StubSource] if this wraps one. */
fun SourceGateway.asStubSource(): StubSource? = when (this) {
    is LegacySourceGateway -> delegate as? StubSource
    else -> null
}

/** Returns the language of the legacy source if this wraps one. */
val SourceGateway.lang: String
    get() = (this as? LegacySourceGateway)?.delegate?.lang ?: ""

/** Returns the legacy numeric source id if this wraps a legacy source. */
val SourceGateway.legacyId: Long?
    get() = (this as? LegacySourceGateway)?.delegate?.id

/** Returns true if this source is a local source. */
val SourceGateway.isLocal: Boolean
    get() = this.asLocalSource() != null

/** Returns true if this source is a local or stub source. */
fun SourceGateway.isLocalOrStub(): Boolean = isLocal || this.asStubSource() != null

/** Returns the name of the source. */
val SourceGateway.name: String
    get() = descriptor.displayName

/** Converts a legacy [FilterList] to a domain [SourceFilter]. */
fun fromLegacyFilterList(filterList: FilterList): SourceFilter = SourceFilter(filterList.list)

/** Returns the legacy [ResolvableSource] if this wraps one. */
fun SourceGateway.asResolvableSource(): ResolvableSource? = when (this) {
    is LegacySourceGateway -> delegate as? ResolvableSource
    else -> null
}
