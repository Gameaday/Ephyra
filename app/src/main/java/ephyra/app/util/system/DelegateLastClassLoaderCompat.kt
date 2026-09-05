package ephyra.app.util.system

import dalvik.system.DelegateLastClassLoader

/**
 * Creates an Android-native delegate-last class loader used to load extension APKs.
 *
 * The extension's own dex classes take priority, but any class it does not bundle
 * resolves against the parent (host) class loader. This matches the resolution
 * semantics of current Mihon extensions (extension-lib 1.6) and remains compatible
 * with 1.4/1.5 extensions, replacing the previous child-first loader which could
 * shadow host-provided shared classes.
 *
 * [DelegateLastClassLoader] is final and requires API 28+; the app's minimum SDK is
 * well above this, so no fallback or subclassing is needed.
 */
@Suppress("ktlint:standard:function-naming") // PascalCase factory mirroring the wrapped DelegateLastClassLoader API
fun DelegateLastClassLoaderCompat(
    dexPath: String,
    parent: ClassLoader,
): DelegateLastClassLoader = DelegateLastClassLoader(dexPath, parent)
