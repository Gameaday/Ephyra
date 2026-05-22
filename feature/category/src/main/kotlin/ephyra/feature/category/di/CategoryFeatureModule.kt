package ephyra.feature.category.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import ephyra.feature.category.CategoryFeatureApi
import ephyra.presentation.core.feature.FeatureApi

@Module
@InstallIn(SingletonComponent::class)
interface CategoryFeatureModule {

    @Binds
    @IntoSet
    fun bindCategoryFeatureApi(impl: CategoryFeatureApi): FeatureApi
}
