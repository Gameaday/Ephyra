package ephyra.app.security

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * SEC-003 — `largeHeap` and the battery-optimization exemption must be justified, not incidental.
 *
 * Both are still present in the shipped manifest and the task is "remove or justify". Removal is
 * not yet safe: the reconstruction has not replaced the reader, and the large-heap request and the
 * exemption are load-bearing for the current continuous reader's decode and prefetch. Deleting
 * either today would change the behaviour of code the program has not yet replaced.
 *
 * So the honest state is *declared debt*, and the useful gate is not "is it gone" but "is every
 * sensitive privilege still individually accounted for, and has no new one appeared". Ordinary
 * permissions such as INTERNET are not in scope; the sensitive set is the one below. Each entry
 * states why the privilege ships and what retires it, and the manifest may not carry a sensitive
 * permission that is undeclared. Declarations are also checked against the manifest in both
 * directions, so the record cannot drift from reality.
 *
 * E2 by construction — reads the manifest from disk, so it needs no device.
 */
class ManifestPrivilegeTest {

    @Test
    fun `no sensitive permission is requested without a written justification`() {
        val undeclared = sensitivePermissionsRequested() - DECLARED_PRIVILEGES.keys
        assertTrue(
            undeclared.isEmpty(),
            "Sensitive permission(s) requested without a justification: ${undeclared.sorted()}. " +
                "SEC-003 requires a reason and a removal condition in DECLARED_PRIVILEGES.",
        )
    }

    @Test
    fun `no declaration outlives the permission it describes`() {
        val stale = DECLARED_PRIVILEGES.keys - sensitivePermissionsRequested()
        assertTrue(
            stale.isEmpty(),
            "DECLARED_PRIVILEGES still lists ${stale.sorted()}, which the manifest no longer " +
                "requests. Delete the declaration so the record stays truthful.",
        )
    }

    @Test
    fun `every declaration states both a reason and a removal condition`() {
        val incomplete = DECLARED_PRIVILEGES.filterValues { it.reason.isBlank() || it.removalCondition.isBlank() }
        assertTrue(
            incomplete.isEmpty(),
            "Declaration(s) missing a reason or a removal condition: ${incomplete.keys.sorted()}. " +
                "SEC-003 debt without an expiry is just an undocumented permission.",
        )
    }

    @Test
    fun `large heap is either absent or explicitly justified`() {
        val largeHeap = manifestText().contains("android:largeHeap=\"true\"")
        if (largeHeap) {
            assertTrue(
                LARGE_HEAP_DECLARATION.reason.isNotBlank() &&
                    LARGE_HEAP_DECLARATION.removalCondition.isNotBlank(),
                "largeHeap=true is requested but its justification is incomplete. SEC-003 debt " +
                    "without a removal condition is just an undocumented request for more memory.",
            )
        }
    }

    private fun sensitivePermissionsRequested(): Set<String> =
        PERMISSION_PATTERN.findAll(manifestText()).map { it.groupValues[1] }.toSet() - ORDINARY

    private fun manifestText(): String = manifest().readText()

    private fun manifest(): File = File(repositoryRoot(), "app/src/main/AndroidManifest.xml")

    private fun repositoryRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null && !File(directory, "settings.gradle.kts").exists()) {
            directory = directory.parentFile
        }
        return requireNotNull(directory) { "Could not locate repository root" }
    }

    private data class Justification(val reason: String, val removalCondition: String)

    private companion object {
        val PERMISSION_PATTERN = Regex("""<uses-permission\s+android:name="([^"]+)"""")

        /** Routine permissions that carry no privilege argument and need no justification. */
        val ORDINARY = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            "android.permission.FOREGROUND_SERVICE_SHORT_SERVICE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.WAKE_LOCK",
        )

        val DECLARED_PRIVILEGES = mapOf(
            "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" to Justification(
                reason = "Legacy chapter prefetch and decode are interrupted under Doze, so the app " +
                    "asks to be exempted to let an in-flight fetch finish.",
                removalCondition = "Move prefetch onto WorkManager constraints, then drop the exemption.",
            ),
            "android.permission.REQUEST_INSTALL_PACKAGES" to Justification(
                reason = "Self-update of a sideloaded build, which is how nightly channel updates ship.",
                removalCondition = "Replace with an in-app store update path and remove sideloading.",
            ),
            "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION" to Justification(
                reason = "Paired with REQUEST_INSTALL_PACKAGES so an update does not interrupt the user " +
                    "at a system prompt.",
                removalCondition = "Removed together with REQUEST_INSTALL_PACKAGES.",
            ),
            "android.permission.READ_APP_SPECIFIC_LOCALES" to Justification(
                reason = "Lets the reader and settings surfaces follow the per-app language the user " +
                    "chose in system settings rather than only the device locale. Declared alongside " +
                    "an explicit tools:ignore for the ProtectedPermissions lint check.",
                removalCondition = "None required; this is a deliberate user-facing capability. Drop " +
                    "the declaration only if per-app locale support is removed.",
            ),
            "android.permission.WRITE_EXTERNAL_STORAGE" to Justification(
                reason = "Capped at maxSdkVersion=28 so the pre-scoped-storage download and sideload " +
                    "path can place chapter files in shared storage. It grants nothing on API 29+.",
                removalCondition = "Delete the declaration once the minimum supported SDK reaches 29 " +
                    "and all remaining file writes go through scoped storage or MediaStore.",
            ),
        )

        /**
         * `android:largeHeap` is a manifest attribute, not a permission, so it cannot appear in
         * [DECLARED_PRIVILEGES]. It carries its own slot and the same justification requirement.
         */
        val LARGE_HEAP_DECLARATION = Justification(
            reason = "The current continuous reader decodes and holds several full-width page bitmaps " +
                "at once, which exceeds the default heap ceiling on large-display devices.",
            removalCondition = "RDR-005 replaces the continuous reader with a tile pipeline that " +
                "bounds working memory; re-measure peak heap on device and remove the attribute.",
        )
    }
}
