package ephyra.presentation.core.feature

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * A safe container that isolates a submodule from crashing the main application during
 * dependency injection (Hilt) or ViewModel construction.
 */
@Composable
fun <VM : ViewModel> SafeFeatureContainer(
    featureName: String,
    viewModelClass: Class<VM>,
    onBack: () -> Unit = {},
    content: @Composable (VM) -> Unit,
) {
    val owner = LocalViewModelStoreOwner.current
    val context = LocalContext.current

    val factory = remember(owner, context) {
        val activity = context.findActivity()
        if (activity is HasDefaultViewModelProviderFactory) {
            activity.defaultViewModelProviderFactory
        } else if (owner is HasDefaultViewModelProviderFactory) {
            owner.defaultViewModelProviderFactory
        } else {
            null
        }
    }

    var error by remember { mutableStateOf<Throwable?>(null) }
    var resolvedVm by remember { mutableStateOf<VM?>(null) }

    if (error != null) {
        FeatureUnavailableView(
            featureName = featureName,
            error = error!!,
            onRetry = {
                error = null
                resolvedVm = null
            },
            onBack = onBack,
        )
    } else {
        if (resolvedVm == null && owner != null && factory != null) {
            try {
                // Resolving the ViewModel in a standard try-catch block using Hilt's factory!
                resolvedVm = ViewModelProvider(owner, factory)[viewModelClass]
            } catch (t: Throwable) {
                Log.e("SafeFeatureContainer", "Failed to resolve ViewModel for feature $featureName", t)
                error = t
            }
        }

        if (resolvedVm != null) {
            content(resolvedVm!!)
        }
    }
}
