package ephyra.domain.extension.interactor

import ephyra.core.common.preference.getAndSet
import ephyra.domain.extension.model.ExtensionPackageInfo
import ephyra.domain.extensionrepo.repository.ExtensionRepoRepository
import ephyra.domain.source.service.SourcePreferences

class TrustExtension(
    private val extensionRepoRepository: ExtensionRepoRepository,
    private val preferences: SourcePreferences,
) {

    suspend fun isTrusted(pkgInfo: ExtensionPackageInfo, fingerprints: List<String>): Boolean {
        // Hex fingerprints are compared case-insensitively: repo metadata and APK
        // signatures may differ in hex casing, which would silently fail trust.
        val trustedFingerprints = extensionRepoRepository.getAll()
            .mapTo(HashSet()) { it.signingKeyFingerprint.lowercase() }
        val key = "${pkgInfo.packageName}:${pkgInfo.versionCode}:${fingerprints.last()}"
        return fingerprints.any { it.lowercase() in trustedFingerprints } ||
            key in preferences.trustedExtensions().get()
    }

    suspend fun trust(pkgName: String, versionCode: Long, signatureHash: String) {
        preferences.trustedExtensions().getAndSet { exts ->
            // Remove previously trusted versions
            val removed = exts.filterNot { it.startsWith("$pkgName:") }.toMutableSet()

            removed.also { it += "$pkgName:$versionCode:$signatureHash" }
        }
    }

    fun revokeAll() {
        preferences.trustedExtensions().delete()
    }
}
