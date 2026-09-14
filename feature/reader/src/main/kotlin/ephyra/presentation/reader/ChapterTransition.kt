package ephyra.presentation.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.service.calculateChapterGap
import ephyra.feature.reader.model.ChapterTransition
import ephyra.feature.reader.model.ReaderChapter
import ephyra.presentation.core.i18n.pluralStringResource
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.util.secondaryItemAlpha
import ephyra.presentation.theme.EphyraPreviewTheme
import kotlinx.collections.immutable.persistentMapOf

@Composable
fun ChapterTransition(
    transition: ChapterTransition,
    currChapterDownloaded: Boolean,
    goingToChapterDownloaded: Boolean,
    onTransitionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val currChapter = transition.from.chapter
    val goingToChapter = transition.to?.chapter
    val hasDestination = transition.to != null

    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
        when (transition) {
            is ChapterTransition.Prev -> {
                TransitionCard(
                    topLabel = stringResource(ephyra.app.core.common.R.string.transition_previous),
                    topChapter = goingToChapter,
                    topChapterDownloaded = goingToChapterDownloaded,
                    bottomLabel = stringResource(ephyra.app.core.common.R.string.transition_current),
                    bottomChapter = currChapter,
                    bottomChapterDownloaded = currChapterDownloaded,
                    fallbackLabel = stringResource(ephyra.app.core.common.R.string.transition_no_previous),
                    chapterGap = calculateChapterGap(currChapter, goingToChapter),
                    onTransitionClick = onTransitionClick.takeIf { hasDestination },
                    actionLabel = stringResource(ephyra.app.core.common.R.string.action_previous_chapter),
                    isNext = false,
                    modifier = modifier,
                )
            }

            is ChapterTransition.Next -> {
                TransitionCard(
                    topLabel = stringResource(ephyra.app.core.common.R.string.transition_finished),
                    topChapter = currChapter,
                    topChapterDownloaded = currChapterDownloaded,
                    bottomLabel = stringResource(ephyra.app.core.common.R.string.transition_next),
                    bottomChapter = goingToChapter,
                    bottomChapterDownloaded = goingToChapterDownloaded,
                    fallbackLabel = stringResource(ephyra.app.core.common.R.string.transition_no_next),
                    chapterGap = calculateChapterGap(goingToChapter, currChapter),
                    onTransitionClick = onTransitionClick.takeIf { hasDestination },
                    actionLabel = stringResource(ephyra.app.core.common.R.string.action_next_chapter),
                    isNext = true,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun TransitionCard(
    topLabel: String,
    topChapter: Chapter?,
    topChapterDownloaded: Boolean,
    bottomLabel: String,
    bottomChapter: Chapter?,
    bottomChapterDownloaded: Boolean,
    fallbackLabel: String,
    chapterGap: Int,
    onTransitionClick: (() -> Unit)? = null,
    actionLabel: String? = null,
    isNext: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    RoundedCornerShape(24.dp),
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (topChapter != null) {
                    ChapterSection(
                        badgeLabel = topLabel,
                        isDestination = false,
                        name = topChapter.name,
                        scanlator = topChapter.scanlator,
                        downloaded = topChapterDownloaded,
                    )
                } else {
                    NoChapterNotification(
                        text = fallbackLabel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (chapterGap > 0) {
                    Spacer(Modifier.height(16.dp))
                    ChapterGapWarning(
                        gapCount = chapterGap,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                } else if (topChapter != null && bottomChapter != null) {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.ArrowDownward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                } else {
                    Spacer(Modifier.height(16.dp))
                }

                if (bottomChapter != null) {
                    ChapterSection(
                        badgeLabel = bottomLabel,
                        isDestination = true,
                        name = bottomChapter.name,
                        scanlator = bottomChapter.scanlator,
                        downloaded = bottomChapterDownloaded,
                    )
                } else {
                    NoChapterNotification(
                        text = fallbackLabel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (onTransitionClick != null && actionLabel != null) {
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onTransitionClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = actionLabel,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Icon(
                                imageVector = if (isNext) {
                                    Icons.AutoMirrored.Filled.ArrowForward
                                } else {
                                    Icons.AutoMirrored.Filled.ArrowBack
                                },
                                contentDescription = null,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(ephyra.app.core.common.R.string.transition_swipe_or_tap),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterSection(
    badgeLabel: String,
    isDestination: Boolean,
    name: String,
    scanlator: String?,
    downloaded: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isDestination) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Text(
                text = badgeLabel.trimEnd(':').uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                ),
                color = if (isDestination) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        Text(
            text = buildAnnotatedString {
                if (downloaded) {
                    appendInlineContent(DOWNLOADED_ICON_ID)
                    append(' ')
                }
                append(name)
            },
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = if (isDestination) FontWeight.Bold else FontWeight.Medium,
            ),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            inlineContent = persistentMapOf(
                DOWNLOADED_ICON_ID to InlineTextContent(
                    Placeholder(
                        width = 22.sp,
                        height = 22.sp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = stringResource(ephyra.app.core.common.R.string.label_downloaded),
                    )
                },
            ),
        )

        scanlator?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                modifier = Modifier
                    .secondaryItemAlpha()
                    .padding(top = 4.dp),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun NoChapterNotification(
    text: String,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardColor,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = null,
            )

            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ChapterGapWarning(
    gapCount: Int,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                tint = MaterialTheme.colorScheme.error,
                contentDescription = null,
            )

            Text(
                text = pluralStringResource(
                    ephyra.app.core.common.R.plurals.missing_chapters_warning,
                    count = gapCount,
                    gapCount,
                ),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val CardColor: CardColors
    @Composable
    get() = CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    )

private val VerticalSpacerSize = 24.dp
private const val DOWNLOADED_ICON_ID = "downloaded"

private fun previewChapter(name: String, scanlator: String, chapterNumber: Double) = Chapter.create().copy(
    id = 0L,
    mangaId = 0L,
    url = "",
    name = name,
    scanlator = scanlator,
    chapterNumber = chapterNumber,
)

private val FakeChapter = previewChapter(
    name = "Vol.1, Ch.1 - Fake Chapter Title",
    scanlator = "Scanlator Name",
    chapterNumber = 1.0,
)
private val FakeGapChapter = previewChapter(
    name = "Vol.5, Ch.44 - Fake Gap Chapter Title",
    scanlator = "Scanlator Name",
    chapterNumber = 44.0,
)
private val FakeChapterLongTitle = previewChapter(
    name = "Vol.1, Ch.0 - The Mundane Musings of a Metafictional Manga: A Chapter About a Chapter, Featuring" +
        " an Absurdly Long Title and a Surprisingly Normal Day in the Lives of Our Heroes, as They Grapple with the " +
        "Daily Challenges of Existence, from Paying Rent to Finding Love, All While Navigating the Strange World of " +
        "Fictional Realities and Reality-Bending Fiction, Where the Fourth Wall is Always in Danger of Being Broken " +
        "and the Line Between Author and Character is Forever Blurred.",
    scanlator = "Long Long Funny Scanlator Sniper Group Name Reborn",
    chapterNumber = 1.0,
)

@PreviewLightDark
@Composable
private fun TransitionTextPreview() {
    EphyraPreviewTheme {
        Surface(modifier = Modifier.padding(48.dp)) {
            ChapterTransition(
                transition = ChapterTransition.Next(ReaderChapter(FakeChapter), ReaderChapter(FakeChapter)),
                currChapterDownloaded = false,
                goingToChapterDownloaded = true,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun TransitionTextLongTitlePreview() {
    EphyraPreviewTheme {
        Surface(modifier = Modifier.padding(48.dp)) {
            ChapterTransition(
                transition = ChapterTransition.Next(ReaderChapter(FakeChapterLongTitle), ReaderChapter(FakeChapter)),
                currChapterDownloaded = true,
                goingToChapterDownloaded = true,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun TransitionTextWithGapPreview() {
    EphyraPreviewTheme {
        Surface(modifier = Modifier.padding(48.dp)) {
            ChapterTransition(
                transition = ChapterTransition.Next(ReaderChapter(FakeChapter), ReaderChapter(FakeGapChapter)),
                currChapterDownloaded = true,
                goingToChapterDownloaded = false,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun TransitionTextNoNextPreview() {
    EphyraPreviewTheme {
        Surface(modifier = Modifier.padding(48.dp)) {
            ChapterTransition(
                transition = ChapterTransition.Next(ReaderChapter(FakeChapter), null),
                currChapterDownloaded = true,
                goingToChapterDownloaded = false,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun TransitionTextNoPreviousPreview() {
    EphyraPreviewTheme {
        Surface(modifier = Modifier.padding(48.dp)) {
            ChapterTransition(
                transition = ChapterTransition.Prev(ReaderChapter(FakeChapter), null),
                currChapterDownloaded = true,
                goingToChapterDownloaded = false,
            )
        }
    }
}
