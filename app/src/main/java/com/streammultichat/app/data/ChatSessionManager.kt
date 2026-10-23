package com.streammultichat.app.data

import android.content.Context
import com.streammultichat.app.data.preferences.UserPreferences
import com.streammultichat.app.data.preferences.UserPreferencesRepository
import com.streammultichat.app.service.StreamForegroundService
import com.streammultichat.app.tts.TtsManager
import com.streammultichat.core.chat.UnifiedChatProcessor
import com.streammultichat.core.chat.MockStreamConnector
import com.streammultichat.core.model.ChatMessage
import com.streammultichat.core.model.ConnectionStatus
import com.streammultichat.core.model.NotificationType
import com.streammultichat.core.model.PlatformType
import com.streammultichat.core.model.StreamChannelConfig
import com.streammultichat.core.model.StreamEvent
import com.streammultichat.core.model.StreamHealth
import com.streammultichat.core.model.StreamNotification
import com.streammultichat.platform.kick.KickConnector
import com.streammultichat.platform.starvios.StarviosConnector
import com.streammultichat.platform.twitch.TwitchConnector
import com.streammultichat.platform.youtube.YouTubeConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object ChatSessionManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val chatProcessor = UnifiedChatProcessor(scope, maxBufferCapacity = 250)

    // Real platform connectors
    private val twitchConnector = TwitchConnector(scope = scope)
    private val kickConnector = KickConnector(scope = scope)
    private val youtubeConnector = YouTubeConnector(scope = scope)
    private val starviosConnector = StarviosConnector(scope = scope)

    // Simulation connectors
    private val mockTwitchConnector = MockStreamConnector(PlatformType.TWITCH, scope)
    private val mockKickConnector = MockStreamConnector(PlatformType.KICK, scope)
    private val mockYoutubeConnector = MockStreamConnector(PlatformType.YOUTUBE, scope)
    private val mockStarviosConnector = MockStreamConnector(PlatformType.STARVIOS, scope)

    val messages: StateFlow<List<ChatMessage>> = chatProcessor.filteredMessages
    val notifications: StateFlow<List<StreamNotification>> = chatProcessor.notifications
    val connectionStatuses: StateFlow<Map<PlatformType, ConnectionStatus>> = chatProcessor.connectionStatuses
    val streamHealth: StateFlow<Map<PlatformType, StreamHealth>> = chatProcessor.streamHealth
    val activeFilters: StateFlow<Set<PlatformType>> = chatProcessor.activeFilters.asStateFlow()
    val incomingEvents: SharedFlow<StreamEvent> = chatProcessor.incomingEvents

    // Unread counter for the floating overlay bubble
    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _isSimulationActive = MutableStateFlow(false)
    val isSimulationActive: StateFlow<Boolean> = _isSimulationActive.asStateFlow()

    private val _twitchChannel = MutableStateFlow("ibai")
    val twitchChannel: StateFlow<String> = _twitchChannel.asStateFlow()

    private val _kickChannel = MutableStateFlow("iceposeidon")
    val kickChannel: StateFlow<String> = _kickChannel.asStateFlow()

    private val _youtubeChannel = MutableStateFlow("0sfDth4lMLc")
    val youtubeChannel: StateFlow<String> = _youtubeChannel.asStateFlow()

    private val _starviosChannel = MutableStateFlow("costelinhatv")
    val starviosChannel: StateFlow<String> = _starviosChannel.asStateFlow()

    // Native Text-To-Speech Manager (Single central instance for entire app)
    private var ttsManager: TtsManager? = null

    private val _isTtsSpeaking = MutableStateFlow(false)
    val isTtsSpeaking: StateFlow<Boolean> = _isTtsSpeaking.asStateFlow()

    private val _isTtsInitialized = MutableStateFlow(false)
    val isTtsInitialized: StateFlow<Boolean> = _isTtsInitialized.asStateFlow()

    private val _ttsEngineName = MutableStateFlow<String?>(null)
    val ttsEngineName: StateFlow<String?> = _ttsEngineName.asStateFlow()

    private val _ttsErrorMessage = MutableStateFlow<String?>(null)
    val ttsErrorMessage: StateFlow<String?> = _ttsErrorMessage.asStateFlow()

    private val _userPreferences = MutableStateFlow(UserPreferences())
    val userPreferences: StateFlow<UserPreferences> = _userPreferences.asStateFlow()

    private var userPreferencesRepo: UserPreferencesRepository? = null
    private var appContext: Context? = null
    private var isInitialized = false

    init {
        // Register default connectors
        chatProcessor.registerConnector(twitchConnector)
        chatProcessor.registerConnector(kickConnector)
        chatProcessor.registerConnector(youtubeConnector)
        chatProcessor.registerConnector(starviosConnector)

        // Increment unread count when new messages arrive
        scope.launch {
            incomingEvents.collect { event ->
                if (event is StreamEvent.Message) {
                    _unreadCount.value = (_unreadCount.value + 1).coerceAtMost(99)
                }
            }
        }
    }

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        appContext = context.applicationContext

        val repo = UserPreferencesRepository(context.applicationContext)
        userPreferencesRepo = repo

        // Initialize Native TTS exactly once
        val tts = TtsManager(context.applicationContext)
        ttsManager = tts

        scope.launch {
            tts.isSpeaking.collect { _isTtsSpeaking.value = it }
        }
        scope.launch {
            tts.isInitialized.collect { _isTtsInitialized.value = it }
        }
        scope.launch {
            tts.engineName.collect { _ttsEngineName.value = it }
        }
        scope.launch {
            tts.errorMessage.collect { _ttsErrorMessage.value = it }
        }

        scope.launch {
            repo.userPreferencesFlow.collect { prefs ->
                _userPreferences.value = prefs
            }
        }

        // Single centralized TTS speech listener (strictly one instance in entire app lifecycle)
        scope.launch {
            incomingEvents.collect { event ->
                val prefs = _userPreferences.value
                when (event) {
                    is StreamEvent.Message -> tts.speakMessage(event.message, prefs)
                    is StreamEvent.Notification -> tts.speakNotification(event.notification, prefs)
                    else -> {}
                }
            }
        }

        // Reactive Foreground Service Manager:
        // Automatically keeps Android process alive when streams or simulation are active
        scope.launch {
            combine(connectionStatuses, isSimulationActive) { statuses, isSim ->
                val hasActive = statuses.values.any { it == ConnectionStatus.CONNECTED || it == ConnectionStatus.CONNECTING } || isSim
                hasActive
            }.distinctUntilChanged().collect { hasActive ->
                appContext?.let { ctx ->
                    if (hasActive) {
                        StreamForegroundService.start(ctx)
                    } else {
                        StreamForegroundService.stop(ctx)
                    }
                }
            }
        }

        scope.launch {
            val prefs = repo.userPreferencesFlow.first()
            _twitchChannel.value = prefs.twitchChannel
            _kickChannel.value = prefs.kickChannel
            _youtubeChannel.value = prefs.youtubeChannel
            _starviosChannel.value = prefs.starviosChannel

            if (prefs.autoConnectOnLaunch) {
                if (prefs.twitchChannel.isNotBlank()) connectTwitch(prefs.twitchChannel, save = false)
                if (prefs.kickChannel.isNotBlank()) connectKick(prefs.kickChannel, save = false)
                if (prefs.youtubeChannel.isNotBlank()) connectYouTube(prefs.youtubeChannel, save = false)
                if (prefs.starviosChannel.isNotBlank()) connectStarvios(prefs.starviosChannel, save = false)
            }
        }
    }

    fun openTtsSettings(ctx: Context) {
        ttsManager?.openTtsSettings(ctx)
    }

    fun testTtsVoice() {
        val prefs = _userPreferences.value
        ttsManager?.speakTest(prefs.ttsSpeechRate, prefs.ttsPitch)
    }

    fun testTtsAlert() {
        val prefs = _userPreferences.value
        ttsManager?.speakAlertTest(prefs.ttsSpeechRate, prefs.ttsPitch)
    }

    fun simulateTestAlert() {
        val platforms = listOf(PlatformType.TWITCH, PlatformType.KICK, PlatformType.YOUTUBE)
        val platform = platforms.random()
        val author = "DonadorGenial"
        val notifId = "test-notif-${System.currentTimeMillis()}"
        val desc = "¡Donó $10.00 USD! Excelente directo crack."
        val notif = StreamNotification(
            id = notifId,
            platform = platform,
            type = NotificationType.DONATION,
            user = author,
            title = "Donación en ${platform.displayName}",
            description = desc,
            amount = 10.0,
            currency = "USD",
            timestamp = System.currentTimeMillis()
        )
        chatProcessor.handleEvent(StreamEvent.Notification(notif))
        chatProcessor.handleEvent(
            StreamEvent.Message(
                ChatMessage(
                    id = "chat-$notifId",
                    platform = platform,
                    channelName = "live",
                    authorName = author,
                    authorColorHex = platform.brandColorHex,
                    messageText = "⭐ $desc",
                    isHighlight = true,
                    timestamp = System.currentTimeMillis()
                )
            )
        )
    }

    fun stopTts() {
        ttsManager?.stop()
    }

    fun disconnectAll() {
        disconnectTwitch()
        disconnectKick()
        disconnectYouTube()
        disconnectStarvios()
        if (_isSimulationActive.value) {
            toggleSimulation(false)
        }
    }

    fun resetUnreadCount() {
        _unreadCount.value = 0
    }

    fun connectAll() {
        connectTwitch(_twitchChannel.value)
        connectKick(_kickChannel.value)
        if (_youtubeChannel.value.isNotBlank()) connectYouTube(_youtubeChannel.value)
        if (_starviosChannel.value.isNotBlank()) connectStarvios(_starviosChannel.value)
    }

    fun connectTwitch(channel: String, token: String? = null, save: Boolean = true) {
        val cleanChannel = channel.trim()
            .removePrefix("https://www.twitch.tv/")
            .removePrefix("https://twitch.tv/")
            .removePrefix("@")
            .removePrefix("#")

        if (cleanChannel.isBlank()) return
        _twitchChannel.value = cleanChannel

        if (save) {
            scope.launch { userPreferencesRepo?.updateTwitchChannel(cleanChannel) }
        }

        if (!_isSimulationActive.value) {
            val config = StreamChannelConfig(
                platform = PlatformType.TWITCH,
                channelName = cleanChannel,
                authToken = token
            )
            chatProcessor.connectPlatform(config)
        }
    }

    fun disconnectTwitch() {
        chatProcessor.disconnectPlatform(PlatformType.TWITCH)
    }

    fun connectKick(channel: String, save: Boolean = true) {
        val cleanChannel = channel.trim()
            .removePrefix("https://kick.com/")
            .removePrefix("https://www.kick.com/")
            .removePrefix("@")

        if (cleanChannel.isBlank()) return
        _kickChannel.value = cleanChannel

        if (save) {
            scope.launch { userPreferencesRepo?.updateKickChannel(cleanChannel) }
        }

        if (!_isSimulationActive.value) {
            val config = StreamChannelConfig(
                platform = PlatformType.KICK,
                channelName = cleanChannel
            )
            chatProcessor.connectPlatform(config)
        }
    }

    fun disconnectKick() {
        chatProcessor.disconnectPlatform(PlatformType.KICK)
    }

    fun connectYouTube(channelOrVideoId: String, save: Boolean = true) {
        val cleanId = channelOrVideoId.trim()
            .removePrefix("https://www.youtube.com/watch?v=")
            .removePrefix("https://youtube.com/watch?v=")
            .removePrefix("https://youtu.be/")
            .removePrefix("https://www.youtube.com/live/")
            .removePrefix("https://youtube.com/live/")
            .substringBefore("&")

        if (cleanId.isBlank()) return
        _youtubeChannel.value = cleanId

        if (save) {
            scope.launch { userPreferencesRepo?.updateYouTubeChannel(cleanId) }
        }

        if (!_isSimulationActive.value) {
            val config = StreamChannelConfig(
                platform = PlatformType.YOUTUBE,
                channelName = cleanId
            )
            chatProcessor.connectPlatform(config)
        }
    }

    fun disconnectYouTube() {
        chatProcessor.disconnectPlatform(PlatformType.YOUTUBE)
    }

    fun connectStarvios(channelOrUser: String, save: Boolean = true) {
        val cleanUser = channelOrUser.trim()
            .removePrefix("https://starvios.com/")
            .removePrefix("https://www.starvios.com/")
            .removePrefix("@")

        if (cleanUser.isBlank()) return
        _starviosChannel.value = cleanUser

        if (save) {
            scope.launch { userPreferencesRepo?.updateStarviosChannel(cleanUser) }
        }

        if (!_isSimulationActive.value) {
            val config = StreamChannelConfig(
                platform = PlatformType.STARVIOS,
                channelName = cleanUser
            )
            chatProcessor.connectPlatform(config)
        }
    }

    fun disconnectStarvios() {
        chatProcessor.disconnectPlatform(PlatformType.STARVIOS)
    }

    fun toggleSimulation(enable: Boolean) {
        _isSimulationActive.value = enable
        if (enable) {
            chatProcessor.disconnectPlatform(PlatformType.TWITCH)
            chatProcessor.disconnectPlatform(PlatformType.KICK)
            chatProcessor.disconnectPlatform(PlatformType.YOUTUBE)
            chatProcessor.disconnectPlatform(PlatformType.STARVIOS)

            chatProcessor.registerConnector(mockTwitchConnector)
            chatProcessor.registerConnector(mockKickConnector)
            chatProcessor.registerConnector(mockYoutubeConnector)
            chatProcessor.registerConnector(mockStarviosConnector)

            chatProcessor.connectPlatform(StreamChannelConfig(PlatformType.TWITCH, "SimulatedTwitch"))
            chatProcessor.connectPlatform(StreamChannelConfig(PlatformType.KICK, "SimulatedKick"))
            chatProcessor.connectPlatform(StreamChannelConfig(PlatformType.YOUTUBE, "SimulatedYouTube"))
            chatProcessor.connectPlatform(StreamChannelConfig(PlatformType.STARVIOS, "SimulatedStarvios"))
        } else {
            chatProcessor.disconnectPlatform(PlatformType.TWITCH)
            chatProcessor.disconnectPlatform(PlatformType.KICK)
            chatProcessor.disconnectPlatform(PlatformType.YOUTUBE)
            chatProcessor.disconnectPlatform(PlatformType.STARVIOS)

            chatProcessor.registerConnector(twitchConnector)
            chatProcessor.registerConnector(kickConnector)
            chatProcessor.registerConnector(youtubeConnector)
            chatProcessor.registerConnector(starviosConnector)
        }
    }

    fun triggerBurstSpike() {
        if (_isSimulationActive.value) {
            mockTwitchConnector.triggerSpike(25)
            mockKickConnector.triggerSpike(25)
            mockYoutubeConnector.triggerSpike(25)
            mockStarviosConnector.triggerSpike(25)
        }
    }

    fun toggleFilter(platform: PlatformType) {
        chatProcessor.toggleFilter(platform)
    }

    fun clearFilters() {
        chatProcessor.activeFilters.value = emptySet()
    }

    fun clearChat() {
        chatProcessor.clearChat()
    }

    fun getPreviewUrl(platform: PlatformType): String {
        return when (platform) {
            PlatformType.TWITCH -> "https://player.twitch.tv/?channel=${twitchChannel.value.trim().removePrefix("#").lowercase()}&parent=localhost&muted=false&autoplay=true"
            PlatformType.KICK -> "https://player.kick.com/${kickChannel.value.trim().lowercase()}?autoplay=true"
            PlatformType.YOUTUBE -> "https://www.youtube.com/embed/${youtubeChannel.value.trim()}?autoplay=1&playsinline=1"
            PlatformType.STARVIOS -> "https://starvios.com/${starviosChannel.value.trim().removePrefix("@")}"
            PlatformType.TIKTOK -> ""
        }
    }
}
