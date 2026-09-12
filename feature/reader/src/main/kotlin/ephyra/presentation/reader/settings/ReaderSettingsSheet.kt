package ephyra.presentation.reader.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import ephyra.feature.reader.setting.ReaderSettingsViewModel
import ephyra.presentation.core.components.material.TabText
import ephyra.presentation.core.i18n.stringResource
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    onDismissRequest: () -> Unit,
    onShowMenus: () -> Unit,
    onHideMenus: () -> Unit,
    viewModel: ReaderSettingsViewModel,
    initialPage: Int = 0,
) {
    val tabTitles = persistentListOf(
        stringResource(ephyra.app.core.common.R.string.pref_category_reading_mode),
        stringResource(ephyra.app.core.common.R.string.pref_category_general),
        stringResource(ephyra.app.core.common.R.string.custom_filter),
    )
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, tabTitles.size - 1)) { tabTitles.size }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 2) {
            onHideMenus()
        } else {
            onShowMenus()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            onDismissRequest()
            onShowMenus()
        },
        sheetState = sheetState,
        scrimColor = if (pagerState.currentPage == 2) Color.Transparent else BottomSheetDefaults.ScrimColor,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                divider = { HorizontalDivider() },
            ) {
                tabTitles.fastForEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = { TabText(text = title) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    when (page) {
                        0 -> ReadingModePage(viewModel)
                        1 -> GeneralPage(viewModel)
                        2 -> ColorFilterPage(viewModel)
                    }
                }
            }
        }
    }
}
