package ephyra.app.extension.api

import android.content.Context
import ephyra.app.extension.model.LoadResult
import ephyra.app.extension.util.ExtensionLoader
import ephyra.core.common.core.security.SecurityPreferences
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.domain.extension.model.Extension
import ephyra.domain.extensionrepo.interactor.GetExtensionRepo
import ephyra.domain.extensionrepo.interactor.UpdateExtensionRepo
import ephyra.domain.extensionrepo.model.ExtensionRepo
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import java.time.Instant
import kotlin.time.Duration.Companion.days

class ExtensionApi(
    private val networkService: NetworkHelper,
    private val preferenceStore: PreferenceStore,
    private val getExtensionRepo: GetExtensionRepo,
    private val updateExtensionRepo: UpdateExtensionRepo,
    private val securityPreferences: SecurityPreferences,
    private val extensionLoader: ExtensionLoader,
    private val json: Json,
) {

    private val lastExtCheck: Preference<Long> by lazy {
        preferenceStore.getLong(Preference.appStateKey("last_ext_check"), 0)
    }

    suspend fun findExtensions(): List<Extension.Available> {
        return withIOContext {
            getExtensionRepo.getAll()
                .map { async { getExtensions(it) } }
                .awaitAll()
                .flatten()
        }
    }

    private suspend fun getExtensions(extRepo: ExtensionRepo): List<Extension.Available> {
        val repoBaseUrl = extRepo.baseUrl
        val modernExtensions = fetchAndParseExtensions("$repoBaseUrl/index.json", repoBaseUrl)
        if (modernExtensions != null) return modernExtensions

        return fetchAndParseExtensions("$repoBaseUrl/index.min.json", repoBaseUrl).orEmpty()
    }

    suspend fun checkForUpdates(
        context: Context,
        availableExtensions: List<Extension.Available>? = null,
    ): List<Extension.Installed>? {
        // Limit checks to once a day at most
        if (availableExtensions == null &&
            Instant.now().toEpochMilli() < lastExtCheck.get() + 1.days.inWholeMilliseconds
        ) {
            return null
        }

        // Update extension repo details
        updateExtensionRepo.awaitAll()

        val extensions = availableExtensions
            ?: findExtensions().also { lastExtCheck.set(Instant.now().toEpochMilli()) }

        val installedExtensions = extensionLoader.loadExtensions(context)
            .filterIsInstance<LoadResult.Success>()
            .map { it.extension }

        val extensionsByPkg = extensions.associateBy { it.pkgName }
        val extensionsWithUpdate = mutableListOf<Extension.Installed>()
        for (installedExt in installedExtensions) {
            val availableExt = extensionsByPkg[installedExt.pkgName] ?: continue
            val hasUpdatedVer = availableExt.versionCode > installedExt.versionCode
            val hasUpdatedLib = availableExt.libVersion > installedExt.libVersion
            val hasUpdate = hasUpdatedVer || hasUpdatedLib
            if (hasUpdate) {
                extensionsWithUpdate.add(installedExt)
            }
        }

        if (extensionsWithUpdate.isNotEmpty()) {
            ExtensionUpdateNotifier(context, securityPreferences).promptUpdates(extensionsWithUpdate.map { it.name })
        }

        return extensionsWithUpdate
    }

    private suspend fun fetchAndParseExtensions(indexUrl: String, repoUrl: String): List<Extension.Available>? {
        return try {
            val indexBody = networkService.client
                .newCall(GET(indexUrl))
                .awaitSuccess()
                .body
                .string()
            parseExtensions(indexBody, repoUrl)
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e) { "Failed to parse extension index from $indexUrl" }
            null
        }
    }

    private fun parseExtensions(indexBody: String, repoUrl: String): List<Extension.Available>? {
        runCatching { json.decodeFromString<ModernExtensionIndexJsonObject>(indexBody) }
            .getOrNull()
            ?.toExtensions(repoUrl)
            .takeIf { it.isNotEmpty() }
            ?.let { return it }

        return runCatching { json.decodeFromString<List<LegacyExtensionJsonObject>>(indexBody).toExtensions(repoUrl) }
            .getOrNull()
    }

    private fun List<LegacyExtensionJsonObject>.toExtensions(repoUrl: String): List<Extension.Available> {
        return this
            .mapNotNull {
                val libVersion = it.extractLibVersion() ?: return@mapNotNull null
                if (libVersion < ExtensionLoader.LIB_VERSION_MIN || libVersion > ExtensionLoader.LIB_VERSION_MAX) return@mapNotNull null
                Extension.Available(
                    name = it.name.substringAfter("Tachiyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = libVersion,
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    sources = it.sources?.map(legacyExtensionSourceMapper).orEmpty(),
                    apkName = it.apk,
                    iconUrl = "$repoUrl/icon/${it.pkg}.png",
                    repoUrl = repoUrl,
                )
            }
    }

    private fun ModernExtensionIndexJsonObject.toExtensions(repoUrl: String): List<Extension.Available> {
        return extensionList.extensions.mapNotNull { extension ->
            val libVersion = extension.extensionLib.toDoubleOrNull()
                ?: extension.versionName.substringBeforeLast('.').toDoubleOrNull()
                ?: return@mapNotNull null
            if (libVersion < ExtensionLoader.LIB_VERSION_MIN || libVersion > ExtensionLoader.LIB_VERSION_MAX) return@mapNotNull null

            val languageFromPackage = extension.packageName.substringAfter("extension.").substringBefore('.')
            val lang = languageFromPackage.ifBlank {
                extension.sources.firstOrNull()?.language ?: "all"
            }
            val versionCode = extension.versionCode.toLongOrNull() ?: return@mapNotNull null

            Extension.Available(
                name = extension.name,
                pkgName = extension.packageName,
                versionName = extension.versionName,
                versionCode = versionCode,
                libVersion = libVersion,
                lang = lang,
                isNsfw = extension.contentWarning == "CONTENT_WARNING_NSFW",
                sources = extension.sources.map(modernExtensionSourceMapper),
                apkName = extension.resources.apkUrl,
                iconUrl = extension.resources.iconUrl ?: "$repoUrl/icon/${extension.packageName}.png",
                repoUrl = repoUrl,
            )
        }
    }

    fun getApkUrl(extension: Extension.Available): String {
        if (extension.apkName.startsWith("https://") || extension.apkName.startsWith("http://")) {
            return extension.apkName
        }
        return "${extension.repoUrl}/apk/${extension.apkName}"
    }

    private fun LegacyExtensionJsonObject.extractLibVersion(): Double? {
        return version.substringBeforeLast('.').toDoubleOrNull()
    }

    private val legacyExtensionSourceMapper: (LegacyExtensionSourceJsonObject) -> Extension.Available.Source = {
        Extension.Available.Source(
            id = it.id,
            lang = it.lang,
            name = it.name,
            baseUrl = it.baseUrl,
        )
    }

    private val modernExtensionSourceMapper: (ModernExtensionSourceJsonObject) -> Extension.Available.Source = {
        Extension.Available.Source(
            id = it.id.toLongOrNull() ?: it.id.hashCode().toLong(),
            lang = it.language,
            name = it.name,
            baseUrl = it.homeUrl,
        )
    }
}

@Serializable
private data class LegacyExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val sources: List<LegacyExtensionSourceJsonObject>?,
)

@Serializable
private data class LegacyExtensionSourceJsonObject(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
)

@Serializable
private data class ModernExtensionIndexJsonObject(
    val extensionList: ModernExtensionListJsonObject,
)

@Serializable
private data class ModernExtensionListJsonObject(
    val extensions: List<ModernExtensionJsonObject> = emptyList(),
)

@Serializable
private data class ModernExtensionJsonObject(
    val name: String,
    val packageName: String,
    val resources: ModernExtensionResourcesJsonObject,
    val extensionLib: String,
    val versionCode: String,
    val versionName: String,
    val contentWarning: String? = null,
    val sources: List<ModernExtensionSourceJsonObject> = emptyList(),
)

@Serializable
private data class ModernExtensionResourcesJsonObject(
    val apkUrl: String,
    val iconUrl: String? = null,
)

@Serializable
private data class ModernExtensionSourceJsonObject(
    val id: String,
    val language: String,
    val name: String,
    val homeUrl: String,
)
