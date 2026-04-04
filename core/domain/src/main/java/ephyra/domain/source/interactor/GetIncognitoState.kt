package ephyra.domain.source.interactor

import ephyra.domain.base.BasePreferences
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.runBlocking

class GetIncognitoState(
    private val basePreferences: BasePreferences,
    private val sourcePreferences: SourcePreferences,
    private val extensionManager: ExtensionManager,
) {
    fun await(sourceId: Long?): Boolean {
        if (runBlocking { basePreferences.incognitoMode().get() }) return true
        if (sourceId == null) return false
        val extensionPackage = extensionManager.getExtensionPackage(sourceId) ?: return false

        return extensionPackage in runBlocking { sourcePreferences.incognitoExtensions().get() }
    }

    fun subscribe(sourceId: Long?): Flow<Boolean> {
        if (sourceId == null) return basePreferences.incognitoMode().changes()

        return combine(
            basePreferences.incognitoMode().changes(),
            sourcePreferences.incognitoExtensions().changes(),
            extensionManager.getExtensionPackageAsFlow(sourceId),
        ) { incognito, incognitoExtensions, extensionPackage ->
            incognito || (extensionPackage in incognitoExtensions)
        }
            .distinctUntilChanged()
    }
}
