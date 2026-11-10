package ephyra.feature.upcoming.components.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.theme.MotionTokens
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalenderHeader(
    yearMonth: YearMonth,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = yearMonth,
            transitionSpec = { getAnimation() },
            label = "Change Month",
        ) { monthYear ->
            Text(
                text = getTitleText(monthYear),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Row {
            IconButton(onClick = onPreviousClick) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    stringResource(ephyra.app.core.common.R.string.upcoming_calendar_prev),
                )
            }
            IconButton(onClick = onNextClick) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    stringResource(ephyra.app.core.common.R.string.upcoming_calendar_next),
                )
            }
        }
    }
}

private fun AnimatedContentTransitionScope<YearMonth>.getAnimation(): ContentTransform {
    // The month title crossfades rather than rolling vertically: a full-height slide for a
    // one-line title is the "element flying up and down" the owner's motion audit (2026-11-09)
    // rejected — movement that reads as loading rather than as a change of month. SizeTransform
    // stays because the title's width still changes between months.
    val enterTransition = fadeIn(
        animationSpec = tween(durationMillis = MotionTokens.DURATION_SHORT_4),
    )
    val exitTransition = fadeOut(
        animationSpec = tween(durationMillis = MotionTokens.DURATION_SHORT_4),
    )
    return (enterTransition togetherWith exitTransition)
        .using(SizeTransform(clip = false))
}

@Composable
@ReadOnlyComposable
private fun getTitleText(monthYear: YearMonth): String {
    val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    return formatter.format(monthYear)
}

@Preview
@Composable
private fun CalenderHeaderPreview() {
    CalenderHeader(
        yearMonth = YearMonth.now(),
        onNextClick = {},
        onPreviousClick = {},
    )
}
