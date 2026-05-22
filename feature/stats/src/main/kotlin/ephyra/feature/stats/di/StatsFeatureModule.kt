package ephyra.feature.stats.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import ephyra.feature.stats.StatsFeatureApi
import ephyra.presentation.core.feature.FeatureApi

@Module
@InstallIn(SingletonComponent::class)
interface StatsFeatureModule {

    @Binds
    @IntoSet
    fun bindStatsFeatureApi(impl: StatsFeatureApi): FeatureApi
}
