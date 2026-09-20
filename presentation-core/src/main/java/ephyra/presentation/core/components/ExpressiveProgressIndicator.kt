package ephyra.presentation.core.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ephyra.presentation.core.theme.MotionTokens

/**
 * An expressive circular progress indicator using M3 Expressive motion and shapes.
 *
 * @param progress The current progress (0.0 to 1.0). If -1.0, it is treated as indeterminate.
 * @param modifier Modifier to apply to the component.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveCircularProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val isIndeterminate = progress < 0f

    if (isIndeterminate) {
        CircularProgressIndicator(
            modifier = modifier,
            strokeWidth = 4.dp,
        )
    } else {
        val animatedProgress by animateFloatAsState(
            targetValue = progress,
            animationSpec = MotionTokens.springExpressive(),
            label = "ExpressiveProgress",
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * An expressive linear progress indicator using M3 Expressive motion and shapes.
 *
 * @param progress The current progress (0.0 to 1.0).
 * @param modifier Modifier to apply to the component.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveLinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = MotionTokens.springExpressive(),
        label = "ExpressiveProgress",
    )

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .padding(horizontal = 4.dp),
    ) {
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        )
    }
}
