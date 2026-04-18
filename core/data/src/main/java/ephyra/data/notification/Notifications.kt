package ephyra.data.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_DEFAULT
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_HIGH
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_LOW
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.notification.NotificationIds
import ephyra.core.common.util.system.buildNotificationChannel
import ephyra.core.common.util.system.buildNotificationChannelGroup
import ephyra.i18n.MR

/**
 * Notification channel and ID constants for the app.
 *
 * All public constants are now delegates to [NotificationIds] (in `core/common`) so that
 * modules that cannot depend on `data` can still reference the same values.
 *
 * This object retains the `createChannels()` method and the private GROUP_* strings because
 * channel creation requires Android [Context] and belongs in the data layer.
 */
object Notifications {

    /**
     * Common notification channel and ids used anywhere.
     */
    const val CHANNEL_COMMON = NotificationIds.CHANNEL_COMMON
    const val ID_DOWNLOAD_IMAGE = NotificationIds.ID_DOWNLOAD_IMAGE

    /**
     * Notification channel and ids used by the library updater.
     */
    private const val GROUP_LIBRARY = "group_library"
    const val CHANNEL_LIBRARY_PROGRESS = NotificationIds.CHANNEL_LIBRARY_PROGRESS
    const val ID_LIBRARY_PROGRESS = NotificationIds.ID_LIBRARY_PROGRESS
    const val ID_LIBRARY_SIZE_WARNING = NotificationIds.ID_LIBRARY_SIZE_WARNING
    const val CHANNEL_LIBRARY_ERROR = NotificationIds.CHANNEL_LIBRARY_ERROR
    const val ID_LIBRARY_ERROR = NotificationIds.ID_LIBRARY_ERROR
    const val ID_LIBRARY_DEAD_SOURCES = NotificationIds.ID_LIBRARY_DEAD_SOURCES
    const val ID_LIBRARY_MIGRATION_SUGGESTION = NotificationIds.ID_LIBRARY_MIGRATION_SUGGESTION

    /**
     * Notification channel and ids used by the authority matching job.
     */
    const val CHANNEL_MATCH_PROGRESS = NotificationIds.CHANNEL_MATCH_PROGRESS
    const val ID_MATCH_PROGRESS = NotificationIds.ID_MATCH_PROGRESS
    const val ID_MATCH_COMPLETE = NotificationIds.ID_MATCH_COMPLETE

    /**
     * Notification channel and ids used by the downloader.
     */
    private const val GROUP_DOWNLOADER = "group_downloader"
    const val CHANNEL_DOWNLOADER_PROGRESS = NotificationIds.CHANNEL_DOWNLOADER_PROGRESS
    const val ID_DOWNLOAD_CHAPTER_PROGRESS = NotificationIds.ID_DOWNLOAD_CHAPTER_PROGRESS
    const val CHANNEL_DOWNLOADER_ERROR = NotificationIds.CHANNEL_DOWNLOADER_ERROR
    const val ID_DOWNLOAD_CHAPTER_ERROR = NotificationIds.ID_DOWNLOAD_CHAPTER_ERROR

    /**
     * Notification channel and ids used by the library updater.
     */
    const val CHANNEL_NEW_CHAPTERS = NotificationIds.CHANNEL_NEW_CHAPTERS
    const val ID_NEW_CHAPTERS = NotificationIds.ID_NEW_CHAPTERS
    const val GROUP_NEW_CHAPTERS = NotificationIds.GROUP_NEW_CHAPTERS

    /**
     * Notification channel and ids used by the backup/restore system.
     */
    private const val GROUP_BACKUP_RESTORE = "group_backup_restore"
    const val CHANNEL_BACKUP_RESTORE_PROGRESS = NotificationIds.CHANNEL_BACKUP_RESTORE_PROGRESS
    const val ID_BACKUP_PROGRESS = NotificationIds.ID_BACKUP_PROGRESS
    const val ID_RESTORE_PROGRESS = NotificationIds.ID_RESTORE_PROGRESS
    const val CHANNEL_BACKUP_RESTORE_COMPLETE = NotificationIds.CHANNEL_BACKUP_RESTORE_COMPLETE
    const val ID_BACKUP_COMPLETE = NotificationIds.ID_BACKUP_COMPLETE
    const val ID_RESTORE_COMPLETE = NotificationIds.ID_RESTORE_COMPLETE

    /**
     * Notification channel used for Incognito Mode
     */
    const val CHANNEL_INCOGNITO_MODE = NotificationIds.CHANNEL_INCOGNITO_MODE
    const val ID_INCOGNITO_MODE = NotificationIds.ID_INCOGNITO_MODE

