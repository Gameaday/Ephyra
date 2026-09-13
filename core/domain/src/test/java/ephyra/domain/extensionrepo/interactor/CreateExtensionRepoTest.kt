package ephyra.domain.extensionrepo.interactor

import ephyra.domain.extensionrepo.exception.SaveExtensionRepoException
import ephyra.domain.extensionrepo.model.ExtensionRepo
import ephyra.domain.extensionrepo.repository.ExtensionRepoRepository
import ephyra.domain.extensionrepo.service.ExtensionRepoService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CreateExtensionRepoTest {

    private val repository: ExtensionRepoRepository = mockk(relaxed = true)
    private val service: ExtensionRepoService = mockk(relaxed = true)

    private val createExtensionRepo = CreateExtensionRepo(
        repository = repository,
        service = service,
    )

    private val sampleRepo = ExtensionRepo(
        baseUrl = "https://repo.example.com",
        name = "Community Extensions",
        shortName = "community",
        website = "https://example.com",
        signingKeyFingerprint = "fingerprint123",
    )

    @BeforeEach
    fun setUp() {
        coEvery { service.fetchRepoDetails(any()) } returns sampleRepo
    }

    @Test
    fun `unwraps tachiyomi deep link url successfully`() = runTest {
        val deepLink = "tachiyomi://add-repo?url=" +
            "https%3A%2F%2Frepo.example.com%2Findex.min.json"
        val result = createExtensionRepo.await(deepLink)

        assertEquals(CreateExtensionRepo.Result.Success, result)
        coVerify { service.fetchRepoDetails("https://repo.example.com") }
        coVerify {
            repository.insertRepo(
                sampleRepo.baseUrl,
                sampleRepo.name,
                sampleRepo.shortName,
                sampleRepo.website,
                sampleRepo.signingKeyFingerprint,
            )
        }
    }

    @Test
    fun `unwraps mihon deep link url successfully`() = runTest {
        val deepLink = "mihon://add-repo?url=https%3A%2F%2Frepo.example.com"
        val result = createExtensionRepo.await(deepLink)

        assertEquals(CreateExtensionRepo.Result.Success, result)
        coVerify { service.fetchRepoDetails("https://repo.example.com") }
    }

    @Test
    fun `unwraps ephyra deep link url successfully`() = runTest {
        val deepLink = "ephyra://add-repo?url=https%3A%2F%2Frepo.example.com"
        val result = createExtensionRepo.await(deepLink)

        assertEquals(CreateExtensionRepo.Result.Success, result)
        coVerify { service.fetchRepoDetails("https://repo.example.com") }
    }

    @Test
    fun `strips index min json and repo json file suffixes`() = runTest {
        val customRepo = ExtensionRepo(
            baseUrl = "https://custom.repo.org/manga",
            name = "Custom Repo",
            shortName = "custom",
            website = "https://custom.repo.org",
            signingKeyFingerprint = "custom123",
        )
        coEvery { service.fetchRepoDetails("https://custom.repo.org/manga") } returns customRepo

        val result = createExtensionRepo.await("https://custom.repo.org/manga/index.min.json")
        assertEquals(CreateExtensionRepo.Result.Success, result)

        val result2 = createExtensionRepo.await("https://custom.repo.org/manga/repo.json")
        assertEquals(CreateExtensionRepo.Result.Success, result2)

        coVerify(exactly = 2) { service.fetchRepoDetails("https://custom.repo.org/manga") }
    }

    @Test
    fun `handles duplicate fingerprint when inserting repo`() = runTest {
        val existingRepo = ExtensionRepo(
            baseUrl = "https://old.repo.org",
            name = "Old Repo",
            shortName = "old",
            website = "https://old.repo.org",
            signingKeyFingerprint = sampleRepo.signingKeyFingerprint,
        )
        coEvery {
            repository.insertRepo(any(), any(), any(), any(), any())
        } throws SaveExtensionRepoException(Exception("Conflict"))
        coEvery { repository.getRepo(sampleRepo.baseUrl) } returns null
        coEvery { repository.getRepoBySigningKeyFingerprint(sampleRepo.signingKeyFingerprint) } returns existingRepo

        val result = createExtensionRepo.await(sampleRepo.baseUrl)

        assertInstanceOf(CreateExtensionRepo.Result.DuplicateFingerprint::class.java, result)
        val dupResult = result as CreateExtensionRepo.Result.DuplicateFingerprint
        assertEquals(existingRepo, dupResult.oldRepo)
        assertEquals(sampleRepo, dupResult.newRepo)
    }

    @Test
    fun `handles repo already exists when inserting repo`() = runTest {
        coEvery {
            repository.insertRepo(any(), any(), any(), any(), any())
        } throws SaveExtensionRepoException(Exception("Conflict"))
        coEvery { repository.getRepo(sampleRepo.baseUrl) } returns sampleRepo

        val result = createExtensionRepo.await(sampleRepo.baseUrl)

        assertEquals(CreateExtensionRepo.Result.RepoAlreadyExists, result)
    }
}
