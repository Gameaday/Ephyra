package ephyra.feature.reader.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import ephyra.feature.reader.BookViewerLauncher
import ephyra.feature.reader.MangaViewerLauncher
import ephyra.feature.reader.ReaderFeatureApi
import ephyra.presentation.core.feature.FeatureApi
import ephyra.presentation.core.ui.viewer.MediaViewerLauncher

/**
 * Dependency Injection Module registering the reader launchers (Manga and Book)
 * into the swappable viewer registry.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReaderModule {

    @Binds
    @IntoSet
    abstract fun bindMangaViewerLauncher(
        launcher: MangaViewerLauncher,
    ): MediaViewerLauncher

    @Binds
    @IntoSet
    abstract fun bindBookViewerLauncher(
        launcher: BookViewerLauncher,
    ): MediaViewerLauncher

    @Binds
    @IntoSet
    abstract fun bindReaderFeatureApi(
        api: ReaderFeatureApi,
    ): FeatureApi
}
