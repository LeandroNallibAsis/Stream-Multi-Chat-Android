package com.streammultichat.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val TWITCH_CHANNEL = stringPreferencesKey("twitch_channel")
        val KICK_CHANNEL = stringPreferencesKey("kick_channel")
        val YOUTUBE_CHANNEL = stringPreferencesKey("youtube_channel")
        val STARVIOS_CHANNEL = stringPreferencesKey("starvios_channel")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect_on_launch")

        val FONT_SIZE_SP = intPreferencesKey("chat_font_size_sp")
        val SHOW_TIMESTAMPS = booleanPreferencesKey("chat_show_timestamps")
        val SHOW_PLATFORM_BADGES = booleanPreferencesKey("chat_show_platform_badges")
        val BACKGROUND_OPACITY = floatPreferencesKey("chat_background_opacity")

        // TTS Keys
        val TTS_MODE = stringPreferencesKey("tts_mode")
        val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val TTS_MAX_CHARS = intPreferencesKey("tts_max_chars")
        val TTS_READ_AUTHOR_NAME = booleanPreferencesKey("tts_read_author_name")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val ttsModeStr = preferences[PreferencesKeys.TTS_MODE] ?: TtsMode.OFF.name
            val ttsMode = try {
                TtsMode.valueOf(ttsModeStr)
            } catch (e: Exception) {
                TtsMode.OFF
            }

            UserPreferences(
                twitchChannel = preferences[PreferencesKeys.TWITCH_CHANNEL] ?: "ibai",
                kickChannel = preferences[PreferencesKeys.KICK_CHANNEL] ?: "iceposeidon",
                youtubeChannel = preferences[PreferencesKeys.YOUTUBE_CHANNEL] ?: "0sfDth4lMLc",
                starviosChannel = preferences[PreferencesKeys.STARVIOS_CHANNEL] ?: "costelinhatv",
                autoConnectOnLaunch = preferences[PreferencesKeys.AUTO_CONNECT] ?: true,
                fontSizeSp = preferences[PreferencesKeys.FONT_SIZE_SP] ?: 14,
                showTimestamps = preferences[PreferencesKeys.SHOW_TIMESTAMPS] ?: true,
                showPlatformBadges = preferences[PreferencesKeys.SHOW_PLATFORM_BADGES] ?: true,
                backgroundOpacity = preferences[PreferencesKeys.BACKGROUND_OPACITY] ?: 0.35f,
                ttsMode = ttsMode,
                ttsSpeechRate = preferences[PreferencesKeys.TTS_SPEECH_RATE] ?: 1.0f,
                ttsPitch = preferences[PreferencesKeys.TTS_PITCH] ?: 1.0f,
                ttsMaxChars = preferences[PreferencesKeys.TTS_MAX_CHARS] ?: 120,
                ttsReadAuthorName = preferences[PreferencesKeys.TTS_READ_AUTHOR_NAME] ?: true
            )
        }

    suspend fun updateTwitchChannel(channel: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TWITCH_CHANNEL] = channel
        }
    }

    suspend fun updateKickChannel(channel: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KICK_CHANNEL] = channel
        }
    }

    suspend fun updateYouTubeChannel(channel: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.YOUTUBE_CHANNEL] = channel
        }
    }

    suspend fun updateStarviosChannel(channel: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.STARVIOS_CHANNEL] = channel
        }
    }

    suspend fun updateChannels(twitch: String, kick: String, youtube: String, starvios: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TWITCH_CHANNEL] = twitch
            preferences[PreferencesKeys.KICK_CHANNEL] = kick
            preferences[PreferencesKeys.YOUTUBE_CHANNEL] = youtube
            preferences[PreferencesKeys.STARVIOS_CHANNEL] = starvios
        }
    }

    suspend fun updateAutoConnect(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_CONNECT] = enabled
        }
    }

    suspend fun updateFontSize(fontSizeSp: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FONT_SIZE_SP] = fontSizeSp
        }
    }

    suspend fun updateShowTimestamps(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_TIMESTAMPS] = show
        }
    }

    suspend fun updateShowPlatformBadges(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_PLATFORM_BADGES] = show
        }
    }

    suspend fun updateBackgroundOpacity(opacity: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BACKGROUND_OPACITY] = opacity
        }
    }

    suspend fun updateTtsMode(mode: TtsMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TTS_MODE] = mode.name
        }
    }

    suspend fun updateTtsSpeechRate(rate: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TTS_SPEECH_RATE] = rate
        }
    }

    suspend fun updateTtsPitch(pitch: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TTS_PITCH] = pitch
        }
    }

    suspend fun updateTtsMaxChars(maxChars: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TTS_MAX_CHARS] = maxChars
        }
    }

    suspend fun updateTtsReadAuthorName(read: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TTS_READ_AUTHOR_NAME] = read
        }
    }
}
