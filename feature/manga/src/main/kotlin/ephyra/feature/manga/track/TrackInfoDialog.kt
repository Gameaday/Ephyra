package ephyra.feature.manga.track

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.util.lang.convertEpochMillisZone
import ephyra.core.common.util.lang.launchNonCancellable
import ephyra.core.common.util.lang.toLocalDate
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.interactor.DeleteTrack
import ephyra.domain.track.interactor.GetTracks
import ephyra.domain.track.interactor.RefreshTracks
import ephyra.domain.track.model.Track
import ephyra.domain.track.model.TrackSearch
import ephyra.domain.track.service.DeletableTracker
import ephyra.domain.track.service.EnhancedTracker
import ephyra.domain.track.service.Tracker
import ephyra.domain.track.service.TrackerManager
import ephyra.domain.ui.UiPreferences
import ephyra.presentation.core.components.LabeledCheckbox
import ephyra.presentation.core.components.material.AlertDialogContent
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.util.system.copyToClipboard
import ephyra.presentation.core.util.system.openInBrowser
import ephyra.presentation.core.util.system.toast
import ephyra.presentation.manga.track.TrackChapterSelector
import ephyra.presentation.manga.track.TrackDateSelector
import ephyra.presentation.manga.track.TrackInfoDialogHome
import ephyra.presentation.manga.track.TrackScoreSelector
import ephyra.presentation.manga.track.TrackStatusSelector
import ephyra.presentation.manga.track.TrackerSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

import androidx.compose.runtime.mutableStateListOf

import ephyra.presentation.core.components.AdaptiveSheet
import ephyra.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.Source

@Composable
fun TrackInfoDialog(
    manga: Manga,
    source: Source,
    onDismissRequest: () -> Unit,
) {
    val stack = remember { mutableStateListOf<TrackInfoDialogScreen>(TrackInfoDialogScreen.Home) }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        when (val currentScreen = stack.last()) {
            TrackInfoDialogScreen.Home -> TrackInfoDialogHomeScreen(
                mangaId = manga.id,
                mangaTitle = manga.title,
                sourceId = source.id,
                canonicalId = manga.canonicalId,
                onStatusClick = { stack.add(TrackInfoDialogScreen.Status(it.track!!, it.tracker.id)) },
                onChapterClick = { stack.add(TrackInfoDialogScreen.Chapter(it.track!!, it.tracker.id)) },
                onScoreClick = { stack.add(TrackInfoDialogScreen.Score(it.track!!, it.tracker.id)) },
                onStartDateEdit = { stack.add(TrackInfoDialogScreen.Date(it.track!!, it.tracker.id, true)) },
                onEndDateEdit = { stack.add(TrackInfoDialogScreen.Date(it.track!!, it.tracker.id, false)) },
                onNewSearch = {
                    stack.add(
                        TrackInfoDialogScreen.Search(
                            mangaId = manga.id,
                            initialQuery = it.track?.title ?: manga.title,
                            currentUrl = it.track?.remoteUrl,
                            serviceId = it.tracker.id,
                        ),
                    )
                },
                onRemoved = { stack.add(TrackInfoDialogScreen.Remove(manga.id, it.track!!, it.tracker.id)) },
                onDismissRequest = onDismissRequest,
            )

            is TrackInfoDialogScreen.Status -> TrackStatusSelectorScreen(
                track = currentScreen.track,
                serviceId = currentScreen.serviceId,
                onConfirm = { stack.removeAt(stack.lastIndex) },
                onDismissRequest = { stack.removeAt(stack.lastIndex) },
            )

            is TrackInfoDialogScreen.Chapter -> TrackChapterSelectorScreen(
                track = currentScreen.track,
                serviceId = currentScreen.serviceId,
                onConfirm = { stack.removeAt(stack.lastIndex) },
                onDismissRequest = { stack.removeAt(stack.lastIndex) },
            )

            is TrackInfoDialogScreen.Score -> TrackScoreSelectorScreen(
                track = currentScreen.track,
                serviceId = currentScreen.serviceId,
                onConfirm = { stack.removeAt(stack.lastIndex) },
                onDismissRequest = { stack.removeAt(stack.lastIndex) },
            )

            is TrackInfoDialogScreen.Date -> TrackDateSelectorScreen(
                track = currentScreen.track,
                serviceId = currentScreen.serviceId,
                start = currentScreen.start,
                onConfirm = { stack.removeAt(stack.lastIndex) },
                onRemove = { stack.add(TrackInfoDialogScreen.DateRemover(currentScreen.track, currentScreen.serviceId, currentScreen.start)) },
                onDismissRequest = { stack.removeAt(stack.lastIndex) },
            )

            is TrackInfoDialogScreen.DateRemover -> TrackDateRemoverScreen(
                track = currentScreen.track,
                serviceId = currentScreen.serviceId,
                start = currentScreen.start,
                onConfirm = {
                    stack.removeAt(stack.lastIndex) // remove remover
                    stack.removeAt(stack.lastIndex) // remove date selector
                },
                onDismissRequest = { stack.removeAt(stack.lastIndex) },
            )

            is TrackInfoDialogScreen.Search -> TrackerSearchScreen(
                mangaId = currentScreen.mangaId,
                initialQuery = currentScreen.initialQuery,
                currentUrl = currentScreen.currentUrl,
                serviceId = currentScreen.serviceId,
                onConfirmSelection = { stack.removeAt(stack.lastIndex) },
                onDismissRequest = { stack.removeAt(stack.lastIndex) },
            )

            is TrackInfoDialogScreen.Remove -> TrackerRemoveScreen(
                mangaId = currentScreen.mangaId,
                track = currentScreen.track,
                serviceId = currentScreen.serviceId,
                onConfirm = { stack.removeAt(stack.lastIndex) },
                onDismissRequest = { stack.removeAt(stack.lastIndex) },
            )
        }
    }
}

