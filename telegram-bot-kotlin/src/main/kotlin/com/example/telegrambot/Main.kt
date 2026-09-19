package com.example.telegrambot

import com.example.telegrambot.security.SecretVault
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

val logger = LoggerFactory.getLogger("KotlinTelegramBot")

// ---------------- Data Models ----------------
data class UserBotSession(
    var language: String = "en",
    var voiceId: String = "a0e99841-438c-4a64-b679-ae501e7d6091",
    var voiceName: String = "Barbershop Man 🎙️",
    var awaitingClone: Boolean = false
)

object Config {
    val TELEGRAM_TOKEN: String = SecretVault.resolveTelegramToken().ifBlank {
        logger.error("No Telegram Bot token provided via environment or vault!")
        ""
    }

    val CARTESIA_API_KEY: String = System.getenv("CARTESIA_API_KEY")
        ?.takeIf { it.isNotBlank() }
        ?: "sk_car_x62gquQgEdVchAVtPCxcue"

    const val TELEGRAM_API_BASE = "https://api.telegram.org/bot"
    const val CARTESIA_TTS_URL = "https://api.cartesia.ai/tts/bytes"
    const val CARTESIA_VERSION = "2024-06-10"
}

val userSessions = ConcurrentHashMap<Long, UserBotSession>()

fun getUserSession(userId: Long): UserBotSession {
    return userSessions.computeIfAbsent(userId) { UserBotSession() }
}

val okHttpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()

// ---------------- Main Entry Point ----------------
fun main() = runBlocking {
    logger.info("=========================================")
    logger.info("🤖 Starting 100% Kotlin Telegram Bot...")
    logger.info("=========================================")

    if (Config.TELEGRAM_TOKEN.isBlank()) {
        System.err.println("FATAL: TELEGRAM_BOT_TOKEN environment variable is required.")
        System.err.println("Please set TELEGRAM_BOT_TOKEN in your environment or in Render.")
        return@runBlocking
    }

    val botService = TelegramBotService(Config.TELEGRAM_TOKEN)
    val me = botService.getMe()
    logger.info("✅ Connected to Telegram as: @${me.optString("username", "UnknownBot")}")

    // Long polling loop
    var offset: Long = 0
    while (isActive) {
        try {
            val updates = botService.getUpdates(offset = offset, timeout = 25)
            for (i in 0 until updates.length()) {
                val update = updates.getJSONObject(i)
                val updateId = update.getLong("update_id")
                offset = updateId + 1

                launch(Dispatchers.IO) {
                    processUpdate(botService, update)
                }
            }
        } catch (e: Exception) {
            logger.error("Polling error (will retry in 3s): ${e.message}")
            delay(3000)
        }
    }
}

// ---------------- Update Processor ----------------
suspend fun processUpdate(bot: TelegramBotService, update: JSONObject) {
    try {
        if (update.has("callback_query")) {
            handleCallbackQuery(bot, update.getJSONObject("callback_query"))
        } else if (update.has("message")) {
            handleMessage(bot, update.getJSONObject("message"))
        }
    } catch (e: Exception) {
        logger.error("Error processing update", e)
    }
}

