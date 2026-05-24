package ephyra.source.api

import eu.kanade.tachiyomi.network.JavaScriptEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scriptable engine evaluating dynamic user-supplied JavaScript scraping rules.
 * Uses the underlying sandboxed QuickJS engine for Play Store compliant execution.
 */
@Singleton
class ScriptableSourceEngine @Inject constructor(
    private val jsEngine: JavaScriptEngine,
) {
    /**
     * Pre-loads custom JS scraper rule scripts and evaluates selectors to resolve items.
     */
    suspend fun executeScraper(
        scriptRule: String,
        functionName: String,
        payload: String,
    ): String {
        val wrappedScript = """
            $scriptRule

            // Execute the entry function
            $functionName('$payload');
        """.trimIndent()
        return jsEngine.evaluate(wrappedScript)
    }
}