private sealed interface TrackInfoDialogScreen {
    data object Home : TrackInfoDialogScreen
    data class Status(val track: Track, val serviceId: Long) : TrackInfoDialogScreen
    data class Chapter(val track: Track, val serviceId: Long) : TrackInfoDialogScreen
    data class Score(val track: Track, val serviceId: Long) : TrackInfoDialogScreen
    data class Date(val track: Track, val serviceId: Long, val start: Boolean) : TrackInfoDialogScreen
    data class DateRemover(val track: Track, val serviceId: Long, val start: Boolean) : TrackInfoDialogScreen
    data class Search(val mangaId: Long, val initialQuery: String, val currentUrl: String?, val serviceId: Long) : TrackInfoDialogScreen
    data class Remove(val mangaId: Long, val track: Track, val serviceId: Long) : TrackInfoDialogScreen
}

@Composable
private fun TrackInfoDialogHomeScreen(
    mangaId: Long,
    mangaTitle: String,
    sourceId: Long,
    canonicalId: String? = null,
    onStatusClick: (TrackItem) -> Unit,
    onChapterClick: (TrackItem) -> Unit,
    onScoreClick: (TrackItem) -> Unit,
    onStartDateEdit: (TrackItem) -> Unit,
    onEndDateEdit: (TrackItem) -> Unit,
    onNewSearch: (TrackItem) -> Unit,
    onRemoved: (TrackItem) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current

    val screenModel = hiltViewModel<TrackInfoHomeViewModel, TrackInfoHomeViewModel.Factory> { factory ->
        factory.create(mangaId, sourceId)
    }

    val state by screenModel.state.collectAsStateWithLifecycle()
    val dateFormat = remember { UiPreferences.dateFormat(screenModel.uiPreferences.dateFormat().getSync()) }

    LaunchedEffect(screenModel) {
        screenModel.effectFlow.collect { effect ->
            when (effect) {
                is TrackInfoHomeViewModel.Effect.ShowToast -> context.toast(effect.message)
            }
        }
    }

    TrackInfoDialogHome(
        trackItems = state.trackItems,
        dateFormat = dateFormat,
        canonicalId = canonicalId,
        onStatusClick = onStatusClick,
        onChapterClick = onChapterClick,
        onScoreClick = onScoreClick,
        onStartDateEdit = onStartDateEdit,
        onEndDateEdit = onEndDateEdit,
        onNewSearch = {
            if (it.tracker is EnhancedTracker) {
                screenModel.registerEnhancedTracking(it)
            } else {
                onNewSearch(it)
            }
        },
        onOpenInBrowser = {
            val url = it.track?.remoteUrl ?: return@TrackInfoDialogHome
            if (url.isNotBlank()) {
                context.openInBrowser(url)
            }
        },
        onRemoved = onRemoved,
        onCopyLink = {
            val url = it.track?.remoteUrl ?: return@TrackInfoDialogHome
            if (url.isNotBlank()) {
                context.copyToClipboard(url, url)
            }
        },
        onTogglePrivate = screenModel::togglePrivate,
    )
}

