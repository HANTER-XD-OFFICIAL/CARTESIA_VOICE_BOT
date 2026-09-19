package com.example.data.model

enum class MessageSender {
    BOT,
    USER
}

data class ChatMessage(
    val id: String,
    val sender: MessageSender,
    val text: String? = null,
    val audioFilePath: String? = null,
    val audioDurationMs: Long = 0,
    val voiceUsed: Voice? = null,
    val languageUsed: Language? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val inlineButtons: List<InlineButton> = emptyList(),
    val isAudioGenerating: Boolean = false,
    val errorText: String? = null
)
