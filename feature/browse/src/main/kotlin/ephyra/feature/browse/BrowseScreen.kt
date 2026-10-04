package ephyra.feature.browse

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ephyra.feature.browse.extension.ExtensionsViewModel
import ephyra.feature.browse.extension.extensionsTab
import ephyra.feature.browse.migration.sources.migrateSourceTab
import ephyra.feature.browse.source.SourcesViewModel
import ephyra.feature.browse.source.authority.discoverTab
import ephyra.feature.browse.source.sourcesTab
import ephyra.presentation.core.components.TabContent
import ephyra.presentation.core.components.TabbedScreen
import ephyra.presentation.core.ui.AppReadySignal
import ephyra.presentation.core.ui.navigation.LocalNavController
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Declarative per-tab search wiring. Adding a tab with a search bar means adding one
 * [TabSearchBinding] here — no index-coupled `when (state.currentPage)` routing to edit,
 * which is exactly how the old pager/search-bar mapping drifted out of sync before.
 */
data class TabSearchBinding(
    val query: () -> String?,
    val onQueryChange: (String?) -> Unit,
)

/** One Browse pager tab: its [TabContent] plus the optional search binding above. */
data class BrowseTabSpec(
    val key: String,
    val tab: TabContent,
    val search: TabSearchBinding? = null,
)

@Composable
fun BrowseTabScreen(
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current

    // Hoisted for extensions tab's search bar
    val extensionsViewModel = hiltViewModel<ExtensionsViewModel>()
    val extensionsState by extensionsViewModel.state.collectAsStateWithLifecycle()

    // Hoisted for sources tab's search bar
    val sourcesViewModel = hiltViewModel<SourcesViewModel>()
    val sourcesState by sourcesViewModel.state.collectAsStateWithLifecycle()

    val tabs: ImmutableList<BrowseTabSpec> = persistentListOf(
        BrowseTabSpec(
            key = KEY_DISCOVER,
            tab = discoverTab(navController),
        ),
        BrowseTabSpec(
            key = KEY_SOURCES,
            tab = sourcesTab(sourcesViewModel, navController),
            search = TabSearchBinding(
                query = { sourcesState.searchQuery },
                onQueryChange = { sourcesViewModel.search(it) },
            ),
        ),
        BrowseTabSpec(
            key = KEY_EXTENSIONS,
            tab = extensionsTab(extensionsViewModel, navController),
            search = TabSearchBinding(
                query = { extensionsState.searchQuery },
                onQueryChange = { extensionsViewModel.search(it) },
            ),
        ),
        BrowseTabSpec(
            key = KEY_MIGRATE,
            tab = migrateSourceTab(navController),
        ),
    )

    val state = rememberPagerState { tabs.size }

    // Search-bar routing is data-driven from the current tab's binding instead of a
    // hand-maintained page-index switch.
    val currentBinding = tabs[state.currentPage].search

    TabbedScreen(
        titleRes = ephyra.app.core.common.R.string.label_discover,
        tabs = tabs.map(BrowseTabSpec::tab).toPersistentList(),
        state = state,
        searchQuery = currentBinding?.query(),
        onChangeSearchQuery = { query -> currentBinding?.onQueryChange(query) },
    )
    LaunchedEffect(Unit) {
        BrowseTab.switchToExtensionTabChannel.receiveAsFlow()
            .collectLatest {
                // Resolve the page from the spec list rather than a hard-coded index, so
                // reordering tabs can't silently point this at the wrong page.
                val extensionsPage = tabs.indexOfFirst { it.key == KEY_EXTENSIONS }
                if (extensionsPage >= 0) state.scrollToPage(extensionsPage)
            }
    }

    LaunchedEffect(Unit) {
        (context as? AppReadySignal)?.signalReady()
    }
}

private const val KEY_DISCOVER = "discover"
private const val KEY_SOURCES = "sources"
private const val KEY_EXTENSIONS = "extensions"
private const val KEY_MIGRATE = "migrate"


object BrowseTab {
    val switchToExtensionTabChannel = kotlinx.coroutines.channels.Channel<Unit>(capacity = 1)
}