@HiltViewModel(assistedFactory = TrackInfoHomeViewModel.Factory::class)
class TrackInfoHomeViewModel @AssistedInject constructor(
    @Assisted private val mangaId: Long,
    @Assisted private val sourceId: Long,
    private val getTracks: GetTracks,
    private val trackerManager: TrackerManager,
    private val sourceManager: SourceManager,
    private val getManga: GetManga,
    private val refreshTracks: RefreshTracks,
    private val application: Application,
    private val deleteTrack: DeleteTrack,
    val uiPreferences: UiPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val effectChannel = Channel<Effect>(Channel.BUFFERED)
    val effectFlow = effectChannel.receiveAsFlow()

    @AssistedFactory
    interface Factory {
        fun create(mangaId: Long, sourceId: Long): TrackInfoHomeViewModel
    }

    init {
        viewModelScope.launch {
            refreshTrackers()
        }

        viewModelScope.launch {
            getTracks.subscribe(mangaId)
                .catch { logcat(LogPriority.ERROR, it) }
                .distinctUntilChanged()
                .map { it.mapToTrackItem() }
                .collectLatest { trackItems -> _state.update { it.copy(trackItems = trackItems) } }
        }
    }

    fun registerEnhancedTracking(item: TrackItem) {
        item.tracker as EnhancedTracker
        viewModelScope.launchNonCancellable {
            val manga = getManga.await(mangaId) ?: return@launchNonCancellable
            try {
                val matchResult = item.tracker.match(manga) ?: throw Exception()
                val track = Track(
                    id = 0L,
                    mangaId = mangaId,
                    trackerId = item.tracker.id,
                    remoteId = matchResult.remote_id,
                    libraryId = null,
                    title = matchResult.title,
                    lastChapterRead = 0.0,
                    totalChapters = matchResult.total_chapters,
                    status = item.tracker.getReadingStatus(),
                    score = 0.0,
                    remoteUrl = matchResult.tracking_url,
                    startDate = 0L,
                    finishDate = 0L,
                    isPrivate = matchResult.isPrivate,
                )
                item.tracker.register(track, mangaId)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) {
                    "Failed to register track for tracker '${item.tracker.name}'; manga id=$mangaId"
                }
                effectChannel.send(Effect.ShowToast(application.stringResource(ephyra.app.core.common.R.string.error_no_match)))
            }
        }
    }

    private suspend fun refreshTrackers() {
        refreshTracks.await(mangaId)
            .filter { it.first != null }
            .forEach { (track, e) ->
                logcat(LogPriority.ERROR, e) {
                    "Failed to refresh track data mangaId=$mangaId for service ${track!!.id}"
                }
                effectChannel.send(
                    Effect.ShowToast(
                        application.stringResource(
                            ephyra.app.core.common.R.string.track_error,
                            track!!.name,
                            e.message ?: "",
                        ),
                    ),
                )
            }
    }

    fun togglePrivate(item: TrackItem) {
        viewModelScope.launchNonCancellable {
            item.tracker.setRemotePrivate(item.track!!, !item.track.isPrivate)
        }
    }

    private suspend fun List<Track>.mapToTrackItem(): List<TrackItem> {
        val loggedInTrackers = trackerManager.loggedInTrackers()
        val source = sourceManager.getOrStub(sourceId)
        // Include Jellyfin even when not logged in so users can discover it
        val jellyfin = trackerManager.get(TrackerManager.JELLYFIN)
        val visibleTrackers = if (jellyfin == null || jellyfin.isLoggedIn()) {
            loggedInTrackers
        } else {
            loggedInTrackers + jellyfin
        }
        return visibleTrackers
            // Map to TrackItem
            .map { service -> TrackItem(find { it.trackerId == service.id }, service) }
            // Show only if the service supports this manga's source
            .filter { (it.tracker as? EnhancedTracker)?.accept(source) ?: true }
    }

    @Immutable
    data class State(
        val trackItems: List<TrackItem> = emptyList(),
    )

    sealed interface Effect {
        data class ShowToast(val message: String) : Effect
    }
}

