package ephyra.domain.extension.interactor

import ephyra.domain.extension.service.ExtensionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Count of installed extensions with an update waiting, from the locally cached
 * extension list (populated by the background update checker).
 *
 * Reading this costs no network and does not initialise anything heavy: it exists so
 * UI surfaces (the Sources page chip) can badge "updates available" without waking
 * repository refresh logic just to render a chip.
 */
class GetExtensionUpdateCount(
    private val extensionManager: ExtensionManager,
) {
    fun subscribe(): Flow<Int> = extensionManager.installedExtensionsFlow
        .map { installed -> installed.count { it.hasUpdate } }
}
