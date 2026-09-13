package ephyra.domain.extensionrepo.interactor

import ephyra.core.common.util.system.logcat
import ephyra.domain.extensionrepo.exception.SaveExtensionRepoException
import ephyra.domain.extensionrepo.model.ExtensionRepo
import ephyra.domain.extensionrepo.repository.ExtensionRepoRepository
import ephyra.domain.extensionrepo.service.ExtensionRepoService
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class CreateExtensionRepo(
    private val repository: ExtensionRepoRepository,
    private val service: ExtensionRepoService,
) {

    private val supportedIndexFiles = setOf(
        "index.min.json",
        "index.json",
        "index.pb",
        "repo.json",
    )

    suspend fun await(indexUrl: String): Result {
        val baseUrl = parseBaseUrl(indexUrl)
            ?: return Result.InvalidUrl

        return service.fetchRepoDetails(baseUrl)?.let { insert(it) } ?: Result.InvalidUrl
    }

    private fun parseBaseUrl(input: String): String? {
        var rawInput = input.trim()
        val schemes = listOf(
            "tachiyomi://add-repo?url=",
            "mihon://add-repo?url=",
            "ephyra://add-repo?url=",
            "tachiyomi://",
            "mihon://",
            "ephyra://",
        )
        for (scheme in schemes) {
            if (rawInput.startsWith(scheme, ignoreCase = true)) {
                val param = rawInput.substring(scheme.length)
                val urlParam = if (scheme.contains("url=")) {
                    param.substringBefore('&')
                } else if (rawInput.contains("url=")) {
                    rawInput.substringAfter("url=").substringBefore('&')
                } else {
                    param
                }
                rawInput = try {
                    java.net.URLDecoder.decode(urlParam, java.nio.charset.StandardCharsets.UTF_8.name())
                } catch (_: Exception) {
                    urlParam
                }
                break
            }
        }

        if (!rawInput.startsWith("http://", ignoreCase = true) && !rawInput.startsWith("https://", ignoreCase = true)) {
            rawInput = "https://$rawInput"
        }

        val httpUrl = rawInput.toHttpUrlOrNull() ?: return null
        val cleanUrl = httpUrl.newBuilder()
            .query(null)
            .fragment(null)
            .build()

        val cleanUrlString = cleanUrl.toString().removeSuffix("/")

        val suffix = supportedIndexFiles.firstOrNull { cleanUrlString.endsWith("/$it") }
        if (suffix != null) {
            return cleanUrlString.removeSuffix("/$suffix")
        }

        val lastPathSegment = cleanUrl.pathSegments.lastOrNull().orEmpty()
        if (lastPathSegment.isNotEmpty() && lastPathSegment.contains('.')) {
            return null
        }

        return cleanUrlString
    }

    private suspend fun insert(repo: ExtensionRepo): Result {
        return try {
            repository.insertRepo(
                repo.baseUrl,
                repo.name,
                repo.shortName,
                repo.website,
                repo.signingKeyFingerprint,
            )
            Result.Success
        } catch (e: SaveExtensionRepoException) {
            logcat(LogPriority.WARN, e) { "SQL Conflict attempting to add new repository ${repo.baseUrl}" }
            return handleInsertionError(repo)
        }
    }

    /**
     * Error Handler for insert when there are trying to create new repositories
     *
     * SaveExtensionRepoException doesn't provide constraint info in exceptions.
     * First check if the conflict was on primary key. if so return RepoAlreadyExists
     * Then check if the conflict was on fingerprint. if so Return DuplicateFingerprint
     * If neither are found, there was some other Error, and return Result.Error
     *
     * @param repo Extension Repo holder for passing to DB/Error Dialog
     */
    private suspend fun handleInsertionError(repo: ExtensionRepo): Result {
        val repoExists = repository.getRepo(repo.baseUrl)
        if (repoExists != null) {
            return Result.RepoAlreadyExists
        }
        val matchingFingerprintRepo = repository.getRepoBySigningKeyFingerprint(repo.signingKeyFingerprint)
        if (matchingFingerprintRepo != null) {
            return Result.DuplicateFingerprint(matchingFingerprintRepo, repo)
        }
        return Result.Error
    }

    sealed interface Result {
        data class DuplicateFingerprint(val oldRepo: ExtensionRepo, val newRepo: ExtensionRepo) : Result
        data object InvalidUrl : Result
        data object RepoAlreadyExists : Result
        data object Success : Result
        data object Error : Result
    }
}