@Composable
private fun TrackStatusSelectorScreen(
    track: Track,
    serviceId: Long,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val screenModel = hiltViewModel<TrackStatusViewModel, TrackStatusViewModel.Factory> { factory ->
        factory.create(track, serviceId)
    }
    val state by screenModel.state.collectAsStateWithLifecycle()
    TrackStatusSelector(
        selection = state.selection,
        onSelectionChange = screenModel::setSelection,
        selections = remember { screenModel.getSelections() },
        onConfirm = {
            screenModel.setStatus()
            onConfirm()
        },
        onDismissRequest = onDismissRequest,
    )
}

@HiltViewModel(assistedFactory = TrackStatusViewModel.Factory::class)
class TrackStatusViewModel @AssistedInject constructor(
    @Assisted private val track: Track,
    @Assisted private val serviceId: Long,
    private val trackerManager: TrackerManager,
) : ViewModel() {

    private val tracker = trackerManager.get(serviceId)!!
    private val _state = MutableStateFlow(State(track.status))
    val state = _state.asStateFlow()

    @AssistedFactory
    interface Factory {
        fun create(track: Track, serviceId: Long): TrackStatusViewModel
    }

    fun getSelections(): Map<Long, Int?> {
        return tracker.getStatusList().associateWith { tracker.getStatus(it) }
    }

    fun setSelection(selection: Long) {
        _state.update { it.copy(selection = selection) }
    }

    fun setStatus() {
        viewModelScope.launchNonCancellable {
            tracker.setRemoteStatus(track, state.value.selection)
        }
    }

    @Immutable
    data class State(
        val selection: Long,
    )
}

@Composable
private fun TrackChapterSelectorScreen(
    track: Track,
    serviceId: Long,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val screenModel = hiltViewModel<TrackChapterViewModel, TrackChapterViewModel.Factory> { factory ->
        factory.create(track, serviceId)
    }
    val state by screenModel.state.collectAsStateWithLifecycle()

    TrackChapterSelector(
        selection = state.selection,
        onSelectionChange = screenModel::setSelection,
        range = remember { screenModel.getRange() },
        onConfirm = {
            screenModel.setChapter()
            onConfirm()
        },
        onDismissRequest = onDismissRequest,
    )
}

@HiltViewModel(assistedFactory = TrackChapterViewModel.Factory::class)
class TrackChapterViewModel @AssistedInject constructor(
    @Assisted private val track: Track,
    @Assisted private val serviceId: Long,
    private val trackerManager: TrackerManager,
) : ViewModel() {

    private val tracker = trackerManager.get(serviceId)!!
    private val _state = MutableStateFlow(State(track.lastChapterRead.toInt()))
    val state = _state.asStateFlow()

    @AssistedFactory
    interface Factory {
        fun create(track: Track, serviceId: Long): TrackChapterViewModel
    }

    fun getRange(): Iterable<Int> {
        val endRange = if (track.totalChapters > 0) {
            track.totalChapters
        } else {
            10000
        }
        return 0..endRange.toInt()
    }

    fun setSelection(selection: Int) {
        _state.update { it.copy(selection = selection) }
    }

    fun setChapter() {
        viewModelScope.launchNonCancellable {
            tracker.setRemoteLastChapterRead(track, state.value.selection)
        }
    }

    @Immutable
    data class State(
        val selection: Int,
    )
}

@Composable
private fun TrackScoreSelectorScreen(
    track: Track,
    serviceId: Long,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val screenModel = hiltViewModel<TrackScoreViewModel, TrackScoreViewModel.Factory> { factory ->
        factory.create(track, serviceId)
    }
    val state by screenModel.state.collectAsStateWithLifecycle()

    TrackScoreSelector(
        selection = state.selection,
        onSelectionChange = screenModel::setSelection,
        selections = remember { screenModel.getSelections().toImmutableList() },
        onConfirm = {
            screenModel.setScore()
            onConfirm()
        },
        onDismissRequest = onDismissRequest,
    )
}

