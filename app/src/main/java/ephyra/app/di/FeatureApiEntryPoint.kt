package ephyra.app.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ephyra.presentation.core.feature.FeatureApi

/**
 * Hilt EntryPoint exposing the app's registered navigation feature APIs -- the same
 * `Set<@JvmSuppressWildcards FeatureApi>` multibinding `MainActivity` field-injects.
 *
 * ### Why this lives in the main source set
 *
 * The generated `SingletonComponent` only implements entry points that were visible to its
 * KSP aggregation, which covers main sources. The navigation graph gates
 * (`RootGraphDestinationGateTest`, `MangaDetailsGraphReproTest`) originally declared this
 * interface inside the test source set, and every run since they were written failed with
 * `ClassCastException: DaggerApp_HiltComponents_SingletonC$SingletonCImpl cannot be cast to
 * ...FeatureApiEntryPoint` -- the component had never heard of the test-local interface.
 * Declaring the entry point here (the same pattern as [CoreEntryPoint]) lets the gates read
 * the real injected multibinding on the JVM.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface FeatureApiEntryPoint {
    fun featureApis(): Set<@JvmSuppressWildcards FeatureApi>
}
