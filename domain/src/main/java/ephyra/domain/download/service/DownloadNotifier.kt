package ephyra.domain.download.service

import android.app.PendingIntent
import ephyra.domain.download.model.Download

interface DownloadNotifier {
    fun dismissProgress()
    suspend fun onProgressChange(download: Download)
    fun onPaused()
    fun onComplete()
    fun onWarning(reason: String, timeout: Long? = null, contentIntent: PendingIntent? = null, mangaId: Long? = null)
    fun onError(error: String? = null, chapter: String? = null, mangaTitle: String? = null, mangaId: Long? = null)
}