@HiltViewModel(assistedFactory = TrackScoreViewModel.Factory::class)
class TrackScoreViewModel @AssistedInject constructor(
    @Assisted private val track: Track,
    @Assisted private val serviceId: Long,
    private val trackerManager: TrackerManager,
) : ViewModel() {

    private val tracker = trackerManager.get(serviceId)!!
    private val _state = MutableStateFlow(State(tracker.displayScore(track)))
    val state = _state.asStateFlow()

    @AssistedFactory
    interface Factory {
        fun create(track: Track, serviceId: Long): TrackScoreViewModel
    }

    fun getSelections(): List<String> {
        return tracker.getScoreList()
    }

    fun setSelection(selection: String) {
        _state.update { it.copy(selection = selection) }
    }

    fun setScore() {
        viewModelScope.launchNonCancellable {
            tracker.setRemoteScore(track, state.value.selection)
        }
    }

    @Immutable
    data class State(
        val selection: String,
    )
}

@Composable
private fun TrackDateSelectorScreen(
    track: Track,
    serviceId: Long,
    start: Boolean,
    onConfirm: (Long) -> Unit,
    onRemove: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val selectableDates = remember(track, start) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val targetDate = Instant.ofEpochMilli(utcTimeMillis).toLocalDate(ZoneOffset.UTC)

                // Disallow future dates
                if (targetDate > LocalDate.now(ZoneOffset.UTC)) return false

                return when {
                    // Disallow setting start date after finish date
                    start && track.finishDate > 0 -> {
                        val finishDate = Instant.ofEpochMilli(track.finishDate).toLocalDate(ZoneOffset.UTC)
                        targetDate <= finishDate
                    }
                    // Disallow setting finish date before start date
                    !start && track.startDate > 0 -> {
                        val startDate = Instant.ofEpochMilli(track.startDate).toLocalDate(ZoneOffset.UTC)
                        startDate <= targetDate
                    }

                    else -> {
                        true
                    }
                }
            }

            override fun isSelectableYear(year: Int): Boolean {
                // Disallow future years
                if (year > LocalDate.now(ZoneOffset.UTC).year) return false

                return when {
                    // Disallow setting start year after finish year
                    start && track.finishDate > 0 -> {
                        val finishDate = Instant.ofEpochMilli(track.finishDate).toLocalDate(ZoneOffset.UTC)
                        year <= finishDate.year
                    }
                    // Disallow setting finish year before start year
                    !start && track.startDate > 0 -> {
                        val startDate = Instant.ofEpochMilli(track.startDate).toLocalDate(ZoneOffset.UTC)
                        startDate.year <= year
                    }

                    else -> {
                        true
                    }
                }
            }
        }
    }

    val screenModel = hiltViewModel<TrackDateViewModel, TrackDateViewModel.Factory> { factory ->
        factory.create(track, serviceId, start)
    }

    val canRemove = if (start) {
        track.startDate > 0
    } else {
        track.finishDate > 0
    }
    TrackDateSelector(
        title = if (start) {
            stringResource(ephyra.app.core.common.R.string.track_started_reading_date)
        } else {
            stringResource(ephyra.app.core.common.R.string.track_finished_reading_date)
        },
        initialSelectedDateMillis = screenModel.initialSelection,
        selectableDates = selectableDates,
        onConfirm = {
            screenModel.setDate(it)
            onConfirm(it)
        },
        onRemove = onRemove.takeIf { canRemove },
        onDismissRequest = onDismissRequest,
    )
}