    /**
     * Notification channel and ids used for app and extension updates.
     */
    private const val GROUP_APK_UPDATES = "group_apk_updates"
    const val CHANNEL_APP_UPDATE = NotificationIds.CHANNEL_APP_UPDATE
    const val ID_APP_UPDATER = NotificationIds.ID_APP_UPDATER
    const val ID_APP_UPDATE_PROMPT = NotificationIds.ID_APP_UPDATE_PROMPT
    const val ID_APP_UPDATE_ERROR = NotificationIds.ID_APP_UPDATE_ERROR
    const val CHANNEL_EXTENSIONS_UPDATE = NotificationIds.CHANNEL_EXTENSIONS_UPDATE
    const val ID_UPDATES_TO_EXTS = NotificationIds.ID_UPDATES_TO_EXTS
    const val ID_EXTENSION_INSTALLER = NotificationIds.ID_EXTENSION_INSTALLER

    private val deprecatedChannels = listOf(
        "downloader_channel",
        "downloader_complete_channel",
        "backup_restore_complete_channel",
        "library_channel",
        "library_progress_channel",
        "updates_ext_channel",
        "downloader_cache_renewal",
        "crash_logs_channel",
        "library_skipped_channel",
    )

    /**
     * Creates the notification channels introduced in Android Oreo.
     * This won't do anything on Android versions that don't support notification channels.
     *
     * @param context The application context.
     */
    fun createChannels(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)

        // Delete old notification channels
        deprecatedChannels.forEach(notificationManager::deleteNotificationChannel)

        notificationManager.createNotificationChannelGroupsCompat(
            listOf(
                buildNotificationChannelGroup(GROUP_BACKUP_RESTORE) {
                    setName(context.stringResource(MR.strings.label_backup))
                },
                buildNotificationChannelGroup(GROUP_DOWNLOADER) {
                    setName(context.stringResource(MR.strings.download_notifier_downloader_title))
                },
                buildNotificationChannelGroup(GROUP_LIBRARY) {
                    setName(context.stringResource(MR.strings.label_library))
                },
                buildNotificationChannelGroup(GROUP_APK_UPDATES) {
                    setName(context.stringResource(MR.strings.label_recent_updates))
                },
            ),
        )

        notificationManager.createNotificationChannelsCompat(
            listOf(
                buildNotificationChannel(CHANNEL_COMMON, IMPORTANCE_LOW) {
                    setName(context.stringResource(MR.strings.channel_common))
                },
                buildNotificationChannel(CHANNEL_LIBRARY_PROGRESS, IMPORTANCE_LOW) {
                    setName(context.stringResource(MR.strings.channel_progress))
                    setGroup(GROUP_LIBRARY)
                    setShowBadge(false)
                },
                buildNotificationChannel(CHANNEL_LIBRARY_ERROR, IMPORTANCE_LOW) {
                    setName(context.stringResource(MR.strings.channel_errors))
                    setGroup(GROUP_LIBRARY)
                    setShowBadge(false)
                },
                buildNotificationChannel(CHANNEL_NEW_CHAPTERS, IMPORTANCE_DEFAULT) {
                    setName(context.stringResource(MR.strings.channel_new_chapters))
                },
                buildNotificationChannel(CHANNEL_DOWNLOADER_PROGRESS, IMPORTANCE_LOW) {
                    setName(context.stringResource(MR.strings.channel_progress))
                    setGroup(GROUP_DOWNLOADER)
                    setShowBadge(false)
                },
                buildNotificationChannel(CHANNEL_DOWNLOADER_ERROR, IMPORTANCE_LOW) {
                    setName(context.stringResource(MR.strings.channel_errors))
                    setGroup(GROUP_DOWNLOADER)
                    setShowBadge(false)
                },
                buildNotificationChannel(CHANNEL_BACKUP_RESTORE_PROGRESS, IMPORTANCE_LOW) {
                    setName(context.stringResource(MR.strings.channel_progress))
                    setGroup(GROUP_BACKUP_RESTORE)
                    setShowBadge(false)
                },
                buildNotificationChannel(CHANNEL_BACKUP_RESTORE_COMPLETE, IMPORTANCE_HIGH) {
                    setName(context.stringResource(MR.strings.channel_complete))
                    setGroup(GROUP_BACKUP_RESTORE)
                    setShowBadge(false)
                    setSound(null, null)
                },
                buildNotificationChannel(CHANNEL_INCOGNITO_MODE, IMPORTANCE_LOW) {
                    setName(context.stringResource(MR.strings.pref_incognito_mode))
                },
                buildNotificationChannel(CHANNEL_APP_UPDATE, IMPORTANCE_DEFAULT) {
                    setGroup(GROUP_APK_UPDATES)
                    setName(context.stringResource(MR.strings.channel_app_updates))
                },
                buildNotificationChannel(CHANNEL_EXTENSIONS_UPDATE, IMPORTANCE_DEFAULT) {
                    setGroup(GROUP_APK_UPDATES)
                    setName(context.stringResource(MR.strings.channel_ext_updates))
                },
                buildNotificationChannel(CHANNEL_MATCH_PROGRESS, IMPORTANCE_LOW) {
                    setName(context.stringResource(MR.strings.channel_match_progress))
                    setGroup(GROUP_LIBRARY)
                    setShowBadge(false)
                },
            ),
        )
    }
}
