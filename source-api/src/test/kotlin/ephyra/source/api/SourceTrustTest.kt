package ephyra.source.api

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourceTrustTest {
    @Test
    fun `grant authorizes only explicitly granted permissions`() {
        val grant = grant(setOf(SourcePermission.SEARCH, SourcePermission.READ_LIBRARY))

        assertTrue(SourceTrustPolicy.canUse(grant, SourcePermission.SEARCH, nowMillis = 0L))
        assertFalse(SourceTrustPolicy.canUse(grant, SourcePermission.WRITE_LIBRARY, nowMillis = 0L))
    }

    @Test
    fun `expired credential blocks source use without deleting grant`() {
        val credential = SourceCredentialRef("token-1", "bearer")
        val grant = grant(setOf(SourcePermission.SEARCH), setOf(credential))
        val expired = mapOf(credential.id to SourceCredentialState(credential, expiresAtMillis = 10L))

        assertFalse(SourceTrustPolicy.canUse(grant, SourcePermission.SEARCH, expired, nowMillis = 10L))
        assertTrue(SourceTrustPolicy.canUse(grant, SourcePermission.SEARCH, expired, nowMillis = 9L))
    }

    @Test
    fun `revoking credentials requires granted identity and returns audit event`() {
        val credential = SourceCredentialRef("token-1", "bearer")
        val grant = grant(setOf(SourcePermission.SEARCH), setOf(credential))

        val (revoked, event) = SourceTrustPolicy.revokeCredentials(grant, setOf("token-1"), 42L)

        assertTrue(revoked.credentials.isEmpty())
        assertTrue(event.action == SourceTrustAction.CREDENTIAL_REVOKED)
        assertTrue(event.credentialId == "token-1")
    }

    @Test
    fun `unknown or blank credential revocation is rejected`() {
        val grant = grant(setOf(SourcePermission.SEARCH))

        assertThrowsIllegalArgument {
            SourceTrustPolicy.revokeCredentials(grant, setOf("unknown"), 0L)
        }
        assertThrowsIllegalArgument {
            SourceTrustPolicy.revokeCredentials(grant, setOf(" "), 0L)
        }
    }

    private fun grant(
        permissions: Set<SourcePermission>,
        credentials: Set<SourceCredentialRef> = emptySet(),
    ) = SourceTrustGrant(
        sourceId = SourceId("source"),
        permissions = permissions,
        credentials = credentials,
        grantedAtMillis = 0L,
    )

    private inline fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
