package com.streammultichat.platform.youtube

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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class YouTubeConnector(
    private val httpClient: OkHttpClient = NetworkClientProvider.httpClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : StreamConnector {

    private val ytHttpClient: OkHttpClient = httpClient.newBuilder()
        .cookieJar(object : okhttp3.CookieJar {
            private val store = mutableListOf<okhttp3.Cookie>()
            override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
                synchronized(store) {
                    store.removeAll { c -> cookies.any { it.name == c.name } }
                    store.addAll(cookies)
                }
            }
            override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
                return synchronized(store) { store.toList() }
            }
        })
        .build()

    override val platform: PlatformType = PlatformType.YOUTUBE

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

    private var pollJob: Job? = null
    private var currentConfig: StreamChannelConfig? = null
    private var isUserDisconnect = false
    private val backoffPolicy = BackoffPolicy()
    private var reconnectAttempt = 0
    private var lastResolvedTitle: String? = null

    private val seenMessageIds = Collections.synchronizedSet(LinkedHashSet<String>())

    companion object {
        private const val TAG = "YouTubeConnector"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")
    }

    override suspend fun connect(config: StreamChannelConfig) {
        currentConfig = config
        isUserDisconnect = false
        reconnectAttempt = 0
        seenMessageIds.clear()

        _connectionStatus.value = ConnectionStatus.CONNECTING

        pollJob?.cancel()
        pollJob = scope.launch {
            startPollingLoop(config)
        }
    }

    override suspend fun disconnect() {
        isUserDisconnect = true
        pollJob?.cancel()
        pollJob = null
        seenMessageIds.clear()
        _health.value = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    private data class YouTubeLiveMetadata(
        val videoId: String,
        val title: String?,
        val viewerCount: Long,
        val startedAt: Long?,
        val isLive: Boolean
    )

    private suspend fun CoroutineScope.startPollingLoop(config: StreamChannelConfig) {
        while (isActive && !isUserDisconnect) {
            val rawInput = config.channelName.trim()
            if (rawInput.isBlank()) {
                _connectionStatus.value = ConnectionStatus.ERROR
                _events.tryEmit(
                    StreamEvent.StatusChanged(PlatformType.YOUTUBE, ConnectionStatus.ERROR, "ID o canal de YouTube vacío")
                )
                return
            }

            Log.d(TAG, "Resolving YouTube metadata for: '$rawInput'")
            val targetUrl = resolveTargetUrl(rawInput)
            val metadata = fetchLiveMetadata(targetUrl)

            if (metadata == null || !metadata.isLive || metadata.videoId.isBlank()) {
                Log.d(TAG, "Channel '$rawInput' has no active live stream right now. In waiting/offline mode.")
                val offlineHealth = StreamHealth(
                    platform = PlatformType.YOUTUBE,
                    isLive = false,
                    viewerCount = 0L,
                    title = "Canal @${rawInput.removePrefix("@")} (En espera de directo)",
                    channelName = rawInput,
                    previewUrl = null
                )
                _health.value = offlineHealth
                _events.tryEmit(StreamEvent.HealthUpdate(offlineHealth))
                _connectionStatus.value = ConnectionStatus.CONNECTED
                _events.tryEmit(
                    StreamEvent.StatusChanged(
                        PlatformType.YOUTUBE,
                        ConnectionStatus.CONNECTED,
                        "Conectado a @${rawInput.removePrefix("@")} (esperando directo)"
                    )
                )

                // Poll every 25s to detect when user goes live
                delay(25_000L)
                continue
            }

            val videoId = metadata.videoId
            Log.d(TAG, "YouTube live stream detected: $videoId (viewers=${metadata.viewerCount}). Obtaining InnerTube session...")
            val session = fetchLiveChatSession(videoId)
            if (session == null) {
                Log.w(TAG, "Failed to obtain InnerTube live chat session for video: $videoId. Retrying in 10s...")
                delay(10_000L)
                continue
            }

            val (apiKey, initialContinuation) = session
            Log.i(TAG, "InnerTube live chat session ready for video: $videoId. Polling chat...")
            _connectionStatus.value = ConnectionStatus.CONNECTED
            _events.tryEmit(StreamEvent.StatusChanged(PlatformType.YOUTUBE, ConnectionStatus.CONNECTED))
            reconnectAttempt = 0

            val health = StreamHealth(
                platform = PlatformType.YOUTUBE,
                isLive = true,
                viewerCount = metadata.viewerCount,
                startedAt = metadata.startedAt,
                channelName = rawInput,
                title = metadata.title ?: "Transmisión en directo ($videoId)",
                previewUrl = "https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1"
            )
            _health.value = health
            _events.tryEmit(StreamEvent.HealthUpdate(health))

            var continuation: String? = initialContinuation
            var lastHealthRefreshMs = System.currentTimeMillis()
            var currentMeta = metadata

            while (isActive && !isUserDisconnect && continuation != null) {
                try {
                    val (nextContinuation, waitMs) = pollInnerTubeMessages(apiKey, continuation, rawInput)
                    continuation = nextContinuation

                    // Refresh viewers and status periodically every 35s
                    if (System.currentTimeMillis() - lastHealthRefreshMs > 35_000L) {
                        lastHealthRefreshMs = System.currentTimeMillis()
                        scope.launch {
                            val fresh = fetchLiveMetadata("https://www.youtube.com/watch?v=$videoId")
                            if (fresh != null && fresh.isLive) {
                                currentMeta = fresh
                                val updatedHealth = StreamHealth(
                                    platform = PlatformType.YOUTUBE,
                                    isLive = true,
                                    viewerCount = fresh.viewerCount,
                                    startedAt = fresh.startedAt ?: currentMeta.startedAt,
                                    channelName = rawInput,
                                    title = fresh.title ?: currentMeta.title ?: "Transmisión en directo ($videoId)",
                                    previewUrl = "https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1"
                                )
                                _health.value = updatedHealth
                                _events.tryEmit(StreamEvent.HealthUpdate(updatedHealth))
                            }
                        }
                    }

                    val safeDelay = (waitMs ?: 2500L).coerceIn(1200L, 5000L)
                    delay(safeDelay)
                } catch (e: Exception) {
                    if (!isActive || isUserDisconnect) break
                    Log.w(TAG, "Error in YouTube poll: ${e.message}")
                    _connectionStatus.value = ConnectionStatus.RECONNECTING
                    delay(3000)
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                }
            }

            Log.i(TAG, "YouTube stream $videoId ended or disconnected. Re-checking channel...")
        }
    }

    private fun resolveTargetUrl(input: String): String {
        val clean = input.trim()
        return when {
            VIDEO_ID_REGEX.matches(clean) -> "https://www.youtube.com/watch?v=$clean"
            clean.contains("youtu.be/") -> {
                val id = clean.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                "https://www.youtube.com/watch?v=$id"
            }
            clean.contains("watch?v=") -> {
                val id = clean.substringAfter("watch?v=").substringBefore("&")
                "https://www.youtube.com/watch?v=$id"
            }
            clean.contains("/live/") -> {
                val id = clean.substringAfter("/live/").substringBefore("?").substringBefore("&")
                "https://www.youtube.com/watch?v=$id"
            }
            clean.startsWith("UC") && clean.length == 24 -> {
                "https://www.youtube.com/channel/$clean/live"
            }
            else -> {
                val handle = if (clean.startsWith("@")) clean else "@$clean"
                "https://www.youtube.com/$handle/live"
            }
        }
    }

    private suspend fun fetchLiveMetadata(targetUrl: String): YouTubeLiveMetadata? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "es-ES,es;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext null

                // Determine canonical URL or video ID
                val canonicalUrl = Regex("<link\\s+rel=[\"']canonical[\"']\\s+href=[\"']([^\"']+)[\"']").find(body)?.groupValues?.get(1)
                    ?: Regex("\"canonicalUrl\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)

                val isChannelEndpoint = targetUrl.contains("/live") || targetUrl.contains("/@") || targetUrl.contains("/channel/")
                val isCanonicalWatch = canonicalUrl != null && canonicalUrl.contains("watch?v=")

                val videoId = if (isCanonicalWatch) {
                    Regex("watch\\?v=([a-zA-Z0-9_-]{11})").find(canonicalUrl!!)?.groupValues?.get(1)
                } else if (!isChannelEndpoint) {
                    // Direct video or watch URL passed by user
                    Regex("/watch\\?v=([a-zA-Z0-9_-]{11})").find(targetUrl)?.groupValues?.get(1)
                        ?: Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\"").find(body)?.groupValues?.get(1)
                } else {
                    // Channel is offline (canonical URL is /channel/UC... or /@...)
                    null
                }

                if (videoId.isNullOrBlank()) {
                    // Channel is offline / no active live broadcast
                    return@withContext YouTubeLiveMetadata(
                        videoId = "",
                        title = null,
                        viewerCount = 0L,
                        startedAt = null,
                        isLive = false
                    )
                }

                // Check if this video is truly live or upcoming/ended
                val isLiveBadge = body.contains("\"isLive\":true") ||
                                  body.contains("\"isLiveContent\":true") ||
                                  body.contains("liveStreamability") ||
                                  isCanonicalWatch

                // 2. Title
                val title = Regex("<title>(.+?)</title>").find(body)?.groupValues?.get(1)
                    ?.removeSuffix(" - YouTube")?.trim()

                // 3. Viewers (concurrent live viewers)
                val viewersRaw = Regex("\"originalViewCount\":\"(\\d+)\"").find(body)?.groupValues?.get(1)
                    ?: Regex("\"viewCount\":\\{\"videoViewCountRenderer\":\\{\"viewCount\":\\{\"runs\":\\[\\{\"text\":\"([0-9,.]+)\"\\}").find(body)?.groupValues?.get(1)
                    ?: Regex("\"viewCountText\":\\{\"runs\":\\[\\{\"text\":\"([0-9,.]+)\"\\}").find(body)?.groupValues?.get(1)
                val viewerCount = viewersRaw?.replace(",", "")?.replace(".", "")?.toLongOrNull() ?: 0L

                // 4. Start Time (ISO-8601 to epoch millis)
                val startIso = Regex("\"startTimestamp\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                    ?: Regex("itemprop=\"startDate\"\\s+content=\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                val startedAt = startIso?.let { parseIsoTimestamp(it) }

                return@withContext YouTubeLiveMetadata(
                    videoId = videoId,
                    title = title,
                    viewerCount = viewerCount,
                    startedAt = startedAt,
                    isLive = isLiveBadge
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching YouTube live metadata: ${e.message}")
        }
        null
    }

    private fun parseIsoTimestamp(isoString: String): Long? {
        return try {
            val clean = if (isoString.contains(".")) {
                isoString.substringBefore(".")
            } else {
                isoString.substringBefore("+").substringBefore("Z")
            }
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            format.parse(clean)?.time
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchLiveChatSession(videoId: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val chatUrl = "https://www.youtube.com/live_chat?v=$videoId"
            val request = Request.Builder()
                .url(chatUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "SOCS=CAESEwgDEgk2NDIyMzg1NDQaAmVuIAEaBgiA_LyaBg; CONSENT=YES+cb.20230531-04-p0.es+FX+999")
                .build()

            ytHttpClient.newCall(request).execute().use { response ->
                val html = response.body?.string() ?: return@withContext null

                val apiKeyMatch = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"").find(html)
                val contMatch = Regex("\"continuation\":\"([^\"]+)\"").find(html)

                val apiKey = apiKeyMatch?.groupValues?.get(1)
                val continuation = contMatch?.groupValues?.get(1)

                if (apiKey != null && continuation != null) {
                    return@withContext Pair(apiKey, continuation)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch live chat session: ${e.message}")
        }
        null
    }

    private suspend fun pollInnerTubeMessages(
        apiKey: String,
        continuation: String,
        channelName: String
    ): Pair<String?, Long?> = withContext(Dispatchers.IO) {
        val reqUrl = "https://www.youtube.com/youtubei/v1/live_chat/get_live_chat?key=$apiKey"
        val payload = json.encodeToString(YouTubeInnerTubeRequest(continuation = continuation))

        val request = Request.Builder()
            .url(reqUrl)
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        ytHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext Pair(null, null)
            val body = response.body?.string() ?: return@withContext Pair(null, null)

            val chatResponse = json.decodeFromString<YouTubeGetLiveChatResponse>(body)
            val liveChatCont = chatResponse.continuationContents?.liveChatContinuation

            liveChatCont?.actions?.forEach { action ->
                val textRenderer = action.addChatItemAction?.item?.liveChatTextMessageRenderer
                if (textRenderer != null) {
                    val msgId = textRenderer.id ?: UUID.randomUUID().toString()
                    if (seenMessageIds.add(msgId)) {
                        val author = textRenderer.authorName?.simpleText ?: "YouTubeUser"
                        val emotes = mutableListOf<Emote>()
                        val textBuilder = StringBuilder()
                        textRenderer.message?.runs?.forEach { run ->
                            if (run.text != null) {
                                textBuilder.append(run.text)
                            } else if (run.emoji != null) {
                                val code = run.emoji.shortcuts?.firstOrNull() ?: run.emoji.emojiId ?: "emoji"
                                val emoteUrl = run.emoji.image?.thumbnails?.firstOrNull()?.url
                                if (!emoteUrl.isNullOrBlank()) {
                                    val cleanUrl = if (emoteUrl.startsWith("//")) "https:$emoteUrl" else emoteUrl
                                    val emoteId = run.emoji.emojiId ?: "yt-emoji-${emotes.size}"
                                    if (emotes.none { it.id == emoteId }) {
                                        emotes.add(Emote(id = emoteId, code = code, imageUrl = cleanUrl))
                                    }
                                    textBuilder.append(code)
                                }
                            }
                        }
                        val text = textBuilder.toString()
                        val timestamp = (textRenderer.timestampUsec?.toLongOrNull()?.div(1000))
                            ?: System.currentTimeMillis()

                        val badges = mutableListOf<UserBadge>()
                        textRenderer.authorBadges?.forEach { b ->
                            val badgeRenderer = b.liveChatAuthorBadgeRenderer
                            val tooltip = badgeRenderer?.tooltip
                            val customUrl = badgeRenderer?.customThumbnail?.thumbnails?.firstOrNull()?.url
                                ?.let { if (it.startsWith("//")) "https:$it" else it }
                            val iconType = badgeRenderer?.icon?.iconType

                            val badgeUrl = customUrl ?: when (iconType) {
                                "OWNER" -> "https://www.gstatic.com/youtube/img/creator/verified_badge.png"
                                "MODERATOR" -> "https://www.gstatic.com/youtube/img/creator/mod_badge.png"
                                "VERIFIED" -> "https://www.gstatic.com/youtube/img/creator/verified_badge.png"
                                else -> null
                            }

                            if (!tooltip.isNullOrBlank()) {
                                badges.add(UserBadge(id = tooltip, name = tooltip, imageUrl = badgeUrl))
                            }
                        }

                        val chatMessage = ChatMessage(
                            id = msgId,
                            platform = PlatformType.YOUTUBE,
                            channelName = channelName,
                            authorName = author,
                            authorColorHex = PlatformType.YOUTUBE.brandColorHex,
                            messageText = text,
                            badges = badges,
                            emotes = emotes,
                            timestamp = timestamp
                        )
                        _events.tryEmit(StreamEvent.Message(chatMessage))
                    }
                }

                // SuperChat
                val paidRenderer = action.addChatItemAction?.item?.liveChatPaidMessageRenderer
                if (paidRenderer != null) {
                    val msgId = paidRenderer.id ?: UUID.randomUUID().toString()
                    if (seenMessageIds.add(msgId)) {
                        val author = paidRenderer.authorName?.simpleText ?: "YouTubeUser"
                        val text = paidRenderer.message?.runs?.joinToString("") { it.text.orEmpty() }.orEmpty()
                        val amount = paidRenderer.purchaseAmountText?.simpleText ?: "SuperChat"
                        val timestamp = (paidRenderer.timestampUsec?.toLongOrNull()?.div(1000))
                            ?: System.currentTimeMillis()

                        val chatMessage = ChatMessage(
                            id = msgId,
                            platform = PlatformType.YOUTUBE,
                            channelName = channelName,
                            authorName = author,
                            authorColorHex = PlatformType.YOUTUBE.brandColorHex,
                            messageText = if (text.isNotBlank()) "[$amount] $text" else "[$amount]",
                            timestamp = timestamp,
                            isHighlight = true
                        )
                        _events.tryEmit(StreamEvent.Message(chatMessage))

                        val notification = StreamNotification(
                            id = msgId,
                            platform = PlatformType.YOUTUBE,
                            type = NotificationType.DONATION,
                            user = author,
                            title = "Super Chat",
                            description = "$author envió $amount: $text",
                            timestamp = timestamp
                        )
                        _events.tryEmit(StreamEvent.Notification(notification))
                    }
                }
            }

            trimSeenCache()

            val nextContData = liveChatCont?.continuations?.firstOrNull()
            val nextCont = nextContData?.invalidationContinuationData?.continuation
                ?: nextContData?.timedContinuationData?.continuation
            val timeoutMs = nextContData?.invalidationContinuationData?.timeoutMs
                ?: nextContData?.timedContinuationData?.timeoutMs

            return@withContext Pair(nextCont, timeoutMs)
        }
    }

    private fun trimSeenCache() {
        if (seenMessageIds.size > 1000) {
            val iterator = seenMessageIds.iterator()
            var count = 0
            while (iterator.hasNext() && count < 500) {
                iterator.next()
                iterator.remove()
                count++
            }
        }
    }
}
