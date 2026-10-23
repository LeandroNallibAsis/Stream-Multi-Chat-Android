package com.streammultichat.platform.twitch

import com.streammultichat.core.chat.StreamConnector
import com.streammultichat.core.model.ChatMessage
import com.streammultichat.core.model.ConnectionStatus
import com.streammultichat.core.model.NotificationType
import com.streammultichat.core.model.PlatformType
import com.streammultichat.core.model.StreamChannelConfig
import com.streammultichat.core.model.StreamEvent
import com.streammultichat.core.model.StreamHealth
import com.streammultichat.core.model.StreamNotification
import com.streammultichat.core.network.BackoffPolicy
import com.streammultichat.core.network.NetworkClientProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class TwitchConnector(
    private val client: OkHttpClient = NetworkClientProvider.defaultClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : StreamConnector {

    override val platform: PlatformType = PlatformType.TWITCH

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _events = MutableSharedFlow<StreamEvent>(extraBufferCapacity = 128)
    override val events: SharedFlow<StreamEvent> = _events.asSharedFlow()

    private val _health = MutableStateFlow<StreamHealth?>(null)
    override val health: StateFlow<StreamHealth?> = _health.asStateFlow()

    private var activeWebSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var currentConfig: StreamChannelConfig? = null
    private var isUserDisconnect = false
    private val backoffPolicy = BackoffPolicy()
    private var reconnectAttempt = 0

    companion object {
        private const val TWITCH_IRC_WS_URL = "wss://irc-ws.chat.twitch.tv:443"
    }

    override suspend fun connect(config: StreamChannelConfig) {
        currentConfig = config
        isUserDisconnect = false
        reconnectAttempt = 0
        establishWebSocketConnection(config)
    }

    override suspend fun disconnect() {
        isUserDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        activeWebSocket?.close(1000, "User requested disconnect")
        activeWebSocket = null
        _health.value = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    private fun establishWebSocketConnection(config: StreamChannelConfig) {
        _connectionStatus.value = if (reconnectAttempt > 0) ConnectionStatus.RECONNECTING else ConnectionStatus.CONNECTING

        val request = Request.Builder()
            .url(TWITCH_IRC_WS_URL)
            .build()

        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                _connectionStatus.value = ConnectionStatus.CONNECTED

                val cleanChannel = config.channelName.trim().removePrefix("#").lowercase()
                val health = StreamHealth(
                    platform = PlatformType.TWITCH,
                    isLive = true,
                    channelName = cleanChannel,
                    previewUrl = "https://player.twitch.tv/?channel=$cleanChannel&parent=localhost&muted=false&autoplay=true"
                )
                _health.value = health
                _events.tryEmit(StreamEvent.HealthUpdate(health))

                // 1. Request IRCv3 capabilities for tags and commands
                webSocket.send("CAP REQ :twitch.tv/tags twitch.tv/commands")

                // 2. Authentication (Anonymous read or OAuth token)
                val token = config.authToken?.takeIf { it.isNotBlank() }
                if (token != null) {
                    val formattedToken = if (token.startsWith("oauth:")) token else "oauth:$token"
                    webSocket.send("PASS $formattedToken")
                    webSocket.send("NICK ${config.channelName.lowercase()}")
                } else {
                    // Anonymous readonly mode
                    webSocket.send("PASS SCHMENDRIK")
                    webSocket.send("NICK justinfan${(10000..99999).random()}")
                }

                // 3. Join the target channel
                webSocket.send("JOIN #$cleanChannel")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Lines can be separated by \r\n in IRC
                text.lines().forEach { line ->
                    val cleanLine = line.trim()
                    if (cleanLine.isBlank()) return@forEach

                    // Respond to Twitch PING keep-alives immediately
                    if (cleanLine.startsWith("PING")) {
                        val pingPayload = cleanLine.substringAfter("PING", "").trim()
                        webSocket.send("PONG $pingPayload")
                        return@forEach
                    }

                    // Parse incoming chat messages
                    val chatMessage = TwitchIrcParser.parseMessage(cleanLine, config.channelName)
                    if (chatMessage != null) {
                        _events.tryEmit(StreamEvent.Message(chatMessage))

                        // If message contains bits (Twitch Cheers donation)
                        val raw = chatMessage.rawPayload ?: ""
                        if (raw.contains("bits=")) {
                            val bits = Regex("bits=(\\d+)").find(raw)?.groupValues?.get(1)?.toDoubleOrNull()
                            if (bits != null && bits > 0) {
                                _events.tryEmit(
                                    StreamEvent.Notification(
                                        StreamNotification(
                                            id = "bits-${chatMessage.id}",
                                            platform = PlatformType.TWITCH,
                                            type = NotificationType.DONATION,
                                            user = chatMessage.authorName,
                                            title = "Cheer de Bits",
                                            description = "¡Donó ${bits.toLong()} bits! ${chatMessage.messageText}",
                                            amount = bits,
                                            currency = "Bits",
                                            timestamp = chatMessage.timestamp
                                        )
                                    )
                                )
                            }
                        }
                    } else {
                        // Parse incoming Twitch USERNOTICE (Subscriptions, Resubs, Raids)
                        val notification = TwitchIrcParser.parseUserNotice(cleanLine, config.channelName)
                        if (notification != null) {
                            _events.tryEmit(StreamEvent.Notification(notification))
                            // Also mirror as a highlighted chat message so it displays in chat
                            _events.tryEmit(
                                StreamEvent.Message(
                                    ChatMessage(
                                        id = "chat-${notification.id}",
                                        platform = PlatformType.TWITCH,
                                        channelName = config.channelName,
                                        authorName = notification.user,
                                        authorColorHex = PlatformType.TWITCH.brandColorHex,
                                        messageText = "⭐ ${notification.description}",
                                        isHighlight = true,
                                        timestamp = notification.timestamp,
                                        rawPayload = cleanLine
                                    )
                                )
                            )
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleDisconnection("WebSocket Failure: ${t.localizedMessage}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isUserDisconnect) {
                    handleDisconnection("Socket Closed: $reason ($code)")
                }
            }
        })
    }

    private fun handleDisconnection(reason: String) {
        if (isUserDisconnect) return
        _connectionStatus.value = ConnectionStatus.ERROR
        _events.tryEmit(StreamEvent.StatusChanged(PlatformType.TWITCH, ConnectionStatus.ERROR, reason))

        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        val config = currentConfig ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            reconnectAttempt++
            val delayMs = backoffPolicy.calculateDelay(reconnectAttempt)
            _connectionStatus.value = ConnectionStatus.RECONNECTING
            delay(delayMs)
            if (isActive && !isUserDisconnect) {
                establishWebSocketConnection(config)
            }
        }
    }
}
