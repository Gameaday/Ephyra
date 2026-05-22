package ephyra.feature.manga.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import ephyra.feature.manga.MangaFeatureApi
import ephyra.presentation.core.feature.FeatureApi

@Module
@InstallIn(SingletonComponent::class)
interface MangaFeatureModule {

    @Binds
    @IntoSet
    fun bindMangaFeatureApi(impl: MangaFeatureApi): FeatureApi
}
