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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.service.calculateChapterGap
import ephyra.feature.reader.model.ChapterTransition
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.TransitionState
import ephyra.feature.reader.model.toTransitionState
import ephyra.presentation.core.components.material.SECONDARY_ALPHA
import ephyra.presentation.core.i18n.pluralStringResource
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.util.secondaryItemAlpha
import ephyra.presentation.theme.EphyraPreviewTheme
import kotlinx.collections.immutable.persistentMapOf

/**
 * The reading flow direction of the active viewer, used to orient chapter-transition
 * arrows. "Previous chapter" is left in L2R pagers, right in R2L pagers, and up in
 * vertical/webtoon readers — a hard-coded horizontal arrow points the wrong way in
 * every mode except L2R.
 */
enum class TransitionDirection { LTR, RTL, VERTICAL }

@Composable
fun ChapterTransition(
    transition: ChapterTransition,
    currChapterDownloaded: Boolean,
    goingToChapterDownloaded: Boolean,
    onTransitionClick: (() -> Unit)? = null,
    onReturnClick: (() -> Unit)? = null,
    onReturnToSeries: (() -> Unit)? = null,
    direction: TransitionDirection = TransitionDirection.LTR,
    modifier: Modifier = Modifier,
) {
    val currChapter = transition.from.chapter
    val goingToChapter = transition.to?.chapter
    val hasDestination = transition.to != null

    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
        when (val state = transition.toTransitionState()) {
            is TransitionState.ToPrevious -> {
                TransitionCard(
                    topLabel = stringResource(ephyra.app.core.common.R.string.transition_previous),
                    topChapter = state.targetChapter?.chapter,
                    topChapterDownloaded = goingToChapterDownloaded,
                    bottomLabel = stringResource(ephyra.app.core.common.R.string.transition_current),
                    bottomChapter = currChapter,
                    bottomChapterDownloaded = currChapterDownloaded,
                    fallbackLabel = stringResource(ephyra.app.core.common.R.string.transition_no_previous),
                    chapterGap = calculateChapterGap(currChapter, goingToChapter),
                    onTransitionClick = onTransitionClick.takeIf { hasDestination },
                    onReturnClick = onReturnClick,
                    actionLabel = stringResource(ephyra.app.core.common.R.string.action_previous_chapter),
                    isNext = false,
                    direction = direction,
                    modifier = modifier,
                )
            }

            is TransitionState.ToNext -> {
                TransitionCard(
                    topLabel = stringResource(ephyra.app.core.common.R.string.transition_finished),
                    topChapter = currChapter,
                    topChapterDownloaded = currChapterDownloaded,
                    bottomLabel = stringResource(ephyra.app.core.common.R.string.transition_next),
                    bottomChapter = state.targetChapter?.chapter,
                    bottomChapterDownloaded = goingToChapterDownloaded,
                    fallbackLabel = stringResource(ephyra.app.core.common.R.string.transition_no_next),
                    chapterGap = calculateChapterGap(goingToChapter, currChapter),
                    onTransitionClick = onTransitionClick.takeIf { hasDestination },
                    onReturnClick = onReturnClick,
                    actionLabel = stringResource(ephyra.app.core.common.R.string.action_next_chapter),
                    isNext = true,
                    direction = direction,
                    modifier = modifier,
                )
            }

            // Forward navigation with no next chapter: the reader has caught up with the
            // series. Render the terminal summary card with a high-prominence exit affordance.
            TransitionState.EndOfSeries -> {
                EndOfSeriesCard(
                    onReturnToSeries = onReturnToSeries,
                    modifier = modifier,
                )
            }
        }
    }
}

/**
 * Terminal "End of Series" transition card. Shown when forward navigation has no next
 * chapter: summarizes that the reader is caught up and offers a clean exit back to the
 * series detail view instead of leaving the user stranded on an empty boundary page.
 */
