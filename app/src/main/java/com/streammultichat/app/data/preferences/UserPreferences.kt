package com.streammultichat.app.data.preferences

enum class TtsMode(val displayName: String) {
    OFF("Desactivado"),
    DONATIONS_ONLY("Solo Alertas / Donaciones"),
    ALL_MESSAGES("Todos los mensajes")
}

data class UserPreferences(
    // Canales
    val twitchChannel: String = "ibai",
    val kickChannel: String = "iceposeidon",
    val youtubeChannel: String = "0sfDth4lMLc",
    val starviosChannel: String = "costelinhatv",
    val autoConnectOnLaunch: Boolean = true,

    // Apariencia visual del chat
    val fontSizeSp: Int = 14,
    val showTimestamps: Boolean = true,
    val showPlatformBadges: Boolean = true,
    val backgroundOpacity: Float = 0.35f,

    // Lector de Voz (TTS)
    val ttsMode: TtsMode = TtsMode.OFF,
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsMaxChars: Int = 120,
    val ttsReadAuthorName: Boolean = true
)
