package ephyra.presentation.reader

import androidx.compose.runtime.Composable

@Composable
fun ReaderPageActionsDialog(
    onDismissRequest: () -> Unit,
    onSetAsCover: () -> Unit,
    onShare: (Boolean) -> Unit,
    onSave: () -> Unit,
    onBlockPage: () -> Unit,
    onUnblockPage: (String) -> Unit,
    findMatchingBlockedHash: suspend () -> String?,
) {
    ReaderPageActionsSheet(
        onDismissRequest = onDismissRequest,
        onSetAsCover = onSetAsCover,
        onShare = onShare,
        onSave = onSave,
        onBlockPage = onBlockPage,
        onUnblockPage = onUnblockPage,
        findMatchingBlockedHash = findMatchingBlockedHash,
    )
}