@Composable
private fun EndOfSeriesCard(
    onReturnToSeries: (() -> Unit)?,
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
                .fillMaxWidth(),
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = stringResource(ephyra.app.core.common.R.string.caught_up_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(ephyra.app.core.common.R.string.caught_up_description),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
                )
                if (onReturnToSeries != null) {
                    Button(
                        onClick = onReturnToSeries,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(text = stringResource(ephyra.app.core.common.R.string.action_return_to_series))
                    }
                }
            }
        }
    }
}

/**
 * The glyph a transition affordance renders, before layout direction is applied.
 *
 * Horizontal cases map to AutoMirrored icons so the platform supplies the mirrored form;
 * vertical cases are distinct because up/down arrows are never mirrored.
 */
internal enum class TransitionArrowGlyph {
    HORIZONTAL_NEXT,
    HORIZONTAL_PREVIOUS,
    VERTICAL_NEXT,
    VERTICAL_PREVIOUS,
}

/**
 * Resolves which glyph an affordance that moves toward [isNext] should render in [direction].
 *
 * Deliberately free of Compose so the mapping - the part that previously pointed the wrong
 * way for backward navigation and vertical readers - can be asserted directly in unit tests.
 */
internal fun transitionArrowGlyph(isNext: Boolean, direction: TransitionDirection): TransitionArrowGlyph = when {
    direction == TransitionDirection.VERTICAL && isNext -> TransitionArrowGlyph.VERTICAL_NEXT
    direction == TransitionDirection.VERTICAL -> TransitionArrowGlyph.VERTICAL_PREVIOUS
    isNext -> TransitionArrowGlyph.HORIZONTAL_NEXT
    else -> TransitionArrowGlyph.HORIZONTAL_PREVIOUS
}

private fun TransitionArrowGlyph.toImageVector(): ImageVector = when (this) {
    TransitionArrowGlyph.HORIZONTAL_NEXT -> Icons.AutoMirrored.Filled.ArrowForward
    TransitionArrowGlyph.HORIZONTAL_PREVIOUS -> Icons.AutoMirrored.Filled.ArrowBack
    TransitionArrowGlyph.VERTICAL_NEXT -> Icons.Filled.ArrowDownward
    TransitionArrowGlyph.VERTICAL_PREVIOUS -> Icons.Filled.ArrowUpward
}

/**
 * Renders a directional [imageVector] under an explicit [layoutDirection].
 *
 * AutoMirrored icons resolve their orientation from the ambient layout direction at draw
 * time, so the reader's own flow is provided here instead of relying on the device locale.
 * Vertical arrows carry `autoMirror = false` and are therefore unaffected.
 */
@Composable
private fun DirectionalIcon(
    imageVector: ImageVector,
    layoutDirection: LayoutDirection,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            modifier = modifier,
        )
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
    onReturnClick: (() -> Unit)? = null,
    actionLabel: String? = null,
    isNext: Boolean = true,
    direction: TransitionDirection = TransitionDirection.LTR,
    modifier: Modifier = Modifier,
) {
    // The layout direction is supplied explicitly rather than inherited from the device locale:
    // an R2L manga pager advances leftwards even on an LTR device, and an LTR pager advances
    // rightwards even on an RTL device. Reading direction, not locale, decides the arrows.
    val directionLayout = when (direction) {
        TransitionDirection.LTR -> LayoutDirection.Ltr
        TransitionDirection.RTL -> LayoutDirection.Rtl
        // Vertical arrows are not mirrored, so the ambient direction is irrelevant.
        TransitionDirection.VERTICAL -> LocalLayoutDirection.current
    }
    val actionArrow = transitionArrowGlyph(isNext, direction).toImageVector()
    // "Return to current chapter" points back the way the reader came, so it is always the
    // opposite affordance of the chapter-navigation button.
    val returnArrow = transitionArrowGlyph(!isNext, direction).toImageVector()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            onClick = onTransitionClick ?: {},
            enabled = onTransitionClick != null,
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
                            DirectionalIcon(
                                imageVector = actionArrow,
                                layoutDirection = directionLayout,
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

                if (onReturnClick != null) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onReturnClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DirectionalIcon(
                                imageVector = returnArrow,
                                layoutDirection = directionLayout,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(ephyra.app.core.common.R.string.transition_return_to_chapter),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                            )
                        }
                    }
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
