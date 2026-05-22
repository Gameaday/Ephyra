package ephyra.feature.more.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import ephyra.feature.more.MoreFeatureApi
import ephyra.presentation.core.feature.FeatureApi

@Module
@InstallIn(SingletonComponent::class)
interface MoreFeatureModule {

    @Binds
    @IntoSet
    fun bindMoreFeatureApi(impl: MoreFeatureApi): FeatureApi
}
