package ephyra.feature.settings.screen

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import ephyra.domain.ui.UiPreferences
import ephyra.domain.ui.model.TabletUiMode
import ephyra.domain.ui.model.ThemeMode
import ephyra.domain.ui.model.setAppCompatDelegateThemeMode
import ephyra.feature.settings.Preference
import ephyra.feature.settings.widget.AppThemeModePreferenceWidget
import ephyra.feature.settings.widget.AppThemePreferenceWidget
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.navigation.LocalNavController
import ephyra.presentation.core.ui.navigation.ScreenRoutes
import ephyra.presentation.core.util.collectAsState
import ephyra.presentation.core.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import java.time.LocalDate

object SettingsAppearanceScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = ephyra.app.core.common.R.string.pref_category_appearance

    @Composable
    override fun getPreferences(): List<Preference> {
        val screenModel = hiltViewModel<SettingsAppearanceScreenModel>()
        val uiPreferences = screenModel.uiPreferences
        val navController = LocalNavController.current

        return listOf(
            getThemeGroup(uiPreferences = uiPreferences),
            getDisplayGroup(uiPreferences = uiPreferences, navController = navController),
        )
    }

    @Composable
    private fun getThemeGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current

        val themeModePref = uiPreferences.themeMode()
        val themeMode by themeModePref.collectAsState()

        val appThemePref = uiPreferences.appTheme()
        val appTheme by appThemePref.collectAsState()

        val amoledPref = uiPreferences.themeDarkAmoled()
        val amoled by amoledPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(ephyra.app.core.common.R.string.pref_category_theme),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(ephyra.app.core.common.R.string.pref_app_theme),
                ) {
                    Column {
                        AppThemeModePreferenceWidget(
                            value = themeMode,
                            onItemClick = {
                                themeModePref.set(it)
                                setAppCompatDelegateThemeMode(it)
                            },
                        )

                        AppThemePreferenceWidget(
                            value = appTheme,
                            amoled = amoled,
                            onItemClick = { appThemePref.set(it) },
                        )
                    }
                },
                Preference.PreferenceItem.SwitchPreference(
                    preference = amoledPref,
                    title = stringResource(ephyra.app.core.common.R.string.pref_dark_theme_pure_black),
                    enabled = themeMode != ThemeMode.LIGHT,
                    onValueChanged = {
                        (context as? Activity)?.let { ActivityCompat.recreate(it) }
                        true
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getDisplayGroup(
        uiPreferences: UiPreferences,
        navController: NavController,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current

        val now = remember { LocalDate.now() }

        val dateFormat by uiPreferences.dateFormat().collectAsState()
        val formattedNow = remember(dateFormat) {
            UiPreferences.dateFormat(dateFormat).format(now)
        }

        return Preference.PreferenceGroup(
            title = stringResource(ephyra.app.core.common.R.string.pref_category_display),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(ephyra.app.core.common.R.string.pref_app_language),
                    onClick = { navController.navigate(ScreenRoutes.AppLanguage.route) },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.tabletUiMode(),
                    entries = TabletUiMode.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_tablet_ui_mode),
                    onValueChanged = {
                        context.toast(ephyra.app.core.common.R.string.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.dateFormat(),
                    entries = DateFormats
                        .associateWith {
                            val formattedDate = UiPreferences.dateFormat(it).format(now)
                            "${it.ifEmpty { stringResource(ephyra.app.core.common.R.string.label_default) }} ($formattedDate)"
                        }
                        .toImmutableMap(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_date_format),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.relativeTime(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_relative_format),
                    subtitle = stringResource(
                        ephyra.app.core.common.R.string.pref_relative_format_summary,
                        stringResource(ephyra.app.core.common.R.string.relative_time_today),
                        formattedNow,
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.imagesInDescription(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_display_images_description),
                ),
            ),
        )
    }
}

private val DateFormats = listOf(
    "", // Default
    "MM/dd/yy",
    "dd/MM/yy",
    "yyyy-MM-dd",
    "dd MMM yyyy",
    "MMM dd, yyyy",
)
