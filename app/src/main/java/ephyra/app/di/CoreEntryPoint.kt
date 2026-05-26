package ephyra.app.di

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ephyra.domain.source.service.SourceManager
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.serialization.XML

/**
 * Hilt EntryPoint to expose common dependencies to non-Android components and legacy code.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CoreEntryPoint {
    @ApplicationContext
    fun applicationContext(): Context
    fun json(): Json
    fun xml(): XML
    fun sourceManager(): SourceManager
}
