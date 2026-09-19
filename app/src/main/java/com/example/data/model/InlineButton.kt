package com.example.data.model

enum class ButtonAction {
    SELECT_LANGUAGE,
    FEATURE_VOICE_CLONING,
    FEATURE_GENERATE_SPEECH,
    FEATURE_SELECT_VOICE,
    FEATURE_SETTINGS,
    FEATURE_HELP,
    ACTION_START_RECORDING,
    ACTION_DEMO_CLONE,
    ACTION_TRY_SAMPLE_TEXT,
    ACTION_SWITCH_LANGUAGE,
    SELECT_SPECIFIC_VOICE
}

data class InlineButton(
    val id: String,
    val text: String,
    val action: ButtonAction,
    val payload: String? = null
)
