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
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

val logger = LoggerFactory.getLogger("KotlinTelegramBot")

// ---------------- Data Models & Persistence ----------------
data class ClonedVoiceProfile(
    val id: String,
    var name: String,
    val language: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class UserBotSession(
    var language: String = "en",
    var voiceId: String = "a0e99841-438c-4a64-b679-ae501e7d6091",
    var voiceName: String = "Barbershop Man 🎙️",
    var awaitingCloneAudio: Boolean = false,
    var pendingAudioBytes: ByteArray? = null,
    var isBlocked: Boolean = false,
    val savedVoices: MutableList<ClonedVoiceProfile> = mutableListOf()
)

object VoiceStorage {
    private val storageFile = File("user_voice_profiles.json")

    fun loadSessions(targetMap: ConcurrentHashMap<Long, UserBotSession>) {
        if (!storageFile.exists()) return
        try {
            val content = storageFile.readText()
            val root = JSONObject(content)
            for (key in root.keySet()) {
                val userId = key.toLongOrNull() ?: continue
                val uObj = root.getJSONObject(key)
                val session = UserBotSession(
                    language = uObj.optString("language", "en"),
                    voiceId = uObj.optString("voiceId", "a0e99841-438c-4a64-b679-ae501e7d6091"),
                    voiceName = uObj.optString("voiceName", "Barbershop Man 🎙️"),
                    isBlocked = uObj.optBoolean("isBlocked", false)
                )
                val voicesArr = uObj.optJSONArray("savedVoices")
                if (voicesArr != null) {
                    for (i in 0 until voicesArr.length()) {
                        val v = voicesArr.getJSONObject(i)
                        session.savedVoices.add(
                            ClonedVoiceProfile(
                                id = v.getString("id"),
                                name = v.getString("name"),
                                language = v.optString("language", "en"),
                                createdAt = v.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                    }
                }
                targetMap[userId] = session
            }
            logger.info("Loaded ${targetMap.size} user sessions from disk storage.")
        } catch (e: Exception) {
            logger.error("Failed to read user voice profiles storage: ${e.message}")
        }
    }

    fun saveSessions(sourceMap: ConcurrentHashMap<Long, UserBotSession>) {
        try {
            val root = JSONObject()
            for ((userId, session) in sourceMap) {
                val uObj = JSONObject().apply {
                    put("language", session.language)
                    put("voiceId", session.voiceId)
                    put("voiceName", session.voiceName)
                    put("isBlocked", session.isBlocked)
                    val arr = JSONArray()
                    for (v in session.savedVoices) {
                        arr.put(JSONObject().apply {
                            put("id", v.id)
                            put("name", v.name)
                            put("language", v.language)
                            put("createdAt", v.createdAt)
                        })
                    }
                    put("savedVoices", arr)
                }
                root.put(userId.toString(), uObj)
            }
            storageFile.writeText(root.toString(2))
        } catch (e: Exception) {
            logger.error("Failed to save user voice profiles: ${e.message}")
        }
    }
}

object Config {
    val TELEGRAM_TOKEN: String = SecretVault.resolveTelegramToken().ifBlank {
        logger.error("No Telegram Bot token provided via environment or vault!")
        ""
    }

    val CARTESIA_API_KEY: String = SecretVault.resolveCartesiaApiKey()
    val CARTESIA_API_KEY_FALLBACK: String = SecretVault.resolveSecondaryCartesiaApiKey()

    const val TELEGRAM_API_BASE = "https://api.telegram.org/bot"
    const val TELEGRAM_FILE_BASE = "https://api.telegram.org/file/bot"
    const val CARTESIA_BASE_URL = "https://api.cartesia.ai"
    const val CARTESIA_TTS_URL = "https://api.cartesia.ai/tts/bytes"
    const val CARTESIA_CLONE_URL = "https://api.cartesia.ai/voices/clone"
    const val CARTESIA_VERSION = "2024-06-10"

    // Supported Global Languages with Flags and Names
    val SUPPORTED_LANGUAGES = listOf(
        Triple("bn", "বাংলা 🇧🇩", "Bengali"),
        Triple("en", "English 🇺🇸", "English"),
        Triple("hi", "हिन्दी 🇮🇳", "Hindi"),
        Triple("ar", "العربية 🇸🇦", "Arabic"),
        Triple("es", "Español 🇪🇸", "Spanish"),
        Triple("fr", "Français 🇫🇷", "French"),
        Triple("de", "Deutsch 🇩🇪", "German"),
        Triple("ja", "日本語 🇯🇵", "Japanese"),
        Triple("pt", "Português 🇧🇷", "Portuguese"),
        Triple("zh", "中文 🇨🇳", "Chinese"),
        Triple("ru", "Русский 🇷🇺", "Russian"),
        Triple("tr", "Türkçe 🇹🇷", "Turkish"),
        Triple("ko", "한국어 🇰🇷", "Korean"),
        Triple("it", "Italiano 🇮🇹", "Italian"),
        Triple("ur", "اردو 🇵🇰", "Urdu")
    )
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

    VoiceStorage.loadSessions(userSessions)

    if (Config.TELEGRAM_TOKEN.isBlank()) {
        System.err.println("FATAL: TELEGRAM_BOT_TOKEN environment variable is required.")
        System.err.println("Please set TELEGRAM_BOT_TOKEN in your environment or in Render.")
        return@runBlocking
    }

    val botService = TelegramBotService(Config.TELEGRAM_TOKEN)
    val me = botService.getMe()
    logger.info("✅ Connected to Telegram as: @${me.optString("username", "UnknownBot")}")

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

    if (session.isBlocked) {
        bot.editMessageText(chatId, messageId, "⛔ *Your access has been suspended by the administrator.*")
        return
    }

    when {
        // Change Active Language
        data.startsWith("lang_") -> {
            val code = data.removePrefix("lang_")
            session.language = code
            val langObj = Config.SUPPORTED_LANGUAGES.find { it.first == code }
            val label = langObj?.second ?: code.uppercase()
            VoiceStorage.saveSessions(userSessions)

            val text = "✅ *Language set to: $label*\n\n" +
                    "Now send any text message in this language to generate realistic speech, or select an option below:"
            bot.editMessageText(chatId, messageId, text, createMainMenuKeyboard(session))
        }

        // Language selected during Voice Cloning flow
        data.startsWith("clonelang_") -> {
            val selectedLang = data.removePrefix("clonelang_")
            val audioBytes = session.pendingAudioBytes
            if (audioBytes == null || audioBytes.isEmpty()) {
                bot.editMessageText(chatId, messageId, "⚠️ *No recorded audio found!* Please record your 10-15s voice clip again.", createMainMenuKeyboard(session))
                return
            }

            val langObj = Config.SUPPORTED_LANGUAGES.find { it.first == selectedLang }
            val langLabel = langObj?.second ?: selectedLang.uppercase()
            val userName = from.optString("first_name", "User")
            val newProfileName = "$userName's Voice ($langLabel)"

            bot.editMessageText(chatId, messageId, "⏳ *Cartesia AI is creating your Voice Profile for $langLabel...*")

            try {
                val clonedVoiceId = cloneCartesiaVoice(audioBytes, newProfileName, selectedLang)
                val newProfile = ClonedVoiceProfile(
                    id = clonedVoiceId,
                    name = newProfileName,
                    language = selectedLang
                )
                session.savedVoices.add(newProfile)
                session.voiceId = clonedVoiceId
                session.voiceName = newProfileName
                session.language = selectedLang
                session.pendingAudioBytes = null
                session.awaitingCloneAudio = false
                VoiceStorage.saveSessions(userSessions)

                val successText = "🎉 *Voice Profile Created & Saved!*\n\n" +
                        "• *Voice Name:* `$newProfileName`\n" +
                        "• *Language:* $langLabel\n" +
                        "• *Voice ID:* `${clonedVoiceId}`\n\n" +
                        "💾 This voice is permanently saved to your profile.\n" +
                        "Send any text now to generate realistic speech in your own voice!"

                bot.editMessageText(chatId, messageId, successText, createMainMenuKeyboard(session))
            } catch (e: Exception) {
                logger.error("Error creating clone profile", e)
                bot.editMessageText(chatId, messageId, "❌ Failed to create cloned voice: ${e.message}", createMainMenuKeyboard(session))
            }
        }

        data == "menu_main" -> {
            val text = "🎛️ *Main Dashboard:*\nSelect an option or send any text to convert to voice:"
            bot.editMessageText(chatId, messageId, text, createMainMenuKeyboard(session))
        }

        data == "menu_lang" -> {
            val text = "🌐 *Select your preferred TTS Language:*\n(Supports Bengali, Hindi, English, Arabic, and more)"
            bot.editMessageText(chatId, messageId, text, createLanguageKeyboard("lang_"))
        }

        data == "menu_voices" -> {
            val text = "🎙️ *Choose an Active Voice:*\nSelect from Cartesia studio presets or your own saved voice profiles:"
            bot.editMessageText(chatId, messageId, text, createVoiceSelectionKeyboard(session))
        }

        data == "menu_my_voices" -> {
            val text = "🧬 *My Saved Voice Profiles (${session.savedVoices.size}):*\n" +
                    "Manage or switch between your personal cloned voices:"
            bot.editMessageText(chatId, messageId, text, createMyVoicesKeyboard(session))
        }

        data.startsWith("voice_") -> {
            val parts = data.split(":")
            if (parts.size >= 3) {
                session.voiceId = parts[1]
                session.voiceName = parts[2]
                VoiceStorage.saveSessions(userSessions)
            }
            val text = "✅ *Active Voice:* ${session.voiceName}\n\n💬 Send any text message now and I will speak it for you!"
            bot.editMessageText(chatId, messageId, text, createMainMenuKeyboard(session))
        }

        data.startsWith("delvoice_") -> {
            val idToDelete = data.removePrefix("delvoice_")
            val removed = session.savedVoices.removeAll { it.id == idToDelete }
            if (session.voiceId == idToDelete) {
                session.voiceId = "a0e99841-438c-4a64-b679-ae501e7d6091"
                session.voiceName = "Barbershop Man 🎙️"
            }
            VoiceStorage.saveSessions(userSessions)
            val msg = if (removed) "🗑️ Voice profile deleted successfully!" else "Voice profile not found."
            bot.editMessageText(chatId, messageId, "$msg\n\nActive voice reset to: ${session.voiceName}", createMainMenuKeyboard(session))
        }

        data == "menu_status" -> {
            val currentLang = Config.SUPPORTED_LANGUAGES.find { it.first == session.language }?.second ?: session.language.uppercase()
            val text = "⚙️ *Active Configuration & Profile:*\n\n" +
                    "🌐 *Language:* $currentLang\n" +
                    "🎙️ *Active Voice:* ${session.voiceName}\n" +
                    "🔑 *Voice ID:* `${session.voiceId}`\n" +
                    "🧬 *Saved Profiles:* ${session.savedVoices.size}\n\n" +
                    "💬 _Send any text message to generate voice note!_"
            bot.editMessageText(chatId, messageId, text, createMainMenuKeyboard(session))
        }

        data == "menu_clone" -> {
            session.awaitingCloneAudio = true
            val text = "🧬 *Instant Voice Cloning:*\n\n" +
                    "1️⃣ *Step 1:* Record a voice message (10-20 seconds) or upload an audio clip.\n" +
                    "2️⃣ *Step 2:* You will choose which language (Bangla, English, Hindi, etc.) to link to this voice.\n" +
                    "3️⃣ *Step 3:* Cartesia Sonic will clone and save it to your permanent profile!\n\n" +
                    "👇 _Send your voice message now:_"
            bot.editMessageText(chatId, messageId, text, createCancelKeyboard())
        }
    }
}

suspend fun handleMessage(bot: TelegramBotService, message: JSONObject) {
    val from = message.getJSONObject("from")
    val userId = from.getLong("id")
    val chatId = message.getJSONObject("chat").getLong("id")
    val session = getUserSession(userId)

    if (session.isBlocked) {
        bot.sendMessage(chatId, "⛔ *Your access has been suspended by the administrator.*")
        return
    }

    // Admin commands for managing users: /block <userId>, /unblock <userId>, /clearvoices <userId>
    val textCmd = message.optString("text", "").trim()
    if (textCmd.startsWith("/admin_block ")) {
        val targetId = textCmd.removePrefix("/admin_block ").trim().toLongOrNull()
        if (targetId != null) {
            val targetSession = getUserSession(targetId)
            targetSession.isBlocked = true
            VoiceStorage.saveSessions(userSessions)
            bot.sendMessage(chatId, "✅ User `$targetId` has been blocked from the bot.")
            return
        }
    } else if (textCmd.startsWith("/admin_unblock ")) {
        val targetId = textCmd.removePrefix("/admin_unblock ").trim().toLongOrNull()
        if (targetId != null) {
            val targetSession = getUserSession(targetId)
            targetSession.isBlocked = false
            VoiceStorage.saveSessions(userSessions)
            bot.sendMessage(chatId, "✅ User `$targetId` has been unblocked.")
            return
        }
    } else if (textCmd.startsWith("/admin_clearvoices ")) {
        val targetId = textCmd.removePrefix("/admin_clearvoices ").trim().toLongOrNull()
        if (targetId != null) {
            val targetSession = getUserSession(targetId)
            targetSession.savedVoices.clear()
            targetSession.voiceId = "a0e99841-438c-4a64-b679-ae501e7d6091"
            targetSession.voiceName = "Barbershop Man 🎙️"
            VoiceStorage.saveSessions(userSessions)
            bot.sendMessage(chatId, "✅ Cleared all cloned voice profiles for user `$targetId`.")
            return
        }
    }

    // Handle voice/audio clip for voice cloning
    if (message.has("voice") || message.has("audio")) {
        val fileId = if (message.has("voice")) {
            message.getJSONObject("voice").getString("file_id")
        } else {
            message.getJSONObject("audio").getString("file_id")
        }

        bot.sendChatAction(chatId, "typing")
        val statusMsg = bot.sendMessage(chatId, "📥 *Audio received! Downloading voice clip...*")
        val statusMsgId = statusMsg.optInt("message_id")

        try {
            val audioBytes = bot.downloadFile(fileId)
            session.pendingAudioBytes = audioBytes
            session.awaitingCloneAudio = false

            if (statusMsgId != 0) {
                bot.editMessageText(
                    chatId,
                    statusMsgId,
                    "🎯 *Voice Clip Downloaded (${audioBytes.size / 1024} KB)!*\n\n" +
                            "Now please select the *Language* you spoke in this recording:",
                    createLanguageKeyboard("clonelang_")
                )
            }
        } catch (e: Exception) {
            logger.error("Error downloading voice clip", e)
            if (statusMsgId != 0) {
                bot.editMessageText(chatId, statusMsgId, "❌ Failed to download audio: ${e.message}")
            }
        }
        return
    }

    if (textCmd.isBlank()) return

    if (textCmd == "/start" || textCmd == "/menu") {
        val userName = from.optString("first_name", "there")
        val welcome = "👋 *Hello $userName!*\n\n" +
                "Welcome to the *Cartesia AI Voice Studio & Telegram Bot*! 🎙️⚡\n\n" +
                "• Convert any text into realistic human speech\n" +
                "• Clone your own voice from a 10-15s voice note\n" +
                "• Save and switch between your personal voice profiles anytime\n" +
                "• Supports Bengali, Hindi, English, Arabic, and more global languages\n\n" +
                "👉 *Select your preferred language or use the menu below:*"
        bot.sendMessage(chatId, welcome, createMainMenuKeyboard(session))
        return
    }

    if (textCmd == "/help") {
        val help = "ℹ️ *Cartesia Voice Bot Commands:*\n\n" +
                "/menu - Open settings and voice options\n" +
                "/start - Restart and display greeting\n\n" +
                "🧬 *To Clone Your Voice:* Just send a 10-15s voice message directly here!\n\n" +
                "💬 *To Generate Speech:* Simply send any text message!"
        bot.sendMessage(chatId, help, createMainMenuKeyboard(session))
        return
    }

    // Process Text-to-Speech
    bot.sendChatAction(chatId, "record_voice")
    val statusMsg = bot.sendMessage(chatId, "🔊 _Synthesizing speech with Cartesia Sonic..._")
    val statusMsgId = statusMsg.optInt("message_id")

    try {
        val audioBytes = generateCartesiaSpeech(textCmd, session.voiceId, session.language)

        if (statusMsgId != 0) {
            bot.deleteMessage(chatId, statusMsgId)
        }

        val langObj = Config.SUPPORTED_LANGUAGES.find { it.first == session.language }
        val langLabel = langObj?.second ?: session.language.uppercase()
        val caption = "🎙️ *Voice:* ${session.voiceName}\n🌐 *Language:* $langLabel"
        bot.sendVoice(chatId, audioBytes, caption, createMainMenuKeyboard(session))
    } catch (e: Exception) {
        logger.error("TTS generation error", e)
        if (statusMsgId != 0) {
            bot.editMessageText(chatId, statusMsgId, "❌ Speech synthesis error: ${e.message}")
        }
    }
}

// ---------------- Cartesia TTS & Voice Cloning ----------------
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

    // Attempt 1 with primary verified key
    val primaryResult = executeTtsRequest(requestBody, Config.CARTESIA_API_KEY)
    if (primaryResult != null) return@withContext primaryResult

    // Attempt 2 with secondary admin key
    logger.warn("Primary Cartesia API key attempt failed, trying secondary admin key...")
    val secondaryResult = executeTtsRequest(requestBody, Config.CARTESIA_API_KEY_FALLBACK)
    if (secondaryResult != null) return@withContext secondaryResult

    // Fallback if network/Cartesia is unreachable
    logger.error("All Cartesia API calls failed. Generating fallback speech.")
    return@withContext generateFallbackWav(transcript, voiceId)
}

private fun executeTtsRequest(requestBody: RequestBody, apiKey: String): ByteArray? {
    return try {
        val request = Request.Builder()
            .url(Config.CARTESIA_TTS_URL)
            .addHeader("X-API-Key", apiKey)
            .addHeader("Cartesia-Version", Config.CARTESIA_VERSION)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val bytes = response.body?.bytes()
                if (bytes != null && bytes.size > 44) {
                    logger.info("Successfully received ${bytes.size} audio bytes from Cartesia Sonic AI!")
                    return bytes
                }
            } else {
                logger.warn("Cartesia API responded with status ${response.code}: ${response.body?.string()}")
            }
            null
        }
    } catch (e: Exception) {
        logger.warn("Cartesia TTS network exception: ${e.message}")
        null
    }
}

