package ephyra.feature.settings.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import ephyra.feature.settings.SettingsFeatureApi
import ephyra.presentation.core.feature.FeatureApi

@Module
@InstallIn(SingletonComponent::class)
interface SettingsFeatureModule {

    @Binds
    @IntoSet
    fun bindSettingsFeatureApi(impl: SettingsFeatureApi): FeatureApi
}
