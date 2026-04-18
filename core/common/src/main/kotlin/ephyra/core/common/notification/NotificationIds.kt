package ephyra.core.common.notification

/**
 * Notification channel IDs and notification IDs used by the application.
 *
 * Lives in `core/common` so that any module (including `feature/*`) can reference
 * these constants without taking a dependency on the `data` layer.
 *
 * The [ephyra.data.notification.Notifications] object in `:data` handles channel
 * _creation_ (which requires Android Context) and references these constants directly.
 */
object NotificationIds {

    /**
     * Common notification channel and IDs used anywhere.
     */
    const val CHANNEL_COMMON = "common_channel"
    const val ID_DOWNLOAD_IMAGE = 2

    /**
     * Notification channel and IDs used by the library updater.
     */
    const val CHANNEL_LIBRARY_PROGRESS = "library_progress_channel"
    const val ID_LIBRARY_PROGRESS = -101
    const val ID_LIBRARY_SIZE_WARNING = -103
    const val CHANNEL_LIBRARY_ERROR = "library_errors_channel"
    const val ID_LIBRARY_ERROR = -102
    const val ID_LIBRARY_DEAD_SOURCES = -104
    const val ID_LIBRARY_MIGRATION_SUGGESTION = -105

    /**
     * Notification channel and IDs used by the authority matching job.
     */
    const val CHANNEL_MATCH_PROGRESS = "match_progress_channel"
    const val ID_MATCH_PROGRESS = -601
    const val ID_MATCH_COMPLETE = -602

    /**
     * Notification channel and IDs used by the downloader.
     */
    const val CHANNEL_DOWNLOADER_PROGRESS = "downloader_progress_channel"
    const val ID_DOWNLOAD_CHAPTER_PROGRESS = -201
    const val CHANNEL_DOWNLOADER_ERROR = "downloader_error_channel"
    const val ID_DOWNLOAD_CHAPTER_ERROR = -202

    /**
     * Notification channel and IDs used by the library updater (new chapters).
     */
    const val CHANNEL_NEW_CHAPTERS = "new_chapters_channel"
    const val ID_NEW_CHAPTERS = -301
    const val GROUP_NEW_CHAPTERS = "ephyra.app.NEW_CHAPTERS"

    /**
     * Notification channel and IDs used by the backup/restore system.
     */
    const val CHANNEL_BACKUP_RESTORE_PROGRESS = "backup_restore_progress_channel"
    const val ID_BACKUP_PROGRESS = -501
    const val ID_RESTORE_PROGRESS = -503
    const val CHANNEL_BACKUP_RESTORE_COMPLETE = "backup_restore_complete_channel_v2"
    const val ID_BACKUP_COMPLETE = -502
    const val ID_RESTORE_COMPLETE = -504

    /**
     * Notification channel used for Incognito Mode.
     */
    const val CHANNEL_INCOGNITO_MODE = "incognito_mode_channel"
    const val ID_INCOGNITO_MODE = -701

    /**
     * Notification channel and IDs used for app and extension updates.
     */
    const val CHANNEL_APP_UPDATE = "app_apk_update_channel"
    const val ID_APP_UPDATER = 1
    const val ID_APP_UPDATE_PROMPT = 2
    const val ID_APP_UPDATE_ERROR = 3
    const val CHANNEL_EXTENSIONS_UPDATE = "ext_apk_update_channel"
    const val ID_UPDATES_TO_EXTS = -401
    const val ID_EXTENSION_INSTALLER = -402
}
