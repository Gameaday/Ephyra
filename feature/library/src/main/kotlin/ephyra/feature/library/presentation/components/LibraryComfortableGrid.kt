package ephyra.feature.library.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ephyra.domain.library.model.LibraryManga
import ephyra.feature.library.LibraryItem

@Composable
internal fun LibraryComfortableGrid(
    items: List<LibraryItem>,
    columns: Int,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    onColumnsChange: ((Int) -> Unit)? = null,
) {
    LazyLibraryGrid(
        modifier = Modifier.fillMaxSize(),
        columns = columns,
        contentPadding = contentPadding,
        onColumnsChange = onColumnsChange,
    ) {
        globalSearchItem(searchQuery, onGlobalSearchClicked)

        items(
            items = items,
            key = { it.id },
            contentType = { "library_comfortable_grid_item" },
        ) { libraryItem ->
            MangaComfortableGridItem(
                isSelected = libraryItem.id in selection,
                title = libraryItem.title,
                coverData = libraryItem.cover,
                coverBadgeStart = {
                    DownloadsBadge(count = libraryItem.downloadCount)
                    UnreadBadge(count = libraryItem.unreadCount)
                },
                coverBadgeEnd = {
                    AuthorityBadge(
                        hasCanonicalId = libraryItem.canonicalId != null,
                        canonicalId = libraryItem.canonicalId,
                    )
                    SourceHealthBadge(sourceStatus = libraryItem.sourceStatus)
                    LanguageBadge(
                        isLocal = libraryItem.isLocal,
                        sourceLanguage = libraryItem.sourceLanguage,
                    )
                },
                onLongClick = { onLongClick(libraryItem.libraryManga) },
                onClick = { onClick(libraryItem.libraryManga) },
                onClickContinueReading = if (onClickContinueReading != null && libraryItem.unreadCount > 0) {
                    { onClickContinueReading(libraryItem.libraryManga) }
                } else {
                    null
                },
            )
        }
    }
}
