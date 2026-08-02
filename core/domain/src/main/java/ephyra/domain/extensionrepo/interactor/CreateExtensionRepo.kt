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

        val normalizedBaseUrl = normalizeKnownRepoUrl(baseUrl)
        return service.fetchRepoDetails(normalizedBaseUrl)?.let { insert(it) } ?: Result.InvalidUrl
    }

    private fun parseBaseUrl(input: String): String? {
        val formattedInput = input.toHttpUrlOrNull()
            ?.newBuilder()
            ?.query(null)
            ?.fragment(null)
            ?.build()
            ?.toString()
            ?.removeSuffix("/")
            ?: return null

        val suffix = supportedIndexFiles.firstOrNull { formattedInput.endsWith("/$it") }
        if (suffix != null) {
            return formattedInput.removeSuffix("/$suffix")
        }

        val lastSegment = formattedInput.substringAfterLast('/', "")
        if (lastSegment.contains('.')) {
            return null
        }

        return formattedInput
    }

    private fun normalizeKnownRepoUrl(baseUrl: String): String {
        val host = baseUrl.toHttpUrlOrNull()?.host.orEmpty()
        if (host == "keiyoushi.github.io") {
            return "https://raw.githubusercontent.com/keiyoushi/extensions/repo"
        }
        return baseUrl
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
