package com.streammultichat.platform.starvios

import android.util.Log
import com.streammultichat.core.chat.StreamConnector
import com.streammultichat.core.model.ChatMessage
import com.streammultichat.core.model.ConnectionStatus
import com.streammultichat.core.model.PlatformType
import com.streammultichat.core.model.StreamChannelConfig
import com.streammultichat.core.model.StreamEvent
import com.streammultichat.core.model.StreamHealth
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
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Locale
import java.util.TimeZone

class StarviosConnector(
    private val httpClient: OkHttpClient = NetworkClientProvider.httpClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : StreamConnector {

    override val platform: PlatformType = PlatformType.STARVIOS

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
    }

    private var pollJob: Job? = null
    private var currentConfig: StreamChannelConfig? = null
    private var isUserDisconnect = false
    private val backoffPolicy = BackoffPolicy()
    private var reconnectAttempt = 0

    // Seen message ID cache to prevent duplicates
    private val seenMessageIds = Collections.synchronizedSet(LinkedHashSet<String>())

    companion object {
        private const val TAG = "StarviosConnector"
        private const val SUPABASE_URL = "https://naiijcvngtkkocligwvk.supabase.co"
        private const val ANON_KEY = "sb_publishable_4lF1QyQhWOUE1Pw3rWBwEw_bVmkAxm6"
        private const val POLL_INTERVAL_MS = 1200L
        private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
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

    private suspend fun CoroutineScope.startPollingLoop(config: StreamChannelConfig) {
        while (isActive && !isUserDisconnect) {
            val rawChannel = config.channelName.trim()
                .removePrefix("https://starvios.com/")
                .removePrefix("https://www.starvios.com/")
                .removePrefix("@")
                .trim()

            if (rawChannel.isBlank()) {
                _connectionStatus.value = ConnectionStatus.ERROR
                _events.tryEmit(
                    StreamEvent.StatusChanged(PlatformType.STARVIOS, ConnectionStatus.ERROR, "Nombre de canal inválido")
                )
                return
            }

            Log.d(TAG, "Resolving Starvios stream for: '$rawChannel'")
            val streamId = resolveStreamId(rawChannel)
            if (streamId == null) {
                Log.e(TAG, "Could not resolve stream for '$rawChannel'")
                _connectionStatus.value = ConnectionStatus.ERROR
                _events.tryEmit(
                    StreamEvent.StatusChanged(PlatformType.STARVIOS, ConnectionStatus.ERROR, "No se encontró transmisión activa para '$rawChannel'")
                )
                reconnectAttempt++
                val delayMs = backoffPolicy.calculateDelay(reconnectAttempt)
                delay(delayMs)
                continue
            }

            Log.d(TAG, "Starvios stream resolved: $streamId")
            _connectionStatus.value = ConnectionStatus.CONNECTED
            _events.tryEmit(StreamEvent.StatusChanged(PlatformType.STARVIOS, ConnectionStatus.CONNECTED))
            reconnectAttempt = 0

            // Initial fetch to load recent messages
            var lastCreatedAt: String? = fetchInitialMessages(streamId, rawChannel)

            // Incremental polling loop
            while (isActive && !isUserDisconnect) {
                delay(POLL_INTERVAL_MS)
                try {
                    val newLast = fetchIncrementalMessages(streamId, rawChannel, lastCreatedAt)
                    if (newLast != null) {
                        lastCreatedAt = newLast
                    }
                } catch (e: Exception) {
                    if (!isActive || isUserDisconnect) break
                    Log.w(TAG, "Error polling Starvios messages: ${e.message}")
                    _connectionStatus.value = ConnectionStatus.RECONNECTING
                    delay(3000)
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                }
            }
        }
    }

    private suspend fun resolveStreamId(identifier: String): String? = withContext(Dispatchers.IO) {
        // Direct UUID
        if (UUID_REGEX.matches(identifier)) {
            return@withContext identifier
        }

        try {
            val encodedUser = URLEncoder.encode(identifier.lowercase(), "UTF-8")
            val url = "$SUPABASE_URL/rest/v1/live_streams?select=id,status,title,started_at,created_at,profiles!inner(username)&profiles.username=ilike.$encodedUser&order=created_at.desc&limit=1"

            val request = Request.Builder()
                .url(url)
                .header("apikey", ANON_KEY)
                .header("Authorization", "Bearer $ANON_KEY")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    val streams = json.decodeFromString<List<StarviosStream>>(body)
                    val stream = streams.firstOrNull()
                    if (stream != null) {
                        val parsedStart = stream.startedAt?.let { parseIsoTimestamp(it) }
                        val isLive = stream.status.equals("active", ignoreCase = true) || stream.status.equals("live", ignoreCase = true)
                        val health = StreamHealth(
                            platform = PlatformType.STARVIOS,
                            isLive = isLive,
                            viewerCount = if (isLive) 1L else 0L,
                            startedAt = if (isLive) parsedStart else null,
                            title = stream.title ?: "Transmisión de Starvios (@$identifier)",
                            channelName = identifier,
                            previewUrl = "https://starvios.com/$identifier"
                        )
                        _health.value = health
                        _events.tryEmit(StreamEvent.HealthUpdate(health))
                        return@withContext stream.id
                    } else {
                        val health = StreamHealth(
                            platform = PlatformType.STARVIOS,
                            isLive = false,
                            viewerCount = 0L,
                            startedAt = null,
                            title = "Canal sin transmisión activa (@$identifier)",
                            channelName = identifier,
                            previewUrl = "https://starvios.com/$identifier"
                        )
                        _health.value = health
                        _events.tryEmit(StreamEvent.HealthUpdate(health))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve Starvios streamId: ${e.message}")
        }
        null
    }

    private suspend fun fetchInitialMessages(streamId: String, channelName: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "$SUPABASE_URL/rest/v1/live_chat_messages?stream_id=eq.$streamId&select=id,stream_id,body,created_at,profiles(username,display_name,chat_color)&order=created_at.desc&limit=15"
            val request = Request.Builder()
                .url(url)
                .header("apikey", ANON_KEY)
                .header("Authorization", "Bearer $ANON_KEY")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    val messages = json.decodeFromString<List<StarviosChatMessage>>(body)
                    if (messages.isEmpty()) return@withContext null

                    val latestTime = messages.first().createdAt

                    // Deliver messages in chronological order
                    messages.reversed().forEach { msg ->
                        if (seenMessageIds.add(msg.id)) {
                            emitMessage(msg, channelName)
                        }
                    }

                    // Keep cache bounded
                    trimSeenCache()
                    return@withContext latestTime
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch initial Starvios messages: ${e.message}")
        }
        null
    }

    private suspend fun fetchIncrementalMessages(streamId: String, channelName: String, lastCreatedAt: String?): String? = withContext(Dispatchers.IO) {
        try {
            val url = if (lastCreatedAt != null) {
                val encodedTime = URLEncoder.encode(lastCreatedAt, "UTF-8")
                "$SUPABASE_URL/rest/v1/live_chat_messages?stream_id=eq.$streamId&created_at=gt.$encodedTime&select=id,stream_id,body,created_at,profiles(username,display_name,chat_color)&order=created_at.asc"
            } else {
                "$SUPABASE_URL/rest/v1/live_chat_messages?stream_id=eq.$streamId&select=id,stream_id,body,created_at,profiles(username,display_name,chat_color)&order=created_at.desc&limit=10"
            }

            val request = Request.Builder()
                .url(url)
                .header("apikey", ANON_KEY)
                .header("Authorization", "Bearer $ANON_KEY")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    val messages = json.decodeFromString<List<StarviosChatMessage>>(body)
                    if (messages.isEmpty()) return@withContext lastCreatedAt

                    var newestTime = lastCreatedAt
                    messages.forEach { msg ->
                        if (seenMessageIds.add(msg.id)) {
                            emitMessage(msg, channelName)
                            newestTime = msg.createdAt
                        }
                    }

                    trimSeenCache()
                    return@withContext newestTime
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Incremental poll failed: ${e.message}")
        }
        lastCreatedAt
    }

    private fun emitMessage(msg: StarviosChatMessage, channelName: String) {
        val authorName = msg.profiles?.displayName
            ?: msg.profiles?.username
            ?: "StarviosUser"

        val colorHex = msg.profiles?.chatColor?.let { hex ->
            try {
                java.lang.Long.parseLong(hex.removePrefix("#"), 16) or 0xFF000000
            } catch (_: Exception) { null }
        } ?: PlatformType.STARVIOS.brandColorHex

        val timestamp = parseIsoTimestamp(msg.createdAt)

        val chatMessage = ChatMessage(
            id = msg.id,
            platform = PlatformType.STARVIOS,
            channelName = channelName,
            authorName = authorName,
            authorColorHex = colorHex,
            messageText = msg.body,
            timestamp = timestamp
        )

        _events.tryEmit(StreamEvent.Message(chatMessage))
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

    private fun parseIsoTimestamp(isoString: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            // Strip fractional seconds if any
            val clean = if (isoString.contains(".")) {
                isoString.substringBefore(".")
            } else {
                isoString.substringBefore("+").substringBefore("Z")
            }
            format.parse(clean)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
