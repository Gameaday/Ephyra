package ephyra.domain.chapter.interactor

import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.service.ChapterNumber

class ShouldUpdateDbChapter {

    fun await(dbChapter: Chapter, sourceChapter: Chapter): Boolean {
        return dbChapter.scanlator != sourceChapter.scanlator ||
            dbChapter.name != sourceChapter.name ||
            dbChapter.dateUpload != sourceChapter.dateUpload ||
            // DEF-012: compared with `!=` this reports "different" for every decimal chapter
            // number after a backup restore, because the stored value came back through a
            // `Float` field. The chapter is then rewritten on *every* sync, forever, for a
            // difference that does not exist. See `ChapterNumber.sameChapterNumber`.
            !ChapterNumber.sameChapterNumber(dbChapter.chapterNumber, sourceChapter.chapterNumber) ||
            dbChapter.sourceOrder != sourceChapter.sourceOrder
    }
}
