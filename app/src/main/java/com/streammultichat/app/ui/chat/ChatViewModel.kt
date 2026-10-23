package com.streammultichat.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streammultichat.app.data.ChatSessionManager
import com.streammultichat.app.data.preferences.TtsMode
import com.streammultichat.app.data.preferences.UserPreferences
import com.streammultichat.app.data.preferences.UserPreferencesRepository
import com.streammultichat.app.tts.TtsManager
import com.streammultichat.core.model.ChatMessage
import com.streammultichat.core.model.ConnectionStatus
import com.streammultichat.core.model.PlatformType
import com.streammultichat.core.model.StreamEvent
import com.streammultichat.core.model.StreamHealth
import com.streammultichat.core.model.StreamNotification
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatStats(
    val totalReceived: Int = 0,
    val bufferSize: Int = 0,
    val connectedPlatformsCount: Int = 0
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val userPreferencesRepo = UserPreferencesRepository(application.applicationContext)
    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepo.userPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    // Native Text-To-Speech Manager delegated to singleton ChatSessionManager
    val isTtsSpeaking: StateFlow<Boolean> = ChatSessionManager.isTtsSpeaking
    val isTtsInitialized: StateFlow<Boolean> = ChatSessionManager.isTtsInitialized
    val ttsEngineName: StateFlow<String?> = ChatSessionManager.ttsEngineName
    val ttsErrorMessage: StateFlow<String?> = ChatSessionManager.ttsErrorMessage

    fun openTtsSettings() {
        ChatSessionManager.openTtsSettings(getApplication())
    }

    // Delegated to ChatSessionManager for persistent multi-window / overlay continuity
    val messages: StateFlow<List<ChatMessage>> = ChatSessionManager.messages
    val notifications: StateFlow<List<StreamNotification>> = ChatSessionManager.notifications
    val connectionStatuses: StateFlow<Map<PlatformType, ConnectionStatus>> = ChatSessionManager.connectionStatuses
    val activeFilters: StateFlow<Set<PlatformType>> = ChatSessionManager.activeFilters
    val isSimulationActive: StateFlow<Boolean> = ChatSessionManager.isSimulationActive

    val twitchChannel: StateFlow<String> = ChatSessionManager.twitchChannel
    val kickChannel: StateFlow<String> = ChatSessionManager.kickChannel
    val youtubeChannel: StateFlow<String> = ChatSessionManager.youtubeChannel
    val starviosChannel: StateFlow<String> = ChatSessionManager.starviosChannel

    val streamHealth: StateFlow<Map<PlatformType, StreamHealth>> = ChatSessionManager.streamHealth

    fun getPreviewUrl(platform: PlatformType): String = ChatSessionManager.getPreviewUrl(platform)

    val stats: StateFlow<ChatStats> = combine(
        ChatSessionManager.chatProcessor.rawMessages,
        connectionStatuses
    ) { allMessages, statuses ->
        ChatStats(
            totalReceived = allMessages.size,
            bufferSize = allMessages.size,
            connectedPlatformsCount = statuses.values.count { it == ConnectionStatus.CONNECTED }
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, ChatStats())

    init {
        ChatSessionManager.init(application.applicationContext)
    }

    fun connectAll() {
        ChatSessionManager.connectAll()
    }

    fun connectTwitch(channel: String, token: String? = null, save: Boolean = true) {
        ChatSessionManager.connectTwitch(channel, token, save)
    }

    fun disconnectTwitch() {
        ChatSessionManager.disconnectTwitch()
    }

    fun connectKick(channel: String, save: Boolean = true) {
        ChatSessionManager.connectKick(channel, save)
    }

    fun disconnectKick() {
        ChatSessionManager.disconnectKick()
    }

    fun connectYouTube(channelOrVideo: String, save: Boolean = true) {
        ChatSessionManager.connectYouTube(channelOrVideo, save)
    }

    fun disconnectYouTube() {
        ChatSessionManager.disconnectYouTube()
    }

    fun connectStarvios(channel: String, save: Boolean = true) {
        ChatSessionManager.connectStarvios(channel, save)
    }

    fun disconnectStarvios() {
        ChatSessionManager.disconnectStarvios()
    }

    fun updateAutoConnect(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepo.updateAutoConnect(enabled)
        }
    }

    fun updateFontSize(fontSizeSp: Int) {
        viewModelScope.launch {
            userPreferencesRepo.updateFontSize(fontSizeSp)
        }
    }

    fun updateShowTimestamps(show: Boolean) {
        viewModelScope.launch {
            userPreferencesRepo.updateShowTimestamps(show)
        }
    }

    fun updateShowPlatformBadges(show: Boolean) {
        viewModelScope.launch {
            userPreferencesRepo.updateShowPlatformBadges(show)
        }
    }

    fun updateBackgroundOpacity(opacity: Float) {
        viewModelScope.launch {
            userPreferencesRepo.updateBackgroundOpacity(opacity)
        }
    }

    fun updateTtsMode(mode: TtsMode) {
        viewModelScope.launch {
            userPreferencesRepo.updateTtsMode(mode)
        }
    }

    fun updateTtsSpeechRate(rate: Float) {
        viewModelScope.launch {
            userPreferencesRepo.updateTtsSpeechRate(rate)
        }
    }

    fun updateTtsPitch(pitch: Float) {
        viewModelScope.launch {
            userPreferencesRepo.updateTtsPitch(pitch)
        }
    }

    fun updateTtsMaxChars(maxChars: Int) {
        viewModelScope.launch {
            userPreferencesRepo.updateTtsMaxChars(maxChars)
        }
    }

    fun updateTtsReadAuthorName(read: Boolean) {
        viewModelScope.launch {
            userPreferencesRepo.updateTtsReadAuthorName(read)
        }
    }

    fun testTtsVoice() {
        ChatSessionManager.testTtsVoice()
    }

    fun testTtsAlert() {
        ChatSessionManager.testTtsAlert()
    }

    fun simulateTestAlert() {
        ChatSessionManager.simulateTestAlert()
    }

    fun stopTts() {
        ChatSessionManager.stopTts()
    }

    fun toggleSimulation(enable: Boolean) {
        ChatSessionManager.toggleSimulation(enable)
    }

    fun triggerBurstSpike() {
        ChatSessionManager.triggerBurstSpike()
    }

    fun toggleFilter(platform: PlatformType) {
        ChatSessionManager.toggleFilter(platform)
    }

    fun clearFilters() {
        ChatSessionManager.clearFilters()
    }

    fun clearChat() {
        ChatSessionManager.clearChat()
    }
}
