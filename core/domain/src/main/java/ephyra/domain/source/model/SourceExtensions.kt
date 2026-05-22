package ephyra.domain.source.model

import ephyra.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.Source as ApiSource

fun Manga.isLocal(): Boolean = source == 0L

fun ApiSource.isLocal(): Boolean = id == 0L

fun ApiSource.isLocalOrStub(): Boolean = isLocal() || this is StubSource

fun Source.isLocal(): Boolean = id == 0L
