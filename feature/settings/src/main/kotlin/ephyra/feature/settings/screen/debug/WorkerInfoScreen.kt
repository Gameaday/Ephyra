package ephyra.feature.settings.screen.debug

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ephyra.core.common.util.lang.toDateTimestampString
import ephyra.core.common.util.system.workManager
import ephyra.domain.ui.UiPreferences
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.AppBarActions
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.udf.BaseUdfViewModel
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.util.system.copyToClipboard
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

@Composable
fun WorkerInfoScreen(
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current

    val viewModel = hiltViewModel<WorkerInfoViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppBar(
                title = WorkerInfoScreen.TITLE,
                navigateUp = { navController.popBackStack() },
                actions = {
                    AppBarActions(
                        persistentListOf(
                            AppBar.Action(
                                title = stringResource(ephyra.app.core.common.R.string.action_copy_to_clipboard),
                                icon = Icons.Default.ContentCopy,
                                onClick = {
                                    context.copyToClipboard(
                                        WorkerInfoScreen.TITLE,
                                        state.enqueued + state.finished + state.running,
                                    )
                                },
                            ),
                        ),
                    )
                },
                scrollBehavior = it,
            )
        },
    ) { contentPadding ->
        LazyColumn(
            contentPadding = contentPadding + PaddingValues(horizontal = 16.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            item { SectionTitle(title = "Enqueued") }
            item { SectionText(text = state.enqueued) }

            item { SectionTitle(title = "Finished") }
            item { SectionText(text = state.finished) }

            item { SectionTitle(title = "Running") }
            item { SectionText(text = state.running) }
        }
    }
}

object WorkerInfoScreen {
    const val TITLE = "Worker info"
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SectionText(text: String) {
    Text(
        text = text,
        softWrap = false,
        fontFamily = FontFamily.Monospace,
    )
}

@Immutable
data class WorkerInfoState(
    val finished: String = "",
    val running: String = "",
    val enqueued: String = "",
)

@HiltViewModel
class WorkerInfoViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val uiPreferences: UiPreferences,
) : BaseUdfViewModel<WorkerInfoState, Nothing, Nothing>(WorkerInfoState()) {
    private val workManager = context.workManager

    val finished: StateFlow<String> = state
        .map { it.finished }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")

    val running: StateFlow<String> = state
        .map { it.running }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")

    val enqueued: StateFlow<String> = state
        .map { it.enqueued }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")

    init {
        viewModelScope.launch {
            val finishedFlow = workManager
                .getWorkInfosFlow(
                    WorkQuery.fromStates(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED),
                )
                .map(::constructString)

            val runningFlow = workManager
                .getWorkInfosFlow(WorkQuery.fromStates(WorkInfo.State.RUNNING))
                .map(::constructString)

            val enqueuedFlow = workManager
                .getWorkInfosFlow(WorkQuery.fromStates(WorkInfo.State.ENQUEUED))
                .map(::constructString)

            combine(finishedFlow, runningFlow, enqueuedFlow) { fin, run, enq ->
                WorkerInfoState(finished = fin, running = run, enqueued = enq)
            }.collectLatest { newState ->
                updateState { newState }
            }
        }
    }

    override fun onEvent(event: Nothing) = Unit

    private fun constructString(list: List<WorkInfo>) = buildString {
        if (list.isEmpty()) {
            appendLine("-")
        } else {
            list.fastForEach { workInfo ->
                appendLine("Id: ${workInfo.id}")
                appendLine("Tags:")
                workInfo.tags.forEach {
                    appendLine(" - $it")
                }
                appendLine("State: ${workInfo.state}")
                if (workInfo.state == WorkInfo.State.ENQUEUED) {
                    val timestamp = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(workInfo.nextScheduleTimeMillis),
                        ZoneId.systemDefault(),
                    )
                        .toDateTimestampString(
                            UiPreferences.dateFormat(
                                uiPreferences.dateFormat().getSync(),
                            ),
                        )
                    appendLine("Next scheduled run: $timestamp")
                    appendLine("Attempt #${workInfo.runAttemptCount + 1}")
                }
                appendLine()
            }
        }
    }
}

@Composable
private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues {
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    return PaddingValues(
        start = this.calculateStartPadding(layoutDirection) + other.calculateStartPadding(layoutDirection),
        top = this.calculateTopPadding() + other.calculateTopPadding(),
        end = this.calculateEndPadding(layoutDirection) + other.calculateEndPadding(layoutDirection),
        bottom = this.calculateBottomPadding() + other.calculateBottomPadding(),
    )
}
