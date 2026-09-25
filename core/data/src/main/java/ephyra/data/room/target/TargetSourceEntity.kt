package ephyra.data.room.target

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Installed/available source definition and user lifecycle state. Permission grants and credential
 * material are intentionally excluded; secure storage and trust policy own those concerns.
 */
@Entity(tableName = "target_sources")
data class TargetSourceEntity(
    @PrimaryKey
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    val kind: String,
    val revision: Long,
    @ColumnInfo(name = "capabilities_json")
    val capabilitiesJson: String,
    @ColumnInfo(name = "trust_level")
    val trustLevel: String,
    @ColumnInfo(name = "compatibility_level")
    val compatibilityLevel: String,
    @ColumnInfo(name = "content_types_json")
    val contentTypesJson: String,
    @ColumnInfo(name = "installation_state")
    val installationState: String,
    val enabled: Boolean,
    @ColumnInfo(name = "first_seen_at")
    val firstSeenAt: Long,
    @ColumnInfo(name = "last_changed_at")
    val lastChangedAt: Long,
) {
    init {
        require(sourceId.isNotBlank()) { "Target source id must not be blank" }
        require(displayName.isNotBlank()) { "Target source display name must not be blank" }
        require(revision > 0L) { "Target source revision must be positive" }
        require(firstSeenAt >= 0L && lastChangedAt >= 0L) { "Target source timestamps must not be negative" }
        require(installationState != "UNINSTALLED" || !enabled) {
            "An uninstalled target source cannot be enabled"
        }
    }
}
