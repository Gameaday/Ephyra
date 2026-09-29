package ephyra.presentation.core.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import ephyra.domain.navigation.motion.MotionPolicy
import ephyra.presentation.core.R
import ephyra.presentation.core.theme.ShapeTokens
import ephyra.presentation.core.ui.navigation.LocalNavAnimatedVisibilityScope
import ephyra.presentation.core.ui.navigation.LocalSharedTransitionScope
import ephyra.presentation.core.util.rememberResourceBitmapPainter

enum class MangaCover(val ratio: Float) {
    Square(1f / 1f),
    Book(2f / 3f),
    ;

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    operator fun invoke(
        data: Any?,
        modifier: Modifier = Modifier,
        contentDescription: String = "",
        // `ShapeTokens.coverImage`, not `MaterialTheme.shapes.extraSmall`.
        //
        // This default is the shared element's shape on Library <-> Series, so it has to be the
        // cover token on *both* ends. It used to be `extraSmall` (8dp), while the library list item
        // passed `coverImage` (16dp) explicitly — so opening a series from the library changed the
        // cover's corner radius in mid-flight. A shared element that reshapes itself while it
        // travels is one of the things that makes a return look wrong, and it depended on which
        // library layout the user happened to be in.
        shape: Shape = ShapeTokens.coverImage,
        mangaId: Long? = null,
        onClick: (() -> Unit)? = null,
    ) {
        val context = LocalContext.current
        val model = if (data is ImageRequest) {
            data
        } else {
            ImageRequest.Builder(context)
                .data(data)
                .crossfade(true)
                .precision(Precision.EXACT)
                .build()
        }

        val sharedTransitionScope = LocalSharedTransitionScope.current
        val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
        val sharedElementModifier = if (mangaId != null && sharedTransitionScope != null &&
            animatedVisibilityScope != null
        ) {
            with(sharedTransitionScope) {
                Modifier.sharedElement(
                    // Key comes from MotionPolicy so the library cell and the series header cannot
                    // disagree about it; see `MotionPolicy.mangaCoverKey`.
                    rememberSharedContentState(
                        key = MotionPolicy.mangaCoverKey(mangaId),
                    ),
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        } else {
            Modifier
        }

        AsyncImage(
            model = model,
            placeholder = ColorPainter(CoverPlaceholderColor),
            error = rememberResourceBitmapPainter(id = R.drawable.cover_error),
            contentDescription = contentDescription,
            // Order matters here, and it was wrong.
            //
            // `.aspectRatio(ratio)` used to be applied *after* the shared-element modifier, so the
            // element's captured bounds were the ones the incoming `modifier` chain had already
            // produced — not the final, ratio-constrained image box. The library grid requests
            // `fillMaxWidth()` inside a `MangaCover.Book` ratio box while the series header requests
            // `sizeIn(maxWidth = 100.dp)`, so the two ends resolve different intermediate sizes and
            // the element is interpolated between non-uniform shapes. That reads as a visible squash
            // rather than a slide.
            //
            // The ratio is therefore resolved *first*, so the shared element is measured against the
            // same shape on both ends and only the position and scale differ across the flight.
            //
            // This is the second candidate recorded against this transition in `REBUILD_STATUS.md`
            // (2026-09-28). It is confirmed by reading the modifier chain, though only a device
            // capture can confirm how visible it was.
            modifier = modifier
                .aspectRatio(ratio)
                .then(sharedElementModifier)
                .clip(shape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
        )
    }
}

private val CoverPlaceholderColor = Color(0x1F888888)
