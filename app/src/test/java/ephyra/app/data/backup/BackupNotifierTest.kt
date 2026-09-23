package ephyra.app.data.backup

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.data.notification.Notifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class BackupNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // Robolectric denies runtime permissions by default, and the notification helpers
        // no-op without POST_NOTIFICATIONS, which would hide the posted notifications.
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        // Robolectric does not pre-create the app's channels either; posting to a missing
        // channel is dropped instead of being recorded.
        Notifications.createChannels(context)
    }

    private fun postedBackupCompleteNotification() = context
        .getSystemService(NotificationManager::class.java)
        .activeNotifications
        .first { it.id == Notifications.ID_BACKUP_COMPLETE }
        .notification

    private fun shareUriOf(notification: Notification): Uri? {
        val action = notification.actions.orEmpty().firstOrNull() ?: return null
        val chooser = requireNotNull(shadowOf(action.actionIntent).savedIntent)
        val send = requireNotNull(
            IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java),
        )
        return IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java)
    }

    private fun assertNotified(notification: Notification) {
        assertNotNull(notification.extras.getCharSequence(Notification.EXTRA_TITLE))
    }

    @Test
    fun backupCompleteOffersShareActionForSafDocument() {
        val safUri = "content://com.android.externalstorage.documents/document/" +
            "primary%3AEphyra%2Fautobackup%2Fbackup.tachibk"
        BackupNotifier(context).showBackupComplete(safUri)

        val notification = postedBackupCompleteNotification()
        assertEquals(1, notification.actions.orEmpty().size)
        assertEquals("Share", notification.actions[0].title.toString())
        assertEquals(Uri.parse(safUri), shareUriOf(notification))
    }

    @Test
    fun backupCompleteNeverSharesRawFileDestination() {
        val backupDir = requireNotNull(context.getExternalFilesDir("autobackup"))
        val backupFile = File(backupDir, "backup.tachibk").apply { writeText("backup") }
        BackupNotifier(context).showBackupComplete(backupFile.toURI().toString())

        val notification = postedBackupCompleteNotification()
        assertNotified(notification)

        // A bare file:// URI cannot be granted to a receiving app, so it must either be
        // rewritten to a FileProvider URI or the action must be dropped. (Whether the
        // provider resolves depends on the host: a JVM test running on Windows cannot
        // resolve Android-style file paths, so only the invariant is asserted here.)
        val shared = shareUriOf(notification)
        if (shared != null) {
            assertEquals("content", shared.scheme)
            assertEquals("${context.packageName}.provider", shared.authority)
            assertTrue(shared.toString().endsWith("/backup.tachibk"))
        }
    }

    @Test
    fun backupCompleteStillPostsWhenDestinationCannotBeShared() {
        // Not under any configured FileProvider root: the conversion fails internally and
        // must not take the backup notification down with it.
        BackupNotifier(context).showBackupComplete("file:///snapshot/backup.tachibk")

        val notification = postedBackupCompleteNotification()
        assertEquals(0, notification.actions.orEmpty().size)
        assertNotified(notification)
    }

    @Test
    fun backupCompleteOmitsShareActionForUnsupportedDestination() {
        BackupNotifier(context).showBackupComplete("https://example.org/backup.tachibk")

        val notification = postedBackupCompleteNotification()
        assertEquals(0, notification.actions.orEmpty().size)
        assertNotified(notification)
    }
}