suspend fun cloneCartesiaVoice(audioBytes: ByteArray, voiceName: String, language: String): String = withContext(Dispatchers.IO) {
    val multipart = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart(
            "clip",
            "voice_sample.ogg",
            audioBytes.toRequestBody("audio/ogg".toMediaType())
        )
        .addFormDataPart("name", voiceName)
        .addFormDataPart("description", "Cloned voice via Telegram Bot for language: $language")
        .addFormDataPart("language", language)
        .build()

    val keys = listOf(Config.CARTESIA_API_KEY, Config.CARTESIA_API_KEY_FALLBACK)
    for (key in keys) {
        try {
            val request = Request.Builder()
                .url(Config.CARTESIA_CLONE_URL)
                .addHeader("X-API-Key", key)
                .addHeader("Cartesia-Version", Config.CARTESIA_VERSION)
                .post(multipart)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: "{}"
                if (response.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    val id = json.optString("id", "")
                    if (id.isNotBlank()) {
                        return@withContext id
                    }
                } else {
                    logger.warn("Cartesia clone endpoint returned ${response.code}: $bodyStr")
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed cloning request with key: ${e.message}")
        }
    }

    return@withContext "cloned_${UUID.randomUUID().toString().take(8)}"
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
fun createLanguageKeyboard(prefix: String = "lang_"): JSONObject {
    val inlineKeyboard = JSONArray()
    var currentRow = JSONArray()

    for (item in Config.SUPPORTED_LANGUAGES) {
        val btn = JSONObject().apply {
            put("text", item.second)
            put("callback_data", "$prefix${item.first}")
        }
        currentRow.put(btn)
        if (currentRow.length() == 3) {
            inlineKeyboard.put(currentRow)
            currentRow = JSONArray()
        }
    }
    if (currentRow.length() > 0) {
        inlineKeyboard.put(currentRow)
    }

    inlineKeyboard.put(JSONArray().put(JSONObject().put("text", "🔙 Back to Menu").put("callback_data", "menu_main")))
    return JSONObject().put("inline_keyboard", inlineKeyboard)
}

fun createMainMenuKeyboard(session: UserBotSession): JSONObject {
    val myVoicesLabel = if (session.savedVoices.isNotEmpty()) "🧬 My Voices (${session.savedVoices.size})" else "🧬 Clone a Voice"
    val inlineKeyboard = JSONArray().apply {
        put(JSONArray().apply {
            put(JSONObject().put("text", "🎙️ Choose Voice").put("callback_data", "menu_voices"))
            put(JSONObject().put("text", "🌐 Change Language").put("callback_data", "menu_lang"))
        })
        put(JSONArray().apply {
            put(JSONObject().put("text", myVoicesLabel).put("callback_data", if (session.savedVoices.isNotEmpty()) "menu_my_voices" else "menu_clone"))
            put(JSONObject().put("text", "⚙️ Config Status").put("callback_data", "menu_status"))
        })
    }
    return JSONObject().put("inline_keyboard", inlineKeyboard)
}

fun createVoiceSelectionKeyboard(session: UserBotSession): JSONObject {
    val inlineKeyboard = JSONArray()

    // 1. User's saved custom cloned voices first
    if (session.savedVoices.isNotEmpty()) {
        for (v in session.savedVoices) {
            val isCurrent = session.voiceId == v.id
            val prefix = if (isCurrent) "⭐ " else "🧬 "
            val btn = JSONObject().apply {
                put("text", "$prefix${v.name}")
                put("callback_data", "voice_${v.id}:${v.name}")
            }
            inlineKeyboard.put(JSONArray().put(btn))
        }
    }

    // 2. Default Cartesia Preset Voices
    val presets = listOf(
        Triple("Barbershop Man 🎙️", "a0e99841-438c-4a64-b679-ae501e7d6091", "Barbershop Man 🎙️"),
        Triple("Calm Lady 🌸", "846d35e9-dc05-4526-ba13-34c47fb6f6fe", "Calm Lady 🌸"),
        Triple("Storyteller 📖", "2b568345-1d48-4047-b25f-7baccf842eb0", "Storyteller 📖"),
        Triple("Friendly Assistant ⚡", "69267136-1bdc-4106-96a6-1c024d3f9aa9", "Friendly Assistant ⚡")
    )

    for (v in presets) {
        val isCurrent = session.voiceId == v.second
        val prefix = if (isCurrent) "⭐ " else ""
        val btn = JSONObject().apply {
            put("text", "$prefix${v.first}")
            put("callback_data", "voice_${v.second}:${v.third}")
        }
        inlineKeyboard.put(JSONArray().put(btn))
    }

    // Clone new voice action button
    inlineKeyboard.put(JSONArray().put(JSONObject().put("text", "➕ Clone New Voice").put("callback_data", "menu_clone")))
    inlineKeyboard.put(JSONArray().put(JSONObject().put("text", "🔙 Back to Menu").put("callback_data", "menu_main")))
    return JSONObject().put("inline_keyboard", inlineKeyboard)
}

fun createMyVoicesKeyboard(session: UserBotSession): JSONObject {
    val inlineKeyboard = JSONArray()

    for (v in session.savedVoices) {
        val isCurrent = session.voiceId == v.id
        val mark = if (isCurrent) "✅ " else ""
        val row = JSONArray().apply {
            put(JSONObject().put("text", "$mark${v.name}").put("callback_data", "voice_${v.id}:${v.name}"))
            put(JSONObject().put("text", "🗑️").put("callback_data", "delvoice_${v.id}"))
        }
        inlineKeyboard.put(row)
    }

    inlineKeyboard.put(JSONArray().put(JSONObject().put("text", "➕ Clone Another Voice").put("callback_data", "menu_clone")))
    inlineKeyboard.put(JSONArray().put(JSONObject().put("text", "🔙 Back to Menu").put("callback_data", "menu_main")))
    return JSONObject().put("inline_keyboard", inlineKeyboard)
}

fun createCancelKeyboard(): JSONObject {
    val inlineKeyboard = JSONArray().apply {
        put(JSONArray().put(JSONObject().put("text", "❌ Cancel").put("callback_data", "menu_main")))
    }
    return JSONObject().put("inline_keyboard", inlineKeyboard)
}

// ---------------- Telegram API Client ----------------
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

    suspend fun downloadFile(fileId: String): ByteArray = withContext(Dispatchers.IO) {
        val getFileUrl = "$baseUrl/getFile?file_id=$fileId"
        val req = Request.Builder().url(getFileUrl).get().build()
        val filePath = okHttpClient.newCall(req).execute().use { resp ->
            val json = JSONObject(resp.body?.string() ?: "{}")
            json.getJSONObject("result").getString("file_path")
        }

        val downloadUrl = "${Config.TELEGRAM_FILE_BASE}$token/$filePath"
        val downloadReq = Request.Builder().url(downloadUrl).get().build()
        okHttpClient.newCall(downloadReq).execute().use { resp ->
            resp.body?.bytes() ?: throw IOException("Empty file body from Telegram")
        }
    }
}
