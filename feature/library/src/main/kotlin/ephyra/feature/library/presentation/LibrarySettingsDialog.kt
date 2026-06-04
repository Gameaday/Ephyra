package ephyra.feature.library.presentation

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.EntryPointAccessors
import ephyra.core.common.preference.TriState
import ephyra.domain.category.model.Category
import ephyra.domain.library.model.LibraryDisplayMode
import ephyra.domain.library.model.LibrarySort
import ephyra.domain.library.model.sort
import ephyra.domain.library.service.LibraryPreferences
import ephyra.feature.library.LibraryEntryPoint
import ephyra.feature.library.LibrarySettingsScreenEvent
import ephyra.feature.library.LibrarySettingsViewModel
import ephyra.presentation.core.components.BaseSortItem
import ephyra.presentation.core.components.CheckboxItem
import ephyra.presentation.core.components.HeadingItem
import ephyra.presentation.core.components.SettingsChipRow
import ephyra.presentation.core.components.SliderItem
import ephyra.presentation.core.components.SortItem
import ephyra.presentation.core.components.TabbedDialog
import ephyra.presentation.core.components.TabbedDialogPaddings
import ephyra.presentation.core.components.TriStateItem
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.AppInfo
import ephyra.presentation.core.util.collectAsState
import kotlinx.collections.immutable.persistentListOf

