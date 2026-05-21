package ephyra.presentation.core.ui.activity

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import ephyra.domain.ui.UiPreferences
import ephyra.presentation.core.ui.delegate.SecureActivityDelegate
import ephyra.presentation.core.ui.delegate.ThemingDelegate
import ephyra.presentation.core.util.system.prepareTabletUiContext

/**
 * Common foundation for all activities in the application.
 *
 * This class provides standard functionality for theming, security, and tablet UI support.
 * By residing in the presentation-core module, it can be shared across all UI features
 * without having a dependency on the main application module.
 */
@AndroidEntryPoint
open class BaseActivity :
    AppCompatActivity(),
    SecureActivityDelegate,
    ThemingDelegate {

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, BaseActivityEntryPoint::class.java)
    }

    private val uiPreferences: UiPreferences
        get() = entryPoint.uiPreferences()

    override fun registerSecureActivity(activity: AppCompatActivity) {
        entryPoint.secureActivityDelegate().registerSecureActivity(activity)
    }

    override fun applyAppTheme(activity: Activity) {
        entryPoint.themingDelegate().applyAppTheme(activity)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(
            newBase.prepareTabletUiContext(
                EntryPointAccessors.fromApplication(
                    newBase.applicationContext,
                    BaseActivityEntryPoint::class.java,
                ).uiPreferences(),
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme(this)
        super.onCreate(savedInstanceState)
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BaseActivityEntryPoint {
        fun uiPreferences(): UiPreferences
        fun secureActivityDelegate(): SecureActivityDelegate
        fun themingDelegate(): ThemingDelegate
    }
}
