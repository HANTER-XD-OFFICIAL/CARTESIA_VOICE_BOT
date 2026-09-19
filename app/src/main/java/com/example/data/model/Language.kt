package com.example.data.model

data class Language(
    val code: String,
    val name: String,
    val nativeName: String,
    val flagEmoji: String
) {
    companion object {
        val ALL: List<Language> = listOf(
            Language("en", "English", "English", "🇬🇧"),
            Language("es", "Spanish", "Español", "🇪🇸"),
            Language("fr", "French", "Français", "🇫🇷"),
            Language("de", "German", "Deutsch", "🇩🇪"),
            Language("ja", "Japanese", "日本語", "🇯🇵"),
            Language("zh", "Chinese", "中文", "🇨🇳"),
            Language("pt", "Portuguese", "Português", "🇵🇹"),
            Language("it", "Italian", "Italiano", "🇮🇹")
        )

        val DEFAULT = ALL[0] // English
    }
}