@HiltViewModel(assistedFactory = TrackDateViewModel.Factory::class)
class TrackDateViewModel @AssistedInject constructor(
    @Assisted private val track: Track,
    @Assisted private val serviceId: Long,
    @Assisted private val start: Boolean,
    private val trackerManager: TrackerManager,
) : ViewModel() {

    private val tracker = trackerManager.get(serviceId)!!

    @AssistedFactory
    interface Factory {
        fun create(track: Track, serviceId: Long, start: Boolean): TrackDateViewModel
    }

    // In UTC
    val initialSelection: Long
        get() {
            val millis = (if (start) track.startDate else track.finishDate)
                .takeIf { it != 0L }
                ?: Instant.now().toEpochMilli()
            return millis.convertEpochMillisZone(ZoneOffset.systemDefault(), ZoneOffset.UTC)
        }

    // In UTC
    fun setDate(millis: Long) {
        // Convert to local time
        val localMillis = millis.convertEpochMillisZone(ZoneOffset.UTC, ZoneOffset.systemDefault())
        viewModelScope.launchNonCancellable {
            if (start) {
                tracker.setRemoteStartDate(track, localMillis)
            } else {
                tracker.setRemoteFinishDate(track, localMillis)
            }
        }
    }
}

@Composable
private fun TrackDateRemoverScreen(
    track: Track,
    serviceId: Long,
    start: Boolean,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val screenModel = hiltViewModel<TrackDateRemoverViewModel, TrackDateRemoverViewModel.Factory> { factory ->
        factory.create(track, serviceId, start)
    }
    AlertDialogContent(
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
            )
        },
        title = {
            Text(
                text = stringResource(ephyra.app.core.common.R.string.track_remove_date_conf_title),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            val serviceName = screenModel.getServiceName()
            Text(
                text = if (start) {
                    stringResource(ephyra.app.core.common.R.string.track_remove_start_date_conf_text, serviceName)
                } else {
                    stringResource(ephyra.app.core.common.R.string.track_remove_finish_date_conf_text, serviceName)
                },
            )
        },
        buttons = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small, Alignment.End),
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(ephyra.app.core.common.R.string.action_cancel))
                }
                FilledTonalButton(
                    onClick = {
                        screenModel.removeDate()
                        onConfirm()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(text = stringResource(ephyra.app.core.common.R.string.action_remove))
                }
            }
        },
    )
}

@HiltViewModel(assistedFactory = TrackDateRemoverViewModel.Factory::class)
class TrackDateRemoverViewModel @AssistedInject constructor(
    @Assisted private val track: Track,
    @Assisted private val serviceId: Long,
    @Assisted private val start: Boolean,
    private val trackerManager: TrackerManager,
) : ViewModel() {

    private val tracker = trackerManager.get(serviceId)!!

    @AssistedFactory
    interface Factory {
        fun create(track: Track, serviceId: Long, start: Boolean): TrackDateRemoverViewModel
    }

    fun getServiceName() = tracker.name

    fun removeDate() {
        viewModelScope.launchNonCancellable {
            if (start) {
                tracker.setRemoteStartDate(track, 0)
            } else {
                tracker.setRemoteFinishDate(track, 0)
            }
        }
    }
}

@Composable
fun TrackerSearchScreen(
    mangaId: Long,
    initialQuery: String,
    currentUrl: String?,
    serviceId: Long,
    onConfirmSelection: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val screenModel = hiltViewModel<TrackerSearchViewModel, TrackerSearchViewModel.Factory> { factory ->
        factory.create(mangaId, serviceId, currentUrl, initialQuery)
    }

    val state by screenModel.state.collectAsStateWithLifecycle()

    val textFieldState = rememberTextFieldState(initialQuery)
    TrackerSearch(
        state = textFieldState,
        onDispatchQuery = { screenModel.trackingSearch(textFieldState.text.toString()) },
        queryResult = state.queryResult,
        selected = state.selected,
        onSelectedChange = screenModel::updateSelection,
        onConfirmSelection = f@{ isPrivate: Boolean ->
            val selected = state.selected ?: return@f
            selected.isPrivate = isPrivate
            screenModel.registerTracking(selected)
            onConfirmSelection()
        },
        onDismissRequest = onDismissRequest,
        supportsPrivateTracking = screenModel.supportsPrivateTracking,
    )
}

