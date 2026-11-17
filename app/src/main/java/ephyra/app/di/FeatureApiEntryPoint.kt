package ephyra.app.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ephyra.presentation.core.feature.FeatureApi

/**
 * Hilt EntryPoint exposing the app's registered navigation feature APIs — the same
 * `Set<@JvmSuppressWildcards FeatureApi>` multibinding [ephyra.app.ui.main.MainActivity]
 * field-injects.
 *
 * ### Why this lives in the main source set
 *
 * Dagger's generated `SingletonComponent` only implements entry points that were visible to the
 * component's KSP aggregation, which covers **main** sources. The navigation-graph gates
 * (`RootGraphDestinationGateTest`, and the now-removed `MangaDetailsGraphReproTest`) originally
 * declared this interface inside the **test** source set. Every such run failed with
 * `ClassCastException: ...DaggerApp_HiltComponents_SingletonC$SingletonCImpl cannot be cast to
 * ...FeatureApiEntryPoint` — the component had never heard of the test-local interface, so the
 * accessor it generated for the test copy was never wired into the real graph. Declaring the
 * entry point here (the same pattern as [CoreEntryPoint]) lets the gates read the real injected
 * multibinding on the JVM.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface FeatureApiEntryPoint {
    fun featureApis(): Set<@JvmSuppressWildcards FeatureApi>
}