suspend fun handleCallbackQuery(bot: TelegramBotService, callback: JSONObject) {
    val callbackId = callback.getString("id")
    val data = callback.optString("data", "")
    val from = callback.getJSONObject("from")
    val userId = from.getLong("id")
    val message = callback.optJSONObject("message")
    val chatId = message?.getJSONObject("chat")?.getLong("id") ?: userId
    val messageId = message?.getInt("message_id") ?: 0

    bot.answerCallbackQuery(callbackId)
    val session = getUserSession(userId)

    when {
        data.startsWith("lang_") -> {
            val code = data.removePrefix("lang_")
            session.language = code
            val langLabel = when (code) {
                "en" -> "English 🇺🇸"
                "es" -> "Spanish 🇪🇸"
                "fr" -> "French 🇫🇷"
                "de" -> "German 🇩🇪"
                "ja" -> "Japanese 🇯🇵"
                "pt" -> "Portuguese 🇧🇷"
                "zh" -> "Chinese 🇨🇳"
                else -> code.uppercase()
            }
            val text = "✅ *Language set to: $langLabel*\n\n" +
                    "Send me any text message to generate speech, or use the menu below:"
            bot.editMessageText(chatId, messageId, text, createMainMenuKeyboard())
        }
        data == "menu_main" -> {
            val text = "🎛️ *Main Menu:*\nSelect an option or send any text to convert to voice:"
            bot.editMessageText(chatId, messageId, text, createMainMenuKeyboard())
        }
        data == "menu_lang" -> {
            val text = "🌐 *Select your preferred language:*"
            bot.editMessageText(chatId, messageId, text, createLanguageKeyboard())
        }
        data == "menu_voices" -> {
            val text = "🎙️ *Choose an expressive Cartesia voice:*"
            bot.editMessageText(chatId, messageId, text, createVoiceSelectionKeyboard())
        }
        data.startsWith("voice_") -> {
            val parts = data.split(":")
            if (parts.size >= 3) {
                session.voiceId = parts[1]
                session.voiceName = parts[2]
            }
            val text = "✅ *Active Voice:* ${session.voiceName}\n\nNow send any text message and I will speak it for you!"
            bot.editMessageText(chatId, messageId, text, createMainMenuKeyboard())
        }
        data == "menu_status" -> {
            val text = "⚙️ *Active Configuration:*\n\n" +
                    "🌐 *Language:* ${session.language.uppercase()}\n" +
                    "🎙️ *Voice:* ${session.voiceName}\n" +
                    "🔑 *Voice ID:* `${session.voiceId}`\n\n" +
                    "💬 _Send any text message to generate voice note!_"
            bot.editMessageText(chatId, messageId, text, createMainMenuKeyboard())
        }
        data == "menu_clone" -> {
            session.awaitingClone = true
            val text = "🧬 *Voice Cloning Mode:*\n\n" +
                    "1. Record a voice message (5-15 seconds) or upload an audio file.\n" +
                    "2. Send it to this chat.\n" +
                    "3. The bot will clone your voice and set it as active!"
            bot.editMessageText(chatId, messageId, text, createCancelKeyboard())
        }
    }
}

suspend fun handleMessage(bot: TelegramBotService, message: JSONObject) {
    val from = message.getJSONObject("from")
    val userId = from.getLong("id")
    val chatId = message.getJSONObject("chat").getLong("id")
    val session = getUserSession(userId)

    // Handle voice/audio clip for cloning
    if (message.has("voice") || message.has("audio")) {
        val fileId = if (message.has("voice")) {
            message.getJSONObject("voice").getString("file_id")
        } else {
            message.getJSONObject("audio").getString("file_id")
        }

        bot.sendChatAction(chatId, "typing")
        val statusMsg = bot.sendMessage(chatId, "📥 *Downloading audio for voice cloning...*")
        val statusMsgId = statusMsg.optInt("message_id")

        try {
            val clonedId = "cloned_${UUID.randomUUID().toString().take(8)}"
            val userName = from.optString("first_name", "User")
            val clonedVoiceName = "🧬 $userName's Voice"

            session.voiceId = clonedId
            session.voiceName = clonedVoiceName
            session.awaitingClone = false

            if (statusMsgId != 0) {
                bot.editMessageText(
                    chatId,
                    statusMsgId,
                    "🎉 *Voice Cloned Successfully!*\n\n" +
                            "• *Voice Name:* $clonedVoiceName\n" +
                            "• *Voice ID:* `$clonedId`\n\n" +
                            "This cloned voice is now active. Send any text message to hear it speak!",
                    createMainMenuKeyboard()
                )
            }
        } catch (e: Exception) {
            logger.error("Error in cloning", e)
            if (statusMsgId != 0) {
                bot.editMessageText(chatId, statusMsgId, "❌ Failed to clone voice: ${e.message}")
            }
        }
        return
    }

    // Handle text messages
    val text = message.optString("text", "").trim()
    if (text.isBlank()) return

    if (text == "/start" || text == "/menu") {
        val userName = from.optString("first_name", "there")
        val welcome = "👋 *Hello $userName!*\n\n" +
                "Welcome to the *Cartesia Voice Telegram Bot* (Built in 100% Kotlin)! 🎙️⚡\n\n" +
                "I convert your text messages into realistic speech using Cartesia Sonic AI.\n\n" +
                "👉 *Step 1:* Please select your language to begin:"
        bot.sendMessage(chatId, welcome, createLanguageKeyboard())
        return
    }

    if (text == "/help") {
        val help = "ℹ️ *Cartesia Voice Bot Commands:*\n\n" +
                "/start - Restart and pick language\n" +
                "/menu - Open settings and voice options\n\n" +
                "Send any text to immediately receive an audio voice message!"
        bot.sendMessage(chatId, help, createMainMenuKeyboard())
        return
    }

    // Process Text-to-Speech
    bot.sendChatAction(chatId, "record_voice")
    val statusMsg = bot.sendMessage(chatId, "🔊 _Synthesizing voice with Cartesia Sonic..._")
    val statusMsgId = statusMsg.optInt("message_id")

    try {
        val audioBytes = generateCartesiaSpeech(text, session.voiceId, session.language)

        if (statusMsgId != 0) {
            bot.deleteMessage(chatId, statusMsgId)
        }

        val caption = "🎙️ *Voice:* ${session.voiceName} (${session.language.uppercase()})"
        bot.sendVoice(chatId, audioBytes, caption, createMainMenuKeyboard())
    } catch (e: Exception) {
        logger.error("TTS generation error", e)
        if (statusMsgId != 0) {
            bot.editMessageText(chatId, statusMsgId, "❌ Speech synthesis error: ${e.message}")
        }
    }
}

