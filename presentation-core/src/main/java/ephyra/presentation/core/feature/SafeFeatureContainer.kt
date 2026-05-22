package ephyra.presentation.core.feature

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

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
    var error by remember { mutableStateOf<Throwable?>(null) }
    var resolvedVm by remember { mutableStateOf<VM?>(null) }

    if (error != null || (resolvedVm == null && owner == null)) {
        FeatureUnavailableView(
            featureName = featureName,
            error = error ?: IllegalStateException("No ViewModelStoreOwner available"),
            onRetry = {
                error = null
                resolvedVm = null
            },
            onBack = onBack,
        )
    } else {
        if (resolvedVm == null && owner != null) {
            try {
                // Resolving the ViewModel (where Hilt constructor injection and init blocks run)
                // in a standard try-catch block. Since ViewModelProvider is NOT a Composable,
                // this is 100% legal, compiles perfectly, and provides isolation.
                resolvedVm = ViewModelProvider(owner)[viewModelClass]
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
