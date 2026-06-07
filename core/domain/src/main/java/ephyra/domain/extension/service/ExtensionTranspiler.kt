package ephyra.domain.extension.service

import ephyra.domain.extension.model.Extension

interface ExtensionTranspiler {
    suspend fun transpileAndInstall(
        extension: Extension.Available,
        selectedUrls: Set<String>? = null,
    ): Boolean

    fun clearExtensionMetadata(pkgName: String)
}
