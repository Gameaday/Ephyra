package ephyra.data.sourcing

import android.app.Application
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.domain.content.source.interactor.AddCustomSource
import ephyra.domain.content.source.interactor.RemoveCustomSource
import ephyra.domain.extension.model.Extension
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import logcat.LogPriority
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegacyExtensionTranspiler @Inject constructor(
    private val context: Application,
    private val jsEngine: JavaScriptEngine,
    private val networkHelper: NetworkHelper,
    private val scraperUpdater: DynamicScraperUpdater,
    private val addCustomSource: AddCustomSource,
    private val removeCustomSource: RemoveCustomSource,
    private val preferenceStore: PreferenceStore,
) {
    suspend fun transpileAndInstall(
        extension: Extension.Available,
        selectedUrls: Set<String>? = null,
    ): Boolean = withIOContext {
        val pkgName = extension.pkgName
        val name = extension.name
        val lang = extension.lang

        val classPrefix = name.replace(" ", "")
        val pkgSuffix = pkgName.substringAfterLast(".")

        val baseGithubUrl = when {
            extension.repoUrl.contains("/extensions/repo") -> {
                extension.repoUrl.replace("/extensions/repo", "/extensions-source/main/src")
            }
            extension.repoUrl.endsWith("/repo") -> {
                extension.repoUrl.removeSuffix("/repo") + "/main/src"
            }
            else -> {
                extension.repoUrl
            }
        }
        val extensionPath = "$lang/$pkgSuffix/src/eu/kanade/tachiyomi/extension/$lang/$pkgSuffix"

        // Construct raw Kotlin source URLs to try in sequence
        val urlsToTry = listOf(
            "$baseGithubUrl/$extensionPath/$classPrefix.kt",
            "$baseGithubUrl/$extensionPath/${classPrefix.replaceFirstChar { it.uppercase() }}.kt",
            "$baseGithubUrl/$extensionPath/${pkgSuffix.replaceFirstChar { it.uppercase() }}.kt",
            "$baseGithubUrl/$extensionPath/$pkgSuffix.kt",
        )

        var kotlinSource: String? = null
        for (url in urlsToTry) {
            try {
                val response = networkHelper.client.newCall(GET(url)).awaitSuccess()
                val bodyStr = response.body.string()
                if (!bodyStr.isBlank() && !bodyStr.contains("404: Not Found")) {
                    kotlinSource = bodyStr
                    break
                }
            } catch (e: Exception) {
                // Try next URL
            }
        }

        if (kotlinSource == null) {
            logcat(LogPriority.ERROR) { "Failed to download Kotlin source for extension $name ($pkgName)" }
            return@withIOContext false
        }

        // Load transpiler.js asset
        val transpilerJs = try {
            context.assets.open("transpiler.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to load transpiler.js asset" }
            return@withIOContext false
        }

        // Transpile using QuickJS
        val escapedKtSource = escapeJsString(kotlinSource)
        val escapedName = escapeJsString(name)
        val script = """
            $transpilerJs
            transpile('$escapedKtSource', '$escapedName');
        """.trimIndent()

        val jsCode = try {
            jsEngine.evaluate<String>(script)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "QuickJS transpilation failed for $name" }
            return@withIOContext false
        }

        if (jsCode.isBlank()) {
            logcat(LogPriority.ERROR) { "Transpiled JS code is empty for $name" }
            return@withIOContext false
        }

        val filename = "${pkgSuffix}_scraper.js"
        try {
            // Save inside sandbox using dynamic scraper updater
            scraperUpdater.importLocalScraperScript(filename, jsCode)

            // Map the extension's sources to this scraper
            extension.sources.forEach { source ->
                val sourceBaseUrl = source.baseUrl
                if (sourceBaseUrl.isNotBlank()) {
                    if (selectedUrls == null || selectedUrls.contains(sourceBaseUrl)) {
                        addCustomSource.linkScraperToUrl(sourceBaseUrl, filename)
                    } else {
                        removeCustomSource.removeSource(sourceBaseUrl)
                    }
                }
            }

            // Save version and filename mapping for pseudo plugin update checking
            preferenceStore.getLong("transpiled_extension_versioncode_$pkgName", 0L).set(extension.versionCode)
            preferenceStore.getString("transpiled_extension_versionname_$pkgName", "").set(extension.versionName)
            preferenceStore.getString("transpiled_extension_filename_$pkgName", "").set(filename)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to save transpiled scraper for $name" }
            false
        }
    }

    fun clearExtensionMetadata(pkgName: String) {
        preferenceStore.getLong("transpiled_extension_versioncode_$pkgName", 0L).delete()
        preferenceStore.getString("transpiled_extension_versionname_$pkgName", "").delete()
        preferenceStore.getString("transpiled_extension_filename_$pkgName", "").delete()
    }

    private fun escapeJsString(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
