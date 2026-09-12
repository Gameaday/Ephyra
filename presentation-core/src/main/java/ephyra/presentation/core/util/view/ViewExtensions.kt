@file:Suppress("NOTHING_TO_INLINE")

package ephyra.presentation.core.util.view

import android.content.res.Resources
import android.graphics.Rect
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import ephyra.core.common.core.security.PrivacyPreferences
import ephyra.domain.ui.UiPreferences
import ephyra.presentation.core.util.LocalPrivacyPreferences
import ephyra.presentation.core.util.LocalUiPreferences
import ephyra.presentation.theme.TachiyomiTheme

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ViewExtensionsEntryPoint {
    fun uiPreferences(): UiPreferences
    fun privacyPreferences(): PrivacyPreferences
}

inline fun ComponentActivity.setComposeContent(
    parent: CompositionContext? = null,
    crossinline content: @Composable () -> Unit,
) {
    setContent(parent) {
        val entryPoint = androidx.compose.runtime.remember {
            EntryPointAccessors.fromApplication(applicationContext, ViewExtensionsEntryPoint::class.java)
        }
        val uiPreferences = entryPoint.uiPreferences()
        val privacyPreferences = entryPoint.privacyPreferences()
        CompositionLocalProvider(
            LocalUiPreferences provides uiPreferences,
            LocalPrivacyPreferences provides privacyPreferences,
        ) {
            TachiyomiTheme {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodySmall,
                    LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                ) {
                    content()
                }
            }
        }
    }
}

fun View?.isVisibleOnScreen(): Boolean {
    if (this == null) {
        return false
    }
    if (!this.isShown) {
        return false
    }
    val actualPosition = Rect()
    this.getGlobalVisibleRect(actualPosition)
    val screen =
        Rect(0, 0, Resources.getSystem().displayMetrics.widthPixels, Resources.getSystem().displayMetrics.heightPixels)
    return actualPosition.intersect(screen)
}
