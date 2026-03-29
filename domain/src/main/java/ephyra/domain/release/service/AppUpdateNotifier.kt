package ephyra.domain.release.service

import android.net.Uri
import ephyra.domain.release.model.Release

interface AppUpdateNotifier {
    fun cancel()
    fun promptUpdate(release: Release)
    fun onDownloadStarted(title: String? = null)
    fun onProgressChange(progress: Int)
    fun promptInstall(uri: Uri)
    fun onDownloadError(url: String)
}
