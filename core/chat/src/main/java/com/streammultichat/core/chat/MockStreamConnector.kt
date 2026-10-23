package com.streammultichat.core.chat

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
import java.util.UUID
import kotlin.random.Random

class MockStreamConnector(
    override val platform: PlatformType,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : StreamConnector {

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _events = MutableSharedFlow<StreamEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<StreamEvent> = _events.asSharedFlow()

    private val _health = MutableStateFlow<StreamHealth?>(null)
    override val health: StateFlow<StreamHealth?> = _health.asStateFlow()

    private var simulationJob: Job? = null

    private val mockUsers = listOf(
        "AlexGamer", "LunaStream", "PixelKnight", "CyberValkyrie",
        "RetroCoder", "ShadowNinja", "VortexPlay", "CosmicFox",
        "StreamQueen", "TurboSpeed", "NeonSamurai", "EchoWave"
    )

    private val mockEmotesPool = listOf(
        Emote("25", "Kappa", "https://static-cdn.jtvnw.net/emoticons/v2/25/default/dark/2.0"),
        Emote("1902", "Keepo", "https://static-cdn.jtvnw.net/emoticons/v2/1902/default/dark/2.0"),
        Emote("52885", "pepega", "https://files.kick.com/emotes/52885/fullsize"),
        Emote("55338", "KappaPride", "https://static-cdn.jtvnw.net/emoticons/v2/55338/default/dark/2.0")
    )

    private val mockMessagesWithEmotes = listOf(
        "¡Hola a todos en el stream! Kappa",
        "Buena jugada esa última partida Keepo 🔥",
        "GG WP pepega increíble!",
        "Doble emote Kappa Keepo para la victoria!",
        "Llegó el raid KappaPride a celebrar!",
        "Jajajaja no me lo creo 😂 pepega",
        "¡Apoyen con sus likes y follows gente! Kappa",
        "Ese emote está increíble! Keepo"
    )

    private val mockMessages = listOf(
        "¡Hola a todos en el stream!",
        "Buena jugada esa última partida 🔥",
        "¿A qué hora termina el stream hoy?",
        "Jajajaja no me lo creo 😂",
        "GG WP!",
        "Saludos desde Argentina / México!",
        "¿Qué componentes tiene tu PC?",
        "Ese emote está increíble!",
        "Tremendo clip sacaron ahí",
        "¡Apoyen con sus likes y follows gente!"
    )

    private val platformBadges: List<UserBadge> by lazy {
        when (platform) {
            PlatformType.TWITCH -> listOf(
                UserBadge("broadcaster", "Broadcaster", "https://static-cdn.jtvnw.net/badges/v1/5527c58c-fb7d-422d-b71b-f5d3d3d6dd07/2"),
                UserBadge("moderator", "Moderator", "https://static-cdn.jtvnw.net/badges/v1/3267646d-33f0-4b17-b3df-f923a41db1d0/2"),
                UserBadge("vip", "VIP", "https://static-cdn.jtvnw.net/badges/v1/b817aba4-fad8-49e2-ab54-412b8630ad61/2"),
                UserBadge("subscriber", "Subscriber", "https://static-cdn.jtvnw.net/badges/v1/5d9f2208-5dd8-449e-82cc-49d950d3a2ed/2")
            )
            PlatformType.KICK -> listOf(
                UserBadge("host", "Host", "https://files.kick.com/badges/broadcaster.png"),
                UserBadge("moderator", "Mod", "https://files.kick.com/badges/moderator.png"),
                UserBadge("vip", "VIP", "https://files.kick.com/badges/vip.png"),
                UserBadge("subscriber", "Sub", "https://files.kick.com/badges/subscriber.png")
            )
            PlatformType.YOUTUBE -> listOf(
                UserBadge("owner", "Owner", "https://www.gstatic.com/youtube/img/creator/verified_badge.png"),
                UserBadge("mod", "Mod", "https://www.gstatic.com/youtube/img/creator/mod_badge.png")
            )
            PlatformType.STARVIOS -> listOf(
                UserBadge("streamer", "Streamer"),
                UserBadge("vip", "VIP")
            )
            PlatformType.TIKTOK -> emptyList()
        }
    }

    override suspend fun connect(config: StreamChannelConfig) {
        _connectionStatus.value = ConnectionStatus.CONNECTING
        delay(600) // Simulate network handshake
        _connectionStatus.value = ConnectionStatus.CONNECTED

        val baseViewers = when (platform) {
            PlatformType.TWITCH -> 1840L
            PlatformType.KICK -> 620L
            PlatformType.YOUTUBE -> 3250L
            PlatformType.STARVIOS -> 180L
            PlatformType.TIKTOK -> 0L
        }
        val startTime = System.currentTimeMillis() - when (platform) {
            PlatformType.TWITCH -> 45 * 60 * 1000L
            PlatformType.KICK -> 75 * 60 * 1000L
            PlatformType.YOUTUBE -> 130 * 60 * 1000L
            PlatformType.STARVIOS -> 25 * 60 * 1000L
            PlatformType.TIKTOK -> 0L
        }
        val mockTitle = when (platform) {
            PlatformType.TWITCH -> "Torneo Apex Legends | Directo Chill con la Comunidad"
            PlatformType.KICK -> "Just Chatting & Games | Subathon Día 3"
            PlatformType.YOUTUBE -> "Simulación Multi-Chat en Vivo | 1080p 60fps"
            PlatformType.STARVIOS -> "Starvios Community Night | Gameplay & Sorteos"
            PlatformType.TIKTOK -> ""
        }
        val initialHealth = StreamHealth(
            platform = platform,
            isLive = true,
            viewerCount = baseViewers,
            startedAt = startTime,
            title = mockTitle,
            channelName = config.channelName,
            targetResolution = "144p/360p"
        )
        _health.value = initialHealth
        _events.emit(StreamEvent.HealthUpdate(initialHealth))

        simulationJob?.cancel()
        simulationJob = scope.launch {
            var counter = 0
            while (isActive) {
                counter++
                val author = mockUsers.random()
                val hasBadge = Random.nextFloat() < 0.75f
                val badges = if (hasBadge && platformBadges.isNotEmpty()) {
                    listOf(platformBadges.random())
                } else {
                    emptyList()
                }

                val (text, emotes) = if (Random.nextBoolean()) {
                    val t = mockMessagesWithEmotes.random()
                    val matched = mockEmotesPool.filter { t.contains(it.code) }
                    t to matched
                } else {
                    mockMessages.random() to emptyList()
                }

                val chatMessage = ChatMessage(
                    id = "${platform.identifier}-${UUID.randomUUID().toString().take(8)}",
                    platform = platform,
                    channelName = config.channelName,
                    authorName = author,
                    authorColorHex = platform.brandColorHex,
                    messageText = text,
                    badges = badges,
                    emotes = emotes,
                    timestamp = System.currentTimeMillis(),
                    isHighlight = Random.nextInt(10) == 0
                )
                _events.emit(StreamEvent.Message(chatMessage))

                // Occasionally generate a stream event/notification (sub, donation, raid, follow)
                if (counter % 8 == 0) {
                    val notifType = NotificationType.entries.toTypedArray().random()
                    val notifId = "notif-${UUID.randomUUID().toString().take(8)}"
                    val amount = if (notifType == NotificationType.DONATION) 5.0 else null
                    val currency = if (notifType == NotificationType.DONATION) "USD" else null
                    val description = when (notifType) {
                        NotificationType.FOLLOW -> "$author comenzó a seguirte"
                        NotificationType.SUBSCRIPTION -> "$author se ha suscrito por 3 meses"
                        NotificationType.DONATION -> "¡Donó $5.00 USD! Excelente directo crack."
                        NotificationType.RAID -> "$author inició una raid con 45 espectadores"
                        NotificationType.CHANNEL_POINTS -> "$author canjeó 'Destacar mensaje'"
                    }
                    val notif = StreamNotification(
                        id = notifId,
                        platform = platform,
                        type = notifType,
                        user = author,
                        title = "${platform.displayName} Evento",
                        description = description,
                        amount = amount,
                        currency = currency,
                        timestamp = System.currentTimeMillis()
                    )
                    _events.emit(StreamEvent.Notification(notif))

                    // Also mirror as a highlighted chat message so it displays in the live chat feed
                    val chatHighlight = ChatMessage(
                        id = "chat-$notifId",
                        platform = platform,
                        channelName = config.channelName,
                        authorName = author,
                        authorColorHex = platform.brandColorHex,
                        messageText = "⭐ $description",
                        isHighlight = true,
                        timestamp = System.currentTimeMillis()
                    )
                    _events.emit(StreamEvent.Message(chatHighlight))
                }

                // Periodically update viewer metrics slightly to simulate real stream fluctuations
                if (counter % 5 == 0) {
                    val current = _health.value
                    if (current != null) {
                        val updatedCount = (current.viewerCount + Random.nextLong(-3, 4)).coerceAtLeast(1)
                        val updated = current.copy(viewerCount = updatedCount)
                        _health.value = updated
                        _events.emit(StreamEvent.HealthUpdate(updated))
                    }
                }

                // Random delay between 400ms and 1500ms per connector
                delay(Random.nextLong(400, 1500))
            }
        }
    }

    override suspend fun disconnect() {
        simulationJob?.cancel()
        simulationJob = null
        _health.value = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    /**
     * Triggers an intentional spike of messages (e.g. 50 messages)
     * to test UI framerate and bounded buffer stability under stress.
     */
    fun triggerSpike(count: Int = 50) {
        scope.launch {
            repeat(count) { i ->
                val author = mockUsers.random()
                val text = "RÁFAGA #$i: ${mockMessagesWithEmotes.random()}"
                val matched = mockEmotesPool.filter { text.contains(it.code) }
                val badges = if (platformBadges.isNotEmpty()) listOf(platformBadges.random()) else emptyList()
                _events.emit(
                    StreamEvent.Message(
                        ChatMessage(
                            id = "spike-${UUID.randomUUID().toString().take(8)}",
                            platform = platform,
                            channelName = "test-channel",
                            authorName = author,
                            authorColorHex = platform.brandColorHex,
                            messageText = text,
                            badges = badges,
                            emotes = matched,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                )
            }
        }
    }
}
