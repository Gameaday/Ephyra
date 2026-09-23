package ephyra.app.data.backup

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import ephyra.app.R
import ephyra.app.data.notification.NotificationReceiver
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.util.storage.getUriCompat
import ephyra.core.common.util.system.cancelNotification
import ephyra.core.common.util.system.logcat
import ephyra.core.common.util.system.notificationBuilder
import ephyra.core.common.util.system.notify
import ephyra.data.notification.Notifications
import logcat.LogPriority
import java.io.File
import ephyra.domain.backup.service.BackupNotifier as DomainBackupNotifier

class BackupNotifier(private val context: Context) : DomainBackupNotifier {

    private val notificationBitmap by lazy {
        BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
    }

    override fun showBackupProgress() {
        context.notify(
            Notifications.ID_BACKUP_PROGRESS,
            Notifications.CHANNEL_BACKUP_RESTORE_PROGRESS,
        ) {
            setContentTitle(context.stringResource(ephyra.app.core.common.R.string.creating_backup))
            setSmallIcon(ephyra.app.core.common.R.drawable.ic_refresh_24dp)
            setLargeIcon(notificationBitmap)
            setOngoing(true)
        }
    }

    override fun showBackupComplete(uriString: String) {
        context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)

        val backupUri = Uri.parse(uriString)
        // Offer a share action so the user can actually retrieve the file: the destination
        // may be app-specific storage (not browsable) or a SAF provider whose path is not
        // meaningful outside the app. Conversion can legitimately fail (e.g. a destination
        // outside every configured FileProvider root), so the notification must still post.
        val sharePendingIntent = runCatching {
            shareableUri(backupUri)?.let { shareable ->
                NotificationReceiver.shareBackupPendingActivity(context, shareable)
            }
        }.onFailure {
            logcat(LogPriority.WARN, it) { "Sharing unavailable for backup at $uriString" }
        }.getOrNull()

        context.notify(
            Notifications.ID_BACKUP_COMPLETE,
            Notifications.CHANNEL_BACKUP_RESTORE_COMPLETE,
        ) {
            setContentTitle(context.stringResource(ephyra.app.core.common.R.string.backup_created))
            setContentText(backupUri.path)
            setSmallIcon(ephyra.app.core.common.R.drawable.ic_ephyra)
            setLargeIcon(notificationBitmap)
            setAutoCancel(true)
            sharePendingIntent?.let {
                addAction(
                    ephyra.app.core.common.R.drawable.ic_ephyra,
                    context.stringResource(ephyra.app.core.common.R.string.action_share),
                    it,
                )
            }
        }
    }

    /**
     * Converts a backup destination into a URI other apps are allowed to read.
     *
     * SAF destinations are already content URIs. Raw file destinations must be routed
     * through our FileProvider — handing a bare `file://` URI to another app cannot be
     * granted read access on modern Android. Returns `null` when the URI is not shareable,
     * in which case the notification simply omits the action.
     */
    private fun shareableUri(uri: Uri): Uri? = when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> uri
        "file" -> uri.path?.let { path -> File(path).getUriCompat(context) }
        else -> null
    }

    override fun showBackupError(error: String?) {
        context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)

        context.notify(
            Notifications.ID_BACKUP_COMPLETE,
            Notifications.CHANNEL_BACKUP_RESTORE_COMPLETE,
        ) {
            setContentTitle(context.stringResource(ephyra.app.core.common.R.string.creating_backup_error))
            setContentText(error)
            setSmallIcon(ephyra.app.core.common.R.drawable.ic_ephyra)
            setLargeIcon(notificationBitmap)
            setAutoCancel(true)
        }
    }

    override fun showRestoreProgress(progress: Int, total: Int, title: String) {
        context.notify(
            Notifications.ID_RESTORE_PROGRESS,
            Notifications.CHANNEL_BACKUP_RESTORE_PROGRESS,
        ) {
            setContentTitle(context.stringResource(ephyra.app.core.common.R.string.restoring_backup))
            setContentText(title)
            setProgress(total, progress, false)
            setSmallIcon(ephyra.app.core.common.R.drawable.ic_refresh_24dp)
            setLargeIcon(notificationBitmap)
            setOngoing(true)
        }
    }

    override fun showRestoreComplete(time: Long, errorCount: Int, path: String?) {
        context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)

        context.notify(
            Notifications.ID_RESTORE_COMPLETE,
            Notifications.CHANNEL_BACKUP_RESTORE_COMPLETE,
        ) {
            setContentTitle(context.stringResource(ephyra.app.core.common.R.string.restore_completed))
            setContentText(path)
            setSmallIcon(ephyra.app.core.common.R.drawable.ic_ephyra)
            setLargeIcon(notificationBitmap)
            setAutoCancel(true)
        }
    }

    override fun showRestoreError(error: String?) {
        context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)

        context.notify(
            Notifications.ID_RESTORE_COMPLETE,
            Notifications.CHANNEL_BACKUP_RESTORE_COMPLETE,
        ) {
            setContentTitle(context.stringResource(ephyra.app.core.common.R.string.restoring_backup_error))
            setContentText(error)
            setSmallIcon(ephyra.app.core.common.R.drawable.ic_ephyra)
            setLargeIcon(notificationBitmap)
            setAutoCancel(true)
        }
    }
}
