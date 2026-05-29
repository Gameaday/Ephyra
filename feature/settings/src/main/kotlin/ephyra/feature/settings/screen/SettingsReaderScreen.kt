package ephyra.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ephyra.domain.reader.model.ReaderOrientation
import ephyra.domain.reader.model.ReadingMode
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.feature.settings.Preference
import ephyra.presentation.core.i18n.pluralStringResource
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.util.collectAsState
import ephyra.presentation.core.util.system.hasDisplayCutout
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import java.text.NumberFormat

object SettingsReaderScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = ephyra.app.core.common.R.string.pref_category_reader

    @Composable
    override fun getPreferences(): List<Preference> {
        val screenModel = hiltViewModel<SettingsReaderScreenModel>()
        val readerPref = screenModel.readerPreferences

        return listOf(
            Preference.PreferenceItem.ListPreference(
                preference = readerPref.defaultReadingMode(),
                entries = ReadingMode.entries.drop(1)
                    .associate { it.flagValue to stringResource(it.stringRes) }
                    .toImmutableMap(),
                title = stringResource(ephyra.app.core.common.R.string.pref_viewer_type),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = readerPref.doubleTapAnimSpeed(),
                entries = persistentMapOf(
                    1 to stringResource(ephyra.app.core.common.R.string.double_tap_anim_speed_0),
                    500 to stringResource(ephyra.app.core.common.R.string.double_tap_anim_speed_normal),
                    250 to stringResource(ephyra.app.core.common.R.string.double_tap_anim_speed_fast),
                ),
                title = stringResource(ephyra.app.core.common.R.string.pref_double_tap_anim_speed),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.showReadingMode(),
                title = stringResource(ephyra.app.core.common.R.string.pref_show_reading_mode),
                subtitle = stringResource(ephyra.app.core.common.R.string.pref_show_reading_mode_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.showNavigationOverlayOnStart(),
                title = stringResource(ephyra.app.core.common.R.string.pref_show_navigation_mode),
                subtitle = stringResource(ephyra.app.core.common.R.string.pref_show_navigation_mode_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.pageTransitions(),
                title = stringResource(ephyra.app.core.common.R.string.pref_page_transitions),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = readerPref.sliderNavMode(),
                entries = persistentMapOf(
                    ReaderPreferences.SLIDER_NAV_INSTANT to
                        stringResource(ephyra.app.core.common.R.string.slider_nav_instant),
                    ReaderPreferences.SLIDER_NAV_SMOOTH to
                        stringResource(ephyra.app.core.common.R.string.slider_nav_smooth),
                ),
                title = stringResource(ephyra.app.core.common.R.string.pref_slider_nav_mode),
                subtitle = stringResource(ephyra.app.core.common.R.string.pref_slider_nav_mode_subtitle),
            ),
            getDisplayGroup(readerPreferences = readerPref),
            getEInkGroup(readerPreferences = readerPref),
            getReadingGroup(readerPreferences = readerPref),
            getPagedGroup(readerPreferences = readerPref),
            getWebtoonGroup(readerPreferences = readerPref),
            getNavigationGroup(readerPreferences = readerPref),
            getActionsGroup(readerPreferences = readerPref),
        )
    }

    @Composable
    private fun getDisplayGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val fullscreenPref = readerPreferences.fullscreen()
        val fullscreen by fullscreenPref.collectAsState()
        return Preference.PreferenceGroup(
            title = stringResource(ephyra.app.core.common.R.string.pref_category_display),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.defaultOrientationType(),
                    entries = ReaderOrientation.entries.drop(1)
                        .associate { it.flagValue to stringResource(it.stringRes) }
                        .toImmutableMap(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_rotation_type),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.readerTheme(),
                    entries = persistentMapOf(
                        1 to stringResource(ephyra.app.core.common.R.string.black_background),
                        2 to stringResource(ephyra.app.core.common.R.string.gray_background),
                        0 to stringResource(ephyra.app.core.common.R.string.white_background),
                        3 to stringResource(ephyra.app.core.common.R.string.automatic_background),
                    ),
                    title = stringResource(ephyra.app.core.common.R.string.pref_reader_theme),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = fullscreenPref,
                    title = stringResource(ephyra.app.core.common.R.string.pref_fullscreen),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.drawUnderCutout(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_cutout_short),
                    enabled = LocalView.current.hasDisplayCutout() && fullscreen,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.keepScreenOn(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_keep_screen_on),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.showPageNumber(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_show_page_number),
                ),
            ),
        )
    }

    @Composable
    private fun getEInkGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val flashPageState by readerPreferences.flashOnPageChange().collectAsState()

        val flashMillisPref = readerPreferences.flashDurationMillis()
        val flashMillis by flashMillisPref.collectAsState()

        val flashIntervalPref = readerPreferences.flashPageInterval()
        val flashInterval by flashIntervalPref.collectAsState()

        val flashColorPref = readerPreferences.flashColor()

        return Preference.PreferenceGroup(
            title = "E-Ink",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.flashOnPageChange(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_flash_page),
                    subtitle = stringResource(ephyra.app.core.common.R.string.pref_flash_page_summ),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = flashMillis / ReaderPreferences.MILLI_CONVERSION,
                    valueRange = 1..15,
                    title = stringResource(ephyra.app.core.common.R.string.pref_flash_duration),
                    valueString = stringResource(
                        ephyra.app.core.common.R.string.pref_flash_duration_summary,
                        flashMillis,
                    ),
                    enabled = flashPageState,
                    onValueChanged = { flashMillisPref.set(it * ReaderPreferences.MILLI_CONVERSION) },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = flashInterval,
                    valueRange = 1..10,
                    title = stringResource(ephyra.app.core.common.R.string.pref_flash_page_interval),
                    valueString = pluralStringResource(
                        ephyra.app.core.common.R.plurals.pref_pages,
                        flashInterval,
                        flashInterval,
                    ),
                    enabled = flashPageState,
                    onValueChanged = { flashIntervalPref.set(it) },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = flashColorPref,
                    entries = persistentMapOf(
                        ReaderPreferences.FlashColor.BLACK to
                            stringResource(ephyra.app.core.common.R.string.pref_flash_style_black),
                        ReaderPreferences.FlashColor.WHITE to
                            stringResource(ephyra.app.core.common.R.string.pref_flash_style_white),
                        ReaderPreferences.FlashColor.WHITE_BLACK
                            to stringResource(ephyra.app.core.common.R.string.pref_flash_style_white_black),
                    ),
                    title = stringResource(ephyra.app.core.common.R.string.pref_flash_with),
                    enabled = flashPageState,
                ),
            ),
        )
    }

    @Composable
    private fun getReadingGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(ephyra.app.core.common.R.string.pref_category_reading),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.skipRead(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_skip_read_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.skipFiltered(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_skip_filtered_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.skipDupe(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_skip_dupe_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.alwaysShowChapterTransition(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_always_show_chapter_transition),
                ),
            ),
        )
    }

    @Composable
    private fun getPagedGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val navModePref = readerPreferences.navigationModePager()
        val imageScaleTypePref = readerPreferences.imageScaleType()
        val dualPageSplitPref = readerPreferences.dualPageSplitPaged()
        val rotateToFitPref = readerPreferences.dualPageRotateToFit()

        val navMode by navModePref.collectAsState()
        val imageScaleType by imageScaleTypePref.collectAsState()
        val dualPageSplit by dualPageSplitPref.collectAsState()
        val rotateToFit by rotateToFitPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(ephyra.app.core.common.R.string.pager_viewer),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = navModePref,
                    entries = ReaderPreferences.TapZones
                        .mapIndexed { index, it -> index to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_viewer_nav),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.pagerNavInverted(),
                    entries = persistentListOf(
                        ReaderPreferences.TappingInvertMode.NONE,
                        ReaderPreferences.TappingInvertMode.HORIZONTAL,
                        ReaderPreferences.TappingInvertMode.VERTICAL,
                        ReaderPreferences.TappingInvertMode.BOTH,
                    )
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_read_with_tapping_inverted),
                    enabled = navMode != 5,
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = imageScaleTypePref,
                    entries = ReaderPreferences.ImageScaleType
                        .mapIndexed { index, it -> index + 1 to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_image_scale_type),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.zoomStart(),
                    entries = ReaderPreferences.ZoomStart
                        .mapIndexed { index, it -> index + 1 to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_zoom_start),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.cropBorders(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_crop_borders),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.landscapeZoom(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_landscape_zoom),
                    enabled = imageScaleType == 1,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.navigateToPan(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_navigate_pan),
                    enabled = navMode != 5,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = dualPageSplitPref,
                    title = stringResource(ephyra.app.core.common.R.string.pref_dual_page_split),
                    onValueChanged = {
                        rotateToFitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.dualPageInvertPaged(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_dual_page_invert),
                    subtitle = stringResource(ephyra.app.core.common.R.string.pref_dual_page_invert_summary),
                    enabled = dualPageSplit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = rotateToFitPref,
                    title = stringResource(ephyra.app.core.common.R.string.pref_page_rotate),
                    onValueChanged = {
                        dualPageSplitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.dualPageRotateToFitInvert(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_page_rotate_invert),
                    enabled = rotateToFit,
                ),
            ),
        )
    }

    @Composable
    private fun getWebtoonGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val numberFormat = remember { NumberFormat.getPercentInstance() }

        val navModePref = readerPreferences.navigationModeWebtoon()
        val dualPageSplitPref = readerPreferences.dualPageSplitWebtoon()
        val rotateToFitPref = readerPreferences.dualPageRotateToFitWebtoon()
        val webtoonSidePaddingPref = readerPreferences.webtoonSidePadding()

        val navMode by navModePref.collectAsState()
        val dualPageSplit by dualPageSplitPref.collectAsState()
        val rotateToFit by rotateToFitPref.collectAsState()
        val webtoonSidePadding by webtoonSidePaddingPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(ephyra.app.core.common.R.string.webtoon_viewer),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = navModePref,
                    entries = ReaderPreferences.TapZones
                        .mapIndexed { index, it -> index to stringResource(it) }
                        .toMap()
                        .toImmutableMap(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_viewer_nav),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.webtoonNavInverted(),
                    entries = persistentListOf(
                        ReaderPreferences.TappingInvertMode.NONE,
                        ReaderPreferences.TappingInvertMode.HORIZONTAL,
                        ReaderPreferences.TappingInvertMode.VERTICAL,
                        ReaderPreferences.TappingInvertMode.BOTH,
                    )
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_read_with_tapping_inverted),
                    enabled = navMode != 5,
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = webtoonSidePadding,
                    valueRange = ReaderPreferences.let {
                        it.WEBTOON_PADDING_MIN..it.WEBTOON_PADDING_MAX
                    },
                    title = stringResource(ephyra.app.core.common.R.string.pref_webtoon_side_padding),
                    valueString = numberFormat.format(webtoonSidePadding / 100f),
                    onValueChanged = { webtoonSidePaddingPref.set(it) },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.readerHideThreshold(),
                    entries = persistentMapOf(
                        ReaderPreferences.ReaderHideThreshold.HIGHEST to
                            stringResource(ephyra.app.core.common.R.string.pref_highest),
                        ReaderPreferences.ReaderHideThreshold.HIGH to
                            stringResource(ephyra.app.core.common.R.string.pref_high),
                        ReaderPreferences.ReaderHideThreshold.LOW to
                            stringResource(ephyra.app.core.common.R.string.pref_low),
                        ReaderPreferences.ReaderHideThreshold.LOWEST to
                            stringResource(ephyra.app.core.common.R.string.pref_lowest),
                    ),
                    title = stringResource(ephyra.app.core.common.R.string.pref_hide_threshold),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.cropBordersWebtoon(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_crop_borders),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = dualPageSplitPref,
                    title = stringResource(ephyra.app.core.common.R.string.pref_dual_page_split),
                    onValueChanged = {
                        rotateToFitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.dualPageInvertWebtoon(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_dual_page_invert),
                    subtitle = stringResource(ephyra.app.core.common.R.string.pref_dual_page_invert_summary),
                    enabled = dualPageSplit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = rotateToFitPref,
                    title = stringResource(ephyra.app.core.common.R.string.pref_page_rotate),
                    onValueChanged = {
                        dualPageSplitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.dualPageRotateToFitInvertWebtoon(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_page_rotate_invert),
                    enabled = rotateToFit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.webtoonDoubleTapZoomEnabled(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_double_tap_zoom),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.webtoonDisableZoomOut(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_webtoon_disable_zoom_out),
                ),
            ),
        )
    }

    @Composable
    private fun getNavigationGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val readWithVolumeKeysPref = readerPreferences.readWithVolumeKeys()
        val readWithVolumeKeys by readWithVolumeKeysPref.collectAsState()
        return Preference.PreferenceGroup(
            title = stringResource(ephyra.app.core.common.R.string.pref_reader_navigation),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = readWithVolumeKeysPref,
                    title = stringResource(ephyra.app.core.common.R.string.pref_read_with_volume_keys),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.readWithVolumeKeysInverted(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_read_with_volume_keys_inverted),
                    enabled = readWithVolumeKeys,
                ),
            ),
        )
    }

    @Composable
    private fun getActionsGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(ephyra.app.core.common.R.string.pref_reader_actions),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.readWithLongTap(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_read_with_long_tap),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.folderPerManga(),
                    title = stringResource(ephyra.app.core.common.R.string.pref_create_folder_per_manga),
                    subtitle = stringResource(ephyra.app.core.common.R.string.pref_create_folder_per_manga_summary),
                ),
            ),
        )
    }
}
