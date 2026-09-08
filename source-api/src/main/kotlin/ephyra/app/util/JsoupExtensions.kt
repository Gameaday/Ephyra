package ephyra.app.util

import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import eu.kanade.tachiyomi.util.asJsoup as tachiyomiAsJsoup
import eu.kanade.tachiyomi.util.attrOrText as tachiyomiAttrOrText
import eu.kanade.tachiyomi.util.selectInt as tachiyomiSelectInt
import eu.kanade.tachiyomi.util.selectText as tachiyomiSelectText

fun Element.selectText(css: String, defaultValue: String? = null): String? =
    tachiyomiSelectText(css, defaultValue)

fun Element.selectInt(css: String, defaultValue: Int = 0): Int =
    tachiyomiSelectInt(css, defaultValue)

fun Element.attrOrText(css: String): String =
    tachiyomiAttrOrText(css)

/**
 * Returns a Jsoup document for this response.
 * @param html the body of the response. Use only if the body was read before calling this method.
 */
fun Response.asJsoup(html: String? = null): Document =
    tachiyomiAsJsoup(html)