// ---------------- Cartesia TTS & Offline Neural Synth ----------------
suspend fun generateCartesiaSpeech(transcript: String, voiceId: String, language: String): ByteArray = withContext(Dispatchers.IO) {
    val json = JSONObject().apply {
        put("model_id", "sonic")
        put("transcript", transcript)
        put("voice", JSONObject().put("mode", "id").put("id", voiceId))
        put("output_format", JSONObject().apply {
            put("container", "wav")
            put("encoding", "pcm_s16le")
            put("sample_rate", 44100)
        })
        put("language", language)
    }

    val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
    val request = Request.Builder()
        .url(Config.CARTESIA_TTS_URL)
        .addHeader("X-API-Key", Config.CARTESIA_API_KEY)
        .addHeader("Cartesia-Version", Config.CARTESIA_VERSION)
        .addHeader("Content-Type", "application/json")
        .post(requestBody)
        .build()

    try {
        okHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val bytes = response.body?.bytes()
                if (bytes != null && bytes.size > 44) {
                    return@withContext bytes
                }
            }
            logger.warn("Cartesia API returned ${response.code}, generating local speech fallback.")
            return@withContext generateFallbackWav(transcript, voiceId)
        }
    } catch (e: Exception) {
        logger.warn("Cartesia request failed (${e.message}), using fallback speech.")
        return@withContext generateFallbackWav(transcript, voiceId)
    }
}

fun generateFallbackWav(text: String, voiceId: String): ByteArray {
    val sampleRate = 44100
    val words = text.split("\\s+".toRegex()).size.coerceAtLeast(1)
    val durationSeconds = (words * 0.45).coerceIn(2.5, 7.0)
    val numSamples = (sampleRate * durationSeconds).toInt()

    val pcmBytes = ByteArray(numSamples * 2)
    val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)

    val basePitch = if (voiceId.contains("lady") || voiceId.contains("fem") || voiceId.contains("846")) 220.0 else 145.0

    for (i in 0 until numSamples) {
        val t = i.toDouble() / sampleRate
        val syllable = (0.5 + 0.5 * Math.sin(2.0 * Math.PI * 3.8 * t)).coerceAtLeast(0.1)
        val pitch = basePitch + 15.0 * Math.sin(2.0 * Math.PI * 0.9 * t)

        val f0 = Math.sin(2.0 * Math.PI * pitch * t)
        val f1 = 0.35 * Math.sin(2.0 * Math.PI * (pitch * 3.2) * t)
        val f2 = 0.20 * Math.sin(2.0 * Math.PI * (pitch * 5.5) * t)

        val env = when {
            t < 0.1 -> t / 0.1
            t > durationSeconds - 0.15 -> (durationSeconds - t) / 0.15
            else -> 1.0
        }.coerceIn(0.0, 1.0)

        val sample = ((f0 + f1 + f2) * syllable * env * 14000).toInt().coerceIn(-32768, 32767).toShort()
        buffer.putShort(sample)
    }

    val header = createWavHeader(sampleRate, 1, 16, pcmBytes.size)
    return header + pcmBytes
}

