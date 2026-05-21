package ephyra.feature.browse.extension.details

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.preference.DialogPreference
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.forEach
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import ephyra.core.common.util.system.logcat
import ephyra.domain.base.BasePreferences
import ephyra.domain.source.service.SourceManager
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.preference.SharedPreferencesDataStore
import ephyra.presentation.core.screens.LoadingScreen
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.util.SourceUtilEntryPoint
import ephyra.presentation.core.util.ifSourcesLoaded
import ephyra.presentation.core.widget.TachiyomiTextInputEditText.Companion.setIncognito
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.sourcePreferences
import logcat.LogPriority
import javax.inject.Inject

@Composable
fun SourcePreferencesScreen(
    sourceId: Long,
    navController: NavController = LocalNavController.current,
) {
    if (!ifSourcesLoaded()) {
        LoadingScreen()
        return
    }

    val context = LocalContext.current

    Scaffold(
        topBar = {
            val sourceManager = EntryPointAccessors.fromApplication(context.applicationContext, SourceUtilEntryPoint::class.java).sourceManager()
            AppBar(
                title = sourceManager.getOrStub(sourceId).toString(),
                navigateUp = { navController.popBackStack() },
                scrollBehavior = it,
            )
        },
    ) { contentPadding ->
        FragmentContainer(
            fragmentManager = (context as FragmentActivity).supportFragmentManager,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            add(it, SourcePreferencesFragment.getInstance(sourceId), null)
        }
    }
}

/**
 * From https://stackoverflow.com/questions/60520145/fragment-container-in-jetpack-compose/70817794#70817794
 */
@Composable
private fun FragmentContainer(
    fragmentManager: FragmentManager,
    modifier: Modifier = Modifier,
    commit: FragmentTransaction.(containerId: Int) -> Unit,
) {
    val containerId by rememberSaveable {
        mutableIntStateOf(View.generateViewId())
    }
    var initialized by rememberSaveable { mutableStateOf(false) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FragmentContainerView(context)
                .apply { id = containerId }
        },
        update = { view ->
            if (!initialized) {
                fragmentManager.commit { commit(view.id) }
                initialized = true
            } else {
                fragmentManager.onContainerAvailable(view)
            }
        },
    )
}

/** Access to package-private method in FragmentManager through reflection */
private fun FragmentManager.onContainerAvailable(view: FragmentContainerView) {
    val method = FragmentManager::class.java.getDeclaredMethod(
        "onContainerAvailable",
        FragmentContainerView::class.java,
    )
    method.isAccessible = true
    method.invoke(this, view)
}

@AndroidEntryPoint
class SourcePreferencesFragment : PreferenceFragmentCompat() {
    @Inject lateinit var sourceManager: SourceManager
    @Inject lateinit var basePreferences: BasePreferences

    override fun getContext(): Context? {
        val superCtx = super.getContext() ?: return null
        val tv = TypedValue()
        superCtx.theme.resolveAttribute(androidx.preference.R.attr.preferenceTheme, tv, true)
        return ContextThemeWrapper(superCtx, tv.resourceId)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = populateScreen()
    }

    private fun populateScreen(): PreferenceScreen {
        val sourceId = requireArguments().getLong(SOURCE_ID)
        val source = sourceManager.getOrStub(sourceId)
        val sourceScreen = preferenceManager.createPreferenceScreen(requireContext())

        if (source is ConfigurableSource) {
            val dataStore = SharedPreferencesDataStore(source.sourcePreferences())
            preferenceManager.preferenceDataStore = dataStore

            source.setupPreferenceScreen(sourceScreen)
            sourceScreen.forEach { pref ->
                pref.isIconSpaceReserved = false
                pref.isSingleLineTitle = false
                if (pref is DialogPreference && pref.dialogTitle.isNullOrEmpty()) {
                    pref.dialogTitle = pref.title
                }

                // Apply incognito IME for EditTextPreference
                if (pref is EditTextPreference) {
                    val setListener = try {
                        val method = EditTextPreference::class.java.getDeclaredMethod("getOnBindEditTextListener")
                        method.isAccessible = true
                        @Suppress("UNCHECKED_CAST")
                        method.invoke(pref) as? EditTextPreference.OnBindEditTextListener
                    } catch (e: Exception) {
                        logcat(LogPriority.DEBUG, e) {
                            "Reflection access to OnBindEditTextListener unavailable; IME incognito applied without chaining"
                        }
                        null
                    }
                    pref.setOnBindEditTextListener {
                        setListener?.onBindEditText(it)
                        it.setIncognito(lifecycleScope, basePreferences)
                    }
                }
            }
        }

        return sourceScreen
    }

    companion object {
        private const val SOURCE_ID = "source_id"

        fun getInstance(sourceId: Long): SourcePreferencesFragment {
            return SourcePreferencesFragment().apply {
                arguments = bundleOf(SOURCE_ID to sourceId)
            }
        }
    }
}
