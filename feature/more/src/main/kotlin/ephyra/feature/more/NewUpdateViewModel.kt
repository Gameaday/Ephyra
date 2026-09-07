package ephyra.feature.more

import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.domain.release.service.AppUpdateDownloader
import ephyra.presentation.core.udf.BaseUdfViewModel
import javax.inject.Inject

@HiltViewModel
class NewUpdateViewModel @Inject constructor(
    private val appUpdateDownloader: AppUpdateDownloader,
) : BaseUdfViewModel<Unit, NewUpdateEvent, Nothing>(Unit) {

    override fun onEvent(event: NewUpdateEvent) {
        when (event) {
            is NewUpdateEvent.AcceptUpdate -> {
                appUpdateDownloader.start(
                    url = event.downloadLink,
                    title = event.versionName,
                )
            }
        }
    }

    fun acceptUpdate(downloadLink: String, versionName: String) {
        onEvent(NewUpdateEvent.AcceptUpdate(downloadLink, versionName))
    }
}

sealed interface NewUpdateEvent {
    data class AcceptUpdate(val downloadLink: String, val versionName: String) : NewUpdateEvent
}
