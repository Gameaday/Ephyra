package ephyra.feature.upcoming.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import ephyra.feature.upcoming.UpcomingFeatureApi
import ephyra.presentation.core.feature.FeatureApi

@Module
@InstallIn(SingletonComponent::class)
interface UpcomingFeatureModule {

    @Binds
    @IntoSet
    fun bindUpcomingFeatureApi(impl: UpcomingFeatureApi): FeatureApi
}