@Composable
fun LibrarySettingsDialog(
    onDismissRequest: () -> Unit,
    ViewModel: LibrarySettingsViewModel,
    category: Category?,
) {
    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = persistentListOf(
            stringResource(ephyra.app.core.common.R.string.action_filter),
            stringResource(ephyra.app.core.common.R.string.action_sort),
            stringResource(ephyra.app.core.common.R.string.action_display),
        ),
    ) { page ->
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> FilterPage(
                    ViewModel = ViewModel,
                )

                1 -> SortPage(
                    category = category,
                    ViewModel = ViewModel,
                )

                2 -> DisplayPage(
                    ViewModel = ViewModel,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.FilterPage(
    ViewModel: LibrarySettingsViewModel,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appInfo = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            LibraryEntryPoint::class.java,
        ).appInfo()
    }
    val filterDownloaded by ViewModel.libraryPreferences.filterDownloaded().collectAsState()
    val downloadedOnly by ViewModel.preferences.downloadedOnly().collectAsState()
    val autoUpdateMangaRestrictions by ViewModel.libraryPreferences.autoUpdateMangaRestrictions()
        .collectAsState()

    TriStateItem(
        label = stringResource(ephyra.app.core.common.R.string.label_downloaded),
        state = if (downloadedOnly) {
            TriState.ENABLED_IS
        } else {
            filterDownloaded
        },
        enabled = !downloadedOnly,
        onClick = {
            ViewModel.onEvent(LibrarySettingsScreenEvent.ToggleFilter(LibraryPreferences::filterDownloaded))
        },
    )
    val filterUnread by ViewModel.libraryPreferences.filterUnread().collectAsState()
    TriStateItem(
        label = stringResource(ephyra.app.core.common.R.string.action_filter_unread),
        state = filterUnread,
        onClick = { ViewModel.onEvent(LibrarySettingsScreenEvent.ToggleFilter(LibraryPreferences::filterUnread)) },
    )
    val filterStarted by ViewModel.libraryPreferences.filterStarted().collectAsState()
    TriStateItem(
        label = stringResource(ephyra.app.core.common.R.string.label_started),
        state = filterStarted,
        onClick = { ViewModel.onEvent(LibrarySettingsScreenEvent.ToggleFilter(LibraryPreferences::filterStarted)) },
    )
    val filterBookmarked by ViewModel.libraryPreferences.filterBookmarked().collectAsState()
    TriStateItem(
        label = stringResource(ephyra.app.core.common.R.string.action_filter_bookmarked),
        state = filterBookmarked,
        onClick = {
            ViewModel.onEvent(LibrarySettingsScreenEvent.ToggleFilter(LibraryPreferences::filterBookmarked))
        },
    )
    val filterCompleted by ViewModel.libraryPreferences.filterCompleted().collectAsState()
    TriStateItem(
        label = stringResource(ephyra.app.core.common.R.string.completed),
        state = filterCompleted,
        onClick = { ViewModel.onEvent(LibrarySettingsScreenEvent.ToggleFilter(LibraryPreferences::filterCompleted)) },
    )
    val filterSourceHealthDead by ViewModel.libraryPreferences.filterSourceHealthDead().collectAsState()
    TriStateItem(
        label = stringResource(ephyra.app.core.common.R.string.action_filter_source_health_dead),
        state = filterSourceHealthDead,
        onClick = {
            ViewModel.onEvent(LibrarySettingsScreenEvent.ToggleFilter(LibraryPreferences::filterSourceHealthDead))
        },
    )
    val filterContentTypeManga by ViewModel.libraryPreferences.filterContentTypeManga().collectAsState()
    TriStateItem(
        label = stringResource(ephyra.app.core.common.R.string.action_filter_content_type_manga),
        state = filterContentTypeManga,
        onClick = {
            ViewModel.onEvent(LibrarySettingsScreenEvent.ToggleFilter(LibraryPreferences::filterContentTypeManga))
        },
    )
    // TODO: re-enable when custom intervals are ready for stable
    if ((!appInfo.isRelease) && LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in autoUpdateMangaRestrictions) {
        val filterIntervalCustom by ViewModel.libraryPreferences.filterIntervalCustom().collectAsState()
        TriStateItem(
            label = stringResource(ephyra.app.core.common.R.string.action_filter_interval_custom),
            state = filterIntervalCustom,
            onClick = {
                ViewModel.onEvent(LibrarySettingsScreenEvent.ToggleFilter(LibraryPreferences::filterIntervalCustom))
            },
        )
    }

    val trackers by ViewModel.trackersFlow.collectAsStateWithLifecycle()
    when (trackers.size) {
        0 -> {
            // No trackers
        }

        1 -> {
            val service = trackers[0]
            val filterTracker by ViewModel.libraryPreferences.filterTracking(service.id.toInt())
                .collectAsState()
            TriStateItem(
                label = stringResource(ephyra.app.core.common.R.string.action_filter_tracked),
                state = filterTracker,
                onClick = { ViewModel.onEvent(LibrarySettingsScreenEvent.ToggleTracker(service.id.toInt())) },
            )
        }

        else -> {
            HeadingItem(ephyra.app.core.common.R.string.action_filter_tracked)
            trackers.map { service ->
                val filterTracker by ViewModel.libraryPreferences.filterTracking(service.id.toInt())
                    .collectAsState()
                TriStateItem(
                    label = service.name,
                    state = filterTracker,
                    onClick = { ViewModel.onEvent(LibrarySettingsScreenEvent.ToggleTracker(service.id.toInt())) },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.SortPage(
    category: Category?,
    ViewModel: LibrarySettingsViewModel,
) {
    val trackers by ViewModel.trackersFlow.collectAsStateWithLifecycle()
    val sortingMode = category.sort.type
    val sortDescending = !category.sort.isAscending

    val options = remember(trackers.isEmpty()) {
        val trackerMeanPair = if (trackers.isNotEmpty()) {
            ephyra.app.core.common.R.string.action_sort_tracker_score to LibrarySort.Type.TrackerMean
        } else {
            null
        }
        listOfNotNull(
            ephyra.app.core.common.R.string.action_sort_alpha to LibrarySort.Type.Alphabetical,
            ephyra.app.core.common.R.string.action_sort_total to LibrarySort.Type.TotalChapters,
            ephyra.app.core.common.R.string.action_sort_last_read to LibrarySort.Type.LastRead,
            ephyra.app.core.common.R.string.action_sort_last_manga_update to LibrarySort.Type.LastUpdate,
            ephyra.app.core.common.R.string.action_sort_unread_count to LibrarySort.Type.UnreadCount,
            ephyra.app.core.common.R.string.action_sort_latest_chapter to LibrarySort.Type.LatestChapter,
            ephyra.app.core.common.R.string.action_sort_chapter_fetch_date to LibrarySort.Type.ChapterFetchDate,
            ephyra.app.core.common.R.string.action_sort_date_added to LibrarySort.Type.DateAdded,
            trackerMeanPair,
            ephyra.app.core.common.R.string.action_sort_random to LibrarySort.Type.Random,
        )
    }

    options.map { (titleRes, mode) ->
        if (mode == LibrarySort.Type.Random) {
            BaseSortItem(
                label = stringResource(titleRes),
                icon = Icons.Default.Refresh
                    .takeIf { sortingMode == LibrarySort.Type.Random },
                onClick = {
                    ViewModel.onEvent(
                        LibrarySettingsScreenEvent.SetSort(category, mode, LibrarySort.Direction.Ascending),
                    )
                },
            )
            return@map
        }
        SortItem(
            label = stringResource(titleRes),
            sortDescending = sortDescending.takeIf { sortingMode == mode },
            onClick = {
                val isTogglingDirection = sortingMode == mode
                val direction = when {
                    isTogglingDirection -> if (sortDescending) {
                        LibrarySort.Direction.Ascending
                    } else {
                        LibrarySort.Direction.Descending
                    }

                    else -> if (sortDescending) {
                        LibrarySort.Direction.Descending
                    } else {
                        LibrarySort.Direction.Ascending
                    }
                }
                ViewModel.onEvent(LibrarySettingsScreenEvent.SetSort(category, mode, direction))
            },
        )
    }
}

private val displayModes = listOf(
    ephyra.app.core.common.R.string.action_display_grid to LibraryDisplayMode.CompactGrid,
    ephyra.app.core.common.R.string.action_display_comfortable_grid to LibraryDisplayMode.ComfortableGrid,
    ephyra.app.core.common.R.string.action_display_cover_only_grid to LibraryDisplayMode.CoverOnlyGrid,
    ephyra.app.core.common.R.string.action_display_list to LibraryDisplayMode.List,
)

@Composable
private fun ColumnScope.DisplayPage(
    ViewModel: LibrarySettingsViewModel,
) {
    val displayMode by ViewModel.libraryPreferences.displayMode().collectAsState()
    SettingsChipRow(ephyra.app.core.common.R.string.action_display_mode) {
        displayModes.map { (titleRes, mode) ->
            FilterChip(
                selected = displayMode == mode,
                onClick = { ViewModel.onEvent(LibrarySettingsScreenEvent.SetDisplayMode(mode)) },
                label = { Text(stringResource(titleRes)) },
            )
        }
    }

    if (displayMode != LibraryDisplayMode.List) {
        val configuration = LocalConfiguration.current
        val columnPreference = remember {
            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                ViewModel.libraryPreferences.landscapeColumns()
            } else {
                ViewModel.libraryPreferences.portraitColumns()
            }
        }

        val columns by columnPreference.collectAsState()
        SliderItem(
            value = columns,
            valueRange = 0..10,
            label = stringResource(ephyra.app.core.common.R.string.pref_library_columns),
            valueString = if (columns > 0) {
                columns.toString()
            } else {
                stringResource(ephyra.app.core.common.R.string.label_auto)
            },
            onChange = columnPreference::set,
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }

    HeadingItem(ephyra.app.core.common.R.string.overlay_header)
    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.action_display_download_badge),
        pref = ViewModel.libraryPreferences.downloadBadge(),
    )
    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.action_display_unread_badge),
        pref = ViewModel.libraryPreferences.unreadBadge(),
    )
    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.action_display_local_badge),
        pref = ViewModel.libraryPreferences.localBadge(),
    )
    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.action_display_language_badge),
        pref = ViewModel.libraryPreferences.languageBadge(),
    )
    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.action_display_show_continue_reading_button),
        pref = ViewModel.libraryPreferences.showContinueReadingButton(),
    )

    HeadingItem(ephyra.app.core.common.R.string.tabs_header)
    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.action_display_show_tabs),
        pref = ViewModel.libraryPreferences.categoryTabs(),
    )
    CheckboxItem(
        label = stringResource(ephyra.app.core.common.R.string.action_display_show_number_of_items),
        pref = ViewModel.libraryPreferences.categoryNumberOfItems(),
    )
}
