package ephyra.feature.reader.presentation.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ephyra.feature.reader.viewer.render.WebtoonRegionDecoder
import ephyra.presentation.core.components.material.Slider
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.theme.EphyraPreviewTheme
import ephyra.presentation.core.util.isTabletUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterNavigator(
    isRtl: Boolean,
    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,
    currentPage: Int,
    totalPages: Int,
    onPageIndexChange: (Int) -> Unit,
    showSeekBarPreview: Boolean = false,
    previewBytes: ByteArray? = null,
) {
    val isTabletUi = isTabletUi()
    val horizontalPadding = if (isTabletUi) 24.dp else 8.dp
    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val haptic = LocalHapticFeedback.current

    // Match with toolbar background color set in ReaderActivity
    val backgroundColor = MaterialTheme.colorScheme
        .surfaceColorAtElevation(3.dp)
        .copy(alpha = if (isSystemInDarkTheme()) 0.96f else 0.98f)
    // App health & design audit finding F1 (2026-11-12): the disabled container colour used
    // to be pinned to the enabled colour, so a disabled next/previous control was visually
    // identical to an enabled one - the reader's only signal that "next chapter does nothing"
    // was nothing. Drop the override so Material's default disabled treatment (12% onSurface
    // container, 38% content) renders a distinct, accessible disabled state. This is gated by
    // ReaderDisabledControlStateTest.
    val buttonColor = IconButtonDefaults.filledIconButtonColors(
        containerColor = backgroundColor,
    )

    // We explicitly handle direction based on the reader viewer rather than the system direction
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                enabled = enabledPrevious,
                onClick = onPreviousChapter,
                colors = buttonColor,
            ) {
                Icon(
                    imageVector = Icons.Outlined.SkipPrevious,
                    contentDescription = stringResource(ephyra.app.core.common.R.string.action_previous_chapter),
                )
            }

            if (totalPages > 1) {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    val interactionSource = remember { MutableInteractionSource() }
                    val sliderDragged: Boolean by interactionSource.collectIsDraggedAsState()
                    LaunchedEffect(currentPage) {
                        if (sliderDragged) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(backgroundColor)
                            .padding(horizontal = 16.dp),
                    ) {
                        SeekBarRow(
                            currentPage = currentPage,
                            totalPages = totalPages,
                            interactionSource = interactionSource,
                            onPageIndexChange = onPageIndexChange,
                        )

                        SeekPreviewBubble(
                            visible = showSeekBarPreview && sliderDragged,
                            page = currentPage,
                            bytes = previewBytes,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            FilledIconButton(
                enabled = enabledNext,
                onClick = onNextChapter,
                colors = buttonColor,
            ) {
                Icon(
                    imageVector = Icons.Outlined.SkipNext,
                    contentDescription = stringResource(ephyra.app.core.common.R.string.action_next_chapter),
                )
            }
        }
    }
}

/** The page-count readout and the seek bar, kept together so the bar's width never shifts. */
@Composable
private fun SeekBarRow(
    currentPage: Int,
    totalPages: Int,
    interactionSource: MutableInteractionSource,
    onPageIndexChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.CenterEnd) {
            Text(text = currentPage.toString())
            // Transparent duplicate reserves the widest number's space, so the bar does not resize.
            Text(text = totalPages.toString(), color = Color.Transparent)
        }

        Slider(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            value = currentPage,
            valueRange = 1..totalPages,
            onValueChange = { if (it == currentPage) return@Slider else onPageIndexChange(it - 1) },
            interactionSource = interactionSource,
        )

        Text(text = totalPages.toString())
    }
}

/**
 * A bubble above the thumb so a drag has a target the eye can follow.
 *
 * Shows the page thumbnail when the page under the thumb is already resident, and falls back to the
 * page number when it is not. Deliberately no fetch: a drag that decoded every intermediate page is
 * the decode storm that makes scrubbing stutter. The preview is therefore most useful exactly where
 * it costs nothing - scrubbing inside a range the reader has already loaded.
 */
@Composable
private fun SeekPreviewBubble(
    visible: Boolean,
    page: Int,
    bytes: ByteArray?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 4.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.inverseSurface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val thumbnail = rememberPreviewThumbnail(bytes)
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .width(PREVIEW_WIDTH)
                        .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = page.toString(),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** Preview width; the height follows the page's own aspect ratio. */
private val PREVIEW_WIDTH = 72.dp

/**
 * Downsamples [bytes] to a thumbnail no larger than [PREVIEW_WIDTH] wide.
 *
 * Two-pass: bounds first, then the real decode with an `inSampleSize` chosen from
 * [WebtoonRegionDecoder.sampleSizeForWidth], so a 4000px page does not allocate a 4000px bitmap to
 * fill a 72dp box. Returns null for undecodable input, which degrades to the number.
 */
internal fun decodePreview(bytes: ByteArray, targetWidthPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val sourceWidth = bounds.outWidth
    if (sourceWidth <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = WebtoonRegionDecoder.sampleSizeForWidth(sourceWidth, targetWidthPx)
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

/**
 * The thumbnail for [bytes], decoded off the main thread and re-decoded only when the bytes change.
 *
 * `produceState` is the conflation point: a drag that moves faster than the decode cancels the
 * in-flight one instead of queuing a decode per thumb position.
 */
@Composable
private fun rememberPreviewThumbnail(bytes: ByteArray?): ImageBitmap? {
    val targetWidthPx = with(LocalDensity.current) { PREVIEW_WIDTH.roundToPx() }
    return produceState<ImageBitmap?>(initialValue = null, bytes, targetWidthPx) {
        value = if (bytes == null) {
            null
        } else {
            withContext(Dispatchers.Default) {
                runCatching { decodePreview(bytes, targetWidthPx)?.asImageBitmap() }.getOrNull()
            }
        }
    }.value
}

@Preview
@Composable
private fun ChapterNavigatorPreview() {
    var currentPage by remember { mutableIntStateOf(1) }
    EphyraPreviewTheme {
        ChapterNavigator(
            isRtl = false,
            onNextChapter = {},
            enabledNext = true,
            onPreviousChapter = {},
            enabledPrevious = true,
            currentPage = currentPage,
            totalPages = 10,
            onPageIndexChange = { currentPage = (it + 1) },
            showSeekBarPreview = true,
        )
    }
}
