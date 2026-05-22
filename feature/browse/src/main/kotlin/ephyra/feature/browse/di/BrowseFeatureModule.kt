package ephyra.feature.browse.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import ephyra.feature.browse.BrowseFeatureApi
import ephyra.presentation.core.feature.FeatureApi

@Module
@InstallIn(SingletonComponent::class)
interface BrowseFeatureModule {

    @Binds
    @IntoSet
    fun bindBrowseFeatureApi(impl: BrowseFeatureApi): FeatureApi
}
