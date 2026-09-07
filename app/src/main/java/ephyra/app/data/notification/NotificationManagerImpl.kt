package ephyra.app.data.notification

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ephyra.core.common.notification.NotificationManager
import ephyra.data.notification.Notifications
import javax.inject.Inject

/**
 * Concrete implementation of the NotificationManager, bridging core logic with the
 * application's notification system. This implementation must reside in the app module
 * because it depends on specific notification channels and receivers that are part of
 * the monolithic core.
 */
class NotificationManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationManager {
    override fun dismissNewChaptersNotification(mangaId: Long) {
        NotificationReceiver.dismissNotification(context, mangaId.hashCode(), Notifications.ID_NEW_CHAPTERS)
    }
}
