package com.vidya.ai.core

import android.text.Html
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object YouTubeApi {
    /** Returns (videoId, title) of the top result, or null. Several keys can be rotated on quota errors. */
    fun firstVideo(apiKeys: String, query: String): Pair<String, String>? {
        for (k in Keys.list(apiKeys)) {
            try {
                val url = URL(
                    "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=1" +
                        "&safeSearch=moderate&q=${URLEncoder.encode(query, "UTF-8")}&key=$k"
                )
                val c = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6_000
                    readTimeout = 8_000
                }
                val code = c.responseCode
                val raw = (if (code in 200..299) c.inputStream else c.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                c.disconnect()
                if (code in 200..299) {
                    val item = JSONObject(raw).optJSONArray("items")?.optJSONObject(0) ?: return null
                    val id = item.optJSONObject("id")?.optString("videoId").orEmpty()
                    val title = Html.fromHtml(
                        item.optJSONObject("snippet")?.optString("title").orEmpty(), Html.FROM_HTML_MODE_LEGACY
                    ).toString()
                    if (id.isNotBlank()) return id to title
                    return null
                }
                if (code != 403 && code != 429) return null      // quota problem -> try the next key
            } catch (e: Exception) {
                return null
            }
        }
        return null
    }
}
