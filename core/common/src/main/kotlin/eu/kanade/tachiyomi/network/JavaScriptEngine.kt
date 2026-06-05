package eu.kanade.tachiyomi.network

import android.content.Context
import app.cash.quickjs.QuickJs
import ephyra.core.common.util.lang.withIOContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

interface QuickJsHttpClient {
    fun get(url: String, headersJson: String): String
    fun post(url: String, body: String, headersJson: String): String
}

@Suppress("UNUSED", "UNCHECKED_CAST")
class JavaScriptEngine(
    private val context: Context,
    private val networkHelper: NetworkHelper,
) {
    private val httpClient = object : QuickJsHttpClient {
        override fun get(url: String, headersJson: String): String {
            val builder = Request.Builder().url(url)
            if (headersJson.isNotBlank()) {
                try {
                    val json = JSONObject(headersJson)
                    json.keys().forEach { key ->
                        builder.addHeader(key, json.optString(key))
                    }
                } catch (e: Exception) {
                    // Ignore malformed headers
                }
            }
            val response = networkHelper.client.newCall(builder.build()).execute()
            if (!response.isSuccessful) throw IOException("HTTP error ${response.code}")
            return response.body?.string() ?: ""
        }

        override fun post(url: String, body: String, headersJson: String): String {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = body.toRequestBody(mediaType)
            val builder = Request.Builder().url(url).post(requestBody)
            if (headersJson.isNotBlank()) {
                try {
                    val json = JSONObject(headersJson)
                    json.keys().forEach { key ->
                        builder.addHeader(key, json.optString(key))
                    }
                } catch (e: Exception) {
                    // Ignore malformed headers
                }
            }
            val response = networkHelper.client.newCall(builder.build()).execute()
            if (!response.isSuccessful) throw IOException("HTTP error ${response.code}")
            return response.body?.string() ?: ""
        }
    }

    suspend fun <T> evaluate(script: String): T = withIOContext {
        // Increase timeout for scrapers (e.g. 15 seconds) because they perform network requests
        kotlinx.coroutines.withTimeout(15000) {
            QuickJs.create().use { quickJs ->
                quickJs.set("http", QuickJsHttpClient::class.java, httpClient)
                quickJs.evaluate(script) as T
            }
        }
    }
}