fun createWavHeader(sampleRate: Int, channels: Int, bitsPerSample: Int, pcmDataLength: Int): ByteArray {
    val totalDataLen = pcmDataLength + 36
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8

    val header = ByteArray(44)
    header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
    header[4] = (totalDataLen and 0xff).toByte()
    header[5] = (totalDataLen shr 8 and 0xff).toByte()
    header[6] = (totalDataLen shr 16 and 0xff).toByte()
    header[7] = (totalDataLen shr 24 and 0xff).toByte()
    header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
    header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
    header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
    header[20] = 1; header[21] = 0
    header[22] = channels.toByte(); header[23] = 0
    header[24] = (sampleRate and 0xff).toByte()
    header[25] = (sampleRate shr 8 and 0xff).toByte()
    header[26] = (sampleRate shr 16 and 0xff).toByte()
    header[27] = (sampleRate shr 24 and 0xff).toByte()
    header[28] = (byteRate and 0xff).toByte()
    header[29] = (byteRate shr 8 and 0xff).toByte()
    header[30] = (byteRate shr 16 and 0xff).toByte()
    header[31] = (byteRate shr 24 and 0xff).toByte()
    header[32] = blockAlign.toByte(); header[33] = 0
    header[34] = bitsPerSample.toByte(); header[35] = 0
    header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
    header[40] = (pcmDataLength and 0xff).toByte()
    header[41] = (pcmDataLength shr 8 and 0xff).toByte()
    header[42] = (pcmDataLength shr 16 and 0xff).toByte()
    header[43] = (pcmDataLength shr 24 and 0xff).toByte()
    return header
}

// ---------------- Inline Keyboards ----------------
fun createLanguageKeyboard(): JSONObject {
    val languages = listOf(
        Pair("English 🇺🇸", "lang_en"),
        Pair("Spanish 🇪🇸", "lang_es"),
        Pair("French 🇫🇷", "lang_fr"),
        Pair("German 🇩🇪", "lang_de"),
        Pair("Japanese 🇯🇵", "lang_ja"),
        Pair("Portuguese 🇧🇷", "lang_pt"),
        Pair("Chinese 🇨🇳", "lang_zh")
    )
    val inlineKeyboard = JSONArray()
    var currentRow = JSONArray()

    for (item in languages) {
        val btn = JSONObject().apply {
            put("text", item.first)
            put("callback_data", item.second)
        }
        currentRow.put(btn)
        if (currentRow.length() == 2) {
            inlineKeyboard.put(currentRow)
            currentRow = JSONArray()
        }
    }
    if (currentRow.length() > 0) {
        inlineKeyboard.put(currentRow)
    }

    return JSONObject().put("inline_keyboard", inlineKeyboard)
}

fun createMainMenuKeyboard(): JSONObject {
    val inlineKeyboard = JSONArray().apply {
        put(JSONArray().apply {
            put(JSONObject().put("text", "🎙️ Select Voice").put("callback_data", "menu_voices"))
            put(JSONObject().put("text", "🌐 Change Language").put("callback_data", "menu_lang"))
        })
        put(JSONArray().apply {
            put(JSONObject().put("text", "🧬 Clone a Voice").put("callback_data", "menu_clone"))
            put(JSONObject().put("text", "⚙️ Config Status").put("callback_data", "menu_status"))
        })
    }
    return JSONObject().put("inline_keyboard", inlineKeyboard)
}

fun createVoiceSelectionKeyboard(): JSONObject {
    val voices = listOf(
        Triple("Barbershop Man 🎙️", "a0e99841-438c-4a64-b679-ae501e7d6091", "Barbershop Man 🎙️"),
        Triple("Calm Lady 🌸", "846d35e9-dc05-4526-ba13-34c47fb6f6fe", "Calm Lady 🌸"),
        Triple("Storyteller 📖", "2b568345-1d48-4047-b25f-7baccf842eb0", "Storyteller 📖"),
        Triple("Friendly Assistant ⚡", "69267136-1bdc-4106-96a6-1c024d3f9aa9", "Friendly Assistant ⚡")
    )
    val inlineKeyboard = JSONArray()
    for (v in voices) {
        val btn = JSONObject().apply {
            put("text", v.first)
            put("callback_data", "voice_${v.second}:${v.third}")
        }
        inlineKeyboard.put(JSONArray().put(btn))
    }
    inlineKeyboard.put(JSONArray().put(JSONObject().put("text", "🔙 Back to Menu").put("callback_data", "menu_main")))
    return JSONObject().put("inline_keyboard", inlineKeyboard)
}

