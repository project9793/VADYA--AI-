package com.vidya.ai.data

import com.vidya.ai.core.SecureStore
import org.json.JSONArray
import org.json.JSONObject

data class MemoryItem(val id: Long, val text: String)

/** Things the user wants the assistant to remember (stored encrypted on the phone). */
class MemoryRepository(private val store: SecureStore) {

    @Synchronized
    fun all(): List<MemoryItem> = try {
        val a = JSONArray(store.memoryJson)
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            MemoryItem(o.getLong("id"), o.getString("text"))
        }
    } catch (e: Exception) { emptyList() }

    @Synchronized
    fun add(text: String) {
        val t = text.trim().take(300)
        if (t.isEmpty()) return
        save(all() + MemoryItem(System.currentTimeMillis(), t))
    }

    @Synchronized
    fun delete(id: Long) = save(all().filter { it.id != id })

    @Synchronized
    fun clear() = save(emptyList())

    private fun save(list: List<MemoryItem>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("text", it.text)) }
        store.memoryJson = a.toString()
    }
}
