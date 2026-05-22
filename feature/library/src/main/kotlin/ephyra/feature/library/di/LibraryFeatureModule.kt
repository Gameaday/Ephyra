package ephyra.feature.library.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import ephyra.feature.library.LibraryFeatureApi
import ephyra.presentation.core.feature.FeatureApi

@Module
@InstallIn(SingletonComponent::class)
interface LibraryFeatureModule {

    @Binds
    @IntoSet
    fun bindLibraryFeatureApi(impl: LibraryFeatureApi): FeatureApi
}