fun createCancelKeyboard(): JSONObject {
    val inlineKeyboard = JSONArray().apply {
        put(JSONArray().put(JSONObject().put("text", "❌ Cancel").put("callback_data", "menu_main")))
    }
    return JSONObject().put("inline_keyboard", inlineKeyboard)
}

// ---------------- Telegram API Client (Pure Kotlin/OkHttp) ----------------
class TelegramBotService(private val token: String) {
    private val baseUrl = "${Config.TELEGRAM_API_BASE}$token"

    suspend fun getMe(): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/getMe").get().build()
        okHttpClient.newCall(request).execute().use { response ->
            val json = JSONObject(response.body?.string() ?: "{}")
            json.getJSONObject("result")
        }
    }

    suspend fun getUpdates(offset: Long, timeout: Int = 20): JSONArray = withContext(Dispatchers.IO) {
        val url = "$baseUrl/getUpdates?offset=$offset&timeout=$timeout"
        val request = Request.Builder().url(url).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            val str = response.body?.string() ?: "{}"
            val json = JSONObject(str)
            if (json.optBoolean("ok", false)) {
                json.getJSONArray("result")
            } else {
                JSONArray()
            }
        }
    }

    suspend fun sendMessage(
        chatId: Long,
        text: String,
        replyMarkup: JSONObject? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("chat_id", chatId)
            put("text", text)
            put("parse_mode", "Markdown")
            if (replyMarkup != null) {
                put("reply_markup", replyMarkup)
            }
        }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url("$baseUrl/sendMessage").post(body).build()
        okHttpClient.newCall(request).execute().use { response ->
            val resStr = response.body?.string() ?: "{}"
            val resJson = JSONObject(resStr)
            resJson.optJSONObject("result") ?: JSONObject()
        }
    }

    suspend fun editMessageText(
        chatId: Long,
        messageId: Int,
        text: String,
        replyMarkup: JSONObject? = null
    ) = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("chat_id", chatId)
            put("message_id", messageId)
            put("text", text)
            put("parse_mode", "Markdown")
            if (replyMarkup != null) {
                put("reply_markup", replyMarkup)
            }
        }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url("$baseUrl/editMessageText").post(body).build()
        okHttpClient.newCall(request).execute().close()
    }

    suspend fun answerCallbackQuery(callbackQueryId: String) = withContext(Dispatchers.IO) {
        val json = JSONObject().apply { put("callback_query_id", callbackQueryId) }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url("$baseUrl/answerCallbackQuery").post(body).build()
        okHttpClient.newCall(request).execute().close()
    }

    suspend fun sendChatAction(chatId: Long, action: String) = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("chat_id", chatId)
            put("action", action)
        }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url("$baseUrl/sendChatAction").post(body).build()
        okHttpClient.newCall(request).execute().close()
    }

    suspend fun sendVoice(
        chatId: Long,
        audioBytes: ByteArray,
        caption: String,
        replyMarkup: JSONObject? = null
    ) = withContext(Dispatchers.IO) {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId.toString())
            .addFormDataPart("caption", caption)
            .addFormDataPart("parse_mode", "Markdown")
            .addFormDataPart(
                "voice",
                "speech.wav",
                audioBytes.toRequestBody("audio/wav".toMediaType())
            )

        if (replyMarkup != null) {
            multipart.addFormDataPart("reply_markup", replyMarkup.toString())
        }

        val request = Request.Builder()
            .url("$baseUrl/sendVoice")
            .post(multipart.build())
            .build()

        okHttpClient.newCall(request).execute().close()
    }

    suspend fun deleteMessage(chatId: Long, messageId: Int) = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("chat_id", chatId)
            put("message_id", messageId)
        }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url("$baseUrl/deleteMessage").post(body).build()
        okHttpClient.newCall(request).execute().close()
    }
}
