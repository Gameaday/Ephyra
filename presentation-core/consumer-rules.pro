# Dagger Hilt EntryPoints Protection for presentation-core
# R8's aggressive optimization can strip entry point interfaces from SingletonComponent (SingletonCImpl)
# because they are only retrieved via reflection (EntryPointAccessors). This keeps the interfaces
# and their implementations intact in the final optimized APK.
-keep @dagger.hilt.EntryPoint interface * { *; }
-keep interface ephyra.presentation.core.ui.activity.BaseActivityEntryPoint { *; }
-keep class * implements ephyra.presentation.core.ui.activity.BaseActivityEntryPoint { *; }
