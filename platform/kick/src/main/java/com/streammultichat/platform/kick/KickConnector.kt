package com.streammultichat.platform.kick

import android.util.Log
import com.streammultichat.core.chat.StreamConnector
import com.streammultichat.core.model.ChatMessage
import com.streammultichat.core.model.ConnectionStatus
import com.streammultichat.core.model.Emote
import com.streammultichat.core.model.NotificationType
import com.streammultichat.core.model.PlatformType
import com.streammultichat.core.model.StreamChannelConfig
import com.streammultichat.core.model.StreamEvent
import com.streammultichat.core.model.StreamHealth
import com.streammultichat.core.model.StreamNotification
import com.streammultichat.core.model.UserBadge
import com.streammultichat.core.network.BackoffPolicy
import com.streammultichat.core.network.NetworkClientProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID

class KickConnector(
    private val client: OkHttpClient = NetworkClientProvider.defaultClient,
    private val httpClient: OkHttpClient = NetworkClientProvider.httpClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : StreamConnector {

    private val wsClient: OkHttpClient = client.newBuilder()
        .pingInterval(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()

    override val platform: PlatformType = PlatformType.KICK

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _events = MutableSharedFlow<StreamEvent>(
        replay = 0,
        extraBufferCapacity = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val events: SharedFlow<StreamEvent> = _events.asSharedFlow()

    private val _health = MutableStateFlow<StreamHealth?>(null)
    override val health: StateFlow<StreamHealth?> = _health.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private var activeWebSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var currentConfig: StreamChannelConfig? = null
    private var isUserDisconnect = false
    private val backoffPolicy = BackoffPolicy()
    private var reconnectAttempt = 0

    private var resolvedChannelId: Long? = null
    private var resolvedChatroomId: Long? = null

    companion object {
        private const val TAG = "KickConnector"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val FALLBACK_WS_URL =
            "wss://realtime.us-east-1.platform.kick.com/connection/websocket"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    override suspend fun connect(config: StreamChannelConfig) {
        currentConfig = config
        isUserDisconnect = false
        reconnectAttempt = 0

        _connectionStatus.value = ConnectionStatus.CONNECTING

        scope.launch {
            initiateConnection(config)
        }
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

    private suspend fun initiateConnection(config: StreamChannelConfig) {
        val channelSlug = config.channelName.trim().lowercase()
        Log.d(TAG, "Initiating Kick connection for slug: '$channelSlug'")

        // 1. Resolve channel ID and chatroom ID
        val (channelId, chatroomId) = resolveChannelIds(channelSlug)
        if (channelId == null || chatroomId == null) {
            Log.e(TAG, "Failed to resolve channel IDs for slug: '$channelSlug'")
            _connectionStatus.value = ConnectionStatus.ERROR
            _events.tryEmit(
                StreamEvent.StatusChanged(
                    PlatformType.KICK,
                    ConnectionStatus.ERROR,
                    "No se pudo resolver el canal '$channelSlug' en Kick"
                )
            )
            return
        }

        resolvedChannelId = channelId
        resolvedChatroomId = chatroomId
        Log.d(TAG, "Resolved channelId: $channelId, chatroomId: $chatroomId")

        val clientId = UUID.randomUUID().toString()

        // 2. Obtain realtime connection descriptor (Centrifugo WS endpoint)
        val wsUrl = fetchRealtimeConnectionUrl(channelId, channelSlug, clientId) ?: FALLBACK_WS_URL
        Log.d(TAG, "Using Centrifugo WS URL: $wsUrl")

        // 3. Obtain guest JWT authentication token
        val authToken = fetchAuthToken(channelSlug, clientId)
        if (authToken == null) {
            Log.e(TAG, "Failed to obtain guest auth token")
            _connectionStatus.value = ConnectionStatus.ERROR
            _events.tryEmit(
                StreamEvent.StatusChanged(
                    PlatformType.KICK,
                    ConnectionStatus.ERROR,
                    "Error de autenticación guest con Kick Realtime"
                )
            )
            scheduleReconnect()
            return
        }
        Log.d(TAG, "Successfully obtained guest auth token")

        // 4. Connect WebSocket
        establishWebSocketConnection(config, wsUrl, authToken, chatroomId)
    }

    private suspend fun resolveChannelIds(channelSlug: String): Pair<Long?, Long?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://kick.com/api/v2/channels/$channelSlug")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext Pair(null, null)
                    val channelInfo = json.decodeFromString<KickChannelInfo>(body)
                    channelInfo.livestream?.let { ls ->
                        val health = StreamHealth(
                            platform = PlatformType.KICK,
                            isLive = ls.isLive,
                            viewerCount = ls.viewerCount,
                            startedAt = System.currentTimeMillis() - 3600_000L,
                            title = ls.sessionTitle,
                            channelName = channelSlug,
                            previewUrl = "https://player.kick.com/$channelSlug"
                        )
                        _health.value = health
                        _events.tryEmit(StreamEvent.HealthUpdate(health))
                    }
                    return@withContext Pair(channelInfo.id, channelInfo.chatroom?.id)
                }
            }
        } catch (_: Exception) {
            // Ignore & fallback
        }
        Pair(null, null)
    }

    private suspend fun fetchRealtimeConnectionUrl(channelId: Long, channelSlug: String, clientId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val reqBody = KickRealtimeConnectionRequest(
                    client = KickClientDescriptor(id = clientId, type = "web"),
                    capabilities = KickCapabilities(
                        acceptedProviders = listOf(
                            KickProviderItem("pusher"),
                            KickProviderItem("centrifugo")
                        )
                    )
                )
                val jsonPayload = json.encodeToString(reqBody)

                val request = Request.Builder()
                    .url("https://web.kick.com/api/v1/realtime/channels/$channelId/chat/connection")
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("x-app-platform", "web")
                    .header("Referer", "https://kick.com/$channelSlug")
                    .header("User-Agent", USER_AGENT)
                    .post(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@withContext null
                        val connResponse = json.decodeFromString<KickRealtimeConnectionResponse>(body)
                        val centrifugoItem = connResponse.data?.connections?.find { it.provider == "centrifugo" }
                        return@withContext centrifugoItem?.credentials?.url
                    }
                }
            } catch (_: Exception) {
                // Return null to use fallback
            }
            null
        }

    private suspend fun fetchAuthToken(channelSlug: String, clientId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val reqBody = KickAuthConnectionRequest(clientId = clientId)
                val jsonPayload = json.encodeToString(reqBody)

                val request = Request.Builder()
                    .url("https://web.kick.com/api/v1/realtime/auth/connection")
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("x-app-platform", "web")
                    .header("Referer", "https://kick.com/$channelSlug")
                    .header("User-Agent", USER_AGENT)
                    .post(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@withContext null
                        val authResponse = json.decodeFromString<KickAuthConnectionResponse>(body)
                        return@withContext authResponse.data?.token
                    }
                }
            } catch (_: Exception) {
                // Return null on failure
            }
            null
        }

    private fun establishWebSocketConnection(
        config: StreamChannelConfig,
        wsUrl: String,
        authToken: String,
        chatroomId: Long
    ) {
        _connectionStatus.value = if (reconnectAttempt > 0) ConnectionStatus.RECONNECTING else ConnectionStatus.CONNECTING

        val request = Request.Builder()
            .url(wsUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        activeWebSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected, sending Centrifugo connect command")
                // Send Centrifugo connect command with guest JWT token
                val connectCmd = CentrifugoConnectCommand(
                    id = 1,
                    connect = CentrifugoConnectData(token = authToken)
                )
                webSocket.send(json.encodeToString(connectCmd))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val lines = text.trim().split("\n")
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue

                    // Centrifugo server ping
                    if (trimmed == "{}") {
                        webSocket.send("{}")
                        continue
                    }

                    try {
                        val frame = json.decodeFromString<CentrifugoFrame>(trimmed)

                        // 1. Connect response
                        if (frame.id == 1 && frame.connect != null) {
                            Log.d(TAG, "Centrifugo connect handshake success, subscribing to chatrooms.$chatroomId.v2")
                            val subscribeCmd = CentrifugoSubscribeCommand(
                                id = 2,
                                subscribe = CentrifugoSubscribeData(channel = "chatrooms.$chatroomId.v2")
                            )
                            webSocket.send(json.encodeToString(subscribeCmd))
                            continue
                        }

                        // 2. Subscribe response
                        if (frame.id == 2 && frame.subscribe != null) {
                            Log.d(TAG, "Centrifugo subscribed successfully to chatrooms.$chatroomId.v2")
                            reconnectAttempt = 0
                            _connectionStatus.value = ConnectionStatus.CONNECTED
                            _events.tryEmit(StreamEvent.StatusChanged(PlatformType.KICK, ConnectionStatus.CONNECTED))
                            continue
                        }

                        // 3. Incoming publication message or event push
                        val pubDataElement = frame.push?.pub?.data
                        if (pubDataElement != null) {
                            val notif = extractNotification(pubDataElement)
                            if (notif != null) {
                                Log.i(TAG, "Incoming Kick notification: ${notif.title} - ${notif.description}")
                                _events.tryEmit(StreamEvent.Notification(notif))
                                _events.tryEmit(
                                    StreamEvent.Message(
                                        ChatMessage(
                                            id = "chat-${notif.id}",
                                            platform = PlatformType.KICK,
                                            channelName = config.channelName,
                                            authorName = notif.user,
                                            authorColorHex = PlatformType.KICK.brandColorHex,
                                            messageText = "⭐ ${notif.description}",
                                            isHighlight = true,
                                            timestamp = notif.timestamp
                                        )
                                    )
                                )
                                continue
                            }

                            val chatMessagePayload = extractChatMessagePayload(pubDataElement)
                            if (chatMessagePayload != null) {
                                val authorName = chatMessagePayload.sender?.username ?: chatMessagePayload.sender?.slug ?: "KickUser"
                                val textContent = chatMessagePayload.content ?: chatMessagePayload.message ?: ""
                                val msgId = chatMessagePayload.id.ifBlank { UUID.randomUUID().toString() }
                                Log.d(TAG, "Incoming Kick message from $authorName: $textContent")

                                val colorHex = chatMessagePayload.sender?.identity?.color?.let { hex ->
                                    try {
                                        java.lang.Long.parseLong(hex.removePrefix("#"), 16) or 0xFF000000
                                    } catch (_: Exception) { null }
                                } ?: PlatformType.KICK.brandColorHex

                                val badges = mutableListOf<UserBadge>()
                                chatMessagePayload.sender?.identity?.badgesV2?.forEach { b2 ->
                                    val name = b2.name ?: b2.badgeType
                                    if (!name.isNullOrBlank() && badges.none { it.id == name }) {
                                        val imgUrl = b2.imageUrl?.takeIf { it.isNotBlank() } ?: getKickBadgeUrl(name)
                                        badges.add(UserBadge(id = name, name = name.replaceFirstChar { it.uppercase() }, imageUrl = imgUrl))
                                    }
                                }
                                chatMessagePayload.sender?.identity?.badges?.forEach { b ->
                                    val name = b.type ?: b.text
                                    if (!name.isNullOrBlank() && badges.none { it.id == name }) {
                                        badges.add(UserBadge(id = name, name = name.replaceFirstChar { it.uppercase() }, imageUrl = getKickBadgeUrl(name)))
                                    }
                                }

                                // Parse Kick emotes in text e.g. [emote:52885:pepega]
                                val emoteRegex = Regex("""\[emote:(\d+):([^\]]+)\]""")
                                val emotes = mutableListOf<Emote>()
                                var parsedMessageText = textContent

                                emoteRegex.findAll(textContent).forEach { match ->
                                    val emoteId = match.groupValues[1]
                                    val emoteCode = match.groupValues[2]
                                    if (emotes.none { it.id == emoteId }) {
                                        emotes.add(
                                            Emote(
                                                id = emoteId,
                                                code = emoteCode,
                                                imageUrl = "https://files.kick.com/emotes/$emoteId/fullsize"
                                            )
                                        )
                                    }
                                }
                                // Normalize [emote:id:code] to code in messageText for clean UI and TTS
                                if (emotes.isNotEmpty()) {
                                    parsedMessageText = emoteRegex.replace(textContent) { m -> m.groupValues[2] }
                                }

                                val chatMessage = ChatMessage(
                                    id = msgId,
                                    platform = PlatformType.KICK,
                                    channelName = config.channelName,
                                    authorName = authorName,
                                    authorColorHex = colorHex,
                                    messageText = parsedMessageText,
                                    badges = badges,
                                    emotes = emotes,
                                    timestamp = System.currentTimeMillis(),
                                    rawPayload = trimmed
                                )

                                _events.tryEmit(StreamEvent.Message(chatMessage))
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Skip frame due to error: ${e.message} in line: $trimmed")
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.localizedMessage}", t)
                handleDisconnection("WebSocket Failure: ${t.localizedMessage}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closed: $reason ($code)")
                if (!isUserDisconnect) {
                    handleDisconnection("Socket Closed: $reason ($code)")
                }
            }
        })
    }

    private fun extractChatMessagePayload(element: JsonElement): KickChatMessagePayload? {
        return try {
            val actualElement = if (element is JsonPrimitive) {
                try {
                    json.parseToJsonElement(element.content)
                } catch (_: Exception) {
                    return null
                }
            } else {
                element
            }

            val obj = actualElement as? JsonObject ?: return null

            val eventElem = obj["event"]
            val targetDataElem: JsonElement = if (eventElem != null) {
                val eventName = (eventElem as? JsonPrimitive)?.content
                if (eventName != null && !eventName.contains("ChatMessage", ignoreCase = true) && !eventName.contains("Message", ignoreCase = true)) {
                    return null
                }
                obj["data"] ?: return null
            } else {
                obj
            }

            val parsedPayload = when (targetDataElem) {
                is JsonPrimitive -> {
                    json.decodeFromString<KickChatMessagePayload>(targetDataElem.content)
                }
                is JsonObject -> {
                    json.decodeFromJsonElement<KickChatMessagePayload>(targetDataElem)
                }
                else -> null
            }

            // Only return if it has either content/message or a sender
            if (parsedPayload != null && (parsedPayload.content != null || parsedPayload.message != null || parsedPayload.sender != null)) {
                parsedPayload
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract Kick chat message: ${e.message}")
            null
        }
    }

    private fun extractNotification(element: JsonElement): StreamNotification? {
        return try {
            val actualElement = if (element is JsonPrimitive) {
                try { json.parseToJsonElement(element.content) } catch (_: Exception) { return null }
            } else {
                element
            }
            val obj = actualElement as? JsonObject ?: return null
            val eventName = (obj["event"] as? JsonPrimitive)?.content ?: return null
            val dataElem = obj["data"] ?: return null
            val dataObj = (if (dataElem is JsonPrimitive) {
                try { json.parseToJsonElement(dataElem.content) } catch (_: Exception) { null }
            } else dataElem) as? JsonObject ?: return null

            when {
                eventName.contains("SubscriptionEvent", ignoreCase = true) -> {
                    val username = (dataObj["username"] as? JsonPrimitive)?.content ?: "Usuario"
                    val months = (dataObj["months"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1
                    StreamNotification(
                        id = "kick-sub-${UUID.randomUUID()}",
                        platform = PlatformType.KICK,
                        type = NotificationType.SUBSCRIPTION,
                        user = username,
                        title = "Suscripción en Kick",
                        description = "$username se ha suscrito por $months mes(es)",
                        amount = months.toDouble(),
                        currency = "meses",
                        timestamp = System.currentTimeMillis()
                    )
                }
                eventName.contains("GiftedSubscriptionsEvent", ignoreCase = true) -> {
                    val gifter = (dataObj["gifter_username"] as? JsonPrimitive)?.content ?: "Usuario"
                    StreamNotification(
                        id = "kick-giftsub-${UUID.randomUUID()}",
                        platform = PlatformType.KICK,
                        type = NotificationType.SUBSCRIPTION,
                        user = gifter,
                        title = "Sub Regalada en Kick",
                        description = "$gifter regaló suscripciones en el canal",
                        timestamp = System.currentTimeMillis()
                    )
                }
                eventName.contains("StreamHostEvent", ignoreCase = true) -> {
                    val host = (dataObj["host_username"] as? JsonPrimitive)?.content ?: "Canal"
                    val viewers = (dataObj["number_viewers"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
                    StreamNotification(
                        id = "kick-host-${UUID.randomUUID()}",
                        platform = PlatformType.KICK,
                        type = NotificationType.RAID,
                        user = host,
                        title = "Host / Raid en Kick",
                        description = "$host inició un host con $viewers espectadores",
                        amount = viewers.toDouble(),
                        currency = "espectadores",
                        timestamp = System.currentTimeMillis()
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractNotification exception", e)
            null
        }
    }

    private fun handleDisconnection(reason: String) {
        if (isUserDisconnect) return
        _connectionStatus.value = ConnectionStatus.ERROR
        _events.tryEmit(StreamEvent.StatusChanged(PlatformType.KICK, ConnectionStatus.ERROR, reason))
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
                initiateConnection(config)
            }
        }
    }

    private fun getKickBadgeUrl(name: String): String? {
        return when (name.lowercase()) {
            "broadcaster", "host" -> "https://files.kick.com/badges/broadcaster.png"
            "moderator", "mod" -> "https://files.kick.com/badges/moderator.png"
            "vip" -> "https://files.kick.com/badges/vip.png"
            "subscriber", "sub" -> "https://files.kick.com/badges/subscriber.png"
            "og" -> "https://files.kick.com/badges/og.png"
            "verified" -> "https://files.kick.com/badges/verified.png"
            else -> null
        }
    }
}
