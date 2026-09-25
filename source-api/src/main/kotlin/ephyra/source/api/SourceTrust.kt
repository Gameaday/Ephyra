package ephyra.source.api

/** A source permission that can be granted independently from source installation. */
enum class SourcePermission {
    SEARCH,
    READ_LIBRARY,
    WRITE_LIBRARY,
    READ_PROGRESS,
    WRITE_PROGRESS,
}

/** A credential reference; secret material is owned by a secure storage adapter, never here. */
data class SourceCredentialRef(
    val id: String,
    val scheme: String,
) {
    init {
        require(id.isNotBlank()) { "Credential id must not be blank" }
        require(scheme.isNotBlank()) { "Credential scheme must not be blank" }
    }
}

enum class SourceCredentialStatus {
    ACTIVE,
    REVOKED,
    EXPIRED,
}

data class SourceCredentialState(
    val reference: SourceCredentialRef,
    val status: SourceCredentialStatus = SourceCredentialStatus.ACTIVE,
    val expiresAtMillis: Long? = null,
) {
    fun isUsable(nowMillis: Long): Boolean =
        status == SourceCredentialStatus.ACTIVE && (expiresAtMillis?.let { nowMillis < it } ?: true)
}

data class SourceTrustGrant(
    val sourceId: SourceId,
    val permissions: Set<SourcePermission>,
    val credentials: Set<SourceCredentialRef> = emptySet(),
    val grantedAtMillis: Long,
) {
    init {
        require(permissions.isNotEmpty()) { "A source grant must contain at least one permission" }
    }

    fun allows(permission: SourcePermission): Boolean = permission in permissions
}

data class SourceTrustAuditEvent(
    val sourceId: SourceId,
    val action: SourceTrustAction,
    val atMillis: Long,
    val permissions: Set<SourcePermission> = emptySet(),
    val credentialId: String? = null,
    val reason: String? = null,
)

enum class SourceTrustAction {
    INSTALLED,
    GRANTED,
    DENIED,
    CREDENTIAL_ADDED,
    CREDENTIAL_REVOKED,
    UNINSTALLED,
}

/** Pure authorization decisions; secure storage and permission UI are separate adapters. */
object SourceTrustPolicy {
    fun canUse(
        grant: SourceTrustGrant,
        permission: SourcePermission,
        credentials: Map<String, SourceCredentialState> = emptyMap(),
        nowMillis: Long,
    ): Boolean {
        if (!grant.allows(permission)) return false
        return grant.credentials.all { reference ->
            credentials[reference.id]?.isUsable(nowMillis) == true
        }
    }

    fun revokeCredentials(
        grant: SourceTrustGrant,
        credentialIds: Set<String>,
        atMillis: Long,
    ): Pair<SourceTrustGrant, SourceTrustAuditEvent> {
        require(credentialIds.isNotEmpty()) { "credentialIds must not be empty" }
        require(credentialIds.all { it.isNotBlank() }) { "Credential ids must not be blank" }
        val known = grant.credentials.associateBy { it.id }
        require(credentialIds.all(known::containsKey)) { "Cannot revoke a credential not granted to the source" }
        val next = grant.copy(credentials = grant.credentials.filterNot { it.id in credentialIds }.toSet())
        return next to SourceTrustAuditEvent(
            sourceId = grant.sourceId,
            action = SourceTrustAction.CREDENTIAL_REVOKED,
            atMillis = atMillis,
            credentialId = credentialIds.sorted().joinToString(","),
            reason = "Revoked ${credentialIds.size} credential(s)",
        )
    }
}
