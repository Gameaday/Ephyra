package ephyra.feature.more

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.domain.release.service.AppUpdateDownloader
import javax.inject.Inject

@HiltViewModel
class NewUpdateViewModel @Inject constructor(
    private val appUpdateDownloader: AppUpdateDownloader,
) : ViewModel() {

    fun acceptUpdate(downloadLink: String, versionName: String) {
        appUpdateDownloader.start(
            url = downloadLink,
            title = versionName,
        )
    }
}
