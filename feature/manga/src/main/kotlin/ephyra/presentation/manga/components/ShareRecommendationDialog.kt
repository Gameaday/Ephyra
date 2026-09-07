package ephyra.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ephyra.domain.manga.interactor.FormatMangaRecommendation
import ephyra.domain.manga.interactor.RecommendationShareOptions
import ephyra.domain.manga.model.Manga
import ephyra.presentation.core.i18n.stringResource

@Composable
fun ShareRecommendationDialog(
    manga: Manga,
    readChapters: Int,
    totalChapters: Int,
    score: Double?,
    url: String?,
    onDismissRequest: () -> Unit,
    onShare: (String) -> Unit,
) {
    var includeNotes by remember { mutableStateOf(manga.notes.isNotBlank()) }
    var includeProgress by remember { mutableStateOf(readChapters > 0) }
    var includeScore by remember { mutableStateOf(score != null && score > 0.0) }
    var includeUrl by remember { mutableStateOf(!url.isNullOrBlank()) }
    var customNote by remember { mutableStateOf(manga.notes) }

    val formatter = remember { FormatMangaRecommendation() }

    val previewText = remember(
        manga,
        url,
        readChapters,
        totalChapters,
        score,
        customNote,
        includeNotes,
        includeProgress,
        includeScore,
        includeUrl,
    ) {
        formatter(
            title = manga.title,
            author = manga.author,
            url = url,
            notes = manga.notes,
            readChapters = readChapters.takeIf { includeProgress },
            totalChapters = totalChapters.takeIf { includeProgress },
            score = score.takeIf { includeScore },
            genres = manga.genre.orEmpty().split(", ").filter { it.isNotBlank() },
            options = RecommendationShareOptions(
                includeUrl = includeUrl,
                includeNotes = includeNotes,
                includeProgress = includeProgress,
                includeScore = includeScore,
                includeGenres = true,
                customNote = customNote.takeIf { includeNotes },
            ),
        )
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(text = stringResource(ephyra.app.core.common.R.string.action_share))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }

                OutlinedTextField(
                    value = customNote,
                    onValueChange = {
                        customNote = it
                        if (it.isNotBlank()) includeNotes = true
                    },
                    label = { Text(stringResource(ephyra.app.core.common.R.string.action_notes)) },
                    placeholder = { Text("Personal review or recommendation...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )

                if (customNote.isNotBlank() || manga.notes.isNotBlank()) {
                    ShareOptionRow(
                        label = "Include personal notes",
                        checked = includeNotes,
                        onCheckedChange = { includeNotes = it },
                    )
                }

                if (readChapters > 0) {
                    ShareOptionRow(
                        label = "Include reading progress ($readChapters / $totalChapters)",
                        checked = includeProgress,
                        onCheckedChange = { includeProgress = it },
                    )
                }

                if (score != null && score > 0.0) {
                    ShareOptionRow(
                        label = "Include rating ($score/10)",
                        checked = includeScore,
                        onCheckedChange = { includeScore = it },
                    )
                }

                if (!url.isNullOrBlank()) {
                    ShareOptionRow(
                        label = "Include source link",
                        checked = includeUrl,
                        onCheckedChange = { includeUrl = it },
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(ephyra.app.core.common.R.string.action_cancel))
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!url.isNullOrBlank()) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onShare(url)
                        },
                    ) {
                        Text(text = "Link Only")
                    }
                }
                Button(
                    onClick = {
                        onDismissRequest()
                        onShare(previewText)
                    },
                ) {
                    Text(text = stringResource(ephyra.app.core.common.R.string.action_share))
                }
            }
        },
    )
}

@Composable
private fun ShareOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
