package ephyra.feature.player.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import ephyra.feature.player.PlayerFeatureApi
import ephyra.feature.player.VideoPlayerLauncher
import ephyra.presentation.core.feature.FeatureApi
import ephyra.presentation.core.ui.viewer.MediaViewerLauncher

/**
 * Dependency Injection Module for player integrations.
 * Registers launcher in the multibound viewer registry.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    @Binds
    @IntoSet
    abstract fun bindVideoPlayerLauncher(
        launcher: VideoPlayerLauncher,
    ): MediaViewerLauncher

    @Binds
    @IntoSet
    abstract fun bindPlayerFeatureApi(
        api: PlayerFeatureApi,
    ): FeatureApi
}
