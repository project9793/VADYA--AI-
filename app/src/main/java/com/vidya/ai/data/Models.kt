package com.vidya.ai.data

data class ChatMessage(val id: Long, val role: String, val text: String, val time: Long)

data class Conversation(
    val id: String,
    val title: String,
    val updated: Long,
    val messages: List<ChatMessage>
)
