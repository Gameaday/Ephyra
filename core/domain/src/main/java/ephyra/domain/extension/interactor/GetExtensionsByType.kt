package ephyra.domain.extension.interactor

import ephyra.domain.extension.model.Extension
import ephyra.domain.extension.model.Extensions
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GetExtensionsByType(
    private val preferences: SourcePreferences,
    private val extensionManager: ExtensionManager,
) {

    fun subscribe(): Flow<Extensions> {
        val extensionLists = combine(
            extensionManager.installedExtensionsFlow,
            extensionManager.untrustedExtensionsFlow,
            extensionManager.availableExtensionsFlow,
            extensionManager.failedExtensionsFlow,
        ) { _installed, _untrusted, _available, _failed ->
            val (updates, installed) = _installed
                .filter { preferences.showNsfwSource().getSync() || !it.isNsfw }
                .sortedWith(
                    compareBy<Extension.Installed> { !it.isObsolete }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
                )
                .partition { it.hasUpdate }

            val untrusted = _untrusted
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            val failed = _failed
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            val showNsfwSources = preferences.showNsfwSource().getSync()
            val available = _available
                .filter { extension ->
                    _installed.none { it.pkgName == extension.pkgName } &&
                        _untrusted.none { it.pkgName == extension.pkgName } &&
                        _failed.none { it.pkgName == extension.pkgName } &&
                        (showNsfwSources || !extension.isNsfw)
                }
                .flatMap { ext ->
                    if (ext.sources.isEmpty()) {
                        return@flatMap if (ext.lang in preferences.enabledLanguages().getSync()) {
                            listOf(ext)
                        } else {
                            emptyList()
                        }
                    }
                    ext.sources.filter { it.lang in preferences.enabledLanguages().getSync() }
                        .map {
                            ext.copy(
                                name = it.name,
                                lang = it.lang,
                                pkgName = "${ext.pkgName}-${it.id}",
                                sources = listOf(it),
                            )
                        }
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            Extensions(updates, installed, available, untrusted, failed)
        }

        return combine(
            preferences.showNsfwSource().changes(),
            preferences.enabledLanguages().changes(),
            extensionLists,
        ) { _, _, extensions -> extensions }
    }
}
