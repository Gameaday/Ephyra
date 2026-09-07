package ephyra.domain.extension.interactor

import ephyra.core.common.preference.Preference
import ephyra.domain.extension.model.ExtensionPackageInfo
import ephyra.domain.extensionrepo.model.ExtensionRepo
import ephyra.domain.extensionrepo.repository.ExtensionRepoRepository
import ephyra.domain.source.service.SourcePreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TrustExtensionTest {

    private val extensionRepoRepository: ExtensionRepoRepository = mockk()
    private val sourcePreferences: SourcePreferences = mockk()
    private val trustedExtensionsPref: Preference<Set<String>> = mockk(relaxed = true)

    private val storedTrust = mutableSetOf<String>()

    private val trustExtension = TrustExtension(
        extensionRepoRepository = extensionRepoRepository,
        preferences = sourcePreferences,
    )

    @BeforeEach
    fun setUp() {
        storedTrust.clear()
        every { sourcePreferences.trustedExtensions() } returns trustedExtensionsPref
        coEvery { trustedExtensionsPref.get() } answers { storedTrust.toSet() }
        every { trustedExtensionsPref.set(any()) } answers {
            storedTrust.clear()
            storedTrust.addAll(firstArg<Set<String>>())
        }
        every { trustedExtensionsPref.delete() } answers {
            storedTrust.clear()
        }
        coEvery { extensionRepoRepository.getAll() } returns emptyList()
    }

    @Test
    fun `isTrusted returns true when fingerprint matches registered repository case-insensitively`() = runTest {
        val repo = ExtensionRepo(
            baseUrl = "https://example.com/repo",
            name = "Test Repo",
            shortName = "test",
            website = "https://example.com",
            signingKeyFingerprint = "A1B2C3D4E5F6",
        )
        coEvery { extensionRepoRepository.getAll() } returns listOf(repo)

        val pkgInfo = ExtensionPackageInfo("com.test.ext", 100L)
        val result = trustExtension.isTrusted(pkgInfo, listOf("a1b2c3d4e5f6"))

        assertTrue(result)
    }

    @Test
    fun `isTrusted returns true after trust is explicitly granted with casing tolerance`() = runTest {
        val pkgInfo = ExtensionPackageInfo("com.test.ext", 100L)

        // Initially untrusted
        assertFalse(trustExtension.isTrusted(pkgInfo, listOf("A1B2C3D4E5F6")))

        // Grant trust with lowercase or uppercase hash
        trustExtension.trust("com.test.ext", 100L, "A1B2C3D4E5F6")

        // Now trusted even if requested with lowercase hash
        assertTrue(trustExtension.isTrusted(pkgInfo, listOf("a1b2c3d4e5f6")))
    }

    @Test
    fun `isTrusted returns true if any signer in multi-signer APK matches trusted key`() = runTest {
        val pkgInfo = ExtensionPackageInfo("com.test.multi", 200L)
        trustExtension.trust("com.test.multi", 200L, "second_signer_hash")

        // First signer is unknown, second signer is trusted
        val result = trustExtension.isTrusted(pkgInfo, listOf("unknown_signer", "SECOND_SIGNER_HASH"))
        assertTrue(result)
    }

    @Test
    fun `isTrusted returns false for different version code`() = runTest {
        trustExtension.trust("com.test.ext", 100L, "hash123")

        val newVersionPkgInfo = ExtensionPackageInfo("com.test.ext", 101L)
        assertFalse(trustExtension.isTrusted(newVersionPkgInfo, listOf("hash123")))
    }

    @Test
    fun `revokeAll clears all trusted entries`() = runTest {
        val pkgInfo = ExtensionPackageInfo("com.test.ext", 100L)
        trustExtension.trust("com.test.ext", 100L, "hash123")
        assertTrue(trustExtension.isTrusted(pkgInfo, listOf("hash123")))

        trustExtension.revokeAll()
        assertFalse(trustExtension.isTrusted(pkgInfo, listOf("hash123")))
    }
}
