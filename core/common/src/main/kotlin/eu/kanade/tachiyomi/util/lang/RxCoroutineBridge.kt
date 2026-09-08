package eu.kanade.tachiyomi.util.lang

import rx.Observable
import tachiyomi.core.common.util.lang.awaitSingle as coreAwaitSingle

suspend fun <T> Observable<T>.awaitSingle(): T = coreAwaitSingle()
