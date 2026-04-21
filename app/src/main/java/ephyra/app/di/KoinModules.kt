package ephyra.app.di

import ephyra.app.ui.deeplink.DeepLinkScreenModel
import ephyra.feature.download.DownloadQueueScreenModel
import ephyra.feature.migration.config.MigrationConfigScreen
import ephyra.feature.migration.dialog.MigrateDialogScreenModel
import ephyra.feature.migration.list.MigrationListScreenModel
import ephyra.feature.reader.ReaderViewModel
import ephyra.feature.settings.screen.SettingsAdvancedScreenModel
import ephyra.feature.settings.screen.SettingsAppearanceScreenModel
import ephyra.feature.settings.screen.SettingsBrowseScreenModel
import ephyra.feature.settings.screen.SettingsDataScreenModel
import ephyra.feature.settings.screen.SettingsDownloadScreenModel
import ephyra.feature.settings.screen.SettingsLibraryScreenModel
import ephyra.feature.settings.screen.SettingsReaderScreenModel
import ephyra.feature.settings.screen.SettingsSecurityScreenModel
import ephyra.feature.settings.screen.SettingsTrackingScreenModel
import ephyra.feature.settings.screen.about.AboutScreenModel
import ephyra.feature.settings.screen.advanced.ClearDatabaseScreenModel
import ephyra.feature.settings.screen.browse.ExtensionReposScreenModel
import ephyra.feature.settings.screen.debug.WorkerInfoScreen
import ephyra.feature.upcoming.UpcomingScreenModel
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val koinAppModule_UI = module {
    // Parametrized factories: @InjectedParam-style lambdas cannot be replaced with :: references
    factory { (mangaIds: Collection<Long>, extraSearchQuery: String?) ->
        MigrationListScreenModel(
            mangaIds = mangaIds,
            extraSearchQuery = extraSearchQuery,
            preferences = get(),
            sourceManager = get(),
            getManga = get(),
            networkToLocalManga = get(),
            updateManga = get(),
            syncChaptersWithSource = get(),
            getChaptersByMangaId = get(),
            migrateManga = get(),
            getFavoritesByCanonicalId = get(),
        )
    }

    // CategoryScreenModel is registered via @Factory in :feature:category — removed here.

    // ReaderViewModel requires a platform-provided SavedStateHandle (not from the DI graph)
    // and the Android Application reference, so the explicit lambda is retained.
    viewModel {
        ReaderViewModel(
            savedState = get(),
            sourceManager = get(),
            downloadManager = get(),
            downloadProvider = get(),
            imageSaver = get(),
            readerPreferences = get(),
            basePreferences = get(),
            downloadPreferences = get(),
            trackPreferences = get(),
            trackChapter = get(),
            getManga = get(),
            getChaptersByMangaId = get(),
            getNextChapters = get(),
            upsertHistory = get(),
            updateChapter = get(),
            setMangaViewerFlags = get(),
            getIncognitoState = get(),
            libraryPreferences = get(),
            app = androidApplication(),
            coverCache = get(),
            localCoverManager = get(),
            updateManga = get(),
            chapterCache = get(),
        )
    }

    factory(::ExtensionReposScreenModel)
    factory(::ClearDatabaseScreenModel)

    // Parametrized factory: query is an @InjectedParam, keep explicit lambda.
    factory { (query: String) ->
        DeepLinkScreenModel(
            query = query,
            sourceManager = get(),
            networkToLocalManga = get(),
            getChapterByUrlAndMangaId = get(),
            syncChaptersWithSource = get(),
        )
    }

    factory(::DownloadQueueScreenModel)

    // MangaCoverScreenModel and CoverSearchScreenModel are registered via @Factory + @InjectedParam
    // in :feature:manga — removed here.

    factory(MigrationConfigScreen::ScreenModel)
    factory(::UpcomingScreenModel)
    factory { WorkerInfoScreen.Model(androidContext(), get()) }

    factory(::MigrateDialogScreenModel)
    factory(::AboutScreenModel)
    factory(::SettingsDownloadScreenModel)
    factory(::SettingsDataScreenModel)
    factory(::SettingsBrowseScreenModel)
    factory(::SettingsLibraryScreenModel)
    factory(::SettingsTrackingScreenModel)
    factory(::SettingsAppearanceScreenModel)
    factory(::SettingsReaderScreenModel)
    factory(::SettingsSecurityScreenModel)
    factory(::SettingsAdvancedScreenModel)
}

