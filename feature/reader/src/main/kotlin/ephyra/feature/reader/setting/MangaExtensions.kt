package ephyra.domain.manga.model

import ephyra.domain.manga.model.Manga
import ephyra.feature.reader.setting.ReaderOrientation
import ephyra.feature.reader.setting.ReadingMode

val Manga.readingMode: Long
    get() = viewerFlags and ReadingMode.MASK.toLong()

val Manga.readerOrientation: Long
    get() = viewerFlags and ReaderOrientation.MASK.toLong()
