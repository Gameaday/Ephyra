package ephyra.presentation.manga.track

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import ephyra.domain.track.model.TrackSearch
import kotlin.random.Random

internal class TrackerSearchPreviewProvider : PreviewParameterProvider<@Composable () -> Unit> {
    private val fullPageWithSecondSelected = @Composable {
        val items = someTrackSearches().take(30).toList()
        TrackerSearch(
            state = TextFieldState(initialText = "search text"),
            onDispatchQuery = {},
            queryResult = Result.success(items),
            selected = items[1],
            onSelectedChange = {},
            onConfirmSelection = {},
            onDismissRequest = {},
            supportsPrivateTracking = false,
        )
    }
    private val fullPageWithoutSelected = @Composable {
        TrackerSearch(
            state = TextFieldState(),
            onDispatchQuery = {},
            queryResult = Result.success(someTrackSearches().take(30).toList()),
            selected = null,
            onSelectedChange = {},
            onConfirmSelection = {},
            onDismissRequest = {},
            supportsPrivateTracking = false,
        )
    }
    private val loading = @Composable {
        TrackerSearch(
            state = TextFieldState(),
            onDispatchQuery = {},
            queryResult = null,
            selected = null,
            onSelectedChange = {},
            onConfirmSelection = {},
            onDismissRequest = {},
            supportsPrivateTracking = false,
        )
    }
    private val fullPageWithPrivateTracking = @Composable {
        val items = someTrackSearches().take(30).toList()
        TrackerSearch(
            state = TextFieldState(initialText = "search text"),
            onDispatchQuery = {},
            queryResult = Result.success(items),
            selected = items[1],
            onSelectedChange = {},
            onConfirmSelection = {},
            onDismissRequest = {},
            supportsPrivateTracking = true,
        )
    }
    override val values: Sequence<@Composable () -> Unit> = sequenceOf(
        fullPageWithSecondSelected,
        fullPageWithoutSelected,
        loading,
        fullPageWithPrivateTracking,
    )

    private fun someTrackSearches(): Sequence<TrackSearch> = sequence {
        while (true) {
            yield(randTrackSearch())
        }
    }

    private fun randTrackSearch() = TrackSearch(
        remote_id = Random.nextLong(),
        title = lorem((1..10).random()).joinToString(),
        tracking_url = "https://example.com/tracker-example",
        cover_url = "https://example.com/cover.png",
        summary = lorem((0..40).random()).joinToString(),
        publishing_status = if (Random.nextBoolean()) "Finished" else "",
        publishing_type = if (Random.nextBoolean()) "Oneshot" else "",
        artists = randomNames(),
        authors = randomNames(),
        total_chapters = (100L..1000L).random(),
    )

    private fun randomNames(): List<String> = (0..(0..3).random()).map { lorem((3..5).random()).joinToString() }

    private fun lorem(words: Int): Sequence<String> =
        LoremIpsum(words).values
}
