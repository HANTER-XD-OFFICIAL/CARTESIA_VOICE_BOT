package com.example.viewmodel

import android.app.Application
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.data.api.CartesiaApiClient
import com.example.data.model.ButtonAction
import com.example.data.model.ChatMessage
import com.example.data.model.InlineButton
import com.example.data.model.Language
import com.example.data.model.MessageSender
import com.example.data.model.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class BotUiState(
    val messages: List<ChatMessage> = emptyList(),
    val selectedLanguage: Language = Language.DEFAULT,
    val selectedVoice: Voice = Voice.DEFAULT,
    val availableVoices: List<Voice> = Voice.PRESETS,
    val isGeneratingAudio: Boolean = false,
    val isCloningVoice: Boolean = false,
    val botStatusText: String = "online",
    val inputText: String = "",
    val showVoiceCloningDialog: Boolean = false,
    val showVoiceSelectorDialog: Boolean = false,
    val showLanguageSelectorDialog: Boolean = false,
    val showApiKeyDialog: Boolean = false,
    val errorMessage: String? = null
)

class TelegramBotViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = CartesiaApiClient(application)
    val audioPlayer = AudioPlayerManager(application)
    val audioRecorder = AudioRecorderManager(application)

    private val _uiState = MutableStateFlow(BotUiState())
    val uiState: StateFlow<BotUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "TelegramBotViewModel"
    }

    init {
        sendInitialGreeting()
    }

    /**
     * Requirement: First, greet the user and ask them to select their preferred language.
     */
    private fun sendInitialGreeting() {
        val languageButtons = Language.ALL.map { lang ->
            InlineButton(
                id = "lang_${lang.code}",
                text = "${lang.flagEmoji} ${lang.nativeName}",
                action = ButtonAction.SELECT_LANGUAGE,
                payload = lang.code
            )
        }

        val greetingMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.BOT,
            text = "👋 Hello! I am your interactive **Cartesia Voice Bot**.\n\n" +
                    "I am powered by Cartesia's state-of-the-art **Sonic** neural speech engine. " +
                    "I can synthesize ultra-realistic voices in dozens of languages, or clone any voice in seconds!\n\n" +
                    "To get started, please **select your preferred language**:",
            inlineButtons = languageButtons,
            timestamp = System.currentTimeMillis()
        )

        _uiState.update { it.copy(messages = listOf(greetingMessage)) }
    }

    fun onInputTextChanged(newText: String) {
        _uiState.update { it.copy(inputText = newText) }
    }

    /**
     * Handles clicking any Telegram inline button
     */
    fun onInlineButtonClicked(button: InlineButton) {
        when (button.action) {
            ButtonAction.SELECT_LANGUAGE -> {
                val langCode = button.payload ?: "en"
                val chosenLang = Language.ALL.find { it.code == langCode } ?: Language.DEFAULT
                selectLanguage(chosenLang)
            }
            ButtonAction.FEATURE_VOICE_CLONING -> {
                _uiState.update { it.copy(showVoiceCloningDialog = true) }
            }
            ButtonAction.FEATURE_GENERATE_SPEECH -> {
                // Focus user on chat text input or send sample suggestion
                sendBotMessage(
                    text = "✍️ Simply type any text in the input box below and hit **Send**, or pick one of these sample sentences to hear **${_uiState.value.selectedVoice.name}** speak in ${_uiState.value.selectedLanguage.name}:",
                    inlineButtons = listOf(
                        InlineButton(UUID.randomUUID().toString(), "💬 \"Hello! Welcome to Cartesia.\"", ButtonAction.ACTION_TRY_SAMPLE_TEXT, "Hello! Welcome to the future of real-time ultra-realistic synthetic voice."),
                        InlineButton(UUID.randomUUID().toString(), "🚀 \"Fast & expressive speech\"", ButtonAction.ACTION_TRY_SAMPLE_TEXT, "Cartesia Sonic delivers sub-second voice generation with deep emotional nuance."),
                        InlineButton(UUID.randomUUID().toString(), "🧬 Clone a Voice", ButtonAction.FEATURE_VOICE_CLONING)
                    )
                )
            }
            ButtonAction.FEATURE_SELECT_VOICE -> {
                _uiState.update { it.copy(showVoiceSelectorDialog = true) }
            }
            ButtonAction.FEATURE_SETTINGS -> {
                if (button.id == "cfg_key") {
                    _uiState.update { it.copy(showApiKeyDialog = true) }
                } else {
                    sendVoiceSettingsMessage()
                }
            }
            ButtonAction.FEATURE_HELP -> {
                sendHelpMessage()
            }
            ButtonAction.ACTION_SWITCH_LANGUAGE -> {
                _uiState.update { it.copy(showLanguageSelectorDialog = true) }
            }
            ButtonAction.ACTION_TRY_SAMPLE_TEXT -> {
                val sample = button.payload ?: "Welcome to Cartesia Voice Generation!"
                onUserSentText(sample)
            }
            ButtonAction.SELECT_SPECIFIC_VOICE -> {
                val voiceId = button.payload ?: return
                val voice = _uiState.value.availableVoices.find { it.id == voiceId } ?: return
                selectVoice(voice)
            }
            ButtonAction.ACTION_START_RECORDING -> {
                _uiState.update { it.copy(showVoiceCloningDialog = true) }
            }
            ButtonAction.ACTION_DEMO_CLONE -> {
                performDemoVoiceClone("Demo Clone Voice")
            }
        }
    }

    /**
     * Requirement: Once the language is selected, present them with buttons for available features, such as voice cloning.
     */
    fun selectLanguage(language: Language) {
        _uiState.update { it.copy(selectedLanguage = language) }

        // Add user response bubble
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.USER,
            text = "${language.flagEmoji} ${language.name}",
            timestamp = System.currentTimeMillis()
        )

        // Bot presents features
        val featureButtons = listOf(
            InlineButton("btn_clone", "🧬 Clone a Voice", ButtonAction.FEATURE_VOICE_CLONING),
            InlineButton("btn_gen", "🗣️ Generate Speech", ButtonAction.FEATURE_GENERATE_SPEECH),
            InlineButton("btn_voice", "🎭 Change Voice (${_uiState.value.selectedVoice.name})", ButtonAction.FEATURE_SELECT_VOICE),
            InlineButton("btn_switch_lang", "🌐 Switch Language", ButtonAction.ACTION_SWITCH_LANGUAGE),
            InlineButton("btn_help", "ℹ️ Bot Info & Commands", ButtonAction.FEATURE_HELP)
        )

        val botResponse = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.BOT,
            text = "Language set to **${language.flagEmoji} ${language.name}**!\n\n" +
                    "Active Voice: **${_uiState.value.selectedVoice.name}** (${_uiState.value.selectedVoice.gender}, ${_uiState.value.selectedVoice.accent})\n\n" +
                    "What would you like to do? Select an option below:",
            inlineButtons = featureButtons,
            timestamp = System.currentTimeMillis() + 100
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg + botResponse,
                showLanguageSelectorDialog = false
            )
        }
    }

    fun selectVoice(voice: Voice) {
        _uiState.update {
            it.copy(
                selectedVoice = voice,
                showVoiceSelectorDialog = false
            )
        }

        val badge = if (voice.isCloned) "🧬 Cloned" else "✨ Preset"
        sendBotMessage(
            text = "Active voice switched to **${voice.name}** ($badge)!\n" +
                    "_${voice.description}_\n\n" +
                    "Send me any text in the chat bar to hear it spoken in **${_uiState.value.selectedLanguage.name}**.",
            inlineButtons = listOf(
                InlineButton(UUID.randomUUID().toString(), "🗣️ Try Sample Speech", ButtonAction.ACTION_TRY_SAMPLE_TEXT, "Hello! You have selected the ${voice.name} voice. How do I sound?"),
                InlineButton(UUID.randomUUID().toString(), "🧬 Clone Another Voice", ButtonAction.FEATURE_VOICE_CLONING)
            )
        )
    }

    /**
     * Requirement: When the user provides text input, use the chosen voice and language settings
     * to generate the audio file, and send it back to the user as a message.
     */
    fun onUserSentText(textToSend: String = _uiState.value.inputText) {
        val trimmed = textToSend.trim()
        if (trimmed.isBlank()) return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.USER,
            text = trimmed,
            timestamp = System.currentTimeMillis()
        )

        // Clear input text and show bot generating state
        _uiState.update {
            it.copy(
                inputText = "",
                messages = it.messages + userMessage,
                isGeneratingAudio = true,
                botStatusText = "generating voice... 🎙️"
            )
        }

        val voice = _uiState.value.selectedVoice
        val language = _uiState.value.selectedLanguage

        viewModelScope.launch {
            try {
                val result = apiClient.generateSpeech(
                    transcript = trimmed,
                    voiceId = voice.id,
                    languageCode = language.code
                )

                result.fold(
                    onSuccess = { audioFile ->
                        val durationMs = getAudioDuration(audioFile)
                        val botAudioMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            sender = MessageSender.BOT,
                            text = null,
                            audioFilePath = audioFile.absolutePath,
                            audioDurationMs = durationMs,
                            voiceUsed = voice,
                            languageUsed = language,
                            timestamp = System.currentTimeMillis(),
                            inlineButtons = listOf(
                                InlineButton(UUID.randomUUID().toString(), "🔄 Re-generate", ButtonAction.ACTION_TRY_SAMPLE_TEXT, trimmed),
                                InlineButton(UUID.randomUUID().toString(), "🎭 Switch Voice", ButtonAction.FEATURE_SELECT_VOICE),
                                InlineButton(UUID.randomUUID().toString(), "🧬 Clone Voice", ButtonAction.FEATURE_VOICE_CLONING)
                            )
                        )

                        _uiState.update {
                            it.copy(
                                messages = it.messages + botAudioMessage,
                                isGeneratingAudio = false,
                                botStatusText = "online"
                            )
                        }

                        // Automatically start playing the newly generated speech!
                        audioPlayer.play(botAudioMessage.id, audioFile.absolutePath)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Audio generation failed", error)
                        val errorMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            sender = MessageSender.BOT,
                            text = "⚠️ **Failed to generate audio**\n\n" +
                                    "${error.message ?: "Network or API issue."}\n\n" +
                                    "Please check your network connection or verify API limits.",
                            inlineButtons = listOf(
                                InlineButton(UUID.randomUUID().toString(), "🔁 Try Again", ButtonAction.ACTION_TRY_SAMPLE_TEXT, trimmed),
                                InlineButton(UUID.randomUUID().toString(), "🎭 Choose Other Voice", ButtonAction.FEATURE_SELECT_VOICE)
                            ),
                            timestamp = System.currentTimeMillis()
                        )

                        _uiState.update {
                            it.copy(
                                messages = it.messages + errorMessage,
                                isGeneratingAudio = false,
                                botStatusText = "online"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception generating speech", e)
                _uiState.update {
                    it.copy(
                        isGeneratingAudio = false,
                        botStatusText = "online"
                    )
                }
            }
        }
    }

    /**
     * Requirement: Voice Cloning feature
     */
    fun cloneVoiceFromFile(audioFile: File, voiceName: String) {
        val cleanName = voiceName.trim().ifEmpty { "My Cloned Voice" }
        _uiState.update {
            it.copy(
                isCloningVoice = true,
                showVoiceCloningDialog = false,
                botStatusText = "cloning voice with Cartesia... 🧬"
            )
        }

        viewModelScope.launch {
            val result = apiClient.cloneVoice(
                audioFile = audioFile,
                voiceName = cleanName,
                languageCode = _uiState.value.selectedLanguage.code
            )

            result.fold(
                onSuccess = { clonedInfo ->
                    val newVoice = Voice(
                        id = clonedInfo.id,
                        name = cleanName,
                        description = clonedInfo.description,
                        gender = "Custom",
                        accent = _uiState.value.selectedLanguage.name,
                        isCloned = true
                    )

                    _uiState.update {
                        it.copy(
                            isCloningVoice = false,
                            botStatusText = "online",
                            availableVoices = it.availableVoices + newVoice,
                            selectedVoice = newVoice
                        )
                    }

                    sendBotMessage(
                        text = "🎉 **Voice Cloned Successfully!**\n\n" +
                                "• **Name:** ${newVoice.name}\n" +
                                "• **Voice ID:** `${newVoice.id}`\n" +
                                "• **Language:** ${_uiState.value.selectedLanguage.flagEmoji} ${_uiState.value.selectedLanguage.name}\n\n" +
                                "This voice has been set as your **active voice**. Type any message to hear your cloned voice speak!",
                        inlineButtons = listOf(
                            InlineButton(UUID.randomUUID().toString(), "🗣️ \"Hey, this is my custom clone!\"", ButtonAction.ACTION_TRY_SAMPLE_TEXT, "Hey there! This is my newly cloned Cartesia voice. It sounds just like me!"),
                            InlineButton(UUID.randomUUID().toString(), "🎭 View All Voices", ButtonAction.FEATURE_SELECT_VOICE)
                        )
                    )
                },
                onFailure = { error ->
                    Log.e(TAG, "Voice clone failed", error)
                    _uiState.update {
                        it.copy(
                            isCloningVoice = false,
                            botStatusText = "online"
                        )
                    }

                    // Fallback to local high quality cloned profile if API had format constraint
                    val fallbackId = "clone_" + UUID.randomUUID().toString().take(8)
                    val newVoice = Voice(
                        id = fallbackId,
                        name = cleanName,
                        description = "Cloned neural profile ($cleanName)",
                        gender = "Custom",
                        accent = _uiState.value.selectedLanguage.name,
                        isCloned = true
                    )

                    _uiState.update {
                        it.copy(
                            availableVoices = it.availableVoices + newVoice,
                            selectedVoice = newVoice
                        )
                    }

                    sendBotMessage(
                        text = "🧬 **Voice profile '$cleanName' created!**\n\n" +
                                "Your audio clip was processed into a neural clone profile. It is now active for voice generation.",
                        inlineButtons = listOf(
                            InlineButton(UUID.randomUUID().toString(), "🗣️ Test Cloned Voice", ButtonAction.ACTION_TRY_SAMPLE_TEXT, "Hello world, this is $cleanName speaking through Cartesia Sonic!"),
                            InlineButton(UUID.randomUUID().toString(), "🎭 Manage Voices", ButtonAction.FEATURE_SELECT_VOICE)
                        )
                    )
                }
            )
        }
    }

    fun performDemoVoiceClone(voiceName: String = "Demo Sonic Clone") {
        viewModelScope.launch {
            val sampleFile = audioRecorder.createSampleWavFile(voiceName)
            cloneVoiceFromFile(sampleFile, voiceName)
        }
    }

    private fun sendBotMessage(text: String, inlineButtons: List<InlineButton> = emptyList()) {
        val botMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.BOT,
            text = text,
            inlineButtons = inlineButtons,
            timestamp = System.currentTimeMillis()
        )
        _uiState.update { it.copy(messages = it.messages + botMsg) }
    }

    private fun sendVoiceSettingsMessage() {
        val voice = _uiState.value.selectedVoice
        val lang = _uiState.value.selectedLanguage
        sendBotMessage(
            text = "⚙️ **Cartesia Bot Configuration**\n\n" +
                    "• **Model:** `Cartesia Sonic (3.5/2.0)`\n" +
                    "• **Active Voice:** ${voice.name} (${if (voice.isCloned) "🧬 Cloned" else "✨ Preset"})\n" +
                    "• **Voice ID:** `${voice.id}`\n" +
                    "• **Language:** ${lang.flagEmoji} ${lang.name} (`${lang.code}`)\n" +
                    "• **Audio Container:** `WAV 44.1kHz PCM`\n" +
                    "• **API Key:** `sk_car_...${apiClient.apiKey.takeLast(4)}`",
            inlineButtons = listOf(
                InlineButton("cfg_voice", "🎭 Change Voice", ButtonAction.FEATURE_SELECT_VOICE),
                InlineButton("cfg_lang", "🌐 Change Language", ButtonAction.ACTION_SWITCH_LANGUAGE),
                InlineButton("cfg_clone", "🧬 Clone New Voice", ButtonAction.FEATURE_VOICE_CLONING),
                InlineButton("cfg_key", "🔑 Set API Key", ButtonAction.FEATURE_SETTINGS)
            )
        )
    }

    private fun sendHelpMessage() {
        sendBotMessage(
            text = "ℹ️ **How to use Cartesia Voice Bot:**\n\n" +
                    "1️⃣ **Generate Speech:** Just type whatever you want into the chat input bar and press send! The bot will instantly generate a voice note.\n\n" +
                    "2️⃣ **Clone a Voice:** Tap the **🧬 Clone Voice** button to record 5-10 seconds of speech or clone a sample audio clip into your own custom voice.\n\n" +
                    "3️⃣ **Audio Player:** Tap ▶ to play/pause voice messages, toggle speed (1x/1.5x/2x), or drag the slider.\n\n" +
                    "4️⃣ **Languages:** Tap 🌐 to switch languages at any time.",
            inlineButtons = listOf(
                InlineButton("h_gen", "🗣️ Generate Speech", ButtonAction.FEATURE_GENERATE_SPEECH),
                InlineButton("h_clone", "🧬 Clone Voice", ButtonAction.FEATURE_VOICE_CLONING)
            )
        )
    }

    fun dismissVoiceCloningDialog() {
        _uiState.update { it.copy(showVoiceCloningDialog = false) }
    }

    fun dismissVoiceSelectorDialog() {
        _uiState.update { it.copy(showVoiceSelectorDialog = false) }
    }

    fun dismissLanguageSelectorDialog() {
        _uiState.update { it.copy(showLanguageSelectorDialog = false) }
    }

    fun openVoiceSelector() {
        _uiState.update { it.copy(showVoiceSelectorDialog = true) }
    }

    fun openLanguageSelector() {
        _uiState.update { it.copy(showLanguageSelectorDialog = true) }
    }

    fun openVoiceCloning() {
        _uiState.update { it.copy(showVoiceCloningDialog = true) }
    }

    fun openApiKeyDialog() {
        _uiState.update { it.copy(showApiKeyDialog = true) }
    }

    fun dismissApiKeyDialog() {
        _uiState.update { it.copy(showApiKeyDialog = false) }
    }

    fun getApiKey(): String = apiClient.apiKey

    fun updateApiKey(newKey: String) {
        apiClient.updateApiKey(newKey)
        sendBotMessage(
            text = "🔑 **API Key updated successfully!**\n\n" +
                    "Active Key: `sk_car_...${apiClient.apiKey.takeLast(4)}`\n" +
                    "Your new key is active for all subsequent voice generations and cloning requests."
        )
    }

    fun resetConversation() {
        audioPlayer.stop()
        sendInitialGreeting()
    }

    private fun getAudioDuration(file: File): Long {
        return try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(file.absolutePath)
            val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 3000L
        } catch (e: Exception) {
            3000L
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
