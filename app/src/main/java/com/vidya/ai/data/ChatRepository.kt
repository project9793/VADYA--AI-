package com.vidya.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Stores each conversation as a JSON file in the app's private storage. */
class ChatRepository(context: Context) {
    private val dir = File(context.filesDir, "chats").apply { mkdirs() }

    @Synchronized
    fun save(c: Conversation) {
        val arr = JSONArray()
        c.messages.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("role", it.role)
                    .put("text", it.text).put("time", it.time)
            )
        }
        val o = JSONObject().put("id", c.id).put("title", c.title)
            .put("updated", c.updated).put("messages", arr)
        File(dir, "${c.id}.json").writeText(o.toString())
    }

    @Synchronized
    fun loadAll(): List<Conversation> =
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { parse(it) }
            .sortedByDescending { it.updated }

    @Synchronized
    fun load(id: String): Conversation? = parse(File(dir, "$id.json"))

    @Synchronized
    fun delete(id: String) { File(dir, "$id.json").delete() }

    @Synchronized
    fun clearAll() { dir.listFiles()?.forEach { it.delete() } }

    private fun parse(f: File): Conversation? = try {
        val o = JSONObject(f.readText())
        val arr = o.getJSONArray("messages")
        Conversation(
            id = o.getString("id"),
            title = o.optString("title"),
            updated = o.optLong("updated"),
            messages = (0 until arr.length()).map {
                val m = arr.getJSONObject(it)
                ChatMessage(m.getLong("id"), m.getString("role"), m.getString("text"), m.optLong("time"))
            }
        )
    } catch (e: Exception) { null }
}
