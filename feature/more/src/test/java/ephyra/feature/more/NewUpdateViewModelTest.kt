package ephyra.feature.more

import ephyra.domain.release.service.AppUpdateDownloader
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class NewUpdateViewModelTest {

    private val appUpdateDownloader = mockk<AppUpdateDownloader>(relaxed = true)
    private val viewModel = NewUpdateViewModel(appUpdateDownloader)

    @Test
    fun `acceptUpdate starts download with download link and version name`() {
        val downloadLink = "https://example.com/update.apk"
        val versionName = "v1.2.3"

        viewModel.acceptUpdate(downloadLink, versionName)

        verify(exactly = 1) {
            appUpdateDownloader.start(
                url = downloadLink,
                title = versionName,
            )
        }
    }
}