@HiltViewModel(assistedFactory = TrackerSearchViewModel.Factory::class)
class TrackerSearchViewModel @AssistedInject constructor(
    @Assisted private val mangaId: Long,
    @Assisted private val serviceId: Long,
    @Assisted private val currentUrl: String? = null,
    @Assisted initialQuery: String,
    private val trackerManager: TrackerManager,
) : ViewModel() {

    private val tracker = trackerManager.get(serviceId)!!
    val supportsPrivateTracking = tracker.supportsPrivateTracking

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    @AssistedFactory
    interface Factory {
        fun create(mangaId: Long, serviceId: Long, currentUrl: String?, initialQuery: String): TrackerSearchViewModel
    }

    init {
        // Run search on first launch
        if (initialQuery.isNotBlank()) {
            trackingSearch(initialQuery)
        }
    }

    fun trackingSearch(query: String) {
        viewModelScope.launch {
            // To show loading state
            _state.update { it.copy(queryResult = null, selected = null) }

            val result = withIOContext {
                try {
                    val results = tracker.search(query)
                    Result.success(results)
                } catch (e: Throwable) {
                    Result.failure(e)
                }
            }
            _state.update { oldState ->
                oldState.copy(
                    queryResult = result,
                    selected = result.getOrNull()?.find { it.tracking_url == currentUrl },
                )
            }
        }
    }

    fun registerTracking(item: TrackSearch) {
        viewModelScope.launchNonCancellable {
            val track = Track(
                id = 0L,
                mangaId = mangaId,
                trackerId = tracker.id,
                remoteId = item.remote_id,
                libraryId = null,
                title = item.title,
                lastChapterRead = 0.0,
                totalChapters = item.total_chapters,
                status = tracker.getReadingStatus(),
                score = 0.0,
                remoteUrl = item.tracking_url,
                startDate = 0L,
                finishDate = 0L,
                isPrivate = item.isPrivate,
            )
            tracker.register(track, mangaId)
        }
    }

    fun updateSelection(selected: TrackSearch) {
        _state.update { it.copy(selected = selected) }
    }

    @Immutable
    data class State(
        val queryResult: Result<List<TrackSearch>>? = null,
        val selected: TrackSearch? = null,
    )
}

@Composable
private fun TrackerRemoveScreen(
    mangaId: Long,
    track: Track,
    serviceId: Long,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val screenModel = hiltViewModel<TrackerRemoveViewModel, TrackerRemoveViewModel.Factory> { factory ->
        factory.create(mangaId, track, serviceId)
    }
    val serviceName = screenModel.getName()
    var removeRemoteTrack by remember { mutableStateOf(false) }
    AlertDialogContent(
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
            )
        },
        title = {
            Text(
                text = stringResource(ephyra.app.core.common.R.string.track_delete_title, serviceName),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Text(
                    text = stringResource(ephyra.app.core.common.R.string.track_delete_text, serviceName),
                )

                if (screenModel.isDeletable()) {
                    LabeledCheckbox(
                        label = stringResource(ephyra.app.core.common.R.string.track_delete_remote_text, serviceName),
                        checked = removeRemoteTrack,
                        onCheckedChange = { removeRemoteTrack = it },
                    )
                }
            }
        },
        buttons = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    MaterialTheme.padding.small,
                    Alignment.End,
                ),
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(ephyra.app.core.common.R.string.action_cancel))
                }
                FilledTonalButton(
                    onClick = {
                        screenModel.unregisterTracking()
                        if (removeRemoteTrack) screenModel.deleteMangaFromService()
                        onConfirm()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(text = stringResource(ephyra.app.core.common.R.string.action_ok))
                }
            }
        },
    )
}

@HiltViewModel(assistedFactory = TrackerRemoveViewModel.Factory::class)
class TrackerRemoveViewModel @AssistedInject constructor(
    @Assisted private val mangaId: Long,
    @Assisted private val track: Track,
    @Assisted private val serviceId: Long,
    private val trackerManager: TrackerManager,
    private val deleteTrack: DeleteTrack,
) : ViewModel() {

    private val tracker = trackerManager.get(serviceId)!!

    @AssistedFactory
    interface Factory {
        fun create(mangaId: Long, track: Track, serviceId: Long): TrackerRemoveViewModel
    }

    fun getName() = tracker.name

    fun isDeletable() = tracker is DeletableTracker

    fun deleteMangaFromService() {
        viewModelScope.launchNonCancellable {
            try {
                (tracker as DeletableTracker).delete(track)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to delete entry from service" }
            }
        }
    }

    fun unregisterTracking() {
        viewModelScope.launchNonCancellable { deleteTrack.await(mangaId, serviceId) }
    }
}

