package ephyra.feature.webview.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import ephyra.feature.webview.WebViewFeatureApi
import ephyra.presentation.core.feature.FeatureApi

@Module
@InstallIn(SingletonComponent::class)
interface WebViewFeatureModule {

    @Binds
    @IntoSet
    fun bindWebViewFeatureApi(impl: WebViewFeatureApi): FeatureApi
}
