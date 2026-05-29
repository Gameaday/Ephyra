package ephyra.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ephyra.data.sourcing.DynamicScraperUpdater
import ephyra.domain.content.source.ScraperScriptUpdater
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SourcingBindingsModule {
    @Binds
    @Singleton
    abstract fun bindScraperScriptUpdater(
        impl: DynamicScraperUpdater,
    ): ScraperScriptUpdater
}