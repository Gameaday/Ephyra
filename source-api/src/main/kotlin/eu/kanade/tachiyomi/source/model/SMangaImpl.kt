@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.JsonObject

class SMangaImpl : SManga {

    override lateinit var url: String

    override lateinit var title: String

    override var artist: String? = null

    override var author: String? = null

    override var description: String? = null

    override var genre: String? = null

    override var genres: List<String> = emptyList()

    override var readingMode: SManga.ReadingMode? = null

    override var banner: String? = null

    override var altTitles: List<String> = emptyList()

    override var language: String? = null

    override var score: Int? = null

    // SAFE, not null: upstream declares this non-null because an app that blurs covers has to read
    // it without a null check, and an unset field would otherwise mean "unknown", which is the one
    // reading a blur filter must not default to.
    override var contentRating: SManga.ContentRating = SManga.ContentRating.SAFE

    override var status: Int = 0

    override var thumbnail_url: String? = null

    override var update_strategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE

    override var initialized: Boolean = false

    override var memo: JsonObject = JsonObject(emptyMap())
}
