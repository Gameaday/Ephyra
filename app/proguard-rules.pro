-dontobfuscate

-keep,allowoptimization class eu.kanade.**
-keep,allowoptimization class ephyra.**
-keep,allowoptimization class ephyra.**

# Keep common dependencies used in extensions
# If you remove an extension dependency, shim to retain extension compatibility
-keep,allowoptimization class androidx.preference.** { public protected *; }
-keep,allowoptimization class kotlin.** { public protected *; }
-keep,allowoptimization class kotlinx.coroutines.** { public protected *; }
-keep,allowoptimization class kotlinx.serialization.** { public protected *; }
-keep,allowoptimization class kotlin.time.** { public protected *; }
-keep,allowoptimization class okhttp3.** { public protected *; }
-keep,allowoptimization class okio.** { public protected *; }
-keep,allowoptimization class org.jsoup.** { public protected *; }
-keep,allowoptimization class rx.** { public protected *; }
-keep,allowoptimization class app.cash.quickjs.** { public protected *; }

# From extensions-lib
-keep,allowoptimization class eu.kanade.tachiyomi.network.interceptor.RateLimitInterceptorKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.interceptor.SpecificHostRateLimitInterceptorKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.NetworkHelper { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.OkHttpExtensionsKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.RequestsKt { public protected *; }
-keep,allowoptimization class ephyra.app.AppInfo { public protected *; }

##---------------Begin: proguard configuration for RxJava 1.x  ----------
-dontwarn sun.misc.**

-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
   long producerIndex;
   long consumerIndex;
}

-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueProducerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode producerNode;
}

-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueConsumerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode consumerNode;
}

-dontnote rx.internal.util.PlatformDependent
##---------------End: proguard configuration for RxJava 1.x  ----------

##---------------Begin: proguard configuration for okhttp  ----------
-keepclasseswithmembers class okhttp3.MultipartBody$Builder { *; }
##---------------End: proguard configuration for okhttp  ----------

##---------------Begin: proguard configuration for kotlinx.serialization  ----------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.** # core serialization annotations

# kotlinx-serialization-json specific. Add this if you have java.lang.NoClassDefFoundError kotlinx.serialization.json.JsonObjectSerializer
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class eu.kanade.**$$serializer { *; }
-keepclassmembers class eu.kanade.** {
    *** Companion;
}
-keepclasseswithmembers class eu.kanade.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.** {
    <methods>;
}
##---------------End: proguard configuration for kotlinx.serialization  ----------

# XmlUtil
-keep public enum nl.adaptivity.xmlutil.EventType { *; }

# Firebase
-keep class com.google.firebase.installations.** { *; }
-keep interface com.google.firebase.installations.** { *; }

# AndroidX Window
-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.sidecar.**

# Dagger Hilt EntryPoints Protection
# R8's aggressive optimization can strip entry point interfaces from SingletonComponent (SingletonCImpl)
# because they are only retrieved via reflection (EntryPointAccessors). This keeps the interfaces
# and their implementations intact in the final optimized APK.
-keep @dagger.hilt.EntryPoint interface * { *; }

# Explicit Keep Rules for All Dynamic EntryPoints & Their Implementations
-keep interface ephyra.app.data.work.WorkerFactoryEntryPoint { *; }
-keep class * implements ephyra.app.data.work.WorkerFactoryEntryPoint { *; }

-keep interface ephyra.app.di.ScreenEntryPoint { *; }
-keep class * implements ephyra.app.di.ScreenEntryPoint { *; }

-keep interface ephyra.app.di.CoreEntryPoint { *; }
-keep class * implements ephyra.app.di.CoreEntryPoint { *; }

-keep interface ephyra.app.widget.PreferencesEntryPoint { *; }
-keep class * implements ephyra.app.widget.PreferencesEntryPoint { *; }

-keep interface ephyra.core.util.SourceUtilEntryPoint { *; }
-keep class * implements ephyra.core.util.SourceUtilEntryPoint { *; }

-keep interface ephyra.feature.library.LibraryEntryPoint { *; }
-keep class * implements ephyra.feature.library.LibraryEntryPoint { *; }

-keep interface ephyra.feature.more.MoreEntryPoint { *; }
-keep class * implements ephyra.feature.more.MoreEntryPoint { *; }

-keep interface ephyra.presentation.core.ui.activity.BaseActivityEntryPoint { *; }
-keep class * implements ephyra.presentation.core.ui.activity.BaseActivityEntryPoint { *; }

-keep interface ephyra.presentation.core.util.SourceUtilEntryPoint { *; }
-keep class * implements ephyra.presentation.core.util.SourceUtilEntryPoint { *; }

-keep interface ephyra.presentation.core.util.view.ViewExtensionsEntryPoint { *; }
-keep class * implements ephyra.presentation.core.util.view.ViewExtensionsEntryPoint { *; }

-keep interface ephyra.presentation.core.widget.PreferencesEntryPoint { *; }
-keep class * implements ephyra.presentation.core.widget.PreferencesEntryPoint { *; }

-keep interface ephyra.presentation.widget.WidgetEntryPoint { *; }
-keep class * implements ephyra.presentation.widget.WidgetEntryPoint { *; }


