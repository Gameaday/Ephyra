package ephyra.feature.library.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import ephyra.core.common.preference.TriState
import ephyra.domain.category.model.Category
import ephyra.domain.library.model.LibraryDisplayMode
import ephyra.domain.library.model.LibraryManga
import ephyra.feature.library.LibraryItem
import ephyra.presentation.core.components.material.PullRefresh
import ephyra.presentation.core.util.PreferenceMutableState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@Composable
fun LibraryContent(
    categories: List<Category>,
    searchQuery: String?,
    selection: Set<Long>,
    contentPadding: PaddingValues,
    currentPage: Int,
    categoryIndexLoaded: Boolean,
    hasActiveFilters: Boolean,
    showPageTabs: Boolean,
    deadSourceCount: Int,
    degradedSourceCount: Int,
    unreadFilterState: TriState = TriState.DISABLED,
    downloadedFilterState: TriState = TriState.DISABLED,
    startedFilterState: TriState = TriState.DISABLED,
    bookmarkedFilterState: TriState = TriState.DISABLED,
    completedFilterState: TriState = TriState.DISABLED,
    sourceHealthFilterState: TriState = TriState.DISABLED,
    onToggleFilter: ((LibraryFilterType) -> Unit)? = null,
    onChangeCurrentPage: (Int) -> Unit,
    onClickManga: (Long) -> Unit,
    onContinueReadingClicked: ((LibraryManga) -> Unit)?,
    onToggleSelection: (Category, LibraryManga) -> Unit,
    onToggleRangeSelection: (Category, LibraryManga) -> Unit,
    onRefresh: () -> Boolean,
    onGlobalSearchClicked: () -> Unit,
    onClickHealthFilter: () -> Unit,
    getItemCountForCategory: (Category) -> Int?,
    getDisplayMode: (Int) -> PreferenceMutableState<LibraryDisplayMode>,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    getItemsForCategory: (Category) -> List<LibraryItem>,
    onShowSettingsDialog: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .padding(
                top = contentPadding.calculateTopPadding(),
                start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
            )
            .then(
                if (onShowSettingsDialog != null) {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.size == 2) {
                                    val dragAmountX = event.changes.map {
                                        it.position.x - it.previousPosition.x
                                    }.average()
                                    if (kotlin.math.abs(dragAmountX) > 15f) {
                                        onShowSettingsDialog()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val pagerState = rememberPagerState(currentPage) { categories.size }

        // Follow and persist the active category only once the *persisted* index is known.
        //
        // [currentPage] arrives from a preference read that completes after the first composition,
        // so at first the pager is at page 0 because nothing has loaded yet, not because the user
        // chose it. Doing either half of the round trip before that point is wrong: following the
        // index would fight the user, and persisting the pager's page would write 0 over the
        // category they last used. Before the load, both directions are inert.
        LaunchedEffect(categoryIndexLoaded, currentPage) {
            if (!categoryIndexLoaded) return@LaunchedEffect
            if (!pagerState.isScrollInProgress && pagerState.currentPage != currentPage) {
                pagerState.scrollToPage(currentPage)
            }
        }

        LaunchedEffect(pagerState, categoryIndexLoaded) {
            if (!categoryIndexLoaded) return@LaunchedEffect
            // Persist on page *settlement*, and drop the value the flow emits on subscription.
            // Reading `currentPage` directly would persist the pre-load page 0 the moment the flag
            // flips, and would also persist every intermediate page of a fling.
            snapshotFlow { pagerState.settledPage }
                .drop(1)
                .collect { page -> onChangeCurrentPage(page) }
        }
        val scope = rememberCoroutineScope()
        var isRefreshing by remember(pagerState.currentPage) { mutableStateOf(false) }

        if (showPageTabs && categories.isNotEmpty() && (categories.size > 1 || !categories.first().isSystemCategory)) {
            LaunchedEffect(categories) {
                if (categories.size <= pagerState.currentPage) {
                    pagerState.scrollToPage(categories.size - 1)
                }
            }
            LibraryTabs(
                categories = categories,
                pagerState = pagerState,
                getItemCountForCategory = getItemCountForCategory,
                onTabItemClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(it)
                    }
                },
            )
        }

        if (onToggleFilter != null) {
            LibraryFilterChips(
                unreadState = unreadFilterState,
                downloadedState = downloadedFilterState,
                startedState = startedFilterState,
                bookmarkedState = bookmarkedFilterState,
                completedState = completedFilterState,
                sourceHealthState = sourceHealthFilterState,
                deadSourceCount = deadSourceCount,
                degradedSourceCount = degradedSourceCount,
                onToggleFilter = onToggleFilter,
            )
        }

        LibraryHealthBanner(
            deadCount = deadSourceCount,
            degradedCount = degradedSourceCount,
            onClickFilter = onClickHealthFilter,
        )

        PullRefresh(
            refreshing = isRefreshing,
            enabled = selection.isEmpty(),
            onRefresh = {
                val started = onRefresh()
                if (!started) return@PullRefresh
                scope.launch {
                    // Fake refresh status but hide it after a second as it's a long running task
                    isRefreshing = true
                    delay(1.seconds)
                    isRefreshing = false
                }
            },
        ) {
            LibraryPager(
                state = pagerState,
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                hasActiveFilters = hasActiveFilters,
                selection = selection,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
                getCategoryForPage = { page -> categories[page] },
                getDisplayMode = getDisplayMode,
                getColumnsForOrientation = getColumnsForOrientation,
                getItemsForCategory = getItemsForCategory,
                onClickManga = { category, manga ->
                    if (selection.isNotEmpty()) {
                        onToggleSelection(category, manga)
                    } else {
                        onClickManga(manga.manga.id)
                    }
                },
                onLongClickManga = onToggleRangeSelection,
                onClickContinueReading = onContinueReadingClicked,
            )
        }
    }
}
